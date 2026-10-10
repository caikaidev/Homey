import { describe, expect, it } from 'vitest';
import { addDays, bizDate, daysBetween, isValidDate, weekday } from '../src/bizdate';
import { describeSchedule, occursOn, parseSchedule, previewSchedule, type Schedule } from '../src/schedule';

describe('业务日期（Asia/Shanghai）', () => {
  it('北京时间 23:59 与 00:01 分属两天，不受运行环境时区影响', () => {
    expect(bizDate(Date.parse('2026-10-10T15:59:00Z'))).toBe('2026-10-10'); // 北京 23:59
    expect(bizDate(Date.parse('2026-10-10T16:01:00Z'))).toBe('2026-10-11'); // 北京 00:01
  });

  it('日期加减跨月、跨年、闰年', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31');
    expect(daysBetween('2026-12-30', '2027-01-02')).toBe(3);
  });

  it('校验日期', () => {
    expect(isValidDate('2026-10-10')).toBe(true);
    expect(isValidDate('2026-02-30')).toBe(false);
    expect(isValidDate('2026-1-1')).toBe(false);
    expect(isValidDate(20261010)).toBe(false);
  });

  it('星期', () => {
    expect(weekday('2026-10-10')).toBe(6); // 周六
  });
});

describe('排期', () => {
  const ad: Schedule = { type: 'interval_days', every: 2, start: '2026-10-30', times: ['08:00'] };

  it('A1 隔天：连续三天 有/无/有，跨月跨年依然正确', () => {
    expect(['2026-10-30', '2026-10-31', '2026-11-01'].map((d) => occursOn(ad, d))).toEqual([true, false, true]);
    const nye: Schedule = { ...ad, start: '2026-12-31' };
    expect(['2026-12-31', '2027-01-01', '2027-01-02'].map((d) => occursOn(nye, d))).toEqual([true, false, true]);
  });

  it('开始日期之前不出现', () => {
    expect(occursOn(ad, '2026-10-29')).toBe(false);
    expect(occursOn({ type: 'daily', start: '2026-10-10', times: ['19:00'] }, '2026-10-09')).toBe(false);
  });

  it('A2 每天：每天出现', () => {
    const ca: Schedule = { type: 'daily', start: '2026-10-10', times: ['19:00'] };
    expect(previewSchedule(ca, '2026-10-10', 3)).toEqual([
      { date: '2026-10-10', times: ['19:00'] },
      { date: '2026-10-11', times: ['19:00'] },
      { date: '2026-10-12', times: ['19:00'] },
    ]);
  });

  it('校验并规范化', () => {
    expect(parseSchedule({ type: 'daily', start: '2026-10-10', times: ['19:00', '08:00', '08:00'] })).toEqual({
      type: 'daily',
      start: '2026-10-10',
      times: ['08:00', '19:00'],
    });
    expect(parseSchedule({ type: 'interval_days', every: 1, start: '2026-10-10', times: ['08:00'] })).toBeTypeOf('string');
    expect(parseSchedule({ type: 'daily', start: '2026-10-10', times: ['8:00'] })).toBeTypeOf('string');
    expect(parseSchedule({ type: 'daily', start: '2026-10-10', times: [] })).toBeTypeOf('string');
    expect(parseSchedule({ type: 'weekly', start: '2026-10-10', times: ['08:00'] })).toBeTypeOf('string');
    expect(parseSchedule(null)).toBeTypeOf('string');
  });

  it('人话描述', () => {
    expect(describeSchedule(ad)).toBe('隔天 08:00（10月30日起）');
    expect(describeSchedule({ ...ad, every: 3, times: ['08:00', '19:00'] })).toBe('每 3 天 08:00、19:00（10月30日起）');
    expect(describeSchedule({ type: 'daily', start: '2026-10-10', times: ['19:00'] })).toBe('每天 19:00（10月10日起）');
  });
});
