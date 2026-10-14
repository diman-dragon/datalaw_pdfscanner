// Result screen: preview with filters + rotation; "Edges" re-opens the edge editor.
import { warp } from './warp.js';
import { applyFilter, rotate90 } from './filters.js';
import { openEditor } from './editor.js';
import { t } from '../i18n.js';
const $ = (id) => document.getElementById(id);
const root = $('result'), stage = $('rs-stage'), cv = $('rs-canvas'), seg = $('rs-filter');
let src, quad, preview, turns = 0, filter = 'color', resolveFn = null;

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
  preview = warp(src, quad, 1200); // fast preview; the full-size warp happens on save
  show();
}

function finish(v) { root.hidden = true; const r = resolveFn; resolveFn = null; r?.(v); }
export const closeResult = () => finish(null);

seg.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { filter = b.dataset.value; show(); } });
$('rs-left').addEventListener('click', () => { turns -= 1; show(); });
$('rs-right').addEventListener('click', () => { turns += 1; show(); });
$('rs-edges').addEventListener('click', async () => {
  const q = await openEditor(src, quad);
  if (q) { quad = q; rebuild(); }
});
$('rs-save').addEventListener('click', () => {
  const b = $('rs-save');
  b.disabled = true;
  setTimeout(() => { // let the button repaint, then do the heavy full-size warp
    const base = warp(src, quad, 4000);
    b.disabled = false;
    finish({ canvas: applyFilter(rotate90(base, turns), filter), quad: quad.map((p) => p.slice()), filter, turns });
  }, 30);
});

/**
 * opts: filter, turns (initial), saveKey (i18n key of the confirm button).
 * Resolves {canvas, quad, filter, turns} (full size), or null (cancelled).
 */
export function openResult(srcCanvas, q, opts = {}) {
  src = srcCanvas; quad = q.map((p) => p.slice()); turns = opts.turns ?? 0; filter = opts.filter ?? 'color';
  const save = $('rs-save');
  save.dataset.i18n = opts.saveKey ?? 'rs.save';
  save.textContent = t(save.dataset.i18n);
  root.hidden = false;
  requestAnimationFrame(rebuild);
  return new Promise((res) => { resolveFn = res; });
}
