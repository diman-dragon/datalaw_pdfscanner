// QUICK SCAN: no settings, nothing to choose. One tap: the camera opens, a tap on the shutter takes 3 photos at the full
// resolution of the sensor (autofocus, the torch lights a dark scene), the sharpest one is pressed into the PDF quality of the
// Settings WITHOUT any processing and saved at once into the chosen folder as qscan_YYYYMMDD_HHMM.pdf.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { goTo } from '../tabs.js';
import { getFolder, folderInfo } from '../folder.js';
import { pdfStream } from './pdf.js';
import { finalRaw } from './shot.js';
import { focusScore } from './quality.js';
import { ensureFolder, savePdf } from './save.js';

const $ = (id) => document.getElementById(id);
const root = $('qcam'), video = $('qcam-video'), shotBtn = $('qcam-shot'), statusEl = $('qcam-status'), ring = $('qcam-focus'), target = $('quick-target'), fallback = $('quick-cam');
const SHOTS = 3;
let stream = null, capture = null, photoOpts, busy = false, torchCap = false, torchOn = false, focusOk = true;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);
const status = (s) => { statusEl.textContent = s; };
const p2 = (n) => String(n).padStart(2, '0');
const qname = () => { const d = new Date(); return `qscan_${d.getFullYear()}${p2(d.getMonth() + 1)}${p2(d.getDate())}_${p2(d.getHours())}${p2(d.getMinutes())}`; };

export const isQcamOpen = () => !root.hidden;
export function closeQcam() {
  if (busy) return;
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; capture = null; photoOpts = undefined; torchOn = false;
  video.srcObject = null; root.hidden = true; status('');
}

async function torch(on) { // the light of the scene: on for the whole burst, off after it
  if (!stream || !torchCap || torchOn === on) return;
  try { await stream.getVideoTracks()[0].applyConstraints({ advanced: [{ torch: on }] }); torchOn = on; } catch (e) { console.error(e); }
}
async function focusAt(p, mode) {
  const tr = stream?.getVideoTracks()[0];
  if (!tr || !focusOk) return;
  try { await tr.applyConstraints({ advanced: [{ pointsOfInterest: [{ x: p[0], y: p[1] }], focusMode: mode }] }); } catch {
    try { await tr.applyConstraints({ advanced: [{ focusMode: mode }] }); } catch { focusOk = false; }
  }
}
video.addEventListener('pointerdown', (e) => { // tap = focus there
  if (!stream) return;
  const r = video.getBoundingClientRect();
  ring.style.left = e.clientX + 'px'; ring.style.top = e.clientY + 'px';
  ring.classList.remove('go'); void ring.offsetWidth; ring.classList.add('go');
  focusAt([(e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height], 'single-shot');
  setTimeout(() => focusAt([0.5, 0.5], 'continuous'), 1800);
});

const dark = () => { // a tiny frame of the preview: is the scene dark?
  const c = document.createElement('canvas'); c.width = 32; c.height = 24;
  const x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(video, 0, 0, 32, 24);
  const d = x.getImageData(0, 0, 32, 24).data;
  let s = 0;
  for (let i = 0; i < d.length; i += 4) s += d[i] * 0.3 + d[i + 1] * 0.59 + d[i + 2] * 0.11;
  return s / (d.length / 4) < 55;
};

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

/** blob (the photo) -> PDF (quality of the Settings, no processing) -> the chosen folder -> Library. */
async function saveBlob(blob) {
  status(t('scan.saving'));
  const page = await finalRaw(blob);
  const r = await savePdf(pdfStream(1, async () => page), qname());
  toast(`${t('scan.saved')} ${r.name ?? ''}`);
  document.dispatchEvent(new Event('libchange'));
  goTo('library');
}

async function shoot() {
  if (busy || !stream) return;
  busy = true; shotBtn.classList.add('busy');
  try {
    if (dark()) { await torch(true); await sleep(250); } // light "on automatic": only when the scene is dark
    const blobs = [];
    for (let i = 0; i < SHOTS; i++) { status(`${t('qs.shot')} ${i + 1}/${SHOTS}`); blobs.push(await takeOne()); }
    await torch(false);
    status(t('qs.pick'));
    const best = await sharpest(blobs);
    await saveBlob(best);
    busy = false; closeQcam();
  } catch (e) {
    toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000);
    console.error(e);
  } finally { busy = false; shotBtn.classList.remove('busy'); status(''); torch(false); }
}
shotBtn.addEventListener('click', shoot);
$('qcam-close').addEventListener('click', closeQcam);

async function open() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false });
  } catch (e) { console.error(e); toast(t('cam.denied')); fallback.click(); return; } // no camera access: the system camera
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  focusOk = true; torchOn = false;
  const tr = stream.getVideoTracks()[0], caps = tr.getCapabilities?.() ?? {};
  torchCap = !!caps.torch;
  try { if (caps.focusMode?.includes('continuous')) await tr.applyConstraints({ advanced: [{ focusMode: 'continuous' }] }); } catch { /* the camera decides */ }
  if (typeof ImageCapture !== 'undefined') { // the full sensor: the largest photo size; the flash of the camera is left to automatic
    try {
      const cap = new ImageCapture(tr), c = await withTimeout(cap.getPhotoCapabilities(), 3000);
      capture = cap;
      if (c?.imageWidth?.max && c?.imageHeight?.max) photoOpts = { imageWidth: c.imageWidth.max, imageHeight: c.imageHeight.max };
      if (photoOpts && !torchCap && c.fillLightMode?.includes('auto')) photoOpts.fillLightMode = 'auto';
    } catch (e) { console.error(e); }
  }
}

$('quick-shoot').addEventListener('click', async () => { if (!busy && (await ensureFolder())) open(); });
fallback.addEventListener('change', async () => { // the system camera (when the app has no camera access)
  const f = fallback.files[0];
  fallback.value = '';
  if (!f) return;
  busy = true;
  try { await saveBlob(f); } catch (e) { toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000); console.error(e); } finally { busy = false; }
});
const paintTarget = () => { const info = folderInfo(getFolder()); target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label'); };
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', paintTarget);
paintTarget();
