// Page editor screen: preview, filters with live thumbnails, fine adjustment (shadows / creases, brightness, contrast, sharpness),
// rotation, "Edges" (re-opens the edge editor), hold-to-compare with the original.
import { warp } from './warp.js';
import { rotate90, scaled } from './filters.js';
import { applyLook, defaultLook } from './enhance.js';
import { openEditor } from './editor.js';
import { t } from '../i18n.js';
import { MAXSIDE } from './shot.js';
const $ = (id) => document.getElementById(id);
const root = $('result'), stage = $('rs-stage'), cv = $('rs-canvas'), fxBar = $('rs-filters'), adjust = $('rs-adjust'), tabs = $('rs-tabs');
let src, quad, preview, base, turns = 0, look = defaultLook('auto'), resolveFn = null, light = false, stay = false, raf = 0;

export const isResultOpen = () => !root.hidden;

function draw(f) {
  const dpr = window.devicePixelRatio || 1;
  const k = Math.min((stage.clientWidth - 24) / f.width, (stage.clientHeight - 24) / f.height); // 24 = stage padding
  cv.style.opacity = '';
  cv.style.width = f.width * k + 'px'; cv.style.height = f.height * k + 'px';
  cv.width = Math.round(f.width * k * dpr); cv.height = Math.round(f.height * k * dpr);
  cv.getContext('2d').drawImage(f, 0, 0, cv.width, cv.height);
}

function syncControls() {
  [...fxBar.children].forEach((b) => b.classList.toggle('on', b.dataset.value === look.filter));
  adjust.querySelectorAll('input').forEach((i) => { i.value = look[i.dataset.key]; i.previousElementSibling.textContent = look[i.dataset.key]; });
}
function show() { draw(applyLook(base, look)); syncControls(); }
const schedule = () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; show(); }); };

// thumbnails: every preset applied to a small copy of this very page (centre crop)
function chips() {
  const s = scaled(base, 150);
  const side = Math.min(s.width, s.height), sx = (s.width - side) / 2, sy = (s.height - side) / 2;
  for (const b of fxBar.children) {
    const c = b.querySelector('canvas'), look2 = defaultLook(b.dataset.value), f = applyLook(s, look2);
    c.getContext('2d').drawImage(f, sx * (f.width / s.width), sy * (f.height / s.height), side * (f.width / s.width), side * (f.height / s.height), 0, 0, c.width, c.height);
  }
}

function rebuild() {
  preview = warp(src, quad, 1200); // fast preview; the full-size warp happens on save
  base = rotate90(preview, turns);
  chips();
  show();
}
function rotate(d) { turns += d; base = rotate90(preview, turns); chips(); show(); }

function finish(v) { if (!stay) root.hidden = true; const r = resolveFn; resolveFn = null; r?.(v); } // stay: a sequence replaces the screen in place
export function closeResult() { root.hidden = true; const r = resolveFn; resolveFn = null; r?.(null); }

fxBar.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { look = defaultLook(b.dataset.value); show(); } });
adjust.addEventListener('input', (e) => { const i = e.target; if (i.dataset.key) { look[i.dataset.key] = +i.value; i.previousElementSibling.textContent = i.value; schedule(); } });
$('rs-reset').addEventListener('click', () => { look = defaultLook(look.filter); show(); });
tabs.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  [...tabs.children].forEach((x) => x.classList.toggle('on', x === b));
  fxBar.hidden = b.dataset.tab !== 'filters';
  adjust.hidden = b.dataset.tab !== 'adjust';
});
const cmp = $('rs-cmp');
cmp.addEventListener('pointerdown', () => draw(base)); // hold: the original (frame + rotation only)
for (const ev of ['pointerup', 'pointercancel', 'pointerleave']) cmp.addEventListener(ev, () => { if (!root.hidden) show(); });

$('rs-left').addEventListener('click', () => rotate(-1));
$('rs-right').addEventListener('click', () => rotate(1));
$('rs-edges').addEventListener('click', async () => {
  const q = await openEditor(src, quad);
  if (q) { quad = q; rebuild(); }
});
const params = () => ({ quad: quad.map((p) => p.slice()), look: { ...look }, turns });
$('rs-retake').addEventListener('click', () => finish('retake'));
$('rs-back').addEventListener('click', () => finish({ ...params(), back: true }));
$('rs-del').addEventListener('click', () => { if (confirm(t('sc.deleteq'))) finish('delete'); });
$('rs-save').addEventListener('click', () => {
  if (light) { finish(params()); return; } // parameters only, no heavy render
  const b = $('rs-save');
  b.disabled = true;
  setTimeout(() => { // let the button repaint, then do the heavy full-size render
    const full = warp(src, quad, MAXSIDE);
    const canvas = applyLook(rotate90(full, turns), look);
    b.disabled = false;
    finish({ canvas, ...params() });
  }, 30);
});

/**
 * opts: look, turns (initial), saveKey (i18n key of the confirm button), retake ("Retake"), back ("Back"), del (delete icon),
 *       caption (text above the panel), light (return only {quad, look, turns}), stay (keep the screen open for the next call).
 * Resolves {quad, look, turns, canvas? (full size, unless light), back?}, 'retake', 'delete', or null (cancelled).
 */
export function openResult(srcCanvas, q, opts = {}) {
  src = srcCanvas; quad = q.map((p) => p.slice()); turns = opts.turns ?? 0; look = { ...(opts.look ?? defaultLook('auto')) };
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
