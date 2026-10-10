import { Hono } from 'hono';
import { bizDate } from '../bizdate';
import type { AppEnv } from '../env';
import { apiError } from '../errors';
import { parseRules } from '../parse/rules';
import { describeSchedule, previewSchedule } from '../schedule';
import { cleanText, readBody, requireParent } from './util';

export const parse = new Hono<AppEnv>();

/** 一句话 → 排期草稿。只返回结果，不保存；前端让用户核对后再 POST /api/tasks。P2 接 Gemini。 */
parse.post('/', requireParent, async (c) => {
  const body = await readBody(c);
  const text = cleanText(body?.text, 200);
  if (!text) return apiError(c, 400, 'bad_request', '请输入一句话');
  const today = bizDate(Date.now());
  const { title, schedule, warnings } = parseRules(text, today);
  return c.json({
    source: 'rules',
    title,
    schedule,
    describe: describeSchedule(schedule),
    preview: previewSchedule(schedule, today),
    warnings,
  });
});
