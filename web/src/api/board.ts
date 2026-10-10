import { Hono } from 'hono';
import { addDays, bizDate, isValidDate, WEEKDAY_NAMES, weekday } from '../bizdate';
import { getSettings, listTasks, type CompletionRow, type Task } from '../db';
import type { Actor, AppEnv } from '../env';
import { apiError } from '../errors';
import { occursOn } from '../schedule';

/** 登记后多久内可以撤销自己的记录。 */
export const UNDO_WINDOW_MS = 2 * 60 * 1000;
const MAX_HISTORY_DAYS = 60;

export const board = new Hono<AppEnv>();

export function presentCompletion(row: CompletionRow, actor: Actor, now: number) {
  return {
    id: row.id,
    result: row.result,
    reason: row.reason,
    recorded_by: row.recorded_by,
    recorded_at: row.recorded_at,
    source: row.source,
    can_undo: row.recorded_by === actor.name && now - row.recorded_at < UNDO_WINDOW_MS,
  };
}

async function completionsBetween(db: D1Database, from: string, to: string): Promise<CompletionRow[]> {
  const { results } = await db
    .prepare('SELECT * FROM completions WHERE biz_date BETWEEN ? AND ? AND undone_at IS NULL')
    .bind(from, to)
    .all<CompletionRow>();
  return results;
}

/**
 * 某业务日每个事项的实例：按当前排期算出的时间点，再加上当天已登记但排期里已没有的时间点
 * （改过排期时保留已有登记）。
 */
function instancesOn(date: string, tasks: Task[], completions: CompletionRow[]) {
  const byKey = new Map(completions.filter((c) => c.biz_date === date).map((c) => [`${c.task_id}|${c.slot}`, c]));
  const out: { task: Task; slot: string; completion: CompletionRow | null }[] = [];
  for (const task of tasks) {
    const slots = new Set(occursOn(task.schedule, date) ? task.schedule.times : []);
    for (const c of byKey.values()) if (c.task_id === task.id) slots.add(c.slot);
    for (const slot of slots) out.push({ task, slot, completion: byKey.get(`${task.id}|${slot}`) ?? null });
  }
  return out;
}

board.get('/board', async (c) => {
  const now = Date.now();
  const date = c.req.query('date') ?? bizDate(now);
  if (!isValidDate(date)) return apiError(c, 400, 'bad_request', 'date 应为 YYYY-MM-DD');
  const tomorrow = addDays(date, 1);
  const actor = c.get('actor');
  const [settings, tasks, completions] = await Promise.all([
    getSettings(c.env.DB),
    listTasks(c.env.DB, ['active']),
    completionsBetween(c.env.DB, date, date),
  ]);

  const today = instancesOn(date, tasks, completions)
    .map(({ task, slot, completion }) => ({
      task_id: task.id,
      title: task.title,
      kind: task.kind,
      note: task.note,
      priority: task.priority,
      slot,
      status: completion ? completion.result : ('pending' as const),
      completion: completion ? presentCompletion(completion, actor, now) : null,
    }))
    // 未登记在前；同组按时间、优先级
    .sort((a, b) => +(a.status !== 'pending') - +(b.status !== 'pending') || a.slot.localeCompare(b.slot) || b.priority - a.priority);

  const next = instancesOn(tomorrow, tasks, [])
    .map(({ task, slot }) => ({ task_id: task.id, title: task.title, kind: task.kind, slot }))
    .sort((a, b) => a.slot.localeCompare(b.slot));

  return c.json({
    date,
    weekday: WEEKDAY_NAMES[weekday(date)],
    nickname: settings.child_nickname,
    pending_count: today.filter((i) => i.status === 'pending').length,
    today,
    tomorrow: { date: tomorrow, items: next },
    serverTime: now,
  });
});

/**
 * 最近 N 天（含今天）按日分组的记录。按当前排期补出没登记的实例：今天的标“pending”，
 * 以前的标“missed”。只给进行中的事项补，且不早于事项创建那天。
 */
board.get('/history', async (c) => {
  const now = Date.now();
  const daysRaw = Number(c.req.query('days') ?? 14);
  if (!Number.isInteger(daysRaw) || daysRaw < 1 || daysRaw > MAX_HISTORY_DAYS) {
    return apiError(c, 400, 'bad_request', `days 应为 1 到 ${MAX_HISTORY_DAYS} 的整数`);
  }
  const today = bizDate(now);
  const from = addDays(today, -(daysRaw - 1));
  const [allTasks, completions] = await Promise.all([listTasks(c.env.DB), completionsBetween(c.env.DB, from, today)]);
  const taskById = new Map(allTasks.map((t) => [t.id, t]));
  const active = allTasks.filter((t) => t.status === 'active');

  const days = [];
  for (let i = 0; i < daysRaw; i++) {
    const date = addDays(today, -i);
    const items: {
      task_id: string;
      title: string;
      slot: string;
      status: 'done' | 'skipped' | 'missed' | 'pending';
      reason: string;
      recorded_by: string | null;
      recorded_at: number | null;
      source: string | null;
    }[] = [];
    const seen = new Set<string>();
    for (const row of completions) {
      if (row.biz_date !== date) continue;
      seen.add(`${row.task_id}|${row.slot}`);
      items.push({
        task_id: row.task_id,
        title: taskById.get(row.task_id)?.title ?? '（已删除）',
        slot: row.slot,
        status: row.result,
        reason: row.reason,
        recorded_by: row.recorded_by,
        recorded_at: row.recorded_at,
        source: row.source,
      });
    }
    for (const task of active) {
      if (date < bizDate(task.created_at) || !occursOn(task.schedule, date)) continue;
      for (const slot of task.schedule.times) {
        if (seen.has(`${task.id}|${slot}`)) continue;
        items.push({
          task_id: task.id,
          title: task.title,
          slot,
          status: date === today ? 'pending' : 'missed',
          reason: '',
          recorded_by: null,
          recorded_at: null,
          source: null,
        });
      }
    }
    items.sort((a, b) => a.slot.localeCompare(b.slot) || a.title.localeCompare(b.title));
    days.push({ date, weekday: WEEKDAY_NAMES[weekday(date)], items });
  }
  return c.json({ days });
});
