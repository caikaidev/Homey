import { env } from 'cloudflare:workers';
import { exportJWK, generateKeyPair, SignJWT } from 'jose';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import app from '../src/index';
import { addDays, bizDate } from '../src/bizdate';

const KEY = 'test-family-key';
const TEAM = 'https://team.test';
const AUD = 'test-aud';
const ORIGIN = 'https://board.test';
const accessEnv = { ...env, ACCESS_TEAM_DOMAIN: TEAM, ACCESS_AUD: AUD };

let privateKey: CryptoKey;
let jwks: { keys: unknown[] };
const realFetch = globalThis.fetch;
let gemini: (url: string, init?: RequestInit) => Promise<Response> | Response = () => new Response('unexpected', { status: 500 });
const geminiText = (obj: unknown) => Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify(obj) }] } }] });

beforeAll(async () => {
  const pair = await generateKeyPair('RS256');
  privateKey = pair.privateKey;
  jwks = { keys: [{ ...(await exportJWK(pair.publicKey)), kid: 'k1', alg: 'RS256' }] };
  // jose 会去团队域名取公钥，这里拦下返回测试公钥。
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = input instanceof Request ? input.url : String(input);
    if (url === `${TEAM}/cdn-cgi/access/certs`) return Response.json(jwks);
    if (url.startsWith('https://generativelanguage.googleapis.com/')) return gemini(url, init);
    return realFetch(input, init);
  });
});

async function parentToken(email: string) {
  return new SignJWT({ email })
    .setProtectedHeader({ alg: 'RS256', kid: 'k1' })
    .setIssuer(TEAM)
    .setAudience(AUD)
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(privateKey);
}

type Who = { parent: string } | { app: string };
async function call(who: Who, method: string, path: string, body?: unknown) {
  const headers: Record<string, string> = {};
  if ('parent' in who) headers['Cf-Access-Jwt-Assertion'] = await parentToken(who.parent);
  else {
    headers.Authorization = `Bearer ${KEY}`;
    headers['X-Recorder'] = encodeURIComponent(who.app);
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await app.fetch(
    new Request(`${ORIGIN}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) }),
    accessEnv,
  );
  return { status: res.status, body: (await res.json()) as any };
}

const dad = { parent: 'dad@example.com' };
const grandma = { app: '奶奶' };
const today = () => bizDate(Date.now());

async function createTask(title: string, schedule: object) {
  const res = await call(dad, 'POST', '/api/tasks', { title, schedule });
  expect(res.status).toBe(201);
  return res.body.task as { id: string; schedule: { start: string } };
}

describe('事项', () => {
  it('家长能建、改、暂停、恢复、归档；App 只能看', async () => {
    const t = await createTask('AD', { type: 'interval_days', every: 2, start: today(), times: ['08:00'] });
    expect((await call(grandma, 'POST', '/api/tasks', { title: 'x', schedule: {} })).status).toBe(403);

    const patched = await call(dad, 'PATCH', `/api/tasks/${t.id}`, { title: 'AD 一粒', note: '滴在嘴里' });
    expect(patched.body.task).toMatchObject({ title: 'AD 一粒', note: '滴在嘴里', describe: expect.stringContaining('隔天 08:00') });

    expect((await call(dad, 'PATCH', `/api/tasks/${t.id}/status`, { status: 'paused' })).body.task.status).toBe('paused');
    expect((await call(dad, 'PATCH', `/api/tasks/${t.id}/status`, { status: 'nope' })).status).toBe(400);
    expect((await call(dad, 'PATCH', `/api/tasks/${t.id}/status`, { status: 'archived' })).body.task.status).toBe('archived');

    const list = await call(grandma, 'GET', '/api/tasks?status=archived');
    expect(list.body.tasks.map((x: { id: string }) => x.id)).toContain(t.id);
  });

  it('排期不合法时 400', async () => {
    const res = await call(dad, 'POST', '/api/tasks', { title: 'AD', schedule: { type: 'daily', start: 'x', times: ['08:00'] } });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('bad_schedule');
  });

  it('记录创建人为家长显示名（设置里配了映射时）', async () => {
    await call(dad, 'PATCH', '/api/settings', { members: { parents: [{ email: 'Dad@Example.com', name: '爸爸' }], caregivers: ['奶奶'] } });
    const res = await call(dad, 'POST', '/api/tasks', { title: '钙', schedule: { type: 'daily', start: today(), times: ['19:00'] } });
    expect(res.body.task.created_by).toBe('爸爸');
  });
});

describe('看板与登记', () => {
  it('A4 暂停后不出现，恢复后按原锚点继续隔天', async () => {
    const start = addDays(today(), -1); // 昨天开始的隔天：今天没有，明天有
    const t = await createTask('隔天', { type: 'interval_days', every: 2, start, times: ['08:00'] });
    let b = (await call(grandma, 'GET', '/api/board')).body;
    expect(b.today.some((i: { task_id: string }) => i.task_id === t.id)).toBe(false);
    expect(b.tomorrow.items.some((i: { task_id: string }) => i.task_id === t.id)).toBe(true);

    await call(dad, 'PATCH', `/api/tasks/${t.id}/status`, { status: 'paused' });
    b = (await call(grandma, 'GET', '/api/board')).body;
    expect(b.tomorrow.items.some((i: { task_id: string }) => i.task_id === t.id)).toBe(false);

    await call(dad, 'PATCH', `/api/tasks/${t.id}/status`, { status: 'active' });
    b = (await call(grandma, 'GET', `/api/board?date=${addDays(today(), 1)}`)).body;
    expect(b.today.some((i: { task_id: string }) => i.task_id === t.id)).toBe(true);
  });

  it('A3 Web 和 App 同时确认，只有一条记录；撤销后可重新登记', async () => {
    const t = await createTask('钙', { type: 'daily', start: today(), times: ['19:00'] });
    const payload = { task_id: t.id, date: today(), slot: '19:00' };
    const [a, b] = await Promise.all([call(grandma, 'POST', '/api/checkin', payload), call(dad, 'POST', '/api/checkin', payload)]);
    expect([a.status, b.status].sort()).toEqual([200, 201]);
    expect(a.body.completion.id).toBe(b.body.completion.id);
    const { results } = await env.DB.prepare('SELECT * FROM completions WHERE task_id = ?').bind(t.id).all();
    expect(results).toHaveLength(1);

    const winner = a.status === 201 ? grandma : dad;
    const loser = a.status === 201 ? dad : grandma;
    expect((await call(loser, 'POST', '/api/undo', payload)).status).toBe(403);
    expect((await call(winner, 'POST', '/api/undo', payload)).status).toBe(200);
    expect((await call(winner, 'POST', '/api/undo', payload)).status).toBe(404);

    const again = await call(grandma, 'POST', '/api/skip', { ...payload, reason: '在外面' });
    expect(again.status).toBe(201);
    expect(again.body.completion).toMatchObject({ result: 'skipped', reason: '在外面', recorded_by: '奶奶', can_undo: true });
    const rows = await env.DB.prepare('SELECT COUNT(*) AS n FROM completions WHERE task_id = ?').bind(t.id).first<number>('n');
    expect(rows).toBe(1);
  });

  it('看板：未登记在前，已登记显示谁、几点；昵称跟着设置走', async () => {
    await call(dad, 'PATCH', '/api/settings', { child_nickname: '豆豆' });
    const t = await createTask('早晚', { type: 'daily', start: today(), times: ['08:00', '20:00'] });
    await call(grandma, 'POST', '/api/checkin', { task_id: t.id, date: today(), slot: '08:00' });
    const b = (await call(grandma, 'GET', '/api/board')).body;
    expect(b.nickname).toBe('豆豆');
    const mine = b.today.filter((i: { task_id: string }) => i.task_id === t.id);
    expect(mine.map((i: { slot: string; status: string }) => [i.slot, i.status])).toEqual([
      ['20:00', 'pending'],
      ['08:00', 'done'],
    ]);
    expect(mine[1].completion).toMatchObject({ recorded_by: '奶奶', source: 'app' });
    const firstDone = b.today.findIndex((i: { status: string }) => i.status !== 'pending');
    const lastPending = b.today.map((i: { status: string }) => i.status).lastIndexOf('pending');
    expect(lastPending).toBeLessThan(firstDone);
  });

  it('C4 超过 2 分钟不能撤销', async () => {
    const t = await createTask('超时', { type: 'daily', start: today(), times: ['09:00'] });
    const payload = { task_id: t.id, date: today(), slot: '09:00' };
    await call(grandma, 'POST', '/api/checkin', payload);
    const now = Date.now();
    const clock = vi.spyOn(Date, 'now').mockReturnValue(now + 2 * 60 * 1000 + 1);
    const res = await call(grandma, 'POST', '/api/undo', payload).finally(() => clock.mockRestore());
    expect(res.status).toBe(409);
    expect(res.body.error.code).toBe('undo_expired');
  });

  it('不在排期里的时间点、以后的日子、暂停的事项不能登记', async () => {
    const t = await createTask('校验', { type: 'interval_days', every: 2, start: today(), times: ['08:00'] });
    expect((await call(grandma, 'POST', '/api/checkin', { task_id: t.id, date: today(), slot: '09:00' })).body.error.code).toBe('not_scheduled');
    expect((await call(grandma, 'POST', '/api/checkin', { task_id: t.id, date: addDays(today(), -1), slot: '08:00' })).body.error.code).toBe('not_scheduled');
    expect((await call(grandma, 'POST', '/api/checkin', { task_id: t.id, date: addDays(today(), 2), slot: '08:00' })).status).toBe(400);
    expect((await call(grandma, 'POST', '/api/skip', { task_id: t.id, date: today(), slot: '08:00' })).status).toBe(400);
    await call(dad, 'PATCH', `/api/tasks/${t.id}/status`, { status: 'paused' });
    expect((await call(grandma, 'POST', '/api/checkin', { task_id: t.id, date: today(), slot: '08:00' })).status).toBe(409);
  });
});

describe('历史', () => {
  it('B5 显示记录人和时间，漏登记可见', async () => {
    const start = addDays(today(), -2);
    const t = await createTask('历史', { type: 'daily', start, times: ['08:00'] });
    // 事项“创建”于 3 天前
    await env.DB.prepare('UPDATE tasks SET created_at = ? WHERE id = ?').bind(Date.now() - 3 * 86400000, t.id).run();
    await call(grandma, 'POST', '/api/checkin', { task_id: t.id, date: addDays(today(), -1), slot: '08:00' });

    const { days } = (await call(dad, 'GET', '/api/history?days=3')).body;
    expect(days.map((d: { date: string }) => d.date)).toEqual([today(), addDays(today(), -1), addDays(today(), -2)]);
    const statusOf = (i: number) => days[i].items.find((x: { task_id: string }) => x.task_id === t.id);
    expect(statusOf(0)).toMatchObject({ status: 'pending' });
    expect(statusOf(1)).toMatchObject({ status: 'done', recorded_by: '奶奶', recorded_at: expect.any(Number) });
    expect(statusOf(2)).toMatchObject({ status: 'missed', recorded_by: null });
    expect((await call(dad, 'GET', '/api/history?days=0')).status).toBe(400);
  });
});

describe('设置与解析', () => {
  it('App 能读设置，不能改', async () => {
    expect((await call(grandma, 'GET', '/api/settings')).body).toHaveProperty('child_nickname');
    expect((await call(grandma, 'PATCH', '/api/settings', { child_nickname: 'x' })).status).toBe(403);
    expect((await call(dad, 'PATCH', '/api/settings', { members: { parents: [{ email: 'bad', name: 'x' }], caregivers: [] } })).status).toBe(400);
  });

  it('B2 一句话解析出排期和 7 天预览', async () => {
    const res = await call(dad, 'POST', '/api/parse', { text: 'AD隔天吃早上8点' });
    expect(res.body).toMatchObject({
      title: 'AD',
      schedule: { type: 'interval_days', every: 2, start: today(), times: ['08:00'] },
      describe: expect.stringContaining('隔天 08:00'),
      warnings: [],
    });
    expect(res.body.preview).toHaveLength(7);
    expect(res.body.preview.map((d: { times: string[] }) => d.times.length)).toEqual([1, 0, 1, 0, 1, 0, 1]);
  });
});

describe('CSRF', () => {
  it('跨站带 cookie 的写请求不被接受', async () => {
    const token = await parentToken('dad@example.com');
    const res = await app.fetch(
      new Request(`${ORIGIN}/api/tasks`, {
        method: 'POST',
        headers: { Cookie: `CF_Authorization=${token}`, Origin: 'https://evil.test', 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'x', schedule: { type: 'daily', start: today(), times: ['08:00'] } }),
      }),
      accessEnv,
    );
    expect(res.status).toBe(401);
  });
});

describe('Gemini 解析', () => {
  const withKey = { ...accessEnv, GEMINI_API_KEY: 'test-gemini-key' };
  async function parseWith(text: string) {
    const res = await app.fetch(
      new Request(`${ORIGIN}/api/parse`, {
        method: 'POST',
        headers: { 'Cf-Access-Jwt-Assertion': await parentToken('dad@example.com'), 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
      }),
      withKey,
    );
    return { status: res.status, body: (await res.json()) as any };
  }

  it('B4 用 Gemini 解析，Key 只在服务端请求头里，用药加医嘱提醒', async () => {
    let seen: { url: string; key: string | null } | null = null;
    gemini = (url, init) => {
      seen = { url, key: new Headers(init?.headers).get('x-goog-api-key') };
      return geminiText({
        title: '感冒药', kind: 'medicine', type: 'daily', times: ['08:00', '13:00', '19:00'],
        start: today(), end: addDays(today(), 2), warnings: ['没说具体时间，按常见时间填写'],
      });
    };
    const { status, body } = await parseWith('感冒药一天三次，吃3天');
    expect(status).toBe(200);
    expect(seen!.url).toContain('/models/gemini-3.8-flash:generateContent');
    expect(seen!.key).toBe('test-gemini-key');
    expect(body).toMatchObject({
      source: 'gemini', title: '感冒药', kind: 'medicine',
      schedule: { type: 'daily', times: ['08:00', '13:00', '19:00'], end: addDays(today(), 2) },
    });
    expect(body.warnings).toEqual(['没说具体时间，按常见时间填写', expect.stringContaining('医嘱')]);
    expect(JSON.stringify(body)).not.toContain('test-gemini-key');
  });

  it('地区不支持时降级到规则解析，并说明原因', async () => {
    gemini = () => Response.json({ error: { message: 'User location is not supported for the API use.' } }, { status: 400 });
    const { body } = await parseWith('AD隔天吃早上8点');
    expect(body).toMatchObject({ source: 'rules', title: 'AD', schedule: { type: 'interval_days', every: 2 } });
    expect(body.warnings[0]).toContain('地区不可用');
  });

  it('Gemini 给出不合法的排期时降级到规则解析', async () => {
    gemini = () => geminiText({ title: 'AD', kind: 'supplement', type: 'daily', times: ['8点'], start: today(), end: '', warnings: [] });
    const { body } = await parseWith('AD每天早上8点');
    expect(body.source).toBe('rules');
    expect(body.schedule.times).toEqual(['08:00']);
  });

  it('Gemini 返回 500 时降级', async () => {
    gemini = () => new Response('boom', { status: 500 });
    expect((await parseWith('钙每天晚上7点')).body).toMatchObject({ source: 'rules', title: '钙' });
  });

  it('weekdays + end 的事项能建、看板按星期出现', async () => {
    const start = today();
    const res = await call(dad, 'POST', '/api/tasks', {
      title: '每周', schedule: { type: 'weekdays', weekdays: [0, 1, 2, 3, 4, 5, 6], start, end: start, times: ['10:00'] },
    });
    expect(res.status).toBe(201);
    const b = (await call(grandma, 'GET', '/api/board')).body;
    expect(b.today.some((i: { task_id: string }) => i.task_id === res.body.task.id)).toBe(true);
    expect(b.tomorrow.items.some((i: { task_id: string }) => i.task_id === res.body.task.id)).toBe(false);
  });
});

