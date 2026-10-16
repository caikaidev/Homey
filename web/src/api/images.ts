import { Hono } from 'hono';
import { bizDate, isValidDate } from '../bizdate';
import type { AppEnv } from '../env';
import { apiError } from '../errors';
import { requireParent } from './util';

/** 按日期使用的图片类别。目前只有宝宝屋。 */
export const IMAGE_CATEGORIES = ['babyroom'] as const;
export type ImageCategory = (typeof IMAGE_CATEGORIES)[number];

const MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
/** D1 单行上限 2MB；Web 上传前会压缩到几百 KB，这里留余量。 */
export const MAX_IMAGE_BYTES = 1_500_000;

export interface DayImageMeta {
  id: string;
  category: string;
  use_date: string;
  mime: string;
  size: number;
  created_by: string;
  created_at: number;
}

const META_COLUMNS = 'id, category, use_date, mime, size, created_by, created_at';

export function presentImage(m: DayImageMeta) {
  return { ...m, url: `/api/images/${m.id}` };
}

/** 删掉使用日期已过的图片。上传和拉首页卡片时顺手调用，不需要定时任务。 */
export function purgeExpired(db: D1Database, today: string) {
  return db.prepare('DELETE FROM day_images WHERE use_date < ?').bind(today).run();
}

export async function imagesFrom(db: D1Database, from: string): Promise<DayImageMeta[]> {
  const { results } = await db
    .prepare(`SELECT ${META_COLUMNS} FROM day_images WHERE use_date >= ? ORDER BY use_date, category`)
    .bind(from)
    .all<DayImageMeta>();
  return results;
}

export const images = new Hono<AppEnv>();

/** 今天及以后的图片（不含图片内容）。 */
images.get('/', async (c) => {
  const today = bizDate(Date.now());
  return c.json({ images: (await imagesFrom(c.env.DB, today)).map(presentImage) });
});

/** 图片内容。id 每次上传都换，所以可以长期缓存。 */
images.get('/:id', async (c) => {
  const row = await c.env.DB.prepare('SELECT mime, data FROM day_images WHERE id = ?')
    .bind(c.req.param('id'))
    .first<{ mime: string; data: ArrayBuffer | number[] }>();
  if (!row) return apiError(c, 404, 'not_found', '图片不存在或已过期');
  const bytes = row.data instanceof ArrayBuffer ? new Uint8Array(row.data) : new Uint8Array(row.data);
  return new Response(bytes, {
    headers: { 'Content-Type': row.mime, 'Cache-Control': 'private, max-age=31536000, immutable' },
  });
});

/**
 * 上传或替换某天的图片 · 家长。请求体是图片本身（Content-Type 为图片类型），
 * 路径里带类别和使用日期：PUT /api/images/babyroom/2026-10-20。
 */
images.put('/:category/:date', requireParent, async (c) => {
  const category = c.req.param('category');
  const date = c.req.param('date');
  if (!IMAGE_CATEGORIES.includes(category as ImageCategory)) return apiError(c, 400, 'bad_request', '没有这个图片类别');
  if (!isValidDate(date)) return apiError(c, 400, 'bad_request', '使用日期应为 YYYY-MM-DD');
  const now = Date.now();
  const today = bizDate(now);
  if (date < today) return apiError(c, 400, 'bad_request', '使用日期不能早于今天');
  const mime = (c.req.header('Content-Type') ?? '').split(';')[0]!.trim().toLowerCase();
  if (!MIME_TYPES.includes(mime)) return apiError(c, 400, 'bad_request', '只支持 JPG、PNG、WebP 图片');
  const data = await c.req.arrayBuffer();
  if (data.byteLength === 0) return apiError(c, 400, 'bad_request', '图片是空的');
  if (data.byteLength > MAX_IMAGE_BYTES) return apiError(c, 413, 'too_large', '图片太大了，请换一张小一点的（最多 1.5MB）');

  const id = crypto.randomUUID();
  await purgeExpired(c.env.DB, today);
  await c.env.DB.prepare(
    `INSERT INTO day_images (id, category, use_date, mime, size, data, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT (category, use_date) DO UPDATE SET id = excluded.id, mime = excluded.mime, size = excluded.size,
       data = excluded.data, created_by = excluded.created_by, created_at = excluded.created_at`,
  )
    .bind(id, category, date, mime, data.byteLength, data, c.get('actor').name, now)
    .run();
  const meta = await c.env.DB.prepare(`SELECT ${META_COLUMNS} FROM day_images WHERE id = ?`).bind(id).first<DayImageMeta>();
  return c.json({ image: presentImage(meta!) }, 201);
});

images.delete('/:id', requireParent, async (c) => {
  const res = await c.env.DB.prepare('DELETE FROM day_images WHERE id = ?').bind(c.req.param('id')).run();
  if (res.meta.changes === 0) return apiError(c, 404, 'not_found', '图片不存在或已过期');
  return c.json({ ok: true });
});
