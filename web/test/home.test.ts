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

beforeAll(async () => {
  const pair = await generateKeyPair('RS256');
  privateKey = pair.privateKey;
  jwks = { keys: [{ ...(await exportJWK(pair.publicKey)), kid: 'k1', alg: 'RS256' }] };
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = input instanceof Request ? input.url : String(input);
    if (url === `${TEAM}/cdn-cgi/access/certs`) return Response.json(jwks);
    return realFetch(input, init);
  });
});

type Who = { parent: string } | { app: string };
async function send(who: Who, method: string, path: string, body?: BodyInit, contentType?: string) {
  const headers: Record<string, string> = {};
  if ('parent' in who) {
    headers['Cf-Access-Jwt-Assertion'] = await new SignJWT({ email: who.parent })
      .setProtectedHeader({ alg: 'RS256', kid: 'k1' })
      .setIssuer(TEAM)
      .setAudience(AUD)
      .setIssuedAt()
      .setExpirationTime('1h')
      .sign(privateKey);
  } else {
    headers.Authorization = `Bearer ${KEY}`;
    headers['X-Recorder'] = encodeURIComponent(who.app);
  }
  if (contentType) headers['Content-Type'] = contentType;
  return app.fetch(new Request(`${ORIGIN}${path}`, { method, headers, body }), accessEnv);
}
async function call(who: Who, method: string, path: string, body?: unknown) {
  const res = await send(who, method, path, body === undefined ? undefined : JSON.stringify(body), body === undefined ? undefined : 'application/json');
  return { status: res.status, body: (await res.json()) as any };
}

const dad = { parent: 'dad@example.com' };
const grandma = { app: '奶奶' };
const today = () => bizDate(Date.now());
const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4]);

describe('首页卡片', () => {
  it('有进行中事项的类别才出现，附今天还剩几件；改类别后跟着变', async () => {
    expect((await call(grandma, 'GET', '/api/home')).body.cards).toEqual([]);

    const daily = { type: 'daily', start: today(), times: ['08:00', '19:00'] };
    const ad = (await call(dad, 'POST', '/api/tasks', { title: 'AD', schedule: daily })).body.task;
    await call(grandma, 'POST', '/api/checkin', { task_id: ad.id, date: today(), slot: '08:00' });
    let cards = (await call(grandma, 'GET', '/api/home')).body.cards;
    expect(cards).toEqual([{ key: 'supplement', type: 'tasks', title: '补剂', total: 2, pending: 1 }]);

    const later = { type: 'daily', start: addDays(today(), 1), times: ['08:00'] };
    await call(dad, 'POST', '/api/tasks', { title: '感冒药', kind: 'medicine', schedule: later });
    cards = (await call(grandma, 'GET', '/api/home')).body.cards;
    expect(cards.map((c: any) => c.key)).toEqual(['supplement', 'medicine']);
    expect(cards[1]).toMatchObject({ title: '药品', total: 0, pending: 0 });

    const moved = await call(dad, 'PATCH', `/api/tasks/${ad.id}`, { kind: 'medicine' });
    expect(moved.body.task.kind).toBe('medicine');
    expect((await call(dad, 'PATCH', `/api/tasks/${ad.id}`, { kind: 'toy' })).status).toBe(400);
    cards = (await call(grandma, 'GET', '/api/home')).body.cards;
    expect(cards).toEqual([{ key: 'medicine', type: 'tasks', title: '药品', total: 2, pending: 1 }]);
  });
});

describe('宝宝屋图片', () => {
  it('家长按日期上传，今天的出现在首页；App 能取图，不能传', async () => {
    const put = await send(dad, 'PUT', `/api/images/babyroom/${today()}`, PNG, 'image/png');
    expect(put.status).toBe(201);
    const { image } = (await put.json()) as any;
    expect(image).toMatchObject({ category: 'babyroom', use_date: today(), mime: 'image/png', size: PNG.length, created_by: 'dad' });

    expect((await send(grandma, 'PUT', `/api/images/babyroom/${today()}`, PNG, 'image/png')).status).toBe(403);

    const card = (await call(grandma, 'GET', '/api/home')).body.cards.find((c: any) => c.key === 'babyroom');
    expect(card).toMatchObject({ type: 'image', title: '宝宝屋', image: { id: image.id, url: `/api/images/${image.id}` } });

    const raw = await send(grandma, 'GET', image.url);
    expect(raw.headers.get('Content-Type')).toBe('image/png');
    expect(new Uint8Array(await raw.arrayBuffer())).toEqual(PNG);

    // 同一天再传是替换：换新 id，旧 id 取不到
    const again = (await (await send(dad, 'PUT', `/api/images/babyroom/${today()}`, PNG, 'image/jpeg')).json()) as any;
    expect(again.image.id).not.toBe(image.id);
    expect((await send(grandma, 'GET', image.url)).status).toBe(404);

    expect((await call(dad, 'DELETE', `/api/images/${again.image.id}`)).body).toEqual({ ok: true });
    expect((await call(grandma, 'GET', '/api/home')).body.cards.some((c: any) => c.key === 'babyroom')).toBe(false);
  });

  it('只显示当天的；过期的被删掉；参数不对会拒绝', async () => {
    const tomorrow = addDays(today(), 1);
    expect((await send(dad, 'PUT', `/api/images/babyroom/${tomorrow}`, PNG, 'image/png')).status).toBe(201);
    expect((await call(grandma, 'GET', '/api/home')).body.cards.some((c: any) => c.key === 'babyroom')).toBe(false);
    expect((await call(dad, 'GET', '/api/images')).body.images.map((m: any) => m.use_date)).toEqual([tomorrow]);

    // 模拟一张已过期的图
    await env.DB.prepare(
      "INSERT INTO day_images (id, category, use_date, mime, size, data, created_at) VALUES ('old', 'babyroom', ?, 'image/png', 1, x'00', 0)",
    )
      .bind(addDays(today(), -1))
      .run();
    await call(grandma, 'GET', '/api/home');
    expect(await env.DB.prepare("SELECT id FROM day_images WHERE id = 'old'").first()).toBeNull();

    expect((await send(dad, 'PUT', `/api/images/babyroom/${addDays(today(), -1)}`, PNG, 'image/png')).status).toBe(400);
    expect((await send(dad, 'PUT', `/api/images/toys/${today()}`, PNG, 'image/png')).status).toBe(400);
    expect((await send(dad, 'PUT', `/api/images/babyroom/${today()}`, PNG, 'text/plain')).status).toBe(400);
    expect((await send(dad, 'PUT', `/api/images/babyroom/${today()}`, new Uint8Array(1_500_001), 'image/png')).status).toBe(413);
  });
});
