import { createRemoteJWKSet, decodeJwt, jwtVerify } from 'jose';
import type { MiddlewareHandler } from 'hono';
import type { Actor, AppEnv, Env } from './env';
import { getSettings } from './db';
import { apiError } from './errors';

const DEFAULT_APP_RECORDER = '照护人';
const MAX_NAME_LENGTH = 20;

/**
 * /api/* 鉴权，二选一：
 * - App：`Authorization: Bearer <FAMILY_KEY>`，记录人取 `X-Recorder`（URL 编码的显示名）。
 * - Web：Cloudflare Access 的 JWT，校验签名、AUD 与签发方。优先读 Access 注入的
 *   `Cf-Access-Jwt-Assertion` 头；/api/* 被 Access 放行（Bypass）时不会注入这个头，
 *   这时读浏览器随请求带上的 `CF_Authorization` cookie（同一个 JWT）。
 */
export const requireActor: MiddlewareHandler<AppEnv> = async (c, next) => {
  const actor = (await actorFromFamilyKey(c.req.raw, c.env)) ?? (await actorFromAccess(c.req.raw, c.env));
  // 未登录时 message 写明 Access 那一步卡在哪，首页直接显示，方便排查配置。
  if (typeof actor === 'string') return apiError(c, 401, 'unauthorized', actor);
  if (actor.kind === 'web') {
    // 设置页里配了“邮箱 → 显示名”（如爸爸/妈妈）就用它，否则用邮箱前缀。
    const { members } = await getSettings(c.env.DB);
    const parent = members.parents.find((p) => p.email.toLowerCase() === actor.email.toLowerCase());
    if (parent?.name) actor.name = parent.name;
  }
  c.set('actor', actor);
  await next();
};

async function actorFromFamilyKey(req: Request, env: Env): Promise<Actor | null> {
  const header = req.headers.get('Authorization') ?? '';
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match || !env.FAMILY_KEY) return null;
  if (!(await timingSafeEqual(match[1]!.trim(), env.FAMILY_KEY))) return null;
  return { kind: 'app', name: recorderName(req.headers.get('X-Recorder')) };
}

function recorderName(raw: string | null): string {
  if (!raw) return DEFAULT_APP_RECORDER;
  let name: string;
  try {
    name = decodeURIComponent(raw);
  } catch {
    name = raw;
  }
  name = name.trim().slice(0, MAX_NAME_LENGTH);
  return name || DEFAULT_APP_RECORDER;
}

const jwksCache = new Map<string, ReturnType<typeof createRemoteJWKSet>>();

/** 成功返回身份；失败返回原因（不含密钥，可以展示给用户）。 */
async function actorFromAccess(req: Request, env: Env): Promise<Actor | string> {
  const team = env.ACCESS_TEAM_DOMAIN?.replace(/\/+$/, '');
  if (!team || !env.ACCESS_AUD) return '服务器没有配置 Cloudflare Access（团队域名或 AUD 为空）';
  const header = req.headers.get('Cf-Access-Jwt-Assertion');
  const token = header ?? accessCookie(req);
  if (!token) return '请求里没有 Access 登录凭证（既没有 Cf-Access-Jwt-Assertion 头，也没有 CF_Authorization cookie）';
  let jwks = jwksCache.get(team);
  if (!jwks) {
    jwks = createRemoteJWKSet(new URL(`${team}/cdn-cgi/access/certs`));
    jwksCache.set(team, jwks);
  }
  try {
    const { payload } = await jwtVerify(token, jwks, { issuer: team, audience: env.ACCESS_AUD });
    const email = typeof payload.email === 'string' ? payload.email : '';
    if (!email) return 'Access 凭证里没有邮箱（可能是服务令牌）';
    return { kind: 'web', email, name: email.split('@')[0]! };
  } catch (err) {
    const source = header ? '请求头' : 'cookie';
    return `Access 凭证（来自${source}）校验失败：${err instanceof Error ? err.message : String(err)}；${describeToken(token)}；服务器期望 aud=${env.ACCESS_AUD}，iss=${team}`;
  }
}

/** 不验签地读出凭证的 aud / iss，用来对照配置。 */
function describeToken(token: string): string {
  try {
    const { aud, iss } = decodeJwt(token);
    return `凭证 aud=${Array.isArray(aud) ? aud.join(',') : aud}，iss=${iss}`;
  } catch {
    return '凭证不是有效的 JWT';
  }
}

function accessCookie(req: Request): string | null {
  // cookie 会随跨站的表单 POST 一起发出；写操作只接受同源请求，防 CSRF。
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    const origin = req.headers.get('Origin');
    if (!origin || origin !== new URL(req.url).origin) return null;
  }
  for (const part of (req.headers.get('Cookie') ?? '').split(';')) {
    const [name, ...rest] = part.trim().split('=');
    if (name === 'CF_Authorization') return rest.join('=') || null;
  }
  return null;
}

/** 先各自做 SHA-256 再逐字节比较，避免长度和内容通过耗时泄露。 */
async function timingSafeEqual(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [ha, hb] = await Promise.all([
    crypto.subtle.digest('SHA-256', enc.encode(a)),
    crypto.subtle.digest('SHA-256', enc.encode(b)),
  ]);
  const va = new Uint8Array(ha);
  const vb = new Uint8Array(hb);
  let diff = 0;
  for (let i = 0; i < va.length; i++) diff |= va[i]! ^ vb[i]!;
  return diff === 0;
}
