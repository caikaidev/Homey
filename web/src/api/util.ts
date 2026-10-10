import type { Context, MiddlewareHandler } from 'hono';
import type { AppEnv } from '../env';
import { apiError } from '../errors';

/** 读 JSON 请求体；不是对象时返回 null。 */
export async function readBody(c: Context<AppEnv>): Promise<Record<string, unknown> | null> {
  try {
    const body: unknown = await c.req.json();
    return body && typeof body === 'object' && !Array.isArray(body) ? (body as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** 只允许家长（Web 登录）调用：建/改事项、改设置。App 对这些是只读的。 */
export const requireParent: MiddlewareHandler<AppEnv> = async (c, next) => {
  if (c.get('actor').kind !== 'web') return apiError(c, 403, 'forbidden', '只有家长可以修改');
  await next();
};

export function cleanText(v: unknown, max: number): string | null {
  if (typeof v !== 'string') return null;
  return v.trim().slice(0, max);
}
