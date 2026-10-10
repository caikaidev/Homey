import { describe, expect, it } from 'vitest';
import { parseRules } from '../src/parse/rules';

const TODAY = '2026-10-10';

describe('规则解析', () => {
  it('“AD 隔天吃 早上8点”', () => {
    expect(parseRules('AD 隔天吃 早上8点', TODAY)).toEqual({
      title: 'AD',
      kind: 'supplement',
      schedule: { type: 'interval_days', every: 2, start: TODAY, times: ['08:00'] },
      warnings: [],
    });
  });

  it('“AD隔天吃早上8点”（不带空格）', () => {
    expect(parseRules('AD隔天吃早上8点', TODAY).title).toBe('AD');
  });

  it('“钙每天晚上7点”', () => {
    expect(parseRules('钙每天晚上7点', TODAY)).toEqual({
      title: '钙',
      kind: 'supplement',
      schedule: { type: 'daily', start: TODAY, times: ['19:00'] },
      warnings: [],
    });
  });

  it('中文数字、半点、多个时间', () => {
    expect(parseRules('鱼油每天早上八点半 晚上七点', TODAY).schedule.times).toEqual(['08:30', '19:00']);
    expect(parseRules('维生素D3 每天 9:15', TODAY)).toMatchObject({ title: '维生素D3', schedule: { times: ['09:15'] } });
  });

  it('每 N 天', () => {
    expect(parseRules('益生菌每3天早上8点', TODAY).schedule).toMatchObject({ type: 'interval_days', every: 3 });
    const gap = parseRules('益生菌每隔两天早上8点', TODAY);
    expect(gap.schedule).toMatchObject({ type: 'interval_days', every: 3 });
    expect(gap.warnings).toHaveLength(1);
  });

  it('没说时间或频率时给出提醒', () => {
    const r = parseRules('AD', TODAY);
    expect(r.schedule).toEqual({ type: 'daily', start: TODAY, times: ['08:00'] });
    expect(r.warnings).toHaveLength(2);
  });

  it('B4 “感冒药一天三次，吃3天”：每天 3 次 + 结束日期 + 提醒', () => {
    const r = parseRules('感冒药一天三次，吃3天', TODAY);
    expect(r.title).toBe('感冒药');
    expect(r.kind).toBe('medicine');
    expect(r.schedule).toEqual({ type: 'daily', start: TODAY, times: ['08:00', '13:00', '19:00'], end: '2026-10-12' });
    expect(r.warnings).toEqual(expect.arrayContaining([expect.stringContaining('没说具体几点'), expect.stringContaining('医嘱')]));
  });

  it('每周几、工作日、周末、范围', () => {
    expect(parseRules('游泳每周二、四晚上7点', TODAY)).toMatchObject({ title: '游泳', schedule: { type: 'weekdays', weekdays: [2, 4], times: ['19:00'] } });
    expect(parseRules('益生菌每周一三五早上8点', TODAY).schedule).toMatchObject({ type: 'weekdays', weekdays: [1, 3, 5] });
    expect(parseRules('鱼油工作日早上8点', TODAY).schedule).toMatchObject({ type: 'weekdays', weekdays: [1, 2, 3, 4, 5] });
    expect(parseRules('鱼油周末早上9点', TODAY).schedule).toMatchObject({ type: 'weekdays', weekdays: [0, 6] });
    expect(parseRules('鱼油周一到周五早上8点', TODAY).schedule).toMatchObject({ type: 'weekdays', weekdays: [1, 2, 3, 4, 5] });
    expect(parseRules('洗澡每周三次', TODAY).schedule.type).not.toBe('weekdays');
  });

  it('从明天开始、连续一周', () => {
    const r = parseRules('益生菌每天早上8点，从明天开始，连续一周', TODAY);
    expect(r.title).toBe('益生菌');
    expect(r.schedule).toMatchObject({ start: '2026-10-11', end: '2026-10-17' });
  });

  it('一天两次但只说了一个时间时提醒', () => {
    const r = parseRules('钙一天两次 早上8点', TODAY);
    expect(r.schedule.times).toEqual(['08:00']);
    expect(r.warnings.some((w) => w.includes('一天 2 次'))).toBe(true);
  });
});
