// QUICK SCAN: nothing to choose. One tap: the camera opens with a fixed A4 frame; a tap on the shutter takes 3 photos at the full
// resolution of the sensor (autofocus, tap = focus on that spot, flash auto / on / off), the sharpest one is cut to the frame,
// pressed into the PDF quality of the Settings WITHOUT any other processing and saved at once into the chosen folder
// as qscan_YYYYMMDD_HHMM.pdf. Then the Library opens.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { goTo } from '../tabs.js';
import { getFolder, folderInfo } from '../folder.js';
import { getFlash, nextFlash, BOLT, isDark } from '../flash.js';
import { pdfStream } from './pdf.js';
import { finalRaw } from './shot.js';
import { focusScore } from './quality.js';
import { ensureFolder, savePdf } from './save.js';

const $ = (id) => document.getElementById(id);
const root = $('qcam'), wrap = $('qcam-wrap'), video = $('qcam-video'), ov = $('qcam-overlay'), shotBtn = $('qcam-shot'), statusEl = $('qcam-status'), hintEl = $('qcam-hint');
const ring = $('qcam-focus'), flashBtn = $('qcam-flash'), target = $('quick-target'), fallback = $('quick-cam');
const SHOTS = 3, A4 = 210 / 297;
let stream = null, capture = null, photoOpts, fills = [], busy = false, torchCap = false, torchOn = false, focusOk = true, vw = 0, vh = 0, frame = null, cropOk = false, hintHold = 0;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);
const status = (s) => { statusEl.textContent = s; };
const p2 = (n) => String(n).padStart(2, '0');
const qname = () => { const d = new Date(); return `qscan_${d.getFullYear()}${p2(d.getMonth() + 1)}${p2(d.getDate())}_${p2(d.getHours())}${p2(d.getMinutes())}`; };
const setHint = (s, hold = 0) => { hintEl.textContent = s; hintHold = Date.now() + hold; };

export const isQcamOpen = () => !root.hidden;
export function closeQcam() {
  if (busy) return;
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; capture = null; photoOpts = undefined; torchOn = false;
  video.srcObject = null; root.hidden = true; status('');
}

// ---- the fixed A4 frame (normalised to the preview picture); the picture is cut to it when the photo has the same field of view
function layout() {
  const k = Math.min(root.clientWidth / vw, root.clientHeight / vh), w = Math.round(vw * k), h = Math.round(vh * k), dpr = window.devicePixelRatio || 1;
  wrap.style.width = w + 'px'; wrap.style.height = h + 'px';
  ov.width = Math.round(w * dpr); ov.height = Math.round(h * dpr);
  const r = vh > vw ? A4 : 1 / A4; // the frame follows the orientation of the phone
  let fw = 0.92, fh = (fw * vw) / (vh * r);
  if (fh > 0.92) { fh = 0.92; fw = (fh * vh * r) / vw; }
  frame = [(1 - fw) / 2, (1 - fh) / 2, (1 + fw) / 2, (1 + fh) / 2];
  drawFrame(false);
}
function drawFrame(ok) {
  const dpr = window.devicePixelRatio || 1, w = ov.width / dpr, h = ov.height / dpr, x = ov.getContext('2d');
  x.setTransform(dpr, 0, 0, dpr, 0, 0); x.clearRect(0, 0, w, h);
  if (!frame) return;
  const [a, b, c, d] = [frame[0] * w, frame[1] * h, frame[2] * w, frame[3] * h];
  x.fillStyle = 'rgba(0,0,0,.42)'; x.beginPath(); x.rect(0, 0, w, h); x.rect(a, b, c - a, d - b); x.fill('evenodd'); // dim the outside
  x.strokeStyle = ok ? '#3DDC84' : '#fff'; x.lineWidth = 2; x.strokeRect(a, b, c - a, d - b);
  x.lineWidth = 5; x.lineCap = 'round'; const L = 22; // corner brackets
  for (const [px, py, sx, sy] of [[a, b, 1, 1], [c, b, -1, 1], [a, d, 1, -1], [c, d, -1, -1]]) { x.beginPath(); x.moveTo(px, py + sy * L); x.lineTo(px, py); x.lineTo(px + sx * L, py); x.stroke(); }
  x.fillStyle = 'rgba(255,255,255,.85)'; x.font = '600 12px sans-serif'; x.fillText('A4', a + 8, b + 20);
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
async function sharpest(blobs) { // the same scene shot three times: the one with the crispest edges
  let best = blobs[0], bestS = -1;
  for (const b of blobs) {
    try {
      const bmp = await createImageBitmap(b, { imageOrientation: 'from-image' });
      const k = Math.min(1, 800 / Math.max(bmp.width, bmp.height)), c = document.createElement('canvas');
      c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
      c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
      bmp.close?.();
      const s = focusScore(c);
      if (s > bestS) { bestS = s; best = b; }
    } catch (e) { console.error(e); }
  }
  return best;
}

/** blob (the photo) -> PDF (quality of the Settings, cut to the frame) -> the chosen folder -> Library. */
async function saveBlob(blob, crop) {
  status(t('scan.saving'));
  const page = await finalRaw(blob, crop, vw && vh ? vw / vh : 1);
  const r = await savePdf(pdfStream(1, async () => page), qname());
  toast(`${t('scan.saved')} ${r.name ?? ''}`);
  document.dispatchEvent(new Event('libchange'));
  goTo('library');
}
async function shoot() {
  if (busy || !stream) return;
  busy = true; shotBtn.classList.add('busy');
  try {
    const mode = getFlash(), want = mode === 'on' || (mode === 'auto' && isDark(video));
    if (torchCap) { await torch(want); if (want) await sleep(250); } // the torch lights the whole burst
    else if (photoOpts) photoOpts.fillLightMode = fills.includes(mode === 'auto' ? 'auto' : mode === 'on' ? 'flash' : 'off') ? (mode === 'auto' ? 'auto' : mode === 'on' ? 'flash' : 'off') : undefined; // the camera's own flash
    const blobs = [];
    for (let i = 0; i < SHOTS; i++) { status(`${t('qs.shot')} ${i + 1}/${SHOTS}`); blobs.push(await takeOne()); }
    if (mode !== 'on') await torch(false);
    status(t('qs.pick'));
    const best = await sharpest(blobs);
    await saveBlob(best, cropOk ? frame : null);
    busy = false; closeQcam();
  } catch (e) {
    toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000);
    console.error(e);
  } finally { busy = false; shotBtn.classList.remove('busy'); status(''); }
}
shotBtn.addEventListener('click', shoot);
$('qcam-close').addEventListener('click', closeQcam);
setInterval(() => { if (!root.hidden && !busy && Date.now() > hintHold) setHint(t('qs.hint')); }, 500);

async function open() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1440 } }, audio: false }); // 4:3 like most sensors
  } catch (e) { console.error(e); toast(t('cam.denied')); fallback.click(); return; } // no camera access: the system camera
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  if (!video.videoWidth) await new Promise((r) => { video.onloadedmetadata = r; });
  vw = video.videoWidth; vh = video.videoHeight;
  focusOk = true; torchOn = false; cropOk = false; fills = [];
  layout(); paintFlash(); setHint(t('qs.hint'));
  const tr = stream.getVideoTracks()[0], caps = tr.getCapabilities?.() ?? {};
  torchCap = !!caps.torch;
  try { if (caps.focusMode?.includes('continuous')) await tr.applyConstraints({ advanced: [{ focusMode: 'continuous' }] }); } catch { /* the camera decides */ }
  if (getFlash() === 'on') torch(true);
  if (typeof ImageCapture !== 'undefined') { // the full sensor: the largest photo size
    try {
      const cap = new ImageCapture(tr), c = await withTimeout(cap.getPhotoCapabilities(), 3000);
      capture = cap; fills = c.fillLightMode || [];
      if (c?.imageWidth?.max && c?.imageHeight?.max) {
        photoOpts = { imageWidth: c.imageWidth.max, imageHeight: c.imageHeight.max };
        const pr = Math.max(c.imageWidth.max, c.imageHeight.max) / Math.min(c.imageWidth.max, c.imageHeight.max), sr = Math.max(vw, vh) / Math.min(vw, vh);
        cropOk = Math.abs(pr - sr) / pr < 0.03; // the preview shows the same field of view as the photo: the frame can cut the photo exactly
      }
    } catch (e) { console.error(e); }
  } else cropOk = true; // the video frame itself is shot: it is the preview
}

$('quick-shoot').addEventListener('click', async () => { if (!busy && (await ensureFolder())) open(); });
fallback.addEventListener('change', async () => { // the system camera (when the app has no camera access)
  const f = fallback.files[0];
  fallback.value = '';
  if (!f) return;
  busy = true;
  try { await saveBlob(f, null); } catch (e) { toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000); console.error(e); } finally { busy = false; }
});
const paintTarget = () => { const info = folderInfo(getFolder()); target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label'); };
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', () => { paintTarget(); if (!root.hidden) { paintFlash(); setHint(t('qs.hint')); } });
window.addEventListener('resize', () => { if (!root.hidden && vw) layout(); });
paintTarget();
