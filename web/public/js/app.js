import { today } from './views/today.js';
import { editor } from './views/editor.js';
import { tasks } from './views/tasks.js';
import { history } from './views/history.js';
import { settings } from './views/settings.js';

/** 哈希路由：#/today、#/new、#/tasks、#/tasks/<id>/edit、#/history、#/settings */
const routes = [
  [/^\/today$/, 'today', () => today],
  [/^\/new$/, 'new', () => editor],
  [/^\/tasks\/([^/]+)\/edit$/, 'tasks', () => editor],
  [/^\/tasks$/, 'tasks', () => tasks],
  [/^\/history$/, 'history', () => history],
  [/^\/settings$/, 'settings', () => settings],
];

const view = document.getElementById('view');
let cleanup = null;

async function route() {
  const path = location.hash.replace(/^#/, '') || '/today';
  const hit = routes.find(([re]) => re.test(path));
  if (!hit) return void (location.hash = '#/today');
  const [re, tab, load] = hit;
  const params = path.match(re).slice(1).map(decodeURIComponent);

  for (const a of document.querySelectorAll('.tabs a')) a.classList.toggle('on', a.dataset.tab === tab);
  if (typeof cleanup === 'function') cleanup();
  cleanup = null;
  window.scrollTo(0, 0);
  // 每次换页给一个新容器：上一页还没返回的请求只会写进已经移除的旧容器，不会覆盖新页面。
  const el = document.createElement('div');
  el.className = 'stack';
  el.style.gap = '12px';
  view.replaceChildren(el);
  const done = await load()(el, ...params);
  if (el.isConnected) cleanup = done;
  else if (typeof done === 'function') done();
}

view.addEventListener('click', (e) => {
  if (e.target.closest('[data-retry]')) route();
});
window.addEventListener('hashchange', route);
route();
