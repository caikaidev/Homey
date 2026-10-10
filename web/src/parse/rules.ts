import { addDays } from '../bizdate';
import type { Schedule } from '../schedule';

/**
 * 规则解析：一句话 → 名称 + 排期。只认最常见的说法，认不出就留 warnings，
 * 由用户在表单里改。Gemini 解析失败、超时或没配 Key 时也降级到这里。
 */
export interface RuleParseResult {
  title: string;
  kind: 'supplement' | 'medicine';
  schedule: Schedule;
  warnings: string[];
}

export const MEDICINE_WARNING = '涉及用药：请按医嘱核对剂量与疗程';
const MEDICINE_RE = /药|感冒|发烧|退烧|咳嗽|抗生素|消炎|布洛芬|对乙酰氨基酚|美林|泰诺|阿莫西林|头孢|雾化|滴眼|滴耳|糖浆|颗粒剂|医嘱/;

const CN_NUM: Record<string, number> = {
  零: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10,
};

/** 解析“8”“十二”“二十”“十一”这类 0–99 的数字。 */
function toNumber(raw: string): number | null {
  if (/^\d+$/.test(raw)) return Number(raw);
  if (!/^[零一二两三四五六七八九十]+$/.test(raw)) return null;
  if (raw === '十') return 10;
  const i = raw.indexOf('十');
  if (i < 0) return raw.length === 1 ? CN_NUM[raw]! : null;
  const tens = i === 0 ? 1 : CN_NUM[raw.slice(0, i)];
  const ones = i === raw.length - 1 ? 0 : CN_NUM[raw.slice(i + 1)];
  return tens === undefined || ones === undefined ? null : tens * 10 + ones;
}

const NUM = '(\\d{1,2}|[零一二两三四五六七八九十]{1,3})';
const PERIOD = '(早上|早晨|上午|中午|下午|傍晚|晚上|夜里|睡前|凌晨)?';
const TIME_RE = new RegExp(`${PERIOD}\\s*${NUM}\\s*(?:点|时|:|：)\\s*(半|${NUM}\\s*分?)?`, 'g');
const AFTERNOON = new Set(['下午', '傍晚', '晚上', '夜里', '睡前']);

/** 一天 N 次但没说几点时的默认时间。 */
const DEFAULT_TIMES: Record<number, string[]> = {
  1: ['08:00'],
  2: ['08:00', '20:00'],
  3: ['08:00', '13:00', '19:00'],
  4: ['08:00', '12:00', '16:00', '20:00'],
};

const WD = '[一二三四五六日天]';
const WD_INDEX: Record<string, number> = { 日: 0, 天: 0, 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6 };

/** 句子里排期相关的部分从哪开始（之前的就是名称）。 */
const SCHEDULE_START = new RegExp(
  [
    '每天', '天天', '每日', '隔天', '隔日', '隔一天', '每隔', `每\\s*${NUM}\\s*天`, '一天', '一日',
    '每周', '每星期', '工作日', '周末', `周${WD}`, `星期${WD}`,
    '吃', '连续', '共', '从', '明天', '后天', '今天',
    PERIOD.slice(1, -2), `${NUM}\\s*(?:点|:|：)`,
  ].join('|'),
);

function parseTimes(input: string): string[] {
  const times: string[] = [];
  for (const m of input.matchAll(TIME_RE)) {
    let h = toNumber(m[2]!);
    if (h === null) continue;
    const minute = m[3] === '半' ? 30 : m[4] ? toNumber(m[4]) : 0;
    if (minute === null || minute > 59) continue;
    if (m[1] && AFTERNOON.has(m[1]) && h < 12) h += 12;
    if (m[1] === '中午' && h < 6) h += 12;
    if (h > 23) continue;
    times.push(`${String(h).padStart(2, '0')}:${String(minute).padStart(2, '0')}`);
  }
  return [...new Set(times)].sort();
}

/** 每周几：工作日 / 周末 / 周一到周五 / 每周一三五 / 星期二、四。没提到返回 null。 */
function parseWeekdays(input: string): number[] | null {
  if (/工作日/.test(input)) return [1, 2, 3, 4, 5];
  if (/周末/.test(input)) return [0, 6];
  const range = new RegExp(`(?:周|星期)(${WD})\\s*(?:到|至|-|~)\\s*(?:周|星期)?(${WD})`).exec(input);
  if (range) {
    const a = WD_INDEX[range[1]!]!;
    const b = WD_INDEX[range[2]!]!;
    const days: number[] = [];
    for (let d = a; ; d = (d + 1) % 7) {
      days.push(d);
      if (d === b || days.length > 7) break;
    }
    return [...new Set(days)].sort();
  }
  // “每周三次”是一周几次，不是周三；这种不当作星期。
  const list = new RegExp(`(?:每周|每星期|周|星期)((?:${WD}[、，,和\\s]*)+)(?!\\s*次)`).exec(input);
  if (!list) return null;
  const days = [...list[1]!].filter((ch) => ch in WD_INDEX).map((ch) => WD_INDEX[ch]!);
  return days.length ? [...new Set(days)].sort() : null;
}

export function parseRules(text: string, today: string): RuleParseResult {
  const warnings: string[] = [];
  const input = text.trim().replace(/\s+/g, ' ');

  // 开始日期
  let start = today;
  if (/明天(开始|起)|从明天/.test(input)) start = addDays(today, 1);
  else if (/后天(开始|起)|从后天/.test(input)) start = addDays(today, 2);

  // 时间点
  let times = parseTimes(input);
  const perDay = new RegExp(`(?:一天|每天|一日|每日)\\s*${NUM}\\s*次`).exec(input);
  const count = perDay ? toNumber(perDay[1]!) : null;
  if (times.length === 0) {
    if (count && DEFAULT_TIMES[count]) {
      times = DEFAULT_TIMES[count]!;
      warnings.push(`没说具体几点，先按 ${times.join('、')}，请核对`);
    } else {
      times = ['08:00'];
      warnings.push(count ? `一天 ${count} 次请手动填时间` : '没说几点，先按早上 8:00，请核对');
    }
  } else if (count && count !== times.length) {
    warnings.push(`说的是一天 ${count} 次，但只认出 ${times.length} 个时间，请核对`);
  }

  // 频率
  let schedule: Schedule;
  const weekdays = parseWeekdays(input);
  const everyN = new RegExp(`每\\s*${NUM}\\s*天`).exec(input);
  const everyGapN = new RegExp(`每隔\\s*${NUM}\\s*天`).exec(input);
  if (weekdays) {
    schedule = { type: 'weekdays', weekdays, start, times };
  } else if (/隔天|隔日|隔一天/.test(input) && !everyGapN) {
    schedule = { type: 'interval_days', every: 2, start, times };
  } else if (everyGapN) {
    // “每隔两天”有人理解成隔天、有人理解成每 3 天，按字面（中间空 N 天）并提醒。
    const gap = toNumber(everyGapN[1]!) ?? 1;
    schedule = gap <= 0 ? { type: 'daily', start, times } : { type: 'interval_days', every: Math.min(gap + 1, 30), start, times };
    warnings.push(`“每隔${everyGapN[1]}天”按中间空 ${gap} 天理解，请核对`);
  } else if (everyN) {
    const n = toNumber(everyN[1]!) ?? 1;
    schedule = n <= 1 ? { type: 'daily', start, times } : { type: 'interval_days', every: Math.min(n, 30), start, times };
  } else {
    schedule = { type: 'daily', start, times };
    if (!/每天|天天|每日|一天|一日/.test(input)) warnings.push('没说多久一次，先按每天，请核对');
  }

  // 疗程：吃3天 / 连续5天 / 共7天 / 用一周
  const duration = new RegExp(`(?:吃|连续|共|用|服)\\s*${NUM}\\s*天`).exec(input);
  const week = /(?:吃|连续|共|用|服)\s*(?:一周|一个星期|1周)/.test(input);
  const days = duration ? toNumber(duration[1]!) : week ? 7 : null;
  if (days && days > 0) schedule.end = addDays(start, days - 1);

  const kind = MEDICINE_RE.test(input) ? 'medicine' : 'supplement';
  if (kind === 'medicine') warnings.push(MEDICINE_WARNING);

  const cut = SCHEDULE_START.exec(input);
  let title = (cut ? input.slice(0, cut.index) : input).replace(/[，,。\s]+$/, '').trim();
  if (!title) {
    title = input.slice(0, 20);
    warnings.push('没认出名称，请填写');
  }

  return { title: title.slice(0, 40), kind, schedule, warnings };
}
