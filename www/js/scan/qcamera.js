// The camera of the Quick and Scan tabs: a bare picture with a FIXED frame (dimmed outside, corner brackets), the shutter, the flash
// (auto / on / off) and a tap = focus on that spot. A tap on the shutter takes ONE photo at the full resolution of the sensor, checks
// its sharpness (a soft shot: "Retake" / "Save as is") and hands it to cfg.onShot.
//   cfg = { ratio(land) -> frame width/height, label() -> text on the frame, hint() -> hint text, fallback: <input type=file capture>,
//           onShot({blob, crop, vr, land}) -> 'close' | anything else (the camera stays for the next shot),
//           canClose() -> bool | Promise<bool> (asked before X / System Back), onClose() }
// crop = the frame normalised to the photo ([x0, y0, x1, y1]) when the photo has the same field of view as the preview, else null.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { getFlash, nextFlash, BOLT, isDark } from '../flash.js';
import { blobSharpness, isBlurry } from './blobsharp.js';
import { askChoice } from '../dialog.js';

const $ = (id) => document.getElementById(id);
const root = $('qcam'), wrap = $('qcam-wrap'), video = $('qcam-video'), ov = $('qcam-overlay'), shotBtn = $('qcam-shot'), statusEl = $('qcam-status'), hintEl = $('qcam-hint');
const ring = $('qcam-focus'), flashBtn = $('qcam-flash'), badge = $('qcam-count');
let cfg = null, stream = null, capture = null, photoOpts, fills = [], busy = false, torchCap = false, torchOn = false, focusOk = true;
let vw = 0, vh = 0, frame = null, cropOk = false, hintHold = 0, hintFn = null, labelText = null;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);
const fail = (e) => { toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000); console.error(e); };
const setHint = (s, hold = 0) => { hintEl.textContent = s; hintHold = Date.now() + hold; };

export const isQcamOpen = () => !root.hidden;
/** Text of the status line (without the camera on the screen: a toast). */
export function qcamStatus(s) { statusEl.textContent = s; if (root.hidden && s) toast(s, 60000); }
/** Pages counter at the top of the camera ('' hides it). */
export function qcamBadge(s) { badge.textContent = s; badge.hidden = !s; }
/** Another hint under the frame (function) / the usual one (null); another text on the frame (string) / the usual one (null). */
export function qcamTexts(hint, label) { hintFn = hint; labelText = label; setHint(hint ? hint() : cfg?.hint?.() ?? ''); drawFrame(false); }

export async function closeQcam(force = false) {
  if (busy) return;
  if (!force && cfg?.canClose && !(await cfg.canClose())) return;
  if (!cfg) return; // closed while the question was open
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; capture = null; photoOpts = undefined; torchOn = false;
  video.srcObject = null; root.hidden = true; qcamStatus(''); qcamBadge('');
  hintFn = null; labelText = null;
  const c = cfg; cfg = null; c?.onClose?.();
}

// ---- the fixed frame (normalised to the preview picture); the picture is cut to it when the photo has the same field of view
function layout() {
  const k = Math.min(root.clientWidth / vw, root.clientHeight / vh), w = Math.round(vw * k), h = Math.round(vh * k), dpr = window.devicePixelRatio || 1;
  wrap.style.width = w + 'px'; wrap.style.height = h + 'px';
  ov.width = Math.round(w * dpr); ov.height = Math.round(h * dpr);
  const r = cfg.ratio(vw > vh);
  let fw = 0.92, fh = (fw * vw) / (vh * r);
  if (fh > 0.92) { fh = 0.92; fw = (fh * vh * r) / vw; }
  frame = [(1 - fw) / 2, (1 - fh) / 2, (1 + fw) / 2, (1 + fh) / 2];
  drawFrame(false);
}
function drawFrame(ok) {
  const dpr = window.devicePixelRatio || 1, w = ov.width / dpr, h = ov.height / dpr, x = ov.getContext('2d');
  x.setTransform(dpr, 0, 0, dpr, 0, 0); x.clearRect(0, 0, w, h);
  if (!frame || !cfg) return;
  const [a, b, c, d] = [frame[0] * w, frame[1] * h, frame[2] * w, frame[3] * h];
  x.fillStyle = 'rgba(0,0,0,.42)'; x.beginPath(); x.rect(0, 0, w, h); x.rect(a, b, c - a, d - b); x.fill('evenodd'); // dim the outside
  x.strokeStyle = ok ? '#3DDC84' : '#fff'; x.lineWidth = 2; x.strokeRect(a, b, c - a, d - b);
  x.lineWidth = 5; x.lineCap = 'round'; const L = 22; // corner brackets
  for (const [px, py, sx, sy] of [[a, b, 1, 1], [c, b, -1, 1], [a, d, 1, -1], [c, d, -1, -1]]) { x.beginPath(); x.moveTo(px, py + sy * L); x.lineTo(px, py); x.lineTo(px + sx * L, py); x.stroke(); }
  x.fillStyle = 'rgba(255,255,255,.85)'; x.font = '600 12px sans-serif'; x.fillText(labelText ?? cfg.label(), a + 8, b + 20);
}

// ---- flash: auto (only when the scene is dark) / on / off
function paintFlash() {
  const m = getFlash();
  flashBtn.innerHTML = `${BOLT}<span>${t('fl.' + m)}</span>`;
  flashBtn.classList.toggle('on', m === 'on'); flashBtn.classList.toggle('off', m === 'off');
}
flashBtn.addEventListener('click', async () => { nextFlash(); paintFlash(); if (!busy) await torch(getFlash() === 'on'); }); // "on" lights the scene right away
async function torch(on) {
  if (!stream || !torchCap || torchOn === on) return;
  try { await stream.getVideoTracks()[0].applyConstraints({ advanced: [{ torch: on }] }); torchOn = on; } catch (e) { console.error(e); }
}

// ---- focus: continuous by itself; a tap = focus on that spot
async function focusAt(p, mode) {
  const tr = stream?.getVideoTracks()[0];
  if (!tr || !focusOk) return false;
  try { await tr.applyConstraints({ advanced: [{ pointsOfInterest: [{ x: p[0], y: p[1] }], focusMode: mode }] }); return true; } catch {
    try { await tr.applyConstraints({ advanced: [{ focusMode: mode }] }); return true; } catch { focusOk = false; return false; }
  }
}
ov.addEventListener('pointerdown', async (e) => {
  if (!stream || busy) return;
  const r = ov.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
  ring.style.left = x + 'px'; ring.style.top = y + 'px'; ring.classList.remove('go', 'ok'); void ring.offsetWidth; ring.classList.add('go');
  const ok = await focusAt([x / r.width, y / r.height], 'single-shot');
  if (ok) ring.classList.add('ok');
  setHint(t(ok ? 'qs.focused' : 'qs.nofocus'), 1800);
  setTimeout(() => focusAt([x / r.width, y / r.height], 'continuous'), 1800); // then the camera follows the spot by itself
});

// ---- shooting
async function takeOne() {
  if (capture) {
    try { const b = await withTimeout(capture.takePhoto(photoOpts), 9000); if (b?.size) return b; } catch (e) { console.error(e); } // the whole sensor
  }
  const c = document.createElement('canvas'); // fallback: the full video frame
  c.width = video.videoWidth; c.height = video.videoHeight;
  c.getContext('2d').drawImage(video, 0, 0);
  return new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', 0.97));
}

/** The photo -> sharpness check -> cfg.onShot. Returns 'close' when the flow is over. */
async function process(blob, crop, size) {
  qcamStatus(t('qs.check'));
  if (isBlurry(await blobSharpness(blob, crop)) && (await askChoice(t('qs.blur'), [{ id: 'retake', label: t('qs.blur.retake') }, { id: 'save', label: t('qs.blur.save') }])) === 'retake') return 'retake';
  return cfg.onShot({ blob, crop, vr: size.vr, land: size.land });
}

async function shoot() {
  if (busy || !stream) return;
  busy = true; shotBtn.classList.add('busy');
  let res = null;
  try {
    const mode = getFlash(), want = mode === 'on' || (mode === 'auto' && isDark(video));
    if (torchCap) { await torch(want); if (want) await sleep(250); } // the torch lights the shot
    else if (photoOpts) photoOpts.fillLightMode = fills.includes(mode === 'auto' ? 'auto' : mode === 'on' ? 'flash' : 'off') ? (mode === 'auto' ? 'auto' : mode === 'on' ? 'flash' : 'off') : undefined; // the camera's own flash
    qcamStatus(t('qs.shot'));
    const blob = await takeOne();
    if (mode !== 'on') await torch(false);
    res = await process(blob, cropOk ? frame : null, { vr: vw && vh ? vw / vh : 1, land: vw > vh });
  } catch (e) { fail(e); } finally { busy = false; shotBtn.classList.remove('busy'); qcamStatus(''); }
  if (res === 'close') closeQcam(true);
}
shotBtn.addEventListener('click', shoot);
$('qcam-close').addEventListener('click', () => closeQcam());
setInterval(() => { if (!root.hidden && !busy && Date.now() > hintHold) setHint(hintFn ? hintFn() : cfg?.hint?.() ?? ''); }, 500);

// ---- the system camera (when the app has no camera access): one photo per call, the same pipeline
const bound = new WeakSet();
function bindFallback(el) {
  if (!el || bound.has(el)) return;
  bound.add(el);
  el.addEventListener('change', async () => {
    const f = el.files[0];
    el.value = '';
    if (!f || !cfg || busy) return;
    busy = true;
    let res = null;
    try {
      const bmp = await createImageBitmap(f, { imageOrientation: 'from-image' });
      const size = { vr: bmp.width / bmp.height, land: bmp.width > bmp.height };
      bmp.close?.();
      res = await process(f, null, size);
    } catch (e) { fail(e); } finally { busy = false; qcamStatus(''); }
    if (res === 'close') closeQcam(true);
    else if (!stream && cfg) el.click(); // the next photo of the session
  });
}

export async function openQcam(c) {
  cfg?.onClose?.();
  cfg = c; hintFn = null; labelText = null;
  bindFallback(c.fallback);
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1440 } }, audio: false }); // 4:3 like most sensors
  } catch (e) { console.error(e); toast(t('cam.denied')); c.fallback?.click(); return; } // no camera access: the system camera
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  if (!video.videoWidth) await new Promise((r) => { video.onloadedmetadata = r; });
  vw = video.videoWidth; vh = video.videoHeight;
  focusOk = true; torchOn = false; cropOk = false; fills = [];
  layout(); paintFlash(); setHint(c.hint());
  const tr = stream.getVideoTracks()[0], caps = tr.getCapabilities?.() ?? {};
  torchCap = !!caps.torch;
  try { if (caps.focusMode?.includes('continuous')) await tr.applyConstraints({ advanced: [{ focusMode: 'continuous' }] }); } catch { /* the camera decides */ }
  if (getFlash() === 'on') torch(true);
  if (typeof ImageCapture !== 'undefined') { // the full sensor: the largest photo size
    try {
      const cap = new ImageCapture(tr), pc = await withTimeout(cap.getPhotoCapabilities(), 3000);
      capture = cap; fills = pc.fillLightMode || [];
      if (pc?.imageWidth?.max && pc?.imageHeight?.max) {
        photoOpts = { imageWidth: pc.imageWidth.max, imageHeight: pc.imageHeight.max };
        const pr = Math.max(pc.imageWidth.max, pc.imageHeight.max) / Math.min(pc.imageWidth.max, pc.imageHeight.max), sr = Math.max(vw, vh) / Math.min(vw, vh);
        cropOk = Math.abs(pr - sr) / pr < 0.03; // the preview shows the same field of view as the photo: the frame can cut the photo exactly
      }
    } catch (e) { console.error(e); }
  } else cropOk = true; // the video frame itself is shot: it is the preview
}

document.addEventListener('langchange', () => { if (!root.hidden) { paintFlash(); setHint(hintFn ? hintFn() : cfg?.hint?.() ?? ''); drawFrame(false); } });
window.addEventListener('resize', () => { if (!root.hidden && vw) layout(); });
