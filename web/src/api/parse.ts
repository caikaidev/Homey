import { Hono } from 'hono';
import { bizDate } from '../bizdate';
import type { AppEnv } from '../env';
import { apiError } from '../errors';
import { GeminiError, parseWithGemini } from '../parse/gemini';
import { MEDICINE_WARNING, parseRules } from '../parse/rules';
import { describeSchedule, previewSchedule } from '../schedule';
import { cleanText, readBody, requireParent } from './util';

export const parse = new Hono<AppEnv>();

/**
 * 一句话 → 排期草稿。只返回结果，不保存；前端让用户核对后再 POST /api/tasks。
 * 先用 Gemini，没配 Key、超时或出错时降级到规则解析，并在 warnings 里说明。
 */
parse.post('/', requireParent, async (c) => {
  const body = await readBody(c);
  const text = cleanText(body?.text, 200);
  if (!text) return apiError(c, 400, 'bad_request', '请输入一句话');
  const today = bizDate(Date.now());

  let source: 'gemini' | 'rules' = 'rules';
  let result;
  const notes: string[] = [];
  if (c.env.GEMINI_API_KEY) {
    try {
      result = await parseWithGemini(text, today, c.env);
      source = 'gemini';
    } catch (err) {
      if (!(err instanceof GeminiError)) throw err;
      console.warn('gemini parse failed:', err.message);
      notes.push(`AI 解析没成功（${err.message}），已改用简单规则，请仔细核对`);
    }
  }
  result ??= parseRules(text, today);

  const warnings = [...notes, ...result.warnings];
  if (result.kind === 'medicine' && !warnings.includes(MEDICINE_WARNING)) warnings.push(MEDICINE_WARNING);
  return c.json({
    source,
    title: result.title,
    kind: result.kind,
    schedule: result.schedule,
    describe: describeSchedule(result.schedule),
    preview: previewSchedule(result.schedule, result.schedule.start > today ? result.schedule.start : today),
    warnings,
  });
});
