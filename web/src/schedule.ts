import { addDays, daysBetween, isValidDate, weekday } from './bizdate';

/**
 * 排期。`times` 从一开始就是数组；P2 加了 weekdays 类型和可选的 end，旧数据不受影响。
 * - daily：从 start 起每天
 * - interval_days：从 start 起每 every 天一次（隔天 = every 2），以 start 为锚点
 * - weekdays：从 start 起每周的这几天（0 = 周日 … 6 = 周六）
 * - end（可选）：最后一天（含当天）
 */
interface Base {
  start: string;
  times: string[];
  end?: string;
}
export type Schedule =
  | (Base & { type: 'daily' })
  | (Base & { type: 'interval_days'; every: number })
  | (Base & { type: 'weekdays'; weekdays: number[] });

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
const MAX_TIMES = 6;
const MAX_EVERY = 30;
const WEEKDAY_CN = ['日', '一', '二', '三', '四', '五', '六'];

/** 校验并规范化客户端传来的排期；不合法时返回原因。 */
export function parseSchedule(input: unknown): Schedule | string {
  if (!input || typeof input !== 'object') return '排期格式不对';
  const s = input as Record<string, unknown>;
  if (!isValidDate(s.start)) return '开始日期不对（应为 YYYY-MM-DD）';
  if (!Array.isArray(s.times) || s.times.length === 0) return '至少要有一个时间点';
  if (s.times.length > MAX_TIMES) return `一天最多 ${MAX_TIMES} 个时间点`;
  if (!s.times.every((t) => typeof t === 'string' && TIME_RE.test(t))) return '时间点格式应为 HH:MM';
  const base: Base = { start: s.start, times: [...new Set(s.times as string[])].sort() };
  if (s.end !== undefined && s.end !== null && s.end !== '') {
    if (!isValidDate(s.end)) return '结束日期不对（应为 YYYY-MM-DD）';
    if (s.end < s.start) return '结束日期不能早于开始日期';
    base.end = s.end;
  }

  if (s.type === 'daily') return { type: 'daily', ...base };
  if (s.type === 'interval_days') {
    const every = s.every;
    if (typeof every !== 'number' || !Number.isInteger(every) || every < 2 || every > MAX_EVERY) {
      return `间隔天数应为 2 到 ${MAX_EVERY} 的整数`;
    }
    return { type: 'interval_days', every, ...base };
  }
  if (s.type === 'weekdays') {
    const days = s.weekdays;
    if (!Array.isArray(days) || days.length === 0) return '至少选一天';
    if (!days.every((d) => Number.isInteger(d) && d >= 0 && d <= 6)) return '星期应为 0（周日）到 6（周六）';
    return { type: 'weekdays', weekdays: [...new Set(days as number[])].sort(), ...base };
  }
  return '排期类型只支持 daily / interval_days / weekdays';
}

/** 排期在某业务日是否该做。 */
export function occursOn(s: Schedule, date: string): boolean {
  const diff = daysBetween(s.start, date);
  if (diff < 0) return false;
  if (s.end && date > s.end) return false;
  if (s.type === 'daily') return true;
  if (s.type === 'weekdays') return s.weekdays.includes(weekday(date));
  return diff % s.every === 0;
}

function md(date: string): string {
  const [, m, d] = date.split('-');
  return `${+m!}月${+d!}日`;
}

/** 人话描述，如“隔天 08:00（10月10日起）”“每周一、三、五 08:00（10月10日起，到10月12日）”。 */
export function describeSchedule(s: Schedule): string {
  let when: string;
  if (s.type === 'daily') when = '每天';
  else if (s.type === 'interval_days') when = s.every === 2 ? '隔天' : `每 ${s.every} 天`;
  else if (s.weekdays.length === 7) when = '每天';
  else if (s.weekdays.join() === '1,2,3,4,5') when = '工作日';
  else if (s.weekdays.join() === '0,6') when = '周末';
  else when = `每周${s.weekdays.map((d) => WEEKDAY_CN[d]).join('、')}`;
  const range = s.end ? `${md(s.start)}起，到${md(s.end)}` : `${md(s.start)}起`;
  return `${when} ${s.times.join('、')}（${range}）`;
}

/** 从 from 起 days 天里每天的时间点（不该做的日子为空数组）。 */
export function previewSchedule(s: Schedule, from: string, days = 7): { date: string; times: string[] }[] {
  return Array.from({ length: days }, (_, i) => {
    const date = addDays(from, i);
    return { date, times: occursOn(s, date) ? s.times : [] };
  });
}
