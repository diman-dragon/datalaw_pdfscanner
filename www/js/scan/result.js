// Result screen: preview with filters + rotation; "Edges" re-opens the edge editor.
import { warp } from './warp.js';
import { applyFilter, rotate90, scaled } from './filters.js';
import { openEditor } from './editor.js';
const $ = (id) => document.getElementById(id);
const root = $('result'), stage = $('rs-stage'), cv = $('rs-canvas'), seg = $('rs-filter');
let src, quad, base, preview, turns = 0, filter = 'color', resolveFn = null;

export const isResultOpen = () => !root.hidden;

function show() {
  const f = applyFilter(rotate90(preview, turns), filter);
  const dpr = window.devicePixelRatio || 1;
  const k = Math.min(stage.clientWidth / f.width, stage.clientHeight / f.height);
  cv.style.width = f.width * k + 'px'; cv.style.height = f.height * k + 'px';
  cv.width = Math.round(f.width * k * dpr); cv.height = Math.round(f.height * k * dpr);
  cv.getContext('2d').drawImage(f, 0, 0, cv.width, cv.height);
  [...seg.children].forEach((b) => b.classList.toggle('on', b.dataset.value === filter));
}

function rebuild() {
  base = warp(src, quad);
  preview = scaled(base, 1200);
  show();
}

function finish(v) { root.hidden = true; const r = resolveFn; resolveFn = null; r?.(v); }
export const closeResult = () => finish(null);

seg.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { filter = b.dataset.value; show(); } });
$('rs-left').addEventListener('click', () => { turns -= 1; show(); });
$('rs-right').addEventListener('click', () => { turns += 1; show(); });
$('rs-retake').addEventListener('click', () => finish('retake'));
$('rs-edges').addEventListener('click', async () => {
  const q = await openEditor(src, quad);
  if (q) { quad = q; rebuild(); }
});
$('rs-save').addEventListener('click', () => finish(applyFilter(rotate90(base, turns), filter)));

/** Resolves the final full-size canvas, 'retake', or null (cancelled). */
export function openResult(srcCanvas, q, initialFilter = 'color') {
  src = srcCanvas; quad = q.map((p) => p.slice()); turns = 0; filter = initialFilter;
  root.hidden = false;
  requestAnimationFrame(rebuild);
  return new Promise((res) => { resolveFn = res; });
}
