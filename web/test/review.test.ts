import { describe, expect, it } from 'vitest';
import type { HistoryItem } from '../src/api/board';
import { slotTime, summarize } from '../src/review';

const item = (slot: string, status: HistoryItem['status'], recordedAt: number | null = null, extra: Partial<HistoryItem> = {}): HistoryItem => ({
  task_id: `t${slot}`,
  title: 'AD',
  slot,
  status,
  reason: '',
  recorded_by: recordedAt === null ? null : '奶奶',
  recorded_at: recordedAt,
  source: recordedAt === null ? null : 'app',
  ...extra,
});
const min = 60_000;

describe('试用复盘', () => {
  it('及时率、中位延迟、来源和补登', () => {
    const d1 = '2026-10-10';
    const d0 = '2026-10-09';
    const r = summarize(
      [
        // 今天：一件按时，一件还没到（pending 不算到期）
        { date: d1, weekday: '周六', items: [item('08:00', 'done', slotTime(d1, '08:00') + 10 * min), item('19:00', 'pending')] },
        // 昨天：一件晚 3 小时、网页登记；一件第二天补登；一件漏了
        {
          date: d0,
          weekday: '周五',
          items: [
            item('08:00', 'done', slotTime(d0, '08:00') + 180 * min, { source: 'web', recorded_by: '妈妈' }),
            item('12:00', 'done', slotTime(d1, '07:00')),
            item('19:00', 'missed'),
          ],
        },
      ],
      2,
    );
    expect(r).toMatchObject({
      from: d0,
      to: d1,
      due: 4,
      done: 3,
      missed: 1,
      on_time: 1,
      on_time_rate: 0.25,
      median_delay_min: 180,
      late_day: 1,
      by_source: { app: 2, web: 1 },
      by_recorder: { 奶奶: 2, 妈妈: 1 },
      undone: 2,
    });
    expect(r.days).toEqual([
      { date: d1, weekday: '周六', due: 1, on_time: 1, missed: 0 },
      { date: d0, weekday: '周五', due: 3, on_time: 0, missed: 1 },
    ]);
  });

  it('没有到期的事项时比率为空', () => {
    expect(summarize([{ date: '2026-10-10', weekday: '周六', items: [item('19:00', 'pending')] }], 0)).toMatchObject({
      due: 0,
      on_time_rate: null,
      median_delay_min: null,
    });
  });

  it('提前登记算及时，延迟为负', () => {
    const d = '2026-10-10';
    const r = summarize([{ date: d, weekday: '周六', items: [item('08:00', 'done', slotTime(d, '08:00') - 30 * min)] }], 0);
    expect(r).toMatchObject({ on_time: 1, median_delay_min: -30 });
  });
});
