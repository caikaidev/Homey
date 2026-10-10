import { Hono } from 'hono';
import type { AppEnv } from './env';
import { requireActor } from './auth';
import { apiError } from './errors';
import { board } from './api/board';
import { parse } from './api/parse';
import { records } from './api/records';
import { settings } from './api/settings';
import { tasks } from './api/tasks';

const app = new Hono<AppEnv>();

const api = new Hono<AppEnv>();
api.use('*', requireActor);

api.get('/ping', (c) => {
  const actor = c.get('actor');
  return c.json({ ok: true, actor: { kind: actor.kind, name: actor.name }, serverTime: Date.now() });
});

api.route('/', board);
api.route('/', records);
api.route('/tasks', tasks);
api.route('/settings', settings);
api.route('/parse', parse);

// 放在所有接口之后：未知的 /api 路径返回 JSON 404，而不是落到静态页。
api.all('*', (c) => apiError(c, 404, 'not_found', '没有这个接口'));

app.route('/api', api);

// 非 /api 的请求由静态资源处理（wrangler.toml 的 run_worker_first 只把 /api/* 交给 Worker）。
app.all('*', (c) => c.env.ASSETS.fetch(c.req.raw));

app.onError((err, c) => {
  console.error(err);
  return apiError(c, 500, 'internal', '服务器出错了');
});

export default app;
