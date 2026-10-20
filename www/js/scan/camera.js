// Camera screen: live preview, auto frame (smoothed), draggable corners, shutter, self-timer, torch.
// WYSIWYG: the shot is the exact video frame at the moment of the press, at the highest stream resolution -
// no delay, nothing is shot "later". The frame is stored with the shot (normalized).
import { detectQuad, insetQuad } from './detect.js';
import { t } from '../i18n.js';
import { getShotDelay } from '../shotdelay.js';
import { skewOf, focusScore } from './quality.js';
import { getSeries, getFrameMode, setFrameMode } from '../camsettings.js';
import { FRAMES, FRAME_IDS } from '../docTypes.js';
const $ = (id) => document.getElementById(id);
const root = $('camera'), stage = $('cam-stage'), wrap = $('cam-wrap'), video = $('cam-video'), ov = $('cam-overlay');
const ctx = ov.getContext('2d');
const countEl = $('cam-count'), cancelBtn = $('cam-cancel'), timerBtn = $('cam-timer'), tickEl = $('cam-tick'), shotBtn = $('cam-shot'), torchBtn = $('cam-torch'), warnEl = $('cam-warn'), framesEl = $('cam-frames'), coachEl = $('cam-coach'), focusEl = $('cam-focus');
let multi = false, onShot = null, shots = 0, stream = null, quad = null, target = null, manual = false, detTimer = 0, raf = 0, resolveFn = null;
let vw = 0, vh = 0, scale = 1, drag = -1, busy = false, lastTap = 0, cd = 0, shooting = false, calm = 0, torchOn = false, gyro = 0, warnTimer = 0, frameMode = 'auto';
let aligned = false, poi = null, poiAt = 0, scoreHist = [], focusOk = true, focusAt = 0, coachHold = 0, lastFocusScore = 0;

// ---- steadiness (gyroscope) and tilt warnings
const onMotion = (e) => {
  const r = e.rotationRate;
  if (r) gyro = gyro * 0.6 + Math.hypot(r.alpha || 0, r.beta || 0, r.gamma || 0) * 0.4; // deg/s, smoothed
};
function paintWarn() {
  let msg = '';
  if (quad) { const sk = skewOf(quad); if (sk > 0.1) msg = `${t('cam.warn.tilt')} ${Math.round(sk * 100)}%`; }
  if (!msg && gyro > 30) msg = t('cam.warn.shake');
  warnEl.hidden = !msg;
  warnEl.textContent = msg;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);
const toBlob = (c, q) => new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', q));
const paintCount = () => { countEl.hidden = !multi; countEl.textContent = `${t('sc.count')} ${shots}`; };
export const isCameraOpen = () => !root.hidden;

function stop() {
  stopCountdown();
  clearTimeout(detTimer);
  clearInterval(warnTimer);
  window.removeEventListener('devicemotion', onMotion);
  warnEl.hidden = true; gyro = 0;
  cancelAnimationFrame(raf);
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; quad = null; target = null; shooting = false; torchOn = false; aligned = false; poi = null; scoreHist = [];
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
  if (manual) { // fixed / own frame: dim everything outside, like a viewfinder
    const w = ov.width / dpr, h = ov.height / dpr;
    ctx.beginPath(); ctx.rect(0, 0, w, h); ctx.moveTo(...p[0]); p.slice(1).forEach((q) => ctx.lineTo(...q)); ctx.closePath();
    ctx.fillStyle = 'rgba(0,0,0,.42)'; ctx.fill('evenodd');
  }
  ctx.beginPath(); ctx.moveTo(...p[0]); p.slice(1).forEach((q) => ctx.lineTo(...q)); ctx.closePath();
  ctx.fillStyle = 'rgba(56,217,245,.15)'; ctx.fill();
  ctx.lineWidth = 3; ctx.strokeStyle = aligned ? '#3DDC84' : manual ? '#FFC247' : '#38D9F5'; ctx.stroke();
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
const fixedRatio = () => !!FRAMES[frameMode]?.ratio;
const inQuad = (x, y, q) => { // ray casting
  let ins = false;
  for (let i = 0, j = 3; i < 4; j = i++) if ((q[i][1] > y) !== (q[j][1] > y) && x < ((q[j][0] - q[i][0]) * (y - q[i][1])) / (q[j][1] - q[i][1]) + q[i][0]) ins = !ins;
  return ins;
};
// where the camera should focus: the place with the most detail (text) inside the frame, else the middle of the frame
function focusPointIn(q, k) {
  const w = dc.width, h = dc.height, d = dctx.getImageData(0, 0, w, h).data, B = 10;
  let best = 0, bx = 0, by = 0;
  for (let y0 = 1; y0 < h - B; y0 += B) for (let x0 = 1; x0 < w - B; x0 += B) {
    if (!inQuad((x0 + B / 2) / k, (y0 + B / 2) / k, q)) continue;
    let e = 0;
    for (let y = y0; y < y0 + B; y += 2) for (let x = x0; x < x0 + B; x += 2) { const i = (y * w + x) * 4; e += Math.abs(d[i + 4] - d[i]) + Math.abs(d[i + w * 4] - d[i]); }
    if (e > best) { best = e; bx = x0 + B / 2; by = y0 + B / 2; }
  }
  if (best < 400) { const cx = q.reduce((s, p) => s + p[0], 0) / 4, cy = q.reduce((s, p) => s + p[1], 0) / 4; return [cx / vw, cy / vh]; } // almost blank: the middle
  return [bx / w, by / h];
}
async function applyAf(p, mode) { // never blocks, never throws
  const tr = stream?.getVideoTracks()[0];
  if (!tr || !focusOk) return;
  try { await tr.applyConstraints({ advanced: [{ pointsOfInterest: [{ x: p[0], y: p[1] }], focusMode: mode }] }); } catch {
    try { await tr.applyConstraints({ advanced: [{ focusMode: mode }] }); } catch { focusOk = false; } // this camera cannot be steered
  }
}
function maybeFocus(p) { // the focus point follows the text, but not on every jitter
  const now = Date.now();
  if (!poi || Math.hypot(poi[0] - p[0], poi[1] - p[1]) > 0.08 || now - poiAt > 3000) { poi = p; poiAt = now; applyAf(p, 'continuous'); }
}
const fc = document.createElement('canvas');
const fcx = fc.getContext('2d', { willReadFrequently: true });
function trackFocus() { // a rolling record of how sharp the picture is: a drop means the camera lost the focus
  const k = 640 / Math.max(vw, vh);
  fc.width = Math.round(vw * k); fc.height = Math.round(vh * k);
  fcx.drawImage(video, 0, 0, fc.width, fc.height);
  lastFocusScore = focusScore(fc);
  scoreHist.push(lastFocusScore); if (scoreHist.length > 6) scoreHist.shift();
}
const focusLow = () => scoreHist.length >= 3 && lastFocusScore < Math.max(...scoreHist) * 0.7;
let tickN = 0;
function detectTick() {
  if ((manual && !fixedRatio()) || busy || shooting || !vw) return;
  busy = true;
  try {
    const k = Math.min(1, 200 / Math.max(vw, vh));
    dc.width = Math.round(vw * k); dc.height = Math.round(vh * k);
    dctx.drawImage(video, 0, 0, dc.width, dc.height);
    const q = detectQuad(dc, 200).map(([x, y]) => [x / k, y / k]);
    if (manual) { // a fixed frame: tell whether the sheet is in it
      const dd = q.reduce((m, p, i) => m + Math.hypot(p[0] - quad[i][0], p[1] - quad[i][1]), 0) / 4;
      aligned = dd < 0.035 * Math.hypot(vw, vh);
    } else {
      aligned = false;
      if (!quad) quad = q.map((p) => p.slice());
      if (!target) { target = q; calm = 0; } else {
        const d = q.reduce((m, p, i) => m + Math.hypot(p[0] - target[i][0], p[1] - target[i][1]), 0) / 4;
        if (d > 0.012 * Math.hypot(vw, vh)) { target = q; calm = 0; } else calm++; // below ~1.2% of the diagonal it is jitter: the frame stays put
      }
    }
    if (quad) maybeFocus(focusPointIn(manual ? quad : (target ?? q), k));
    if (++tickN % 2 === 0) trackFocus();
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
  if (drag >= 0) { manual = true; if (frameMode !== 'free') applyFrameMode('free'); ov.setPointerCapture(e.pointerId); return; }
  const now = Date.now(); // double-tap away from the corners: automatic frame; one tap: focus on that spot
  if (now - lastTap < 350) applyFrameMode('auto'); else tapFocus(x, y);
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

function tapFocus(x, y) {
  focusEl.style.left = x * scale + 'px'; focusEl.style.top = y * scale + 'px';
  focusEl.classList.remove('go'); void focusEl.offsetWidth; focusEl.classList.add('go');
  applyAf([x / vw, y / vh], 'single-shot');
  setTimeout(() => { if (stream && poi) applyAf(poi, 'continuous'); }, 1800); // then back to following the text
}

// ---- frame mode: automatic search, a fixed format chosen in advance (just bring the sheet into it), or the user's own frame
const fixedQuad = (f) => {
  let r = f.ratio;
  if (f.orient === 'land') r = Math.max(r, 1 / r); else if (f.orient === 'port') r = Math.min(r, 1 / r); else r = vw > vh ? Math.max(r, 1 / r) : Math.min(r, 1 / r);
  let w = vw * 0.92, h = w / r;
  if (h > vh * 0.92) { h = vh * 0.92; w = h * r; }
  const x0 = (vw - w) / 2, y0 = (vh - h) / 2;
  return [[x0, y0], [x0 + w, y0], [x0 + w, y0 + h], [x0, y0 + h]];
};
function paintFrames() {
  framesEl.replaceChildren(...FRAME_IDS.map((id) => {
    const b = document.createElement('button');
    b.textContent = t('cam.fm.' + id);
    b.classList.toggle('on', id === frameMode);
    b.addEventListener('click', () => applyFrameMode(id));
    return b;
  }));
}
function applyFrameMode(mode, persist = true) {
  frameMode = mode; aligned = false;
  if (persist) setFrameMode(mode);
  const f = FRAMES[mode];
  if (f?.ratio) { manual = true; quad = fixedQuad(f); target = quad.map((p) => p.slice()); } else if (mode === 'free') manual = true; else { manual = false; target = null; calm = 0; detectTick(); }
  paintFrames(); draw();
}

// the one-line guidance under the picture
function paintCoach() {
  if (Date.now() < coachHold) return;
  let msg, cls = '';
  if (aligned) { msg = t('cam.coach.ok'); cls = 'ok'; } else if (focusLow()) msg = t('cam.coach.focus'); else msg = t(manual ? 'cam.coach.fixed' : 'cam.coach.auto');
  coachEl.textContent = msg; coachEl.className = cls;
}

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

// ---- shutter
const snapshot = () => {
  const c = document.createElement('canvas');
  c.width = vw; c.height = vh;
  c.getContext('2d').drawImage(video, 0, 0); // synchronous: exactly the frame on the screen right now
  return c;
};

const thumbData = (cv) => {
  const k = 120 / Math.max(cv.width, cv.height), s = document.createElement('canvas');
  s.width = Math.round(cv.width * k); s.height = Math.round(cv.height * k);
  s.getContext('2d').drawImage(cv, 0, 0, s.width, s.height);
  return s.toDataURL('image/jpeg', 0.6);
};

async function shoot() {
  if (shooting || !stream || !vw) return;
  shooting = true;
  shotBtn.classList.add('busy');
  if (gyro > 45) for (let i = 0; i < 10 && gyro > 25; i++) await sleep(25); // shaken by the tap: wait up to 0.25 s for calm
  if (!stream) { shooting = false; shotBtn.classList.remove('busy'); return; }
  if (focusOk && focusLow() && Date.now() - focusAt > 1500) { // the picture is smeared: focus once more, on the text, before shooting
    focusAt = Date.now(); coachHold = Date.now() + 900; coachEl.textContent = t('cam.focusing'); coachEl.className = '';
    await applyAf(poi ?? [0.5, 0.5], 'single-shot'); await sleep(500);
    if (!stream) { shooting = false; shotBtn.classList.remove('busy'); return; }
  }
  // the frame and the first picture are taken at the moment of the press
  const q = quad.map(([x, y]) => [x / vw, y / vh]), vr = vw / vh, n = getSeries(), cands = [];
  const take = () => { const cv = snapshot(); cands.push({ score: focusScore(cv), thumb: thumbData(cv), blob: toBlob(cv, 0.97) }); };
  navigator.vibrate?.(20);
  wrap.classList.add('flash'); setTimeout(() => wrap.classList.remove('flash'), 120);
  try {
    take();
    for (let i = 1; i < n && stream; i++) { await sleep(120); if (!stream) break; take(); } // the series: the user chooses the best one later
    const blobs = await Promise.all(cands.map((c) => c.blob));
    if (!stream) return; // closed meanwhile
    let pick = 0; // the sharpest, but a later picture must beat the one at the press by 15%
    cands.forEach((c, i) => { if (i && c.score > cands[pick].score * (pick === 0 ? 1.15 : 1)) pick = i; });
    const alts = cands.map((c, i) => ({ blob: blobs[i], thumb: c.thumb, score: c.score }));
    const shot = { blob: blobs[pick], quadN: q, vr, alts: n > 1 ? alts : undefined, pick, best: pick };
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
 * opts: {multi, onShot, start, frame (a frame preset id), title}.
 * Single mode: resolves {blob, quadN, vr, alts?, pick, best} after the shutter, or null if cancelled (alts = the pictures of the series).
 * Multi mode (opts.multi): every shot calls opts.onShot(shot) and the camera stays open; resolves null on close.
 * quadN = frame (TL,TR,BR,BL) normalized to 0..1 of the picture; vr = its aspect. Rejects if the camera is unavailable.
 */
export async function openCamera(opts = {}) {
  multi = !!opts.multi; onShot = opts.onShot ?? null; shots = opts.start ?? 0;
  cancelBtn.dataset.i18n = multi ? 'cam.done' : 'ed.cancel';
  cancelBtn.textContent = t(cancelBtn.dataset.i18n);
  $('cam-title').textContent = opts.title ?? t('cam.title');
  paintCount(); paintTimer();
  await openStream();
  quad = insetQuad(vw, vh); target = quad.map((p) => p.slice()); manual = false; calm = 0;
  await new Promise((r) => requestAnimationFrame(r));
  layout();
  focusOk = true; poi = null; scoreHist = []; tickN = 0;
  applyFrameMode(opts.frame ?? getFrameMode(), false);
  detectLoop();
  raf = requestAnimationFrame(animate);
  tuneTrack(stream); // in the background, never awaited
  window.addEventListener('devicemotion', onMotion);
  warnTimer = setInterval(() => { paintWarn(); paintCoach(); }, 300);
  return new Promise((res) => { resolveFn = res; });
}
