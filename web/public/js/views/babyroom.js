import { api, ApiError } from '../api.js';
import { addDays, esc, errorView, icons, md, toast, todayBiz, weekdayOf } from '../ui.js';

/** 不超过这个大小、格式也支持的图片原样上传；否则缩到长边 1600 再存成 JPG。 */
const KEEP_AS_IS_BYTES = 600 * 1024;
const MAX_SIDE = 1600;

/**
 * 宝宝屋图片（#/babyroom）：一张图只管一天。奶奶当天在 App 首页点“宝宝屋”就全屏显示，
 * 过了日期服务器自动删除。
 */
export async function babyroom(el) {
  let list = [];
  let date = todayBiz();
  let busy = false;

  async function load() {
    try {
      ({ images: list } = await api('/images'));
      draw();
    } catch (err) {
      el.innerHTML = errorView(err);
    }
  }

  function dayLabel(d) {
    const rel = d === todayBiz() ? '今天 · ' : d === addDays(todayBiz(), 1) ? '明天 · ' : '';
    return `${rel}${md(d)} ${weekdayOf(d)}`;
  }

  function draw() {
    const taken = list.some((m) => m.category === 'babyroom' && m.use_date === date);
    el.classList.add('form-narrow');
    el.innerHTML = `
      <header class="head"><div class="row">
        <a class="back" href="#/tasks" aria-label="返回">${icons.back}</a>
        <h1 style="font-size:20px">宝宝屋图片</h1></div></header>

      <section class="card">
        <label class="field">使用日期<input name="date" type="date" min="${todayBiz()}" value="${esc(date)}"></label>
        <label class="field">图片（截图或照片都行）<input name="file" type="file" accept="image/*"></label>
        ${taken ? '<div class="small muted">这一天已经有图了，再上传会替换掉。</div>' : ''}
        <p class="error" data-error hidden></p>
        <button type="button" class="btn primary big" data-upload>上传</button>
        <div class="small muted">一张图只管一天。奶奶当天打开 App 首页会多一张“宝宝屋”卡片，点开就是全屏大图；过了日期图片会自动删除。</div>
      </section>

      <h2 class="section-title">已上传 · ${list.length}</h2>
      ${
        list.length
          ? '<div class="cards">' + list
              .map(
                (m) => `<article class="card row" style="align-items:flex-start">
                  <a href="${esc(m.url)}" target="_blank" rel="noopener"><img src="${esc(m.url)}" alt="${esc(md(m.use_date))}的宝宝屋图片"
                    style="width:96px;height:96px;object-fit:cover;border-radius:8px;border:1px solid var(--line)"></a>
                  <div class="grow"><div class="title">${esc(dayLabel(m.use_date))}</div>
                    <div class="small muted">${esc(m.created_by)} 上传 · ${Math.round(m.size / 1024)} KB</div></div>
                  <button type="button" class="btn sm ghost" data-del="${esc(m.id)}" data-day="${esc(dayLabel(m.use_date))}">删除</button>
                </article>`,
              )
              .join('') + '</div>'
          : '<div class="card dashed muted">还没有图片。</div>'
      }`;
  }

  async function upload() {
    if (busy) return;
    const err = el.querySelector('[data-error]');
    const file = el.querySelector('[name=file]').files[0];
    const problem = !date ? '请选使用日期' : date < todayBiz() ? '使用日期不能早于今天' : !file ? '请选一张图片' : '';
    if (problem) {
      err.textContent = problem;
      err.hidden = false;
      return;
    }
    busy = true;
    const btn = el.querySelector('[data-upload]');
    btn.disabled = true;
    btn.textContent = '上传中…';
    try {
      const blob = await shrink(file);
      await put(`/images/babyroom/${date}`, blob);
      toast('已上传');
      await load();
    } catch (e) {
      err.textContent = e.message;
      err.hidden = false;
      btn.disabled = false;
      btn.textContent = '上传';
    }
    busy = false;
  }

  el.oninput = (e) => {
    if (e.target.name !== 'date') return;
    date = e.target.value;
    const file = el.querySelector('[name=file]').files[0];
    draw();
    // 重画后保留已选的文件
    if (file) {
      const dt = new DataTransfer();
      dt.items.add(file);
      el.querySelector('[name=file]').files = dt.files;
    }
  };
  el.onclick = async (e) => {
    const t = e.target.closest('button');
    if (!t) return;
    if (t.hasAttribute('data-upload')) return upload();
    if (t.dataset.del) {
      if (!confirm(`删除${t.dataset.day}的图片？`)) return;
      t.disabled = true;
      try {
        await api(`/images/${encodeURIComponent(t.dataset.del)}`, { method: 'DELETE' });
        toast('已删除');
      } catch (err) {
        toast(err.message);
      }
      load();
    }
  };

  await load();
  return () => (el.oninput = el.onclick = null);
}

/** 小图原样上传；大图或手机特有格式缩小后转成 JPG，二维码仍然清楚。 */
async function shrink(file) {
  if (file.size <= KEEP_AS_IS_BYTES && ['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return file;
  let bmp;
  try {
    bmp = await createImageBitmap(file);
  } catch {
    throw new Error('这张图打不开，请换成截图或 JPG/PNG 再试');
  }
  const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bmp.width * scale);
  canvas.height = Math.round(bmp.height * scale);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#fff'; // 透明 PNG 转 JPG 时底色用白色
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height);
  bmp.close();
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9));
}

async function put(path, blob) {
  let res;
  try {
    res = await fetch(`/api${path}`, { method: 'PUT', credentials: 'same-origin', headers: { 'Content-Type': blob.type }, body: blob });
  } catch {
    throw new ApiError(0, 'network', '连不上服务器，请检查网络');
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data?.error?.code ?? 'http', data?.error?.message ?? `上传失败（${res.status}）`);
  return data;
}
