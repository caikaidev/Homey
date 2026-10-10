import { bizDate } from './bizdate';
import type { HistoryDay } from './api/board';

/** 晚于计划时间多久以内算“及时登记”。 */
export const ON_TIME_MIN = 60;

export interface Review {
  from: string;
  to: string;
  /** 已到期的实例（不含今天还没登记的）。 */
  due: number;
  done: number;
  skipped: number;
  missed: number;
  /** 计划时间后 ON_TIME_MIN 分钟内登记的。 */
  on_time: number;
  /** 及时登记率（on_time / due），due 为 0 时为 null。 */
  on_time_rate: number | null;
  /** 登记相对计划时间的中位延迟（分钟，提前为负），没有登记时为 null。 */
  median_delay_min: number | null;
  /** 不是在当天登记的（第二天补登），可能是日期错位或忘了。 */
  late_day: number;
  by_source: Record<string, number>;
  by_recorder: Record<string, number>;
  /** 撤销次数，误触的参考。 */
  undone: number;
  days: { date: string; weekday: string; due: number; on_time: number; missed: number }[];
}

/** 某天某时间点（北京时间）的毫秒时间戳。 */
export function slotTime(date: string, slot: string): number {
  return Date.parse(`${date}T${slot}:00+08:00`);
}

/** 家庭试用复盘：把最近几天的记录汇总成几项数字（docs/TRIAL.md 里怎么看）。 */
export function summarize(days: HistoryDay[], undone: number): Review {
  const by_source: Record<string, number> = {};
  const by_recorder: Record<string, number> = {};
  const delays: number[] = [];
  let due = 0, done = 0, skipped = 0, missed = 0, onTime = 0, lateDay = 0;
  const perDay = days.map((d) => {
    let dDue = 0, dOnTime = 0, dMissed = 0;
    for (const i of d.items) {
      if (i.status === 'pending') continue;
      dDue++;
      if (i.status === 'missed') {
        dMissed++;
        continue;
      }
      if (i.status === 'skipped') skipped++;
      else done++;
      if (i.recorded_at === null) continue;
      if (i.source) by_source[i.source] = (by_source[i.source] ?? 0) + 1;
      if (i.recorded_by) by_recorder[i.recorded_by] = (by_recorder[i.recorded_by] ?? 0) + 1;
      if (bizDate(i.recorded_at) !== d.date) lateDay++;
      if (i.status !== 'done') continue;
      const delay = Math.round((i.recorded_at - slotTime(d.date, i.slot)) / 60_000);
      delays.push(delay);
      if (delay <= ON_TIME_MIN) dOnTime++;
    }
    due += dDue;
    onTime += dOnTime;
    missed += dMissed;
    return { date: d.date, weekday: d.weekday, due: dDue, on_time: dOnTime, missed: dMissed };
  });
  delays.sort((a, b) => a - b);
  const mid = delays.length >> 1;
  const median = delays.length === 0 ? null : delays.length % 2 ? delays[mid]! : Math.round((delays[mid - 1]! + delays[mid]!) / 2);
  return {
    from: days.at(-1)?.date ?? '',
    to: days[0]?.date ?? '',
    due,
    done,
    skipped,
    missed,
    on_time: onTime,
    on_time_rate: due === 0 ? null : Math.round((onTime / due) * 100) / 100,
    median_delay_min: median,
    late_day: lateDay,
    by_source,
    by_recorder,
    undone,
    days: perDay,
  };
}
