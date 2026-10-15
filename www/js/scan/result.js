// Result screen: preview with filters + rotation; "Edges" re-opens the edge editor.
import { warp } from './warp.js';
import { applyFilter, rotate90 } from './filters.js';
import { openEditor } from './editor.js';
import { t } from '../i18n.js';
import { MAXSIDE } from './shot.js';
const $ = (id) => document.getElementById(id);
const root = $('result'), stage = $('rs-stage'), cv = $('rs-canvas'), seg = $('rs-filter');
let src, quad, preview, turns = 0, filter = 'color', resolveFn = null, light = false, stay = false;

export const isResultOpen = () => !root.hidden;

function show() {
  const f = applyFilter(rotate90(preview, turns), filter);
  const dpr = window.devicePixelRatio || 1;
  const k = Math.min((stage.clientWidth - 24) / f.width, (stage.clientHeight - 24) / f.height); // 24 = stage padding
  cv.style.opacity = '';
  cv.style.width = f.width * k + 'px'; cv.style.height = f.height * k + 'px';
  cv.width = Math.round(f.width * k * dpr); cv.height = Math.round(f.height * k * dpr);
  cv.getContext('2d').drawImage(f, 0, 0, cv.width, cv.height);
  [...seg.children].forEach((b) => b.classList.toggle('on', b.dataset.value === filter));
}

function rebuild() {
  preview = warp(src, quad, 1200); // fast preview; the full-size warp happens on save
  show();
}

function finish(v) { if (!stay) root.hidden = true; const r = resolveFn; resolveFn = null; r?.(v); } // stay: a sequence replaces the screen in place
export function closeResult() { root.hidden = true; const r = resolveFn; resolveFn = null; r?.(null); }

seg.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { filter = b.dataset.value; show(); } });
$('rs-left').addEventListener('click', () => { turns -= 1; show(); });
$('rs-right').addEventListener('click', () => { turns += 1; show(); });
$('rs-edges').addEventListener('click', async () => {
  const q = await openEditor(src, quad);
  if (q) { quad = q; rebuild(); }
});
const params = () => ({ quad: quad.map((p) => p.slice()), filter, turns });
$('rs-retake').addEventListener('click', () => finish('retake'));
$('rs-back').addEventListener('click', () => finish({ ...params(), back: true }));
$('rs-del').addEventListener('click', () => { if (confirm(t('sc.deleteq'))) finish('delete'); });
$('rs-save').addEventListener('click', () => {
  if (light) { finish(params()); return; } // parameters only, no heavy render
  const b = $('rs-save');
  b.disabled = true;
  setTimeout(() => { // let the button repaint, then do the heavy full-size warp
    const base = warp(src, quad, MAXSIDE);
    b.disabled = false;
    finish({ canvas: applyFilter(rotate90(base, turns), filter), ...params() });
  }, 30);
});

/**
 * opts: filter, turns (initial), saveKey (i18n key of the confirm button), retake ("Retake"), back ("Back"), del (delete icon),
 *       caption (text above the buttons), light (return only {quad, filter, turns}), stay (keep the screen open for the next call).
 * Resolves {quad, filter, turns, canvas? (full size, unless light), back?}, 'retake', 'delete', or null (cancelled).
 */
export function openResult(srcCanvas, q, opts = {}) {
  src = srcCanvas; quad = q.map((p) => p.slice()); turns = opts.turns ?? 0; filter = opts.filter ?? 'color';
  light = !!opts.light;
  stay = !!opts.stay;
  $('rs-retake').hidden = !opts.retake;
  $('rs-back').hidden = !opts.back;
  $('rs-del').hidden = !opts.del;
  $('rs-caption').hidden = !opts.caption;
  $('rs-caption').textContent = opts.caption ?? '';
  cv.style.opacity = '.35'; // dimmed until the new page is ready
  const save = $('rs-save');
  save.dataset.i18n = opts.saveKey ?? 'rs.save';
  save.textContent = t(save.dataset.i18n);
  root.hidden = false;
  requestAnimationFrame(rebuild);
  return new Promise((res) => { resolveFn = res; });
}
