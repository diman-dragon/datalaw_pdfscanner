// Camera screen: live preview, auto frame (smoothed), draggable corners, shutter, self-timer, torch.
// WYSIWYG: the shot is the exact video frame at the moment of the press, at the highest stream resolution - no delay, nothing is
// shot "later". (Settings can switch to the full sensor photo: sharper, but slower.) The frame is stored with the shot (normalized).
import { detectQuad, insetQuad } from './detect.js';
import { t } from '../i18n.js';
import { getShotDelay } from '../shotdelay.js';
import { getShotMode } from '../shotmode.js';
const $ = (id) => document.getElementById(id);
const root = $('camera'), stage = $('cam-stage'), wrap = $('cam-wrap'), video = $('cam-video'), ov = $('cam-overlay');
const ctx = ov.getContext('2d');
const countEl = $('cam-count'), cancelBtn = $('cam-cancel'), timerBtn = $('cam-timer'), tickEl = $('cam-tick'), shotBtn = $('cam-shot'), torchBtn = $('cam-torch');
let multi = false, onShot = null, shots = 0, stream = null, quad = null, target = null, manual = false, detTimer = 0, raf = 0, resolveFn = null;
let vw = 0, vh = 0, scale = 1, drag = -1, busy = false, lastTap = 0, cd = 0, shooting = false, capture = null, photoOpts, calm = 0, torchOn = false;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);
const toBlob = (c, q) => new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', q));
const paintCount = () => { countEl.hidden = !multi; countEl.textContent = `${t('sc.count')} ${shots}`; };
export const isCameraOpen = () => !root.hidden;

function stop() {
  stopCountdown();
  clearTimeout(detTimer);
  cancelAnimationFrame(raf);
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; capture = null; photoOpts = undefined; quad = null; target = null; shooting = false; torchOn = false;
  shotBtn.classList.remove('busy'); torchBtn.hidden = true; torchBtn.classList.remove('on');
  video.srcObject = null; root.hidden = true; multi = false; onShot = null;
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

// the shown frame eases toward the detected one: no jumping
function animate() {
  raf = requestAnimationFrame(animate);
  if (manual || !quad || !target) return;
  let moved = false;
  quad = quad.map((p, i) => {
    const dx = target[i][0] - p[0], dy = target[i][1] - p[1];
    if (Math.abs(dx) + Math.abs(dy) < 0.4) return p;
    moved = true;
    return [p[0] + dx * 0.2, p[1] + dy * 0.2];
  });
  if (moved) draw();
}

function layout() {
  const dpr = window.devicePixelRatio || 1;
  scale = Math.min(stage.clientWidth / vw, stage.clientHeight / vh);
  const w = vw * scale, h = vh * scale;
  wrap.style.width = w + 'px'; wrap.style.height = h + 'px';
  ov.width = Math.round(w * dpr); ov.height = Math.round(h * dpr);
  draw();
}

// ---- edge detection: a tiny reused canvas, and slower once the frame is steady (light on the CPU, the preview stays smooth)
const dc = document.createElement('canvas');
const dctx = dc.getContext('2d', { willReadFrequently: true });
function detectTick() {
  if (manual || busy || shooting || !vw) return;
  busy = true;
  try {
    const k = Math.min(1, 200 / Math.max(vw, vh));
    dc.width = Math.round(vw * k); dc.height = Math.round(vh * k);
    dctx.drawImage(video, 0, 0, dc.width, dc.height);
    const q = detectQuad(dc, 200).map(([x, y]) => [x / k, y / k]);
    if (!quad) quad = q.map((p) => p.slice());
    if (!target) { target = q; calm = 0; } else {
      const d = q.reduce((m, p, i) => m + Math.hypot(p[0] - target[i][0], p[1] - target[i][1]), 0) / 4;
      if (d > 0.012 * Math.hypot(vw, vh)) { target = q; calm = 0; } else calm++; // below ~1.2% of the diagonal it is jitter: the frame stays put
    }
  } finally { busy = false; }
}
function detectLoop() {
  detectTick();
  detTimer = setTimeout(detectLoop, calm >= 3 ? 1000 : 450);
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
  if (now - lastTap < 350) { manual = false; target = null; calm = 0; detectTick(); }
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

// ---- stream
async function openStream() {
  // the highest resolution the camera offers for the preview stream (the browser picks the closest supported mode)
  stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: { ideal: 'environment' }, width: { ideal: 4096 }, height: { ideal: 3072 }, frameRate: { ideal: 30 } }, audio: false,
  });
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  if (!video.videoWidth) await new Promise((r) => { video.onloadedmetadata = r; });
  vw = video.videoWidth; vh = video.videoHeight;
}

// continuous focus and the torch button, where the camera offers them (never blocks the preview)
async function tuneTrack(st) {
  try {
    const tr = st.getVideoTracks()[0], caps = tr.getCapabilities?.() ?? {};
    if (caps.focusMode?.includes('continuous')) await tr.applyConstraints({ advanced: [{ focusMode: 'continuous' }] });
    if (st === stream && caps.torch) torchBtn.hidden = false;
  } catch (e) { console.error(e); }
}
torchBtn.addEventListener('click', async () => {
  if (!stream) return;
  try {
    torchOn = !torchOn;
    await stream.getVideoTracks()[0].applyConstraints({ advanced: [{ torch: torchOn }] });
    torchBtn.classList.toggle('on', torchOn);
  } catch (e) { console.error(e); torchOn = false; torchBtn.classList.remove('on'); }
});

// sensor mode only: full-size photo settings
async function initCapture(st) {
  if (getShotMode() !== 'sensor' || typeof ImageCapture === 'undefined') return;
  try {
    const cap = new ImageCapture(st.getVideoTracks()[0]);
    const c = await withTimeout(cap.getPhotoCapabilities(), 3000);
    if (st !== stream) return;
    capture = cap;
    if (c?.imageWidth?.max && c?.imageHeight?.max) photoOpts = { imageWidth: c.imageWidth.max, imageHeight: c.imageHeight.max, fillLightMode: 'off' };
  } catch (e) { console.error(e); }
}

// ---- shutter
const snapshot = () => {
  const c = document.createElement('canvas');
  c.width = vw; c.height = vh;
  c.getContext('2d').drawImage(video, 0, 0); // synchronous: exactly the frame on the screen right now
  return c;
};

async function shoot() {
  if (shooting || !stream || !vw) return;
  shooting = true;
  shotBtn.classList.add('busy');
  // everything that defines the shot is taken at the moment of the press
  const frame = snapshot(), q = quad.map(([x, y]) => [x / vw, y / vh]), vr = vw / vh;
  navigator.vibrate?.(20);
  wrap.classList.add('flash'); setTimeout(() => wrap.classList.remove('flash'), 120);
  try {
    let blob = null;
    if (capture) { // sensor mode: full photo, the frame above is the fallback
      try { blob = await withTimeout(capture.takePhoto(photoOpts), 6000); } catch (e) { console.error(e); }
    }
    if (!blob?.size) blob = await toBlob(frame, 0.97);
    if (!stream) return; // closed meanwhile
    const shot = { blob, quadN: q, vr };
    if (!multi) return finish(shot);
    shots += 1; paintCount();
    onShot?.(shot); // camera stays open for the next page
  } catch (e) { console.error(e); } finally { shooting = false; shotBtn.classList.remove('busy'); }
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
shotBtn.addEventListener('click', shoot);
cancelBtn.addEventListener('click', closeCamera);
document.addEventListener('delaychange', paintTimer);
document.addEventListener('langchange', paintTimer);

/**
 * Single mode: resolves {blob, quadN, vr} after the shutter, or null if cancelled.
 * Multi mode (opts.multi): every shot calls opts.onShot({blob, quadN, vr}) and the camera stays open; resolves null on close.
 * quadN = frame (TL,TR,BR,BL) normalized to 0..1 of the picture; vr = its aspect. Rejects if the camera is unavailable.
 */
export async function openCamera(opts = {}) {
  multi = !!opts.multi; onShot = opts.onShot ?? null; shots = opts.start ?? 0;
  cancelBtn.dataset.i18n = multi ? 'cam.done' : 'ed.cancel';
  cancelBtn.textContent = t(cancelBtn.dataset.i18n);
  paintCount(); paintTimer();
  await openStream();
  quad = insetQuad(vw, vh); target = quad.map((p) => p.slice()); manual = false; calm = 0;
  await new Promise((r) => requestAnimationFrame(r));
  layout();
  detectLoop();
  raf = requestAnimationFrame(animate);
  tuneTrack(stream); initCapture(stream); // in the background, never awaited
  return new Promise((res) => { resolveFn = res; });
}
