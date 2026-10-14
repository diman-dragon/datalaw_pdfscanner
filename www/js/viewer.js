// Light in-app PDF viewer: pages are rendered lazily by the native plugin; zoom buttons; "another app" on request.
import { Folder } from './folder.js';
import { t } from './i18n.js';
import { toast } from './toast.js';

const $ = (id) => document.getElementById(id);
const root = $('viewer'), titleEl = $('vw-title'), posEl = $('vw-pos'), scroll = $('vw-scroll'), list = $('vw-pages'), zoomEl = $('vw-zoom');
const ZOOMS = [1, 1.5, 2, 3];
let doc = null, file = null, zi = 0, gen = 0, queue = [], pumping = false, raf = 0;

export const isViewerOpen = () => !root.hidden;

export function openExternal(f) {
  Folder.open({ uri: f.uri }).catch((e) => alert(String(e?.message ?? e) === 'no_viewer' ? t('lib.noviewer') : t('lib.fail')));
}

// Pages near the viewport are rendered; far ones drop their bitmap to save memory.
const io = new IntersectionObserver((entries) => {
  for (const e of entries) {
    const el = e.target;
    el._vis = e.isIntersecting;
    if (el._vis) {
      if (!el._done && !el._queued) { el._queued = true; queue.push(el); pump(); }
    } else {
      el.firstChild.removeAttribute('src');
      el._done = false;
    }
  }
}, { root: scroll, rootMargin: '1200px 0px' });

async function pump() {
  if (pumping) return;
  pumping = true;
  while (queue.length) {
    const el = queue.shift(), g = gen, d = doc;
    el._queued = false;
    if (!d || !el._vis || el._done) continue;
    try {
      const width = Math.round(Math.min(2000, scroll.clientWidth * (window.devicePixelRatio || 1) * ZOOMS[zi]));
      const r = await Folder.viewPage({ id: d.id, index: el._i, width });
      if (g === gen && doc === d && el._vis) { el.firstChild.src = r.data; el._done = true; }
    } catch (e) { console.error(e); }
  }
  pumping = false;
}

function paintPos() {
  raf = 0;
  if (!doc) return;
  const mid = scroll.scrollTop + scroll.clientHeight / 3;
  let cur = 0;
  for (let i = 0; i < list.children.length; i++) { if (list.children[i].offsetTop <= mid) cur = i; else break; }
  posEl.textContent = `${cur + 1} / ${doc.n}`;
}

function applyZoom() {
  list.style.width = ZOOMS[zi] * 100 + '%';
  zoomEl.textContent = ZOOMS[zi] * 100 + '%';
}

function zoomBy(d) {
  const n = Math.min(ZOOMS.length - 1, Math.max(0, zi + d));
  if (n === zi || !doc) return;
  const k = scroll.scrollTop / Math.max(1, scroll.scrollHeight);
  zi = n; applyZoom();
  scroll.scrollTop = k * scroll.scrollHeight;
  gen++; queue = [];
  for (const el of list.children) { el._done = false; el._queued = false; io.unobserve(el); io.observe(el); } // re-render at the new size
}

export function closeViewer() {
  if (root.hidden) return;
  root.hidden = true; gen++; queue = [];
  io.disconnect();
  list.replaceChildren();
  const d = doc; doc = null; file = null;
  if (d) Folder.viewClose({ id: d.id }).catch(() => {});
}

export async function openViewer(f) {
  if (!root.hidden) return;
  file = f; zi = 0;
  titleEl.textContent = f.name; posEl.textContent = '';
  list.replaceChildren(); applyZoom();
  root.hidden = false;
  try {
    const d = await Folder.viewOpen({ uri: f.uri });
    if (root.hidden || file !== f) { Folder.viewClose({ id: d.id }).catch(() => {}); return; } // closed while opening
    doc = { id: d.id, n: d.ratios.length };
    d.ratios.forEach((ratio, i) => {
      const el = document.createElement('div');
      el.className = 'vp'; el.style.aspectRatio = `1 / ${ratio}`; el._i = i;
      const img = document.createElement('img'); img.alt = '';
      el.append(img); list.append(el);
    });
    gen++; scroll.scrollTop = 0;
    for (const el of list.children) io.observe(el);
    paintPos();
  } catch (e) {
    console.error(e);
    toast(t('vw.fail'));
    closeViewer();
  }
}

scroll.addEventListener('scroll', () => { if (!raf) raf = requestAnimationFrame(paintPos); }, { passive: true });
$('vw-close').addEventListener('click', closeViewer);
$('vw-minus').addEventListener('click', () => zoomBy(-1));
$('vw-plus').addEventListener('click', () => zoomBy(1));
$('vw-ext').addEventListener('click', () => { if (file) openExternal(file); });
