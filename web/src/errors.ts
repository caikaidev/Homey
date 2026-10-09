import type { Context } from 'hono';
import type { ContentfulStatusCode } from 'hono/utils/http-status';

/** 统一错误格式：{ error: { code, message } } */
export function apiError(c: Context, status: ContentfulStatusCode, code: string, message: string) {
  return c.json({ error: { code, message } }, status);
}
