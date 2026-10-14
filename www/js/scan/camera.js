// Own camera screen: live preview, live auto frame, draggable corners, shutter.
import { detectQuad, insetQuad } from './detect.js';
import { t } from '../i18n.js';
const $ = (id) => document.getElementById(id);
const root = $('camera'), stage = $('cam-stage'), wrap = $('cam-wrap'), video = $('cam-video'), ov = $('cam-overlay');
const ctx = ov.getContext('2d');
const fxBar = $('cam-fx'), countEl = $('cam-count'), cancelBtn = $('cam-cancel');
const paintCount = () => { countEl.hidden = !multi; countEl.textContent = `${t('sc.count')} ${shots}`; };
const setFx = (v) => {
  fx = v;
  video.className = 'fx-' + v; // live preview via CSS filter; the saved scan uses the real engine filter
  [...fxBar.children].forEach((b) => b.classList.toggle('on', b.dataset.value === v));
};
fxBar.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) setFx(b.dataset.value); });
let multi = false, onShot = null, shots = 0, fx = 'color', stream = null, quad = null, manual = false, timer = 0, resolveFn = null, vw = 0, vh = 0, scale = 1, drag = -1, busy = false;

export const isCameraOpen = () => !root.hidden;

function stop() {
  clearInterval(timer);
  stream?.getTracks().forEach((t) => t.stop());
  stream = null; video.srcObject = null; root.hidden = true; multi = false; onShot = null;
}
function finish(v) { stop(); const r = resolveFn; resolveFn = null; r?.(v); }
export const closeCamera = () => finish(null);

function draw() {
  const dpr = window.devicePixelRatio || 1;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, ov.width, ov.height);
  if (!quad) return;
  const p = quad.map(([x, y]) => [x * scale, y * scale]);
  ctx.beginPath(); ctx.moveTo(...p[0]); p.slice(1).forEach((q) => ctx.lineTo(...q)); ctx.closePath();
  ctx.fillStyle = 'rgba(56,217,245,.15)'; ctx.fill();
  ctx.lineWidth = 3; ctx.strokeStyle = manual ? '#FFC247' : '#38D9F5'; ctx.stroke();
  for (const [x, y] of p) {
    ctx.beginPath(); ctx.arc(x, y, 13, 0, 7);
    ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fill(); ctx.lineWidth = 2.5; ctx.stroke();
  }
}

function layout() {
  const dpr = window.devicePixelRatio || 1;
  scale = Math.min(stage.clientWidth / vw, stage.clientHeight / vh);
  const w = vw * scale, h = vh * scale;
  wrap.style.width = w + 'px'; wrap.style.height = h + 'px';
  ov.width = Math.round(w * dpr); ov.height = Math.round(h * dpr);
  draw();
}

function detectTick() {
  if (manual || busy || !vw) return;
  busy = true;
  try {
    const k = Math.min(1, 240 / Math.max(vw, vh));
    const c = document.createElement('canvas');
    c.width = Math.round(vw * k); c.height = Math.round(vh * k);
    c.getContext('2d').drawImage(video, 0, 0, c.width, c.height);
    const q = detectQuad(c, 240).map(([x, y]) => [x / k, y / k]);
    quad = quad ? q.map((p, i) => [quad[i][0] * 0.4 + p[0] * 0.6, quad[i][1] * 0.4 + p[1] * 0.6]) : q;
    draw();
  } finally { busy = false; }
}

const pos = (e) => { const r = ov.getBoundingClientRect(); return [(e.clientX - r.left) / scale, (e.clientY - r.top) / scale]; };
ov.addEventListener('pointerdown', (e) => {
  const [x, y] = pos(e);
  let best = -1, bd = 48 / scale;
  quad.forEach((q, i) => { const d = Math.hypot(q[0] - x, q[1] - y); if (d < bd) { bd = d; best = i; } });
  drag = best;
  if (drag >= 0) { manual = true; ov.setPointerCapture(e.pointerId); }
});
ov.addEventListener('pointermove', (e) => {
  if (drag < 0) return;
  const [x, y] = pos(e);
  quad[drag] = [Math.min(vw, Math.max(0, x)), Math.min(vh, Math.max(0, y))];
  draw();
});
ov.addEventListener('pointerup', () => { drag = -1; });
ov.addEventListener('pointercancel', () => { drag = -1; });
$('cam-auto').addEventListener('click', () => { manual = false; detectTick(); });
$('cam-cancel').addEventListener('click', closeCamera);
$('cam-shot').addEventListener('click', () => {
  if (!vw) return;
  const k = Math.min(1, 3000 / Math.max(vw, vh));
  const c = document.createElement('canvas');
  c.width = Math.round(vw * k); c.height = Math.round(vh * k);
  c.getContext('2d').drawImage(video, 0, 0, c.width, c.height);
  const q = quad.map(([x, y]) => [x * k, y * k]);
  const frame = { canvas: c, quad: q, filter: fx };
  if (!multi) return finish(frame);
  shots += 1; paintCount(); navigator.vibrate?.(25);
  wrap.classList.add('flash'); setTimeout(() => wrap.classList.remove('flash'), 120);
  onShot?.(frame); // camera stays open for the next page
});

/**
 * Single mode: resolves {canvas, quad, filter} after the shutter, or null if cancelled.
 * Multi mode (opts.multi): every shutter calls opts.onShot(frame) and the camera stays open; resolves null on close.
 * Rejects if the camera is unavailable.
 */
export async function openCamera(opts = {}) {
  multi = !!opts.multi; onShot = opts.onShot ?? null; shots = opts.start ?? 0;
  cancelBtn.dataset.i18n = multi ? 'cam.done' : 'ed.cancel';
  cancelBtn.textContent = t(cancelBtn.dataset.i18n);
  paintCount();
  stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: { ideal: 'environment' }, width: { ideal: 3840 }, height: { ideal: 2160 } }, audio: false,
  });
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  if (!video.videoWidth) await new Promise((r) => { video.onloadedmetadata = r; });
  vw = video.videoWidth; vh = video.videoHeight;
  quad = insetQuad(vw, vh); manual = false; setFx('color');
  await new Promise((r) => requestAnimationFrame(r));
  layout();
  timer = setInterval(detectTick, 350);
  return new Promise((res) => { resolveFn = res; });
}
