import { api } from '../api.js';
import { badge, esc, errorView, hhmm, md, toast } from '../ui.js';

const SOURCE = { web: '', app: '（App）', widget: '（小组件）' };

/** 今日看板：未登记在前；确认、跳过、2 分钟内撤销；明天只读。跨零点自动换到新的一天。 */
export async function today(el) {
  let board = null;
  let busy = false;

  async function load() {
    try {
      board = await api('/board');
      draw();
    } catch (err) {
      el.innerHTML = errorView(err);
    }
  }

  function draw() {
    const b = board;
    document.title = `${b.nickname}今天的事 · 崽崽小看板`;
    const items = b.today
      .map((i) => {
        const c = i.completion;
        const record = c
          ? `${esc(c.recorded_by)} · ${hhmm(c.recorded_at)} ${c.result === 'done' ? '登记' : '跳过'}${SOURCE[c.source] ?? ''}${c.reason ? `：${esc(c.reason)}` : ''}`
          : '';
        const key = `data-task="${esc(i.task_id)}" data-slot="${esc(i.slot)}"`;
        return `<article class="card">
          <div class="item">
            <div class="time">${esc(i.slot)}</div>
            <div class="body"><div class="title">${esc(i.title)}</div>${i.note ? `<div class="sub">${esc(i.note)}</div>` : ''}</div>
            ${badge(i.status)}
          </div>
          ${
            i.status === 'pending'
              ? `<div class="row indent">
                  <button type="button" class="btn primary grow" data-act="checkin" ${key}>确认已喂</button>
                  <button type="button" class="btn" data-act="skip" data-title="${esc(i.title)}" ${key}>跳过</button>
                </div>`
              : `<div class="row indent small muted"><span class="grow">${record}</span>${
                  c.can_undo ? `<button type="button" class="btn sm" data-act="undo" ${key}>撤销</button>` : ''
                }</div>`
          }
        </article>`;
      })
      .join('');

    const tomorrow = b.tomorrow.items.length
      ? b.tomorrow.items.map((i) => `<div class="tomorrow-row"><span class="time">${esc(i.slot)}</span><span>${esc(i.title)}</span></div>`).join('')
      : '<div class="muted small">明天没有要做的事</div>';

    el.innerHTML = `
      <header class="head">
        <div class="muted small">${md(b.date)} · ${esc(b.weekday)}</div>
        <h1>${esc(b.nickname)}今天的事</h1>
        <div class="row">
          ${
            b.pending_count
              ? `<span class="pill warn">还有 ${b.pending_count} 件没登记</span>`
              : b.today.length
                ? '<span class="pill ok">今天的都登记好了</span>'
                : ''
          }
          <span class="small muted">共 ${b.today.length} 件</span>
        </div>
      </header>
      ${b.today.length ? `<div class="cards">${items}</div>` : `<div class="card dashed"><span class="muted">今天没有要做的事。</span><a class="btn dark" href="#/new">新建事项</a></div>`}
      <h2 class="section-title">明天 · ${md(b.tomorrow.date)} <span class="tag">只读</span></h2>
      <div class="card dashed">${tomorrow}</div>`;
  }

  async function act(btn) {
    if (busy) return;
    const { act, task, slot } = btn.dataset;
    const payload = { task_id: task, date: board.date, slot };
    if (act === 'skip') {
      const reason = await askReason(btn.dataset.title);
      if (!reason) return;
      payload.reason = reason;
    }
    busy = true;
    btn.disabled = true;
    try {
      await api(`/${act}`, { method: 'POST', body: payload });
      toast(act === 'checkin' ? '已登记' : act === 'skip' ? '已跳过' : '已撤销');
    } catch (err) {
      toast(err.message);
    } finally {
      busy = false;
    }
    await load();
  }

  el.onclick = (e) => {
    const btn = e.target.closest('button[data-act]');
    if (btn) act(btn);
  };

  await load();
  // 每分钟刷新：撤销按钮到时消失、别人登记的同步过来、过了零点换到新的一天。
  const timer = setInterval(() => document.visibilityState === 'visible' && load(), 60 * 1000);
  const onVisible = () => document.visibilityState === 'visible' && load();
  document.addEventListener('visibilitychange', onVisible);
  return () => {
    clearInterval(timer);
    document.removeEventListener('visibilitychange', onVisible);
    el.onclick = null;
  };
}

/** 弹出跳过原因；取消返回 null。 */
function askReason(title) {
  const dlg = document.getElementById('skip-dialog');
  const form = dlg.querySelector('form');
  const input = form.elements.reason;
  dlg.querySelector('[data-title]').textContent = title;
  input.value = '';
  dlg.querySelector('[data-reasons]').onclick = (e) => {
    const chip = e.target.closest('.chip');
    if (chip) input.value = chip.value;
  };
  dlg.showModal();
  return new Promise((resolve) => {
    dlg.addEventListener(
      'close',
      () => resolve(dlg.returnValue === 'ok' && input.value.trim() ? input.value.trim() : null),
      { once: true },
    );
  });
}
