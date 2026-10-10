import type { Schedule } from './schedule';

export type TaskStatus = 'active' | 'paused' | 'archived';
export const TASK_STATUSES: readonly TaskStatus[] = ['active', 'paused', 'archived'];
export const TASK_KINDS = ['supplement', 'medicine'] as const;

export interface TaskRow {
  id: string;
  title: string;
  kind: string;
  schedule_json: string;
  priority: number;
  status: TaskStatus;
  note: string;
  created_by: string;
  created_at: number;
  updated_at: number;
}

export interface Task extends Omit<TaskRow, 'schedule_json'> {
  schedule: Schedule;
}

export interface CompletionRow {
  id: string;
  task_id: string;
  biz_date: string;
  slot: string;
  result: 'done' | 'skipped';
  reason: string;
  recorded_by: string;
  source: string;
  recorded_at: number;
  undone_at: number | null;
}

export function toTask(row: TaskRow): Task {
  const { schedule_json, ...rest } = row;
  return { ...rest, schedule: JSON.parse(schedule_json) as Schedule };
}

export async function getTask(db: D1Database, id: string): Promise<Task | null> {
  const row = await db.prepare('SELECT * FROM tasks WHERE id = ?').bind(id).first<TaskRow>();
  return row ? toTask(row) : null;
}

export async function listTasks(db: D1Database, statuses: readonly TaskStatus[] = TASK_STATUSES): Promise<Task[]> {
  const marks = statuses.map(() => '?').join(',');
  const { results } = await db
    .prepare(`SELECT * FROM tasks WHERE status IN (${marks}) ORDER BY priority DESC, created_at`)
    .bind(...statuses)
    .all<TaskRow>();
  return results.map(toTask);
}

// ---- settings ----

export interface Members {
  /** 家长：Access 登录邮箱 → 显示名（如“爸爸”） */
  parents: { email: string; name: string }[];
  /** 照护人显示名，供 App 首次设置时选择“我是谁” */
  caregivers: string[];
}

export interface Settings {
  child_nickname: string;
  members: Members;
}

export const DEFAULT_SETTINGS: Settings = { child_nickname: '崽崽', members: { parents: [], caregivers: [] } };

export async function getSettings(db: D1Database): Promise<Settings> {
  const { results } = await db.prepare('SELECT key, value FROM settings').all<{ key: string; value: string }>();
  const map = new Map(results.map((r) => [r.key, r.value]));
  let members = DEFAULT_SETTINGS.members;
  try {
    if (map.has('members')) members = JSON.parse(map.get('members')!) as Members;
  } catch {
    // 坏数据按默认处理，设置页保存一次即可修复
  }
  return { child_nickname: map.get('child_nickname') || DEFAULT_SETTINGS.child_nickname, members };
}

export function putSetting(db: D1Database, key: string, value: string, now: number) {
  return db
    .prepare('INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at')
    .bind(key, value, now);
}
