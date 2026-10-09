import { env, exports } from 'cloudflare:workers';
import { describe, expect, it } from 'vitest';

const KEY = 'test-family-key';
const call = (path: string, init?: RequestInit) => exports.default.fetch(new Request(`https://board.test${path}`, init));

describe('/api 鉴权', () => {
  it('没有口令返回 401', async () => {
    const res = await call('/api/ping');
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: { code: 'unauthorized', message: expect.any(String) } });
  });

  it('口令错误返回 401', async () => {
    const res = await call('/api/ping', { headers: { Authorization: 'Bearer wrong' } });
    expect(res.status).toBe(401);
  });

  it('口令正确返回 200，记录人取 X-Recorder', async () => {
    const res = await call('/api/ping', {
      headers: { Authorization: `Bearer ${KEY}`, 'X-Recorder': encodeURIComponent('奶奶') },
    });
    expect(res.status).toBe(200);
    const body = await res.json<{ ok: boolean; actor: { kind: string; name: string } }>();
    expect(body.ok).toBe(true);
    expect(body.actor).toEqual({ kind: 'app', name: '奶奶' });
  });

  it('没带 X-Recorder 时使用默认记录人', async () => {
    const res = await call('/api/ping', { headers: { Authorization: `Bearer ${KEY}` } });
    expect((await res.json<{ actor: { name: string } }>()).actor.name).toBe('照护人');
  });

  it('未配置 Access 时，伪造的 Access 头不被接受', async () => {
    const res = await call('/api/ping', { headers: { 'Cf-Access-Jwt-Assertion': 'forged.jwt.token' } });
    expect(res.status).toBe(401);
  });

  it('未知的 /api 路径鉴权后返回 JSON 404', async () => {
    const res = await call('/api/nope', { headers: { Authorization: `Bearer ${KEY}` } });
    expect(res.status).toBe(404);
    expect((await res.json<{ error: { code: string } }>()).error.code).toBe('not_found');
  });
});

describe('D1 迁移', () => {
  it('建好了 tasks / completions / settings，默认昵称为“崽崽”', async () => {
    const tables = await env.DB.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").all<{ name: string }>();
    const names = tables.results.map((r) => r.name);
    expect(names).toEqual(expect.arrayContaining(['completions', 'settings', 'tasks']));
    const nick = await env.DB.prepare("SELECT value FROM settings WHERE key = 'child_nickname'").first<string>('value');
    expect(nick).toBe('崽崽');
  });

  it('同一事项同一天同一时间点只能有一条登记', async () => {
    const now = Date.now();
    await env.DB.prepare(
      "INSERT INTO tasks (id, title, schedule_json, created_at, updated_at) VALUES ('t1', 'AD', '{}', ?, ?)",
    ).bind(now, now).run();
    const insert = (id: string) =>
      env.DB.prepare(
        "INSERT INTO completions (id, task_id, biz_date, slot, result, recorded_by, recorded_at) VALUES (?, 't1', '2026-10-10', '08:00', 'done', '奶奶', ?)",
      ).bind(id, now).run();
    await insert('c1');
    await expect(insert('c2')).rejects.toThrow(/UNIQUE/);
  });
});
