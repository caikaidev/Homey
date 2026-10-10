import type { Schedule } from '../schedule';

/**
 * P1 规则解析：一句话 → 名称 + 排期。只认最常见的说法，认不出就留 warnings，
 * 由用户在表单里改。P2 的 Gemini 解析失败时也降级到这里。
 */
export interface RuleParseResult {
  title: string;
  schedule: Schedule;
  warnings: string[];
}

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

/** 句子里排期相关的部分从哪开始（之前的就是名称）。 */
const SCHEDULE_START = new RegExp(`(每天|天天|每日|隔天|隔日|隔一天|每隔|每${NUM}天|${PERIOD.slice(1, -2)}|${NUM}\\s*(?:点|:|：))`);

export function parseRules(text: string, today: string): RuleParseResult {
  const warnings: string[] = [];
  const input = text.trim().replace(/\s+/g, ' ');

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
  if (times.length === 0) {
    times.push('08:00');
    warnings.push('没说几点，先按早上 8:00，请核对');
  }

  let schedule: Schedule;
  const everyN = new RegExp(`每\\s*${NUM}\\s*天`).exec(input);
  const everyGapN = new RegExp(`每隔\\s*${NUM}\\s*天`).exec(input);
  if (/隔天|隔日|隔一天/.test(input) && !everyGapN) {
    schedule = { type: 'interval_days', every: 2, start: today, times };
  } else if (everyGapN) {
    // “每隔两天”有人理解成隔天、有人理解成每 3 天，按字面（中间空 N 天）并提醒。
    const gap = toNumber(everyGapN[1]!) ?? 1;
    schedule = gap <= 0 ? { type: 'daily', start: today, times } : { type: 'interval_days', every: gap + 1, start: today, times };
    warnings.push(`“每隔${everyGapN[1]}天”按中间空 ${gap} 天理解，请核对`);
  } else if (everyN) {
    const n = toNumber(everyN[1]!) ?? 1;
    schedule = n <= 1 ? { type: 'daily', start: today, times } : { type: 'interval_days', every: Math.min(n, 30), start: today, times };
  } else {
    schedule = { type: 'daily', start: today, times };
    if (!/每天|天天|每日/.test(input)) warnings.push('没说多久一次，先按每天，请核对');
  }

  const cut = SCHEDULE_START.exec(input);
  let title = (cut ? input.slice(0, cut.index) : input).replace(/[，,。\s]+$/, '').trim();
  if (!title) {
    title = input.slice(0, 20);
    warnings.push('没认出名称，请填写');
  }

  return { title: title.slice(0, 40), schedule: { ...schedule, times: [...new Set(times)].sort() }, warnings };
}
