import { api } from '../api.js';
import { esc, errorView, toast } from '../ui.js';

/** 设置：宝宝昵称、家长邮箱 → 显示名、照护人名单，以及给 App 用的服务器地址。 */
export async function settings(el) {
  let s;
  let me;
  try {
    [s, me] = await Promise.all([api('/settings'), api('/ping')]);
  } catch (err) {
    el.innerHTML = errorView(err);
    return;
  }
  const parents = s.members.parents.map((p) => ({ ...p }));
  const caregivers = [...s.members.caregivers];

  function draw() {
    el.innerHTML = `
      <header class="head"><h1>设置</h1><div class="small muted">当前登录：${esc(me.actor.name)}</div></header>

      <section class="card">
        <label for="nick" style="font-weight:500">宝宝昵称</label>
        <div class="row">
          <input id="nick" class="input" maxlength="20" value="${esc(s.child_nickname)}">
          <button type="button" class="btn dark" data-save-nick>保存</button>
        </div>
        <div class="small muted">看板上会显示为“<strong style="color:var(--ink)" data-nick-preview>${esc(s.child_nickname)}</strong>今天的事”，奶奶的 App 也会跟着变。</div>
      </section>

      <section class="card">
        <h2 style="font-size:15px;font-weight:500">家长（Web 登录邮箱 → 显示名）</h2>
        ${parents
          .map(
            (p, i) => `<div class="grid2" style="grid-template-columns: minmax(0,3fr) minmax(0,2fr) auto; align-items:end">
              <label class="field">邮箱<input type="email" data-p="${i}" data-k="email" value="${esc(p.email)}" placeholder="name@example.com"></label>
              <label class="field">显示名<input data-p="${i}" data-k="name" maxlength="20" value="${esc(p.name)}" placeholder="爸爸"></label>
              <button type="button" class="x" data-del-p="${i}" aria-label="删除" style="height:44px">✕</button>
            </div>`,
          )
          .join('')}
        <button type="button" class="btn link" style="align-self:flex-start" data-add-p>+ 添加家长</button>
        <div class="small muted">能不能登录由 Cloudflare Access 的邮箱名单决定，这里只设显示名。没设的显示邮箱前缀。</div>
      </section>

      <section class="card">
        <h2 style="font-size:15px;font-weight:500">照护人（App 里选“我是谁”）</h2>
        <div class="chips">
          ${caregivers.map((n, i) => `<button type="button" class="chip" data-del-c="${i}" aria-label="删除 ${esc(n)}">${esc(n)} <span aria-hidden="true">✕</span></button>`).join('')}
        </div>
        <div class="row">
          <input class="input" data-new-c maxlength="20" placeholder="如：奶奶">
          <button type="button" class="btn" data-add-c>添加</button>
        </div>
      </section>

      <p class="error" data-error hidden></p>
      <button type="button" class="btn primary big" data-save-members>保存家长和照护人</button>

      <section class="card">
        <h2 style="font-size:15px;font-weight:500">给奶奶的手机配 App</h2>
        <div class="small" style="color:var(--ink-2)">服务器地址：<span class="mono">${esc(location.origin)}</span><br>
          家庭口令：在 Cloudflare 后台 Worker 的“变量和机密”里，网页上不显示。</div>
      </section>`;
  }

  async function save(body, okMsg) {
    const err = el.querySelector('[data-error]');
    err.hidden = true;
    try {
      s = await api('/settings', { method: 'PATCH', body });
      toast(okMsg);
      return true;
    } catch (e) {
      err.textContent = e.message;
      err.hidden = false;
      toast(e.message);
      return false;
    }
  }

  el.oninput = (e) => {
    const t = e.target;
    if (t.dataset.p !== undefined) parents[+t.dataset.p][t.dataset.k] = t.value;
    else if (t.id === 'nick') el.querySelector('[data-nick-preview]').textContent = t.value || '崽崽';
  };
  el.onclick = async (e) => {
    const t = e.target.closest('button');
    if (!t) return;
    if (t.hasAttribute('data-save-nick')) {
      if (await save({ child_nickname: el.querySelector('#nick').value }, '昵称已保存')) draw();
    } else if (t.hasAttribute('data-add-p')) {
      parents.push({ email: '', name: '' });
      draw();
    } else if (t.dataset.delP !== undefined) {
      parents.splice(+t.dataset.delP, 1);
      draw();
    } else if (t.dataset.delC !== undefined) {
      caregivers.splice(+t.dataset.delC, 1);
      draw();
    } else if (t.hasAttribute('data-add-c')) {
      const input = el.querySelector('[data-new-c]');
      const name = input.value.trim();
      if (name && !caregivers.includes(name)) caregivers.push(name);
      draw();
    } else if (t.hasAttribute('data-save-members')) {
      const list = parents.filter((p) => p.email.trim() || p.name.trim());
      if (await save({ members: { parents: list, caregivers } }, '已保存')) {
        me = await api('/ping').catch(() => me);
        draw();
      }
    }
  };
  el.onkeydown = (e) => {
    if (e.key === 'Enter' && e.target.matches('[data-new-c]')) el.querySelector('[data-add-c]').click();
  };

  draw();
  return () => (el.oninput = el.onclick = el.onkeydown = null);
}
