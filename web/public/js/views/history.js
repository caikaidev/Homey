import { api } from '../api.js';
import { esc, errorView, hhmm, md, todayBiz, addDays } from '../ui.js';

const RESULT = { done: '✓ 已喂', skipped: '已跳过', missed: '未登记', pending: '待登记' };
const DAYS = 14;
const REVIEW_DAYS = 7;
const SOURCE = { app: '奶奶 App', web: '网页', widget: '小组件' };

/** 最近 14 天记录：按天分组，显示结果、记录人、时间；漏登记标出来。 */
export async function history(el) {
  let data, review;
  try {
    [data, review] = await Promise.all([api(`/history?days=${DAYS}`), api(`/review?days=${REVIEW_DAYS}`)]);
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
    ${reviewCard(review)}
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

/** 试用复盘：P4 七天试用时看这几项（docs/TRIAL.md）。 */
function reviewCard(r) {
  if (!r.due) return '';
  const pct = r.on_time_rate === null ? '—' : `${Math.round(r.on_time_rate * 100)}%`;
  const delay =
    r.median_delay_min === null ? '—' : r.median_delay_min <= 0 ? '准点或提前' : `晚 ${r.median_delay_min} 分钟`;
  const list = (obj, names = {}) =>
    Object.entries(obj)
      .sort((a, b) => b[1] - a[1])
      .map(([k, n]) => `${esc(names[k] ?? k)} ${n}`)
      .join(' · ') || '—';
  const flags = [
    r.missed ? `${r.missed} 次没人登记` : '',
    r.late_day ? `${r.late_day} 次隔天补登` : '',
    r.undone ? `${r.undone} 次撤销（可能点错）` : '',
  ].filter(Boolean);
  return `<section class="card">
    <h2 class="section-title" style="margin:0">试用复盘 · 近 ${REVIEW_DAYS} 天</h2>
    <div class="stats">
      <div class="stat"><span class="k">及时登记</span><span class="v">${pct}</span></div>
      <div class="stat"><span class="k">一般</span><span class="v">${delay}</span></div>
      <div class="stat"><span class="k">到期</span><span class="v">${r.due} 件</span></div>
    </div>
    <div class="muted">谁登记：${list(r.by_recorder)}</div>
    <div class="muted">从哪登记：${list(r.by_source, SOURCE)}</div>
    <div class="${flags.length ? 'error' : 'muted'}">${flags.length ? esc(flags.join('，')) : '没有漏登、补登或撤销'}</div>
    <div class="muted">及时 = 计划时间后 1 小时内登记。</div>
  </section>`;
}
