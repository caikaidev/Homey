import { api } from '../api.js';
import { addDays, esc, errorView, icons, md, toast, todayBiz, weekdayOf } from '../ui.js';

/**
 * 新建（#/new）与编辑（#/tasks/<id>/edit）。新建：一句话 → 解析 → 核对表单 → 保存；
 * 解析不准或失败时直接改表单。保存前一定先给用户看描述和 7 天预览。
 */
export async function editor(el, taskId) {
  const isEdit = Boolean(taskId);
  let form = null; // { title, every, times[], start, note }
  let warnings = [];
  let saving = false;

  if (isEdit) {
    try {
      const { task } = await api(`/tasks/${encodeURIComponent(taskId)}`);
      const s = task.schedule;
      form = { title: task.title, every: s.type === 'daily' ? 1 : s.every, times: [...s.times], start: s.start, note: task.note };
    } catch (err) {
      el.innerHTML = errorView(err);
      return;
    }
  }

  function blank() {
    return { title: '', every: 1, times: ['08:00'], start: todayBiz(), note: '' };
  }

  function schedule() {
    const times = [...new Set(form.times.filter(Boolean))].sort();
    return form.every > 1
      ? { type: 'interval_days', every: form.every, start: form.start, times }
      : { type: 'daily', start: form.start, times };
  }

  function describe(s) {
    const when = s.type === 'daily' ? '每天' : s.every === 2 ? '隔天' : `每 ${s.every} 天`;
    const from = s.start === todayBiz() ? '今天' : s.start === addDays(todayBiz(), 1) ? '明天' : md(s.start);
    return `${esc(form.title || '（未命名）')}，<span style="color:var(--warn)">${when}</span> ${
      s.times.length ? `<span style="color:var(--warn)">${s.times.join('、')}</span>` : '（还没填时间）'
    }，从${from}开始`;
  }

  function preview(s) {
    const from = todayBiz() > s.start ? todayBiz() : s.start;
    return Array.from({ length: 7 }, (_, i) => {
      const date = addDays(from, i);
      const diff = Math.round((Date.parse(date) - Date.parse(s.start)) / 86400000);
      const on = diff >= 0 && (s.type === 'daily' || diff % s.every === 0) && s.times.length > 0;
      return `<div class="d ${on ? 'on' : ''}"><span class="wd">${weekdayOf(date)}</span><span class="day">${+date.slice(8)}</span>
        <span class="mark">${on ? esc(s.times.length > 1 ? `${s.times.length} 次` : s.times[0]) : '—'}</span></div>`;
    }).join('');
  }

  function freqValue() {
    return form.every === 1 ? '1' : form.every === 2 ? '2' : 'n';
  }

  function drawForm() {
    const s = schedule();
    return `<section class="card" aria-label="核对">
      <div class="row"><h2 class="grow" style="font-size:16px">${isEdit ? '修改' : '请核对'}</h2>
        ${warnings.length || isEdit ? '' : '<span class="tag">解析结果 · 仅供核对</span>'}</div>
      <p class="describe" data-describe style="margin:0">${describe(s)}</p>
      ${warnings.map((w) => `<div class="note" role="note">${icons.warn}<div>${esc(w)}</div></div>`).join('')}
      <div class="grid2">
        <label class="field span2">名称<input name="title" maxlength="40" value="${esc(form.title)}" placeholder="如：AD 一粒" required></label>
        <label class="field">频率
          <select name="freq">
            <option value="1" ${freqValue() === '1' ? 'selected' : ''}>每天</option>
            <option value="2" ${freqValue() === '2' ? 'selected' : ''}>隔天</option>
            <option value="n" ${freqValue() === 'n' ? 'selected' : ''}>每 N 天</option>
          </select></label>
        ${
          freqValue() === 'n'
            ? `<label class="field">每几天一次<input name="every" type="number" min="2" max="30" inputmode="numeric" value="${form.every}"></label>`
            : `<label class="field">开始<input name="start" type="date" value="${esc(form.start)}"></label>`
        }
        ${freqValue() === 'n' ? `<label class="field span2">开始<input name="start" type="date" value="${esc(form.start)}"></label>` : ''}
        <div class="field span2">时间
          <div class="times">
            ${form.times
              .map(
                (t, i) => `<span class="time-input"><input class="input" type="time" data-time="${i}" value="${esc(t)}" aria-label="第 ${i + 1} 个时间">${
                  form.times.length > 1 ? `<button type="button" class="x" data-del-time="${i}" aria-label="删除这个时间">✕</button>` : ''
                }</span>`,
              )
              .join('')}
            ${form.times.length < 6 ? '<button type="button" class="btn link" data-add-time>+ 再加一个时间</button>' : ''}
          </div>
        </div>
        <label class="field span2">备注（可不填）<textarea name="note" maxlength="200" placeholder="如：滴在嘴里，饭后">${esc(form.note)}</textarea></label>
      </div>
      <div class="stack">
        <h3 class="small muted" style="font-weight:500">未来 7 天</h3>
        <div class="week" data-week>${preview(s)}</div>
      </div>
      <p class="error" data-error hidden></p>
      <div class="stack">
        <button type="button" class="btn primary big" data-save>${isEdit ? '保存修改' : '确认无误，保存'}</button>
        <a class="btn ghost" href="${isEdit ? '#/tasks' : '#/today'}">取消</a>
      </div>
    </section>`;
  }

  function draw() {
    el.innerHTML = `
      <header class="head"><div class="row">
        <a class="back" href="${isEdit ? '#/tasks' : '#/today'}" aria-label="返回">${icons.back}</a>
        <h1 style="font-size:20px">${isEdit ? '编辑事项' : '新建事项'}</h1></div></header>
      ${
        isEdit
          ? ''
          : `<section class="card">
              <label for="say" class="small muted">说一句话，比如“AD 隔天吃，早上 8 点”</label>
              <div class="row">
                <input id="say" class="input say" autocomplete="off" maxlength="200" placeholder="钙每天晚上7点">
                ${speechSupported() ? `<button type="button" class="icon-btn" data-mic aria-label="语音输入">${icons.mic}</button>` : ''}
              </div>
              <button type="button" class="btn dark" data-parse>解析</button>
              ${form ? '' : '<button type="button" class="btn link" data-manual>不用解析，我自己填</button>'}
            </section>`
      }
      <div data-form>${form ? drawForm() : ''}</div>`;
  }

  /** 改表单时只重画描述和预览，避免输入框失去焦点。 */
  function refreshPreview() {
    const s = schedule();
    el.querySelector('[data-describe]').innerHTML = describe(s);
    el.querySelector('[data-week]').innerHTML = preview(s);
  }

  function redrawForm() {
    el.querySelector('[data-form]').innerHTML = drawForm();
  }

  async function parse() {
    const say = el.querySelector('#say');
    const text = say.value.trim();
    if (!text) return say.focus();
    const btn = el.querySelector('[data-parse]');
    btn.disabled = true;
    btn.textContent = '解析中…';
    try {
      const r = await api('/parse', { method: 'POST', body: { text } });
      const s = r.schedule;
      form = { title: r.title, every: s.type === 'daily' ? 1 : s.every, times: [...s.times], start: s.start, note: '' };
      warnings = r.warnings;
    } catch (err) {
      form = form ?? blank();
      warnings = [`没解析出来（${err.message}），请直接填写下面的表单`];
    }
    draw();
    el.querySelector('#say').value = text;
    el.querySelector('[data-form]').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async function save() {
    if (saving) return;
    const err = el.querySelector('[data-error]');
    const s = schedule();
    const problem = !form.title.trim() ? '请填写名称' : !s.times.length ? '至少要有一个时间' : !form.start ? '请选择开始日期' : '';
    if (problem) {
      err.textContent = problem;
      err.hidden = false;
      return;
    }
    saving = true;
    const btn = el.querySelector('[data-save]');
    btn.disabled = true;
    try {
      const body = { title: form.title.trim(), schedule: s, note: form.note.trim() };
      if (isEdit) await api(`/tasks/${encodeURIComponent(taskId)}`, { method: 'PATCH', body });
      else await api('/tasks', { method: 'POST', body });
      toast(isEdit ? '已保存' : '已新建');
      location.hash = isEdit ? '#/tasks' : '#/today';
    } catch (e) {
      err.textContent = e.message;
      err.hidden = false;
      btn.disabled = false;
      saving = false;
    }
  }

  el.oninput = (e) => {
    if (!form) return;
    const t = e.target;
    if (t.name === 'title') form.title = t.value;
    else if (t.name === 'note') form.note = t.value;
    else if (t.name === 'start') form.start = t.value;
    else if (t.name === 'every') form.every = Math.min(30, Math.max(2, parseInt(t.value, 10) || 2));
    else if (t.dataset.time !== undefined) form.times[+t.dataset.time] = t.value;
    else return;
    refreshPreview();
  };
  el.onchange = (e) => {
    if (e.target.name !== 'freq') return;
    const v = e.target.value;
    form.every = v === '1' ? 1 : v === '2' ? 2 : Math.max(form.every, 3);
    redrawForm();
  };
  el.onclick = (e) => {
    const t = e.target.closest('button');
    if (!t) return;
    if (t.hasAttribute('data-parse')) parse();
    else if (t.hasAttribute('data-manual')) {
      form = blank();
      warnings = [];
      draw();
      el.querySelector('[name=title]').focus();
    } else if (t.hasAttribute('data-add-time')) {
      form.times.push(form.times.length ? '19:00' : '08:00');
      redrawForm();
    } else if (t.dataset.delTime !== undefined) {
      form.times.splice(+t.dataset.delTime, 1);
      redrawForm();
    } else if (t.hasAttribute('data-save')) save();
    else if (t.hasAttribute('data-mic')) listen(t);
  };
  el.onkeydown = (e) => {
    if (e.key === 'Enter' && e.target.id === 'say') {
      e.preventDefault();
      parse();
    }
  };

  draw();
  return () => {
    el.oninput = el.onchange = el.onclick = el.onkeydown = null;
  };

  function listen(btn) {
    const Rec = window.SpeechRecognition || window.webkitSpeechRecognition;
    const rec = new Rec();
    rec.lang = 'zh-CN';
    rec.interimResults = false;
    btn.classList.add('listening');
    rec.onresult = (ev) => {
      el.querySelector('#say').value = ev.results[0][0].transcript;
      parse();
    };
    rec.onerror = () => toast('没听清，请再说一次或直接输入');
    rec.onend = () => btn.classList.remove('listening');
    rec.start();
  }
}

function speechSupported() {
  return Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
}
