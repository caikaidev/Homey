import { addDays, daysBetween, isValidDate } from './bizdate';

/**
 * 排期。`times` 从一开始就是数组，P2 再加 weekdays / end，旧数据不受影响。
 * - daily：从 start 起每天
 * - interval_days：从 start 起每 every 天一次（隔天 = every 2），以 start 为锚点
 */
export type Schedule =
  | { type: 'daily'; start: string; times: string[] }
  | { type: 'interval_days'; every: number; start: string; times: string[] };

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
const MAX_TIMES = 6;
const MAX_EVERY = 30;

/** 校验并规范化客户端传来的排期；不合法时返回原因。 */
export function parseSchedule(input: unknown): Schedule | string {
  if (!input || typeof input !== 'object') return '排期格式不对';
  const s = input as Record<string, unknown>;
  if (!isValidDate(s.start)) return '开始日期不对（应为 YYYY-MM-DD）';
  if (!Array.isArray(s.times) || s.times.length === 0) return '至少要有一个时间点';
  if (s.times.length > MAX_TIMES) return `一天最多 ${MAX_TIMES} 个时间点`;
  if (!s.times.every((t) => typeof t === 'string' && TIME_RE.test(t))) return '时间点格式应为 HH:MM';
  const times = [...new Set(s.times as string[])].sort();
  if (s.type === 'daily') return { type: 'daily', start: s.start, times };
  if (s.type === 'interval_days') {
    const every = s.every;
    if (typeof every !== 'number' || !Number.isInteger(every) || every < 2 || every > MAX_EVERY) {
      return `间隔天数应为 2 到 ${MAX_EVERY} 的整数`;
    }
    return { type: 'interval_days', every, start: s.start, times };
  }
  return '排期类型只支持 daily 或 interval_days';
}

/** 排期在某业务日是否该做。 */
export function occursOn(s: Schedule, date: string): boolean {
  const diff = daysBetween(s.start, date);
  if (diff < 0) return false;
  if (s.type === 'daily') return true;
  return diff % s.every === 0;
}

/** 人话描述，如“隔天 08:00”“每 3 天 08:00、19:00”。 */
export function describeSchedule(s: Schedule): string {
  const when = s.type === 'daily' ? '每天' : s.every === 2 ? '隔天' : `每 ${s.every} 天`;
  const [, m, d] = s.start.split('-');
  return `${when} ${s.times.join('、')}（${+m!}月${+d!}日起）`;
}

/** 从 from 起 days 天里每天的时间点（不该做的日子为空数组）。 */
export function previewSchedule(s: Schedule, from: string, days = 7): { date: string; times: string[] }[] {
  return Array.from({ length: days }, (_, i) => {
    const date = addDays(from, i);
    return { date, times: occursOn(s, date) ? s.times : [] };
  });
}
