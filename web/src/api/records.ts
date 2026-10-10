import { Hono, type Context } from 'hono';
import { bizDate, isValidDate } from '../bizdate';
import { getTask, type CompletionRow } from '../db';
import type { AppEnv } from '../env';
import { apiError } from '../errors';
import { occursOn } from '../schedule';
import { presentCompletion, UNDO_WINDOW_MS } from './board';
import { cleanText, readBody } from './util';

const MAX_REASON = 100;
const SOURCES = ['web', 'app', 'widget'] as const;

export const records = new Hono<AppEnv>();

function findCompletion(db: D1Database, taskId: string, date: string, slot: string) {
  return db
    .prepare('SELECT * FROM completions WHERE task_id = ? AND biz_date = ? AND slot = ?')
    .bind(taskId, date, slot)
    .first<CompletionRow>();
}

/**
 * 登记（done）或跳过（skipped）。幂等：同一实例已有有效登记时原样返回 200；
 * 撤销过的实例复用原来那一行。并发提交由 UNIQUE(task_id, biz_date, slot) 保证只有一条。
 */
async function record(c: Context<AppEnv>, result: 'done' | 'skipped') {
  const body = await readBody(c);
  if (!body) return apiError(c, 400, 'bad_request', '请求体应为 JSON 对象');
  const { task_id: taskId, date, slot } = body;
  if (typeof taskId !== 'string' || typeof slot !== 'string') return apiError(c, 400, 'bad_request', '缺少 task_id 或 slot');
  if (!isValidDate(date)) return apiError(c, 400, 'bad_request', 'date 应为 YYYY-MM-DD');
  const now = Date.now();
  if (date > bizDate(now)) return apiError(c, 400, 'bad_request', '不能登记以后的日子');

  let reason = '';
  if (result === 'skipped') {
    reason = cleanText(body.reason, MAX_REASON) ?? '';
    if (!reason) return apiError(c, 400, 'bad_request', '跳过需要填写原因');
  }
  const source = body.source ?? (c.get('actor').kind === 'web' ? 'web' : 'app');
  if (!SOURCES.includes(source as (typeof SOURCES)[number])) return apiError(c, 400, 'bad_request', 'source 只能是 web / app / widget');

  const task = await getTask(c.env.DB, taskId);
  if (!task) return apiError(c, 404, 'not_found', '没有这个事项');
  if (task.status !== 'active') return apiError(c, 409, 'task_inactive', '这个事项已暂停或归档');
  if (!task.schedule.times.includes(slot) || !occursOn(task.schedule, date)) {
    return apiError(c, 400, 'not_scheduled', '这一天这个时间点不需要做');
  }

  const actor = c.get('actor');
  const res = await c.env.DB.prepare(
    `INSERT INTO completions (id, task_id, biz_date, slot, result, reason, recorded_by, source, recorded_at, undone_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
     ON CONFLICT (task_id, biz_date, slot) DO UPDATE SET
       result = excluded.result, reason = excluded.reason, recorded_by = excluded.recorded_by,
       source = excluded.source, recorded_at = excluded.recorded_at, undone_at = NULL
     WHERE completions.undone_at IS NOT NULL`,
  )
    .bind(crypto.randomUUID(), taskId, date, slot, result, reason, actor.name, source, now)
    .run();
  const row = (await findCompletion(c.env.DB, taskId, date, slot))!;
  return c.json(
    { created: res.meta.changes > 0, task_id: taskId, date, slot, completion: presentCompletion(row, actor, now) },
    res.meta.changes > 0 ? 201 : 200,
  );
}

records.post('/checkin', (c) => record(c, 'done'));
records.post('/skip', (c) => record(c, 'skipped'));

/** 撤销自己 2 分钟内的登记（软删，保留审计）。 */
records.post('/undo', async (c) => {
  const body = await readBody(c);
  const { task_id: taskId, date, slot } = body ?? {};
  if (typeof taskId !== 'string' || typeof slot !== 'string' || !isValidDate(date)) {
    return apiError(c, 400, 'bad_request', '需要 task_id、date、slot');
  }
  const row = await findCompletion(c.env.DB, taskId, date, slot);
  if (!row || row.undone_at !== null) return apiError(c, 404, 'not_found', '没有可撤销的登记');
  const now = Date.now();
  if (row.recorded_by !== c.get('actor').name) return apiError(c, 403, 'forbidden', '只能撤销自己的登记');
  if (now - row.recorded_at >= UNDO_WINDOW_MS) return apiError(c, 409, 'undo_expired', '超过 2 分钟，不能撤销了');
  await c.env.DB.prepare('UPDATE completions SET undone_at = ? WHERE id = ? AND undone_at IS NULL').bind(now, row.id).run();
  return c.json({ ok: true, task_id: taskId, date, slot });
});
