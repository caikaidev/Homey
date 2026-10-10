/**
 * 业务日期：Asia/Shanghai 的自然日，格式 YYYY-MM-DD。
 * 上海没有夏令时，固定 UTC+8，所以直接按偏移计算，不依赖运行环境的时区。
 */
const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/;

export const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const;

/** 某个时刻（毫秒 UTC）对应的业务日期。 */
export function bizDate(nowMs: number): string {
  return new Date(nowMs + SHANGHAI_OFFSET_MS).toISOString().slice(0, 10);
}

/** 是否为真实存在的日期（拒绝 2026-02-30 这类）。 */
export function isValidDate(s: unknown): s is string {
  if (typeof s !== 'string') return false;
  const m = DATE_RE.exec(s);
  if (!m) return false;
  const d = new Date(Date.UTC(+m[1]!, +m[2]! - 1, +m[3]!));
  return d.toISOString().slice(0, 10) === s;
}

function toUtcDays(date: string): number {
  return Math.floor(Date.parse(`${date}T00:00:00Z`) / DAY_MS);
}

export function addDays(date: string, n: number): string {
  return new Date((toUtcDays(date) + n) * DAY_MS).toISOString().slice(0, 10);
}

/** b - a，单位天。 */
export function daysBetween(a: string, b: string): number {
  return toUtcDays(b) - toUtcDays(a);
}

/** 0 = 周日 … 6 = 周六 */
export function weekday(date: string): number {
  return new Date(`${date}T00:00:00Z`).getUTCDay();
}
