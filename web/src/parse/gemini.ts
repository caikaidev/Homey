import { addDays, WEEKDAY_NAMES, weekday } from '../bizdate';
import type { Env } from '../env';
import { parseSchedule, type Schedule } from '../schedule';

/**
 * Gemini 解析：一句话 → 名称 + 排期。只做“翻译”，不给医疗建议。
 * Key 只在 Worker 里（Secret GEMINI_API_KEY），客户端拿不到。
 * 失败时抛出 GeminiError，由调用方降级到规则解析。
 */
export interface GeminiParseResult {
  title: string;
  kind: 'supplement' | 'medicine';
  schedule: Schedule;
  warnings: string[];
}

export class GeminiError extends Error {}

const TIMEOUT_MS = 15000;
const ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models';

const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  properties: {
    title: { type: 'STRING', description: '事项名称，简短，如“AD 一粒”“感冒药”' },
    kind: { type: 'STRING', enum: ['supplement', 'medicine'] },
    type: { type: 'STRING', enum: ['daily', 'interval_days', 'weekdays'] },
    every: { type: 'INTEGER', description: 'interval_days 时每几天一次，隔天为 2' },
    weekdays: { type: 'ARRAY', items: { type: 'INTEGER' }, description: 'weekdays 时的星期，0=周日 … 6=周六' },
    times: { type: 'ARRAY', items: { type: 'STRING' }, description: '每天的时间点，24 小时制 HH:MM' },
    start: { type: 'STRING', description: '开始日期 YYYY-MM-DD' },
    end: { type: 'STRING', description: '最后一天 YYYY-MM-DD；没有疗程或结束日期时留空字符串' },
    warnings: { type: 'ARRAY', items: { type: 'STRING' }, description: '没说清楚、需要用户核对的地方，中文短句' },
  },
  required: ['title', 'kind', 'type', 'times', 'start', 'end', 'warnings'],
};

function prompt(text: string, today: string): string {
  return [
    '你是家庭照护看板的排期解析器，把家长说的一句话转换成结构化排期。只做转换，不给任何医疗建议、不推荐剂量。',
    `今天是 ${today}（${WEEKDAY_NAMES[weekday(today)]}，北京时间），明天是 ${addDays(today, 1)}。`,
    '规则：',
    '- type：每天 = daily；隔天/每 N 天 = interval_days（every 为 N，隔天为 2）；每周固定几天/工作日/周末 = weekdays。',
    '- times：24 小时制 HH:MM。只说“一天三次”没说几点时，按 08:00、13:00、19:00 这类常见时间填，并在 warnings 里提醒核对。',
    '- start：没说从哪天开始就用今天。',
    '- end：说了“吃3天”“连续一周”这类疗程时，填最后一天（含开始那天，吃3天 = start 加 2 天）；没说就填空字符串。',
    '- kind：药品（感冒药、退烧药、抗生素等）为 medicine，营养补充（AD、钙、益生菌等）为 supplement。',
    '- title：只写东西本身，可带剂量，如“AD 一粒”“钙 1 袋”，不要包含时间和频率。',
    '- warnings：凡是你猜的、默认的、或句子里有歧义的地方都写一句，没有就给空数组。',
    `这句话是：${text}`,
  ].join('\n');
}

export async function parseWithGemini(text: string, today: string, env: Env): Promise<GeminiParseResult> {
  if (!env.GEMINI_API_KEY) throw new GeminiError('没有配置 GEMINI_API_KEY');
  const model = env.GEMINI_MODEL || 'gemini-3.8-flash';
  let res: Response;
  try {
    res = await fetch(`${ENDPOINT}/${encodeURIComponent(model)}:generateContent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: prompt(text, today) }] }],
        generationConfig: {
          responseMimeType: 'application/json',
          responseSchema: RESPONSE_SCHEMA,
          temperature: 0,
          // Flash 默认会先“思考”，常常超过超时；排期这种简单转换用 low 就够，也快得多。
          thinkingConfig: { thinkingLevel: 'low' },
        },
      }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (err) {
    throw new GeminiError(err instanceof Error && err.name === 'TimeoutError' ? '请求超时' : '连不上 Gemini');
  }
  if (!res.ok) {
    const body = await res.json<{ error?: { message?: string } }>().catch(() => null);
    const msg = body?.error?.message ?? '';
    // Gemini 不对部分地区开放；Worker 落在这些地区的机房时会收到这个错误。
    if (/location is not supported/i.test(msg)) throw new GeminiError('Gemini 在当前服务器所在地区不可用');
    throw new GeminiError(`Gemini 返回 ${res.status}${msg ? `：${msg.slice(0, 120)}` : ''}`);
  }

  const data = await res.json<{ candidates?: { content?: { parts?: { text?: string }[] } }[] }>();
  const raw = data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? '').join('') ?? '';
  let out: Record<string, unknown>;
  try {
    out = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    throw new GeminiError('Gemini 返回的不是 JSON');
  }

  const schedule = parseSchedule({
    type: out.type,
    every: out.every,
    weekdays: out.weekdays,
    times: out.times,
    start: out.start,
    end: out.end || undefined,
  });
  if (typeof schedule === 'string') throw new GeminiError(`Gemini 给的排期不合法：${schedule}`);
  const title = typeof out.title === 'string' ? out.title.trim().slice(0, 40) : '';
  if (!title) throw new GeminiError('Gemini 没给名称');
  const warnings = Array.isArray(out.warnings)
    ? out.warnings.filter((w): w is string => typeof w === 'string' && w.trim() !== '').map((w) => w.trim().slice(0, 80)).slice(0, 5)
    : [];
  return { title, kind: out.kind === 'medicine' ? 'medicine' : 'supplement', schedule, warnings };
}
