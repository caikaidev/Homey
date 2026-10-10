import { api } from '../api.js';
import { esc, errorView, hhmm, md, todayBiz, addDays } from '../ui.js';

const RESULT = { done: '✓ 已喂', skipped: '已跳过', missed: '未登记', pending: '待登记' };
const DAYS = 14;

/** 最近 14 天记录：按天分组，显示结果、记录人、时间；漏登记标出来。 */
export async function history(el) {
  let data;
  try {
    data = await api(`/history?days=${DAYS}`);
  } catch (err) {
    el.innerHTML = errorView(err);
    return;
  }
  const all = data.days.flatMap((d) => d.items);
  const count = (s) => all.filter((i) => i.status === s).length;
  const due = all.filter((i) => i.status !== 'pending').length;
  const today = todayBiz();

  const label = (d) =>
    d.date === today ? `今天 · ${md(d.date)} ${d.weekday}` : d.date === addDays(today, -1) ? `昨天 · ${md(d.date)} ${d.weekday}` : `${md(d.date)} ${d.weekday}`;

  const who = (i) => {
    if (i.status === 'missed') return '当天没人点';
    if (i.status === 'pending') return '—';
    const base = `${esc(i.recorded_by)} ${hhmm(i.recorded_at)}`;
    return i.status === 'skipped' && i.reason ? `${base} · ${esc(i.reason)}` : base;
  };

  el.innerHTML = `
    <header class="head"><h1>记录</h1></header>
    <div class="stats">
      <div class="stat"><span class="k">近 ${DAYS} 天已喂</span><span class="v">${count('done')} / ${due}</span></div>
      <div class="stat"><span class="k">跳过</span><span class="v">${count('skipped')}</span></div>
      <div class="stat"><span class="k">未登记</span><span class="v" style="color:var(--warn)">${count('missed')}</span></div>
    </div>
    <div class="cards">${data.days
      .filter((d) => d.items.length)
      .map(
        (d) => `<section class="stack" style="gap:6px">
          <h2 class="section-title">${esc(label(d))}</h2>
          <div class="list">${d.items
            .map(
              (i) => `<div class="list-row"><span class="time">${esc(i.slot)}</span><span class="title">${esc(i.title)}</span>
                <span class="res"><span class="r ${i.status}">${RESULT[i.status]}</span><span class="w">${who(i)}</span></span></div>`,
            )
            .join('')}</div>
        </section>`,
      )
      .join('') || '<div class="card dashed muted">最近还没有记录。</div>'}</div>`;
}
