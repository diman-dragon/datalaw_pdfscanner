// Page editor: the photo with the frame right on the screen (drag the corners, double-tap = automatic),
// a small live preview of the result (tap it to see the result large), Original / Auto, rotation.
// Swipe left / right = next / previous page in a sequence.
import { warp } from './warp.js';
import { rotate90, scaled } from './filters.js';
import { applyLook, defaultLook } from './enhance.js';
import { detectQuad } from './detect.js';
import { t } from '../i18n.js';
import { loadSide } from './shot.js';
import { loadImage } from './load.js';
import { pdfTarget } from '../pdfquality.js';
import { sharpness, skewOf } from './quality.js';
const $ = (id) => document.getElementById(id);
const root = $('result'), stage = $('rs-stage'), cv = $('rs-canvas'), mini = $('rs-mini'), lookBar = $('rs-look');
let src, origBlob = null, blurry = false, disp, kd = 1, shown, quad, turns = 0, look = defaultLook('auto'), view = 'frame', ks = 1;
let resolveFn = null, light = false, stay = false, nav = { back: false, next: false }, drag = -1, sw = null, lastTap = 0, raf = 0;

export const isResultOpen = () => !root.hidden;

// ---- geometry: quad is in source pixels; the screen shows the (scaled, rotated) copy
const rotPt = ([x, y], W, H, n) => { n = ((n % 4) + 4) % 4; return n === 0 ? [x, y] : n === 1 ? [H - y, x] : n === 2 ? [W - x, H - y] : [y, W - x]; };
const toScreen = (p) => { const q = rotPt([p[0] * kd, p[1] * kd], disp.width, disp.height, turns); return [q[0] * ks, q[1] * ks]; };
function fromScreen([x, y]) {
  const odd = turns % 2 !== 0, W = odd ? disp.height : disp.width, H = odd ? disp.width : disp.height;
  const q = rotPt([x / ks, y / ks], W, H, -turns);
  return [Math.min(src.width, Math.max(0, q[0] / kd)), Math.min(src.height, Math.max(0, q[1] / kd))];
}

function fit(w, h) { // sizes the main canvas to the stage, returns css size
  const k = Math.min((stage.clientWidth - 24) / w, (stage.clientHeight - 24) / h), dpr = window.devicePixelRatio || 1;
  cv.style.opacity = '';
  cv.style.width = w * k + 'px'; cv.style.height = h * k + 'px';
  cv.width = Math.round(w * k * dpr); cv.height = Math.round(h * k * dpr);
  return { k, dpr };
}

function drawFrame() {
  const { k, dpr } = fit(shown.width, shown.height);
  ks = k;
  const x = cv.getContext('2d');
  x.setTransform(1, 0, 0, 1, 0, 0);
  x.drawImage(shown, 0, 0, cv.width, cv.height);
  x.setTransform(dpr, 0, 0, dpr, 0, 0);
  const p = quad.map(toScreen), w = cv.width / dpr, h = cv.height / dpr;
  x.beginPath(); x.rect(0, 0, w, h); x.moveTo(...p[0]); p.slice(1).forEach((q) => x.lineTo(...q)); x.closePath();
  x.fillStyle = 'rgba(0,0,0,.45)'; x.fill('evenodd'); // dim everything outside the frame
  x.beginPath(); x.moveTo(...p[0]); p.slice(1).forEach((q) => x.lineTo(...q)); x.closePath();
  x.lineWidth = 3; x.strokeStyle = '#38D9F5'; x.stroke();
  for (const [px, py] of p) {
    x.beginPath(); x.arc(px, py, 13, 0, 7);
    x.fillStyle = 'rgba(0,0,0,.35)'; x.fill(); x.lineWidth = 2.5; x.stroke();
  }
}

const resultCanvas = (side) => applyLook(rotate90(warp(disp, quad.map((q) => [q[0] * kd, q[1] * kd]), side), turns), look);
function drawResult() {
  const f = resultCanvas(1100), { dpr } = fit(f.width, f.height);
  ks = 1;
  const x = cv.getContext('2d'); x.setTransform(1, 0, 0, 1, 0, 0);
  x.drawImage(f, 0, 0, cv.width, cv.height);
  if (!dpr) return;
}
function drawMini() {
  const f = resultCanvas(260), k = Math.min(86 / f.width, 120 / f.height), dpr = window.devicePixelRatio || 1;
  mini.style.width = f.width * k + 'px'; mini.style.height = f.height * k + 'px';
  mini.width = Math.round(f.width * k * dpr); mini.height = Math.round(f.height * k * dpr);
  mini.getContext('2d').drawImage(f, 0, 0, mini.width, mini.height);
}
const hint = $('rs-hint');
function paintHint() { // warnings about the shot: smeared picture, strongly tilted phone (the sheet is straightened anyway, but its far side has less detail)
  const sk = skewOf(quad), msgs = [];
  if (blurry) msgs.push(t('rs.warn.blur'));
  if (sk > 0.12) msgs.push(`${t('rs.warn.tilt')} ${Math.round(sk * 100)}%`);
  hint.textContent = msgs.length ? msgs.join(' · ') : t('rs.hint');
  hint.classList.toggle('warn', msgs.length > 0);
}
function paint() {
  raf = 0;
  paintHint();
  mini.hidden = view !== 'frame';
  if (view === 'frame') { drawFrame(); drawMini(); } else drawResult();
  [...lookBar.children].forEach((b) => b.classList.toggle('on', b.dataset.value === look.filter));
}
const schedule = () => { if (!raf) raf = requestAnimationFrame(paint); };

function setView(v) { view = v; paint(); }
function rotate(d) { turns += d; shown = rotate90(disp, turns); paint(); }

// ---- finishing
function finish(v) { if (!stay) root.hidden = true; const r = resolveFn; resolveFn = null; r?.(v); } // stay: a sequence replaces the screen in place
export function closeResult() { root.hidden = true; const r = resolveFn; resolveFn = null; r?.(null); }
const params = () => ({ quad: quad.map((p) => p.slice()), look: { ...look }, turns });
const goBack = () => finish({ ...params(), back: true });

// full render from the ORIGINAL at the size of the chosen PDF quality; if the device cannot afford it, a smaller one, then a plain crop
async function renderFull() {
  const { side } = pdfTarget();
  let img = src, q = quad;
  if (origBlob && side > Math.max(src.width, src.height)) { // the editing copy is smaller than the quality needs: decode the original again
    try {
      img = await loadImage(origBlob, loadSide());
      const k = img.width / src.width;
      q = quad.map((p) => [p[0] * k, p[1] * k]);
    } catch (e) { console.error(e); img = src; q = quad; }
  }
  for (const side2 of [side, Math.round(side * 0.6)]) {
    try { return applyLook(rotate90(warp(img, q, side2), turns), look); } catch (e) { console.error('render', side2, e); }
  }
  return rotate90(warp(src, quad, 1600), turns);
}

// ---- gestures on the picture
const pt = (e) => { const r = cv.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
cv.addEventListener('pointerdown', (e) => {
  if (view !== 'frame') { sw = { x: e.clientX, y: e.clientY, result: true }; return; }
  const [x, y] = pt(e);
  let best = -1, bd = 48;
  quad.map(toScreen).forEach((q, i) => { const d = Math.hypot(q[0] - x, q[1] - y); if (d < bd) { bd = d; best = i; } });
  drag = best;
  if (drag >= 0) cv.setPointerCapture(e.pointerId); else sw = { x: e.clientX, y: e.clientY };
});
cv.addEventListener('pointermove', (e) => {
  if (drag < 0) return;
  quad[drag] = fromScreen(pt(e));
  schedule();
});
const up = (e) => {
  if (drag >= 0) { drag = -1; return; }
  const s = sw; sw = null;
  if (!s) return;
  const dx = e.clientX - s.x, dy = e.clientY - s.y;
  if (Math.abs(dx) > 70 && Math.abs(dy) < Math.abs(dx) * 0.6) { // swipe: next / previous page
    if (dx < 0 && nav.next) finish(params()); else if (dx > 0 && nav.back) goBack();
    return;
  }
  if (Math.abs(dx) < 12 && Math.abs(dy) < 12) {
    if (s.result) { setView('frame'); return; }
    const now = Date.now(); // double-tap: automatic frame
    if (now - lastTap < 350) { quad = detectQuad(src); paint(); }
    lastTap = now;
  }
};
cv.addEventListener('pointerup', up);
cv.addEventListener('pointercancel', () => { drag = -1; sw = null; });
mini.addEventListener('click', () => setView('result'));

lookBar.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { look = defaultLook(b.dataset.value); paint(); } });
$('rs-left').addEventListener('click', () => rotate(-1));
$('rs-right').addEventListener('click', () => rotate(1));
$('rs-retake').addEventListener('click', () => finish('retake'));
$('rs-back').addEventListener('click', goBack);
$('rs-del').addEventListener('click', () => { if (confirm(t('sc.deleteq'))) finish('delete'); });
$('rs-save').addEventListener('click', async () => {
  if (light) { finish(params()); return; } // parameters only, no heavy render
  const b = $('rs-save');
  b.disabled = true;
  await new Promise((r) => setTimeout(r, 30)); // let the button repaint, then do the heavy render
  let canvas;
  try { canvas = await renderFull(); } finally { b.disabled = false; }
  finish({ canvas, ...params() });
});
window.addEventListener('resize', () => { if (!root.hidden && shown) schedule(); });

/**
 * opts: look, turns (initial), saveKey (i18n key of the confirm button), retake ("Retake"), back ("Back"), del (delete icon),
 *       caption (text above the panel), light (return only {quad, look, turns}), stay (keep the screen open for the next call),
 *       swipeNext (a left swipe goes on to the next page), blob (the original photo: the full render is made from it).
 * Resolves {quad, look, turns, canvas? (full size, unless light), back?}, 'retake', 'delete', or null (cancelled).
 */
export function openResult(srcCanvas, q, opts = {}) {
  src = srcCanvas; quad = q.map((p) => p.slice()); turns = opts.turns ?? 0; look = { ...(opts.look ?? defaultLook('auto')) };
  origBlob = opts.blob ?? null;
  disp = scaled(src, 1400); kd = disp.width / src.width; shown = rotate90(disp, turns);
  try { blurry = sharpness(disp) < 0.75; } catch { blurry = false; }
  view = 'frame'; drag = -1; sw = null;
  light = !!opts.light; stay = !!opts.stay;
  nav = { back: !!opts.back, next: !!opts.swipeNext };
  $('rs-retake').hidden = !opts.retake;
  $('rs-back').hidden = !opts.back;
  $('rs-del').hidden = !opts.del;
  $('rs-caption').hidden = !opts.caption;
  $('rs-caption').textContent = opts.caption ?? '';
  const save = $('rs-save');
  save.dataset.i18n = opts.saveKey ?? 'rs.save';
  save.textContent = t(save.dataset.i18n);
  root.hidden = false;
  requestAnimationFrame(paint);
  return new Promise((res) => { resolveFn = res; });
}
