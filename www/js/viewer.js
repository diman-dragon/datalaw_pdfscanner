// Light in-app PDF viewer. Two modes: strip (vertical scroll) and paged (swipe left/right).
// Pinch to zoom (1x-4x), +/- buttons, pages rendered lazily by the native plugin.
import { Folder } from './folder.js';
import { store } from './storage.js';
import { t } from './i18n.js';
import { toast } from './toast.js';
import { openSheet } from './sheet.js';
import { openOcr } from './ocr.js';
import { openDocEdit } from './docedit.js';

const $ = (id) => document.getElementById(id);
const root = $('viewer'), titleEl = $('vw-title'), posEl = $('vw-pos'), scroll = $('vw-scroll'), list = $('vw-pages'), zoomEl = $('vw-zoom');
const MAXZ = 4;
let doc = null, file = null, zoom = 1, mode = 'strip', gen = 0, queue = [], pumping = false, raf = 0, io = null, soon = 0, pz = null;

export const isViewerOpen = () => !root.hidden;

export function openExternal(f) {
  Folder.open({ uri: f.uri }).catch((e) => alert(String(e?.message ?? e) === 'no_viewer' ? t('lib.noviewer') : t('lib.fail')));
}

// ---- lazy rendering: pages near the viewport are rendered, far ones drop their bitmap
function onIntersect(entries) {
  for (const e of entries) {
    const el = e.target;
    el._vis = e.isIntersecting;
    if (el._vis) {
      if (!el._done && !el._queued) { el._queued = true; queue.push(el); pump(); }
    } else {
      el._img.removeAttribute('src');
      el._done = false;
    }
  }
}

/** (Re)starts rendering of every page, e.g. after zoom, mode switch or rotation. Old bitmaps stay until replaced. */
function restart() {
  gen++; queue = [];
  io?.disconnect();
  io = new IntersectionObserver(onIntersect, { root: scroll, rootMargin: mode === 'paged' ? '0px 100%' : '1200px 0px' });
  for (const el of list.children) { el._done = false; el._queued = false; el._vis = false; io.observe(el); }
}
const restartSoon = () => { clearTimeout(soon); soon = setTimeout(() => { if (doc) restart(); }, 150); };

async function pump() {
  if (pumping) return;
  pumping = true;
  while (queue.length) {
    const el = queue.shift(), g = gen, d = doc;
    el._queued = false;
    if (!d || !el._vis || el._done) continue;
    try {
      const width = Math.min(2400, Math.round(el._vi.offsetWidth * (window.devicePixelRatio || 1)));
      const r = await Folder.viewPage({ id: d.id, index: el._i, width });
      if (g === gen && doc === d && el._vis) { el._img.src = r.data; el._done = true; }
    } catch (e) { console.error(e); }
  }
  pumping = false;
}

// ---- layout / position
function layout() {
  zoomEl.textContent = Math.round(zoom * 100) + '%';
  if (mode === 'strip') {
    list.style.width = zoom * 100 + '%';
    for (const el of list.children) el._vi.style.width = '';
  } else {
    list.style.width = '';
    const W = scroll.clientWidth - 16, H = scroll.clientHeight - 16;
    for (const el of list.children) el._vi.style.width = Math.min(W, H / el._r) * zoom + 'px'; // fit the screen, then zoom
  }
}

const current = () => {
  if (mode === 'paged') return Math.round(scroll.scrollLeft / Math.max(1, scroll.clientWidth));
  const mid = scroll.scrollTop + scroll.clientHeight / 3;
  let cur = 0;
  for (let i = 0; i < list.children.length; i++) { if (list.children[i].offsetTop <= mid) cur = i; else break; }
  return cur;
};
function paintPos() {
  raf = 0;
  if (doc) posEl.textContent = `${Math.min(current(), doc.n - 1) + 1} / ${doc.n}`;
}
function toPage(i) {
  if (mode === 'paged') scroll.scrollLeft = i * scroll.clientWidth;
  else scroll.scrollTop = list.children[i]?.offsetTop ?? 0;
}

function setMode(m) {
  const cur = doc ? current() : 0;
  mode = m; zoom = 1;
  root.dataset.mode = m;
  scroll.className = m;
  for (const el of list.children) el.scrollLeft = el.scrollTop = 0;
  layout();
  toPage(cur);
  if (doc) restart();
  paintPos();
}

// ---- zoom (around a point of the viewport)
const box = () => (mode === 'paged' ? list.children[current()] : scroll); // the element that scrolls the page content
function zoomAt(z, b, fx, fy, cx, cy) {
  zoom = Math.min(MAXZ, Math.max(1, z));
  layout();
  b.scrollLeft = fx * b.scrollWidth - cx;
  b.scrollTop = fy * b.scrollHeight - cy;
}
function zoomBy(k) {
  if (!doc) return;
  const b = box(), cx = scroll.clientWidth / 2, cy = scroll.clientHeight / 2;
  zoomAt(zoom * k, b, (b.scrollLeft + cx) / b.scrollWidth, (b.scrollTop + cy) / b.scrollHeight, cx, cy);
  restartSoon();
}

const dist = (t2) => Math.hypot(t2[0].clientX - t2[1].clientX, t2[0].clientY - t2[1].clientY);
scroll.addEventListener('touchstart', (e) => {
  if (e.touches.length !== 2 || !doc) return;
  const r = scroll.getBoundingClientRect(), b = box();
  const cx = (e.touches[0].clientX + e.touches[1].clientX) / 2 - r.left, cy = (e.touches[0].clientY + e.touches[1].clientY) / 2 - r.top;
  pz = { d: dist(e.touches), z: zoom, b, cx, cy, fx: (b.scrollLeft + cx) / b.scrollWidth, fy: (b.scrollTop + cy) / b.scrollHeight };
  scroll.style.overflow = 'hidden'; b.style.overflow = 'hidden'; // no native scrolling while pinching
}, { passive: true });
scroll.addEventListener('touchmove', (e) => {
  if (!pz || e.touches.length !== 2) return;
  e.preventDefault();
  zoomAt(pz.z * dist(e.touches) / pz.d, pz.b, pz.fx, pz.fy, pz.cx, pz.cy);
}, { passive: false });
const endPinch = (e) => {
  if (!pz || e.touches.length >= 2) return;
  scroll.style.overflow = ''; pz.b.style.overflow = '';
  pz = null;
  restartSoon(); // re-render sharp at the new size
};
scroll.addEventListener('touchend', endPinch, { passive: true });
scroll.addEventListener('touchcancel', endPinch, { passive: true });

// ---- open / close
export function closeViewer() {
  if (root.hidden) return;
  root.hidden = true; gen++; queue = []; pz = null;
  scroll.style.overflow = '';
  io?.disconnect(); io = null;
  list.replaceChildren();
  const d = doc; doc = null; file = null;
  if (d) Folder.viewClose({ id: d.id }).catch(() => {});
}

export async function openViewer(f) {
  if (!root.hidden) return;
  file = f; zoom = 1; mode = store.get('vwmode') === 'paged' ? 'paged' : 'strip';
  titleEl.textContent = f.name; posEl.textContent = '';
  list.replaceChildren();
  root.hidden = false;
  setMode(mode);
  try {
    const d = await Folder.viewOpen({ uri: f.uri });
    if (root.hidden || file !== f) { Folder.viewClose({ id: d.id }).catch(() => {}); return; } // closed while opening
    doc = { id: d.id, n: d.ratios.length };
    d.ratios.forEach((ratio, i) => {
      const el = document.createElement('div'), vi = document.createElement('div'), img = document.createElement('img');
      el.className = 'vp'; vi.className = 'vi'; vi.style.aspectRatio = `1 / ${ratio}`; img.alt = '';
      el._i = i; el._r = ratio; el._vi = vi; el._img = img;
      vi.append(img); el.append(vi); list.append(el);
    });
    layout(); scroll.scrollTop = scroll.scrollLeft = 0;
    restart(); paintPos();
  } catch (e) {
    console.error(e);
    toast(t('vw.fail'));
    closeViewer();
  }
}

scroll.addEventListener('scroll', () => { if (!raf) raf = requestAnimationFrame(paintPos); }, { passive: true });
window.addEventListener('resize', () => { if (!doc) return; const cur = current(); layout(); toPage(cur); restartSoon(); });
$('vw-close').addEventListener('click', closeViewer);
$('vw-minus').addEventListener('click', () => zoomBy(1 / 1.5));
$('vw-plus').addEventListener('click', () => zoomBy(1.5));
$('vw-mode').addEventListener('click', () => { const m = mode === 'strip' ? 'paged' : 'strip'; store.set('vwmode', m); setMode(m); });
$('vw-menu').addEventListener('click', () => {
  if (!doc || !file) return;
  const f = file, page = current(), pages = doc.n;
  openSheet(f.name, [
    { label: t('lib.edit'), run: () => { closeViewer(); openDocEdit(f); } },
    { label: t('ocr.menu'), run: () => openOcr({ file: f, page, pages }) },
    { label: t('lib.openext'), run: () => openExternal(f) },
  ]);
});
