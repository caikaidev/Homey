import { Hono } from 'hono';
import { getSettings, putSetting, type Members } from '../db';
import type { AppEnv } from '../env';
import { apiError } from '../errors';
import { cleanText, readBody, requireParent } from './util';

const MAX_NAME = 20;
const MAX_MEMBERS = 10;
const EMAIL_RE = /^[^\s@]+@[^\s@]+$/;

export const settings = new Hono<AppEnv>();

settings.get('/', async (c) => c.json(await getSettings(c.env.DB)));

settings.patch('/', requireParent, async (c) => {
  const body = await readBody(c);
  if (!body) return apiError(c, 400, 'bad_request', '请求体应为 JSON 对象');
  const now = Date.now();
  const writes: D1PreparedStatement[] = [];

  if (body.child_nickname !== undefined) {
    const nick = cleanText(body.child_nickname, MAX_NAME);
    if (!nick) return apiError(c, 400, 'bad_request', '昵称不能为空');
    writes.push(putSetting(c.env.DB, 'child_nickname', nick, now));
  }
  if (body.members !== undefined) {
    const members = parseMembers(body.members);
    if (typeof members === 'string') return apiError(c, 400, 'bad_request', members);
    writes.push(putSetting(c.env.DB, 'members', JSON.stringify(members), now));
  }
  if (writes.length) await c.env.DB.batch(writes);
  return c.json(await getSettings(c.env.DB));
});

function parseMembers(input: unknown): Members | string {
  if (!input || typeof input !== 'object') return 'members 格式不对';
  const m = input as Record<string, unknown>;
  if (!Array.isArray(m.parents) || !Array.isArray(m.caregivers)) return 'members 需要 parents 和 caregivers 两个列表';
  if (m.parents.length > MAX_MEMBERS || m.caregivers.length > MAX_MEMBERS) return `每类最多 ${MAX_MEMBERS} 人`;
  const parents: Members['parents'] = [];
  for (const p of m.parents as unknown[]) {
    const r = (p ?? {}) as Record<string, unknown>;
    const email = cleanText(r.email, 100)?.toLowerCase();
    const name = cleanText(r.name, MAX_NAME);
    if (!email || !EMAIL_RE.test(email)) return '家长邮箱格式不对';
    if (!name) return '家长显示名不能为空';
    parents.push({ email, name });
  }
  const caregivers: string[] = [];
  for (const n of m.caregivers as unknown[]) {
    const name = cleanText(n, MAX_NAME);
    if (!name) return '照护人显示名不能为空';
    if (!caregivers.includes(name)) caregivers.push(name);
  }
  return { parents, caregivers };
}
