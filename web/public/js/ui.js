const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
/** 转义后再拼进 HTML。所有来自服务器或用户的文本都要过一遍。 */
export const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ESC[c]);

export const icons = {
  clock: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  check: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>',
  dash: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 12h12"/></svg>',
  pause: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6v12M15 6v12"/></svg>',
  warn: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l9.5 17h-19z"/><path d="M12 10v4M12 17.5v.01"/></svg>',
  back: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7"/></svg>',
  mic: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>',
};

export const STATUS = {
  pending: { label: '待喂', icon: icons.clock },
  done: { label: '已喂', icon: icons.check },
  skipped: { label: '已跳过', icon: icons.dash },
};

export function badge(status) {
  const s = STATUS[status];
  return `<span class="badge ${status}">${s.icon}${s.label}</span>`;
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/** 北京时间的今天 YYYY-MM-DD（与服务端业务日期一致）。 */
export function todayBiz() {
  return new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10);
}

export function addDays(date, n) {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

export function weekdayOf(date) {
  return WEEKDAYS[new Date(`${date}T00:00:00Z`).getUTCDay()];
}

/** “10月10日” */
export function md(date) {
  const [, m, d] = date.split('-');
  return `${+m}月${+d}日`;
}

/** 毫秒时间戳 → 北京时间 HH:MM */
export function hhmm(ms) {
  return new Date(ms + 8 * 3600 * 1000).toISOString().slice(11, 16);
}

let toastTimer;
export function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), 2600);
}

/** 错误状态整页显示；401 时提示重新登录。 */
export function errorView(err) {
  if (err.status === 401) {
    return `<div class="card"><strong>需要重新登录</strong><p class="muted small">${esc(err.message)}</p>
      <a class="btn dark" href="/">重新打开</a></div>`;
  }
  return `<div class="card"><strong>出错了</strong><p class="error">${esc(err.message)}</p>
    <button class="btn" type="button" data-retry>重试</button></div>`;
}
