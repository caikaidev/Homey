import { describe, expect, it } from 'vitest';
import { parseRules } from '../src/parse/rules';

const TODAY = '2026-10-10';

describe('规则解析', () => {
  it('“AD 隔天吃 早上8点”', () => {
    expect(parseRules('AD 隔天吃 早上8点', TODAY)).toEqual({
      title: 'AD',
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
});
