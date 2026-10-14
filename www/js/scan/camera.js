// Camera screen: live preview, shutter, self-timer. Shots are ORIGINALS at the maximum camera quality:
// no frame, no filters, no downscaling (ImageCapture.takePhoto at the largest photo size; full video frame as a fallback).
import { t } from '../i18n.js';
import { getShotDelay } from '../shotdelay.js';
const $ = (id) => document.getElementById(id);
const root = $('camera'), wrap = $('cam-wrap'), video = $('cam-video');
const countEl = $('cam-count'), cancelBtn = $('cam-cancel'), timerBtn = $('cam-timer'), tickEl = $('cam-tick');
let multi = false, onShot = null, shots = 0, stream = null, capture = null, photoOpts, resolveFn = null, cd = 0, shooting = false;

const paintCount = () => { countEl.hidden = !multi; countEl.textContent = `${t('sc.count')} ${shots}`; };
export const isCameraOpen = () => !root.hidden;

function stop() {
  stopCountdown();
  stream?.getTracks().forEach((tr) => tr.stop());
  stream = null; capture = null; photoOpts = undefined; video.srcObject = null; root.hidden = true; multi = false; onShot = null;
}
function finish(v) { stop(); const r = resolveFn; resolveFn = null; r?.(v); }
export const closeCamera = () => finish(null);

async function initCapture() {
  capture = null; photoOpts = undefined;
  if (typeof ImageCapture === 'undefined') return;
  try {
    capture = new ImageCapture(stream.getVideoTracks()[0]);
    const c = await capture.getPhotoCapabilities();
    if (c.imageWidth?.max && c.imageHeight?.max) photoOpts = { imageWidth: c.imageWidth.max, imageHeight: c.imageHeight.max };
  } catch (e) { console.error(e); }
}

async function grab() {
  if (capture) {
    try {
      const b = await capture.takePhoto(photoOpts);
      if (b?.size) return b;
    } catch (e) { console.error(e); }
  }
  const c = document.createElement('canvas'); // fallback: the full video frame
  c.width = video.videoWidth; c.height = video.videoHeight;
  c.getContext('2d').drawImage(video, 0, 0);
  return new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', 0.98));
}

async function shoot() {
  if (shooting || !stream) return;
  shooting = true;
  navigator.vibrate?.(25);
  wrap.classList.add('flash'); setTimeout(() => wrap.classList.remove('flash'), 120);
  try {
    const blob = await grab();
    if (!stream) return; // closed meanwhile
    if (!multi) return finish({ blob });
    shots += 1; paintCount();
    onShot?.({ blob }); // camera stays open for the next page
  } catch (e) { console.error(e); } finally { shooting = false; }
}

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
    if (multi) { n = getShotDelay(); tickEl.textContent = n; } // series: shoot every N s until stopped
    else stopCountdown();
  }, 1000);
  paintTimer();
}
timerBtn.addEventListener('click', () => (cd ? stopCountdown() : startCountdown()));
$('cam-shot').addEventListener('click', shoot);
cancelBtn.addEventListener('click', closeCamera);
document.addEventListener('delaychange', paintTimer);
document.addEventListener('langchange', paintTimer);

/**
 * Single mode: resolves {blob} after the shutter, or null if cancelled.
 * Multi mode (opts.multi): every shot calls opts.onShot({blob}) and the camera stays open; resolves null on close.
 * Rejects if the camera is unavailable.
 */
export async function openCamera(opts = {}) {
  multi = !!opts.multi; onShot = opts.onShot ?? null; shots = opts.start ?? 0;
  cancelBtn.dataset.i18n = multi ? 'cam.done' : 'ed.cancel';
  cancelBtn.textContent = t(cancelBtn.dataset.i18n);
  paintCount(); paintTimer();
  stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: { ideal: 'environment' }, width: { ideal: 3840 }, height: { ideal: 2160 } }, audio: false,
  });
  video.srcObject = stream;
  await video.play();
  root.hidden = false;
  await initCapture();
  return new Promise((res) => { resolveFn = res; });
}
