import { Hono } from 'hono';
import { bizDate } from '../bizdate';
import { getTask, listTasks, TASK_KINDS, TASK_STATUSES, type Task, type TaskStatus } from '../db';
import type { AppEnv } from '../env';
import { apiError } from '../errors';
import { describeSchedule, parseSchedule, previewSchedule } from '../schedule';
import { cleanText, readBody, requireParent } from './util';

const MAX_TITLE = 40;
const MAX_NOTE = 200;

export const tasks = new Hono<AppEnv>();

/** 给客户端的事项：附带人话描述，免得两端各写一份。 */
export function presentTask(t: Task) {
  return { ...t, describe: describeSchedule(t.schedule) };
}

tasks.get('/', async (c) => {
  const q = c.req.query('status');
  const statuses = q ? (q.split(',').filter((s) => TASK_STATUSES.includes(s as TaskStatus)) as TaskStatus[]) : TASK_STATUSES;
  if (statuses.length === 0) return apiError(c, 400, 'bad_request', 'status 只能是 active / paused / archived');
  return c.json({ tasks: (await listTasks(c.env.DB, statuses)).map(presentTask) });
});

tasks.get('/:id', async (c) => {
  const task = await getTask(c.env.DB, c.req.param('id'));
  if (!task) return apiError(c, 404, 'not_found', '没有这个事项');
  return c.json({ task: presentTask(task), preview: previewSchedule(task.schedule, bizDate(Date.now())) });
});

tasks.post('/', requireParent, async (c) => {
  const body = await readBody(c);
  if (!body) return apiError(c, 400, 'bad_request', '请求体应为 JSON 对象');
  const title = cleanText(body.title, MAX_TITLE);
  if (!title) return apiError(c, 400, 'bad_request', '请填写名称');
  const schedule = parseSchedule(body.schedule);
  if (typeof schedule === 'string') return apiError(c, 400, 'bad_schedule', schedule);
  const kind = body.kind === undefined ? 'supplement' : body.kind;
  if (!TASK_KINDS.includes(kind as (typeof TASK_KINDS)[number])) return apiError(c, 400, 'bad_request', 'kind 只能是 supplement / medicine');
  const priority = body.priority === undefined ? 0 : body.priority;
  if (typeof priority !== 'number' || !Number.isInteger(priority)) return apiError(c, 400, 'bad_request', 'priority 应为整数');
  const note = cleanText(body.note ?? '', MAX_NOTE) ?? '';

  const now = Date.now();
  const id = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO tasks (id, title, kind, schedule_json, priority, status, note, created_by, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, 'active', ?, ?, ?, ?)`,
  )
    .bind(id, title, kind, JSON.stringify(schedule), priority, note, c.get('actor').name, now, now)
    .run();
  return c.json({ task: presentTask((await getTask(c.env.DB, id))!) }, 201);
});

/** 改名称 / 排期 / 备注 / 优先级。改排期只影响以后，已有登记不动。 */
tasks.patch('/:id', requireParent, async (c) => {
  const task = await getTask(c.env.DB, c.req.param('id'));
  if (!task) return apiError(c, 404, 'not_found', '没有这个事项');
  const body = await readBody(c);
  if (!body) return apiError(c, 400, 'bad_request', '请求体应为 JSON 对象');

  const next = { ...task };
  if (body.title !== undefined) {
    const title = cleanText(body.title, MAX_TITLE);
    if (!title) return apiError(c, 400, 'bad_request', '名称不能为空');
    next.title = title;
  }
  if (body.schedule !== undefined) {
    const schedule = parseSchedule(body.schedule);
    if (typeof schedule === 'string') return apiError(c, 400, 'bad_schedule', schedule);
    next.schedule = schedule;
  }
  if (body.note !== undefined) {
    const note = cleanText(body.note, MAX_NOTE);
    if (note === null) return apiError(c, 400, 'bad_request', 'note 应为字符串');
    next.note = note;
  }
  if (body.priority !== undefined) {
    if (typeof body.priority !== 'number' || !Number.isInteger(body.priority)) return apiError(c, 400, 'bad_request', 'priority 应为整数');
    next.priority = body.priority;
  }
  await c.env.DB.prepare('UPDATE tasks SET title = ?, schedule_json = ?, note = ?, priority = ?, updated_at = ? WHERE id = ?')
    .bind(next.title, JSON.stringify(next.schedule), next.note, next.priority, Date.now(), task.id)
    .run();
  return c.json({ task: presentTask((await getTask(c.env.DB, task.id))!) });
});

/** 暂停 / 恢复 / 归档。恢复后隔天事项仍按原来的开始日期为锚点。 */
tasks.patch('/:id/status', requireParent, async (c) => {
  const body = await readBody(c);
  const status = body?.status;
  if (!TASK_STATUSES.includes(status as TaskStatus)) return apiError(c, 400, 'bad_request', 'status 只能是 active / paused / archived');
  const res = await c.env.DB.prepare('UPDATE tasks SET status = ?, updated_at = ? WHERE id = ?')
    .bind(status, Date.now(), c.req.param('id'))
    .run();
  if (res.meta.changes === 0) return apiError(c, 404, 'not_found', '没有这个事项');
  return c.json({ task: presentTask((await getTask(c.env.DB, c.req.param('id')))!) });
});
