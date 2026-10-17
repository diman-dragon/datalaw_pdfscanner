// Light in-app PDF viewer. Two modes:
//  - strip: vertical scrolling, pinch to zoom;
//  - paged: one page at a time, own gestures (swipe left/right = next/previous page, pinch, drag when zoomed, double-tap zoom).
// Page buttons and +/- are always there. Pages are rendered lazily by the native plugin.
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
let pi = 0, pdx = 0, pan = { x: 0, y: 0 }; // paged mode: page index, drag offset, pan of the zoomed page

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
  if (mode === 'paged') return Math.min(pi, Math.max(0, (doc?.n ?? 1) - 1));
  const mid = scroll.scrollTop + scroll.clientHeight / 3;
  let cur = 0;
  for (let i = 0; i < list.children.length; i++) { if (list.children[i].offsetTop <= mid) cur = i; else break; }
  return cur;
};
function paintPos() {
  raf = 0;
  if (doc) posEl.textContent = `${Math.min(current(), doc.n - 1) + 1} / ${doc.n}`;
}

// ---- paged mode helpers
function clampPan() {
  const el = list.children[pi];
  if (!el) return;
  const mx = Math.max(0, (el._vi.offsetWidth - scroll.clientWidth) / 2), my = Math.max(0, (el._vi.offsetHeight - scroll.clientHeight) / 2);
  pan.x = Math.min(mx, Math.max(-mx, pan.x)); pan.y = Math.min(my, Math.max(-my, pan.y));
}
function applyPaged(anim = true) {
  list.classList.toggle('drag', !anim);
  list.style.transform = `translate3d(${-pi * scroll.clientWidth + pdx}px,0,0)`;
  const el = list.children[pi];
  if (el) { el.style.setProperty('--tx', pan.x + 'px'); el.style.setProperty('--ty', pan.y + 'px'); }
}
function setPage(i) {
  if (!doc) return;
  const n = Math.min(doc.n - 1, Math.max(0, i));
  if (n !== pi) {
    const old = list.children[pi];
    if (old) { old.style.removeProperty('--tx'); old.style.removeProperty('--ty'); }
    pi = n; zoom = 1; pan = { x: 0, y: 0 };
    layout();
    restartSoon();
  }
  pdx = 0;
  applyPaged(true);
  paintPos();
}

function setMode(m) {
  const cur = doc ? current() : 0;
  mode = m; zoom = 1; pan = { x: 0, y: 0 }; pdx = 0;
  root.dataset.mode = m;
  scroll.classList.toggle('paged', m === 'paged');
  for (const el of list.children) { el.style.removeProperty('--tx'); el.style.removeProperty('--ty'); }
  list.style.transform = '';
  layout();
  if (m === 'paged') { pi = cur; applyPaged(false); } else scroll.scrollTop = list.children[cur]?.offsetTop ?? 0;
  if (doc) restart();
  paintPos();
}

// ---- zoom: strip mode around a point of the viewport (native scrolling), paged mode by our own pan
function zoomAt(z, b, fx, fy, cx, cy) {
  zoom = Math.min(MAXZ, Math.max(1, z));
  layout();
  b.scrollLeft = fx * b.scrollWidth - cx;
  b.scrollTop = fy * b.scrollHeight - cy;
}
function zoomBy(k) {
  if (!doc) return;
  if (mode === 'paged') {
    const z0 = zoom;
    zoom = Math.min(MAXZ, Math.max(1, zoom * k));
    layout(); pan.x *= zoom / z0; pan.y *= zoom / z0; clampPan(); applyPaged(true);
  } else {
    const cx = scroll.clientWidth / 2, cy = scroll.clientHeight / 2;
    zoomAt(zoom * k, scroll, (scroll.scrollLeft + cx) / scroll.scrollWidth, (scroll.scrollTop + cy) / scroll.scrollHeight, cx, cy);
  }
  restartSoon();
}

// strip mode: pinch (touch events; native scrolling is switched off while two fingers are down)
const dist2 = (t2) => Math.hypot(t2[0].clientX - t2[1].clientX, t2[0].clientY - t2[1].clientY);
scroll.addEventListener('touchstart', (e) => {
  if (mode !== 'strip' || e.touches.length !== 2 || !doc) return;
  const r = scroll.getBoundingClientRect(), b = scroll;
  const cx = (e.touches[0].clientX + e.touches[1].clientX) / 2 - r.left, cy = (e.touches[0].clientY + e.touches[1].clientY) / 2 - r.top;
  pz = { d: dist2(e.touches), z: zoom, b, cx, cy, fx: (b.scrollLeft + cx) / b.scrollWidth, fy: (b.scrollTop + cy) / b.scrollHeight };
  scroll.style.overflow = 'hidden';
}, { passive: true });
scroll.addEventListener('touchmove', (e) => {
  if (!pz || e.touches.length !== 2) return;
  zoomAt(pz.z * dist2(e.touches) / pz.d, pz.b, pz.fx, pz.fy, pz.cx, pz.cy);
}, { passive: true });
const endPinch = (e) => {
  if (!pz || e.touches.length >= 2) return;
  scroll.style.overflow = '';
  pz = null;
  restartSoon(); // re-render sharp at the new size
};
scroll.addEventListener('touchend', endPinch, { passive: true });
scroll.addEventListener('touchcancel', endPinch, { passive: true });

// paged mode: own gestures with pointer events
const ptrs = new Map();
let g = null, pinch = null, lastTap = 0;
scroll.addEventListener('pointerdown', (e) => {
  if (mode !== 'paged' || !doc) return;
  scroll.setPointerCapture(e.pointerId);
  ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (ptrs.size === 2) {
    const [a, b] = [...ptrs.values()];
    pinch = { d: Math.hypot(a.x - b.x, a.y - b.y), z: zoom, px: pan.x, py: pan.y };
    g = null; pdx = 0; applyPaged(false);
  } else if (ptrs.size === 1) {
    g = { x: e.clientX, y: e.clientY, t: Date.now(), px: pan.x, py: pan.y };
    list.classList.add('drag');
  }
});
scroll.addEventListener('pointermove', (e) => {
  if (!ptrs.has(e.pointerId)) return;
  ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (pinch && ptrs.size >= 2) {
    const [a, b] = [...ptrs.values()], z0 = pinch.z;
    zoom = Math.min(MAXZ, Math.max(1, z0 * Math.hypot(a.x - b.x, a.y - b.y) / pinch.d));
    layout(); pan.x = pinch.px * zoom / z0; pan.y = pinch.py * zoom / z0; clampPan(); applyPaged(false);
  } else if (g) {
    const dx = e.clientX - g.x, dy = e.clientY - g.y;
    if (zoom > 1.001) { pan.x = g.px + dx; pan.y = g.py + dy; clampPan(); applyPaged(false); } else {
      const edge = (dx > 0 && pi === 0) || (dx < 0 && pi === (doc?.n ?? 1) - 1);
      pdx = edge ? dx * 0.3 : dx; // resistance at the first / last page
      applyPaged(false);
    }
  }
});
const endPtr = (e) => {
  if (!ptrs.has(e.pointerId)) return;
  const wasPinch = !!pinch;
  ptrs.delete(e.pointerId);
  if (wasPinch) { if (ptrs.size < 2) { pinch = null; restartSoon(); } if (!ptrs.size) g = null; return; }
  if (!g) return;
  const dx = e.clientX - g.x, dy = e.clientY - g.y, dt = Math.max(1, Date.now() - g.t), s = g; g = null;
  if (zoom > 1.001) { applyPaged(true); } else if (Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy)) {
    const W = scroll.clientWidth;
    if (Math.abs(dx) > W * 0.18 || Math.abs(dx / dt) > 0.45) setPage(pi + (dx < 0 ? 1 : -1)); else setPage(pi); // swipe: flip, or snap back
  } else { pdx = 0; applyPaged(true); }
  if (Math.abs(dx) < 10 && Math.abs(dy) < 10 && dt < 350) { // tap; the second one within 350 ms = double-tap zoom
    const now = Date.now();
    if (now - lastTap < 350) {
      const r = scroll.getBoundingClientRect();
      if (zoom > 1.001) { zoom = 1; pan = { x: 0, y: 0 }; } else {
        zoom = 2.5; layout();
        pan = { x: (r.width / 2 - (s.x - r.left)) * (zoom - 1), y: (r.height / 2 - (s.y - r.top)) * (zoom - 1) };
      }
      layout(); clampPan(); applyPaged(true); restartSoon(); lastTap = 0;
    } else lastTap = now;
  }
};
scroll.addEventListener('pointerup', endPtr);
scroll.addEventListener('pointercancel', endPtr);

// ---- open / close
export function closeViewer() {
  if (root.hidden) return;
  root.hidden = true; gen++; queue = []; pz = null; pinch = null; g = null; ptrs.clear();
  scroll.style.overflow = '';
  io?.disconnect(); io = null;
  list.replaceChildren();
  const d = doc; doc = null; file = null;
  if (d) Folder.viewClose({ id: d.id }).catch(() => {});
}

export async function openViewer(f) {
  if (!root.hidden) return;
  file = f; zoom = 1; pi = 0; pdx = 0; pan = { x: 0, y: 0 };
  mode = store.get('vwmode') === 'paged' ? 'paged' : 'strip';
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
    layout(); scroll.scrollTop = 0; applyPaged(false);
    restart(); paintPos();
  } catch (e) {
    console.error(e);
    toast(t('vw.fail'));
    closeViewer();
    document.dispatchEvent(new Event('libchange')); // the file may be gone: refresh the list
  }
}

scroll.addEventListener('scroll', () => { if (!raf) raf = requestAnimationFrame(paintPos); }, { passive: true });
window.addEventListener('resize', () => {
  if (!doc) return;
  const cur = current(); layout();
  if (mode === 'paged') { clampPan(); applyPaged(false); } else scroll.scrollTop = list.children[cur]?.offsetTop ?? 0;
  restartSoon();
});
$('vw-close').addEventListener('click', closeViewer);
$('vw-minus').addEventListener('click', () => zoomBy(1 / 1.5));
$('vw-plus').addEventListener('click', () => zoomBy(1.5));
function go(d) {
  if (!doc) return;
  if (mode === 'paged') { setPage(pi + d); return; }
  const i = Math.min(doc.n - 1, Math.max(0, current() + d));
  scroll.scrollTo({ top: list.children[i].offsetTop - 10, behavior: 'smooth' });
}
$('vw-prev').addEventListener('click', () => go(-1));
$('vw-next').addEventListener('click', () => go(1));
$('vw-mode').addEventListener('click', () => { const m = mode === 'strip' ? 'paged' : 'strip'; store.set('vwmode', m); setMode(m); });
const reopen = (f) => { closeViewer(); openViewer(f); }; // the file was replaced: show the new one
function editPage() {
  if (doc && file) openDocEdit(file, { single: true, page: current(), onSaved: reopen });
}
$('vw-edit').addEventListener('click', editPage);
$('vw-menu').addEventListener('click', () => {
  if (!doc || !file) return;
  const f = file, page = current(), pages = doc.n;
  openSheet(f.name, [
    { label: t('vw.editpage'), run: editPage },
    { label: t('vw.editdoc'), run: () => openDocEdit(f, { onSaved: reopen }) },
    { label: t('ocr.menu'), run: () => openOcr({ file: f, page, pages }) },
    { label: t('lib.openext'), run: () => openExternal(f) },
  ]);
});
