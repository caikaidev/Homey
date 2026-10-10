import { api } from '../api.js';
import { esc, errorView, icons, toast } from '../ui.js';

/** 事项管理：进行中 / 已暂停 / 已归档；编辑、暂停、恢复、归档。 */
export async function tasks(el) {
  async function load() {
    try {
      const { tasks } = await api('/tasks');
      draw(tasks);
    } catch (err) {
      el.innerHTML = errorView(err);
    }
  }

  function draw(list) {
    const by = (s) => list.filter((t) => t.status === s);
    const active = by('active');
    const paused = by('paused');
    const archived = by('archived');
    const edit = (t) => `<a class="btn sm" href="#/tasks/${encodeURIComponent(t.id)}/edit">编辑</a>`;

    el.innerHTML = `
      <header class="head head-row"><h1>事项</h1><a class="btn link" href="#/new">+ 新建</a></header>
      <h2 class="section-title">进行中 · ${active.length}</h2>
      ${
        active.length
          ? active
              .map(
                (t) => `<article class="card">
                  <div class="item"><div class="body"><div class="title">${esc(t.title)}</div><div class="sub">${esc(t.describe)}</div>
                  ${t.note ? `<div class="sub">${esc(t.note)}</div>` : ''}</div></div>
                  <div class="row">${edit(t)}
                    <button type="button" class="btn sm" data-status="paused" data-id="${esc(t.id)}">暂停</button>
                    <button type="button" class="btn sm ghost" data-status="archived" data-id="${esc(t.id)}" data-title="${esc(t.title)}">归档</button>
                  </div></article>`,
              )
              .join('')
          : '<div class="card dashed muted">还没有进行中的事项。</div>'
      }
      ${
        paused.length
          ? `<h2 class="section-title">已暂停 · ${paused.length}</h2>
            ${paused
              .map(
                (t) => `<article class="card paused">
                  <div class="item"><div class="body"><div class="title" style="color:var(--ink-2)">${esc(t.title)}</div>
                  <div class="sub row" style="gap:4px">${icons.pause.replace('<svg', '<svg style="width:14px;height:14px"')}暂停中 · 不会出现在看板</div></div></div>
                  <div class="row">${edit(t)}
                    <button type="button" class="btn sm dark" data-status="active" data-id="${esc(t.id)}">恢复</button>
                    <button type="button" class="btn sm ghost" data-status="archived" data-id="${esc(t.id)}" data-title="${esc(t.title)}">归档</button>
                  </div></article>`,
              )
              .join('')}`
          : ''
      }
      ${
        archived.length
          ? `<details class="small muted"><summary class="pad" style="cursor:pointer">已归档 · ${archived.length}</summary>
              ${archived
                .map(
                  (t) => `<div class="row pad"><span class="grow">${esc(t.title)} <span class="small">· ${esc(t.describe)}</span></span>
                    <button type="button" class="btn sm" data-status="active" data-id="${esc(t.id)}">恢复</button></div>`,
                )
                .join('')}
            </details>`
          : ''
      }`;
  }

  el.onclick = async (e) => {
    const btn = e.target.closest('button[data-status]');
    if (!btn) return;
    const { status, id, title } = btn.dataset;
    if (status === 'archived' && !confirm(`归档“${title}”？归档后不再出现在看板，记录会保留。`)) return;
    btn.disabled = true;
    try {
      await api(`/tasks/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } });
      toast(status === 'paused' ? '已暂停' : status === 'archived' ? '已归档' : '已恢复');
    } catch (err) {
      toast(err.message);
    }
    load();
  };

  await load();
  return () => (el.onclick = null);
}
