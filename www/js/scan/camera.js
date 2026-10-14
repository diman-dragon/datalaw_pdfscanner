// Camera screen: live preview, live auto frame, draggable corners, shutter, self-timer.
// The shot is the ORIGINAL at the maximum camera quality (ImageCapture.takePhoto at the largest size; full video frame as a fallback).
// The frame is stored with it (normalized); filters/warp are applied later.
import { detectQuad, insetQuad } from './detect.js';
import { t } from '../i18n.js';
import { getShotDelay } from '../shotdelay.js';
const $ = (id) => document.getElementById(id);
const root = $('camera'), stage = $('cam-stage'), wrap = $('cam-wrap'), video = $('cam-video'), ov = $('cam-overlay');
const ctx = ov.getContext('2d');
const countEl = $('cam-count'), cancelBtn = $('cam-cancel'), timerBtn = $('cam-timer'), tickEl = $('cam-tick');
let multi = false, onShot = null, shots = 0, stream = null, quad = null, manual = false, detTimer = 0, resolveFn = null;
let vw = 0, vh = 0, scale = 1, drag = -1, busy = false, lastTap = 0, cd = 0, shooting = false, capture = null, photoOpts;

const paintCount = () => { countEl.hidden = !multi; countEl.textContent = `${t('sc.count')} ${shots}`; };
export const isCameraOpen = () => !root.hidden;

function stop() {
  stopCountdown();
  clearInterval(detTimer);
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; capture = null; photoOpts = undefined; video.srcObject = null; root.hidden = true; multi = false; onShot = null;
}
function finish(v) { stop(); const r = resolveFn; resolveFn = null; r?.(v); }
export const closeCamera = () => finish(null);

// ---- frame overlay
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
  if (!quad) return;
  const [x, y] = pos(e);
  let best = -1, bd = 48 / scale;
  quad.forEach((q, i) => { const d = Math.hypot(q[0] - x, q[1] - y); if (d < bd) { bd = d; best = i; } });
  drag = best;
  if (drag >= 0) { manual = true; ov.setPointerCapture(e.pointerId); return; }
  const now = Date.now(); // double-tap away from the corners: back to the automatic frame
  if (now - lastTap < 350) { manual = false; detectTick(); }
  lastTap = now;
});
ov.addEventListener('pointermove', (e) => {
  if (drag < 0) return;
  const [x, y] = pos(e);
  quad[drag] = [Math.min(vw, Math.max(0, x)), Math.min(vh, Math.max(0, y))];
  draw();
});
ov.addEventListener('pointerup', () => { drag = -1; });
ov.addEventListener('pointercancel', () => { drag = -1; });

// ---- stream + original photo
async function openStream() {
  stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: { ideal: 'environment' }, width: { ideal: 3840 }, height: { ideal: 2160 } }, audio: false,
  });
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  if (!video.videoWidth) await new Promise((r) => { video.onloadedmetadata = r; });
  vw = video.videoWidth; vh = video.videoHeight;
}

// Runs in the background: the preview must never wait for it. Until it is ready (or if it fails) the default photo size is used.
async function initCapture(st) {
  if (typeof ImageCapture === 'undefined') return;
  try {
    const cap = new ImageCapture(st.getVideoTracks()[0]);
    capture = cap;
    const c = await Promise.race([cap.getPhotoCapabilities(), new Promise((r) => setTimeout(r, 3000))]);
    if (st === stream && c?.imageWidth?.max && c?.imageHeight?.max) photoOpts = { imageWidth: c.imageWidth.max, imageHeight: c.imageHeight.max };
  } catch (e) { console.error(e); }
}

async function grab() {
  if (capture) {
    try {
      const b = await Promise.race([capture.takePhoto(photoOpts), new Promise((_, rej) => setTimeout(() => rej(new Error('takePhoto timeout')), 8000))]);
      if (b?.size) return b;
    } catch (e) { console.error(e); }
  }
  const c = document.createElement('canvas'); // fallback: the full video frame, no downscale
  c.width = vw; c.height = vh;
  c.getContext('2d').drawImage(video, 0, 0);
  return new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', 0.98));
}

async function shoot() {
  if (shooting || !stream || !vw) return;
  shooting = true;
  const q = quad.map(([x, y]) => [x / vw, y / vh]), vr = vw / vh; // the frame as it is at the moment of the press
  navigator.vibrate?.(25);
  wrap.classList.add('flash'); setTimeout(() => wrap.classList.remove('flash'), 120);
  try {
    const blob = await grab();
    if (!stream) return; // closed meanwhile
    const shot = { blob, quadN: q, vr };
    if (!multi) return finish(shot);
    shots += 1; paintCount();
    onShot?.(shot); // camera stays open for the next page
  } catch (e) { console.error(e); } finally { shooting = false; }
}

// ---- self-timer: in multi mode a series, every N seconds until stopped
const paintTimer = () => { timerBtn.textContent = cd ? t('cam.stop') : `${t('cam.timer')} ${getShotDelay()} ${t('cam.sec')}`; };
function stopCountdown() { clearInterval(cd); cd = 0; tickEl.hidden = true; paintTimer(); }
function startCountdown() {
  if (!stream) return;
  let n = getShotDelay();
  tickEl.textContent = n; tickEl.hidden = false;
  cd = setInterval(() => {
    n -= 1;
    if (n > 0) { tickEl.textContent = n; return; }
    shoot();
    if (multi) { n = getShotDelay(); tickEl.textContent = n; } else stopCountdown();
  }, 1000);
  paintTimer();
}
timerBtn.addEventListener('click', () => (cd ? stopCountdown() : startCountdown()));
$('cam-shot').addEventListener('click', shoot);
cancelBtn.addEventListener('click', closeCamera);
document.addEventListener('delaychange', paintTimer);
document.addEventListener('langchange', paintTimer);

/**
 * Single mode: resolves {blob, quadN, vr} after the shutter, or null if cancelled.
 * Multi mode (opts.multi): every shot calls opts.onShot({blob, quadN, vr}) and the camera stays open; resolves null on close.
 * quadN = frame (TL,TR,BR,BL) normalized to 0..1 of the preview; vr = preview aspect. Rejects if the camera is unavailable.
 */
export async function openCamera(opts = {}) {
  multi = !!opts.multi; onShot = opts.onShot ?? null; shots = opts.start ?? 0;
  cancelBtn.dataset.i18n = multi ? 'cam.done' : 'ed.cancel';
  cancelBtn.textContent = t(cancelBtn.dataset.i18n);
  paintCount(); paintTimer();
  await openStream();
  quad = insetQuad(vw, vh); manual = false;
  await new Promise((r) => requestAnimationFrame(r));
  layout();
  detTimer = setInterval(detectTick, 350);
  initCapture(stream); // not awaited
  return new Promise((res) => { resolveFn = res; });
}
