// SMART FOCUS of the camera. Problem: on a sheet with little text the phone's own continuous autofocus finds no contrast and hunts
// back and forth. Here the focus is searched from the picture itself and then LOCKED, so nothing hunts any more:
//  - with manual focus distance (most phones): the lens is swept through the useful range (2-8 diopters = 12-50 cm... up to 90 cm),
//    the sharpness is measured on every step (focusmeter.js, only the sharpest edges count, so a few lines of text are enough),
//    the best distance is refined and set as MANUAL focus;
//  - without it: ONE hardware single-shot at the most detailed spot, and it stays there (no return to continuous).
// A watcher refocuses by itself when the phone is steady and the sharpness drops (another page, another distance); a tap focuses
// on that spot; the shutter checks the focus first.
//   ctx = { video, track() -> MediaStreamTrack, region() -> [x0, y0, x1, y1], paused() -> bool, hw(point, mode) -> Promise<bool>, onState(state) }
//   states: 'idle' | 'seek' | 'locked' | 'nodetail'
import { focusScore, detailSpot, motion } from './focusmeter.js';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const nextFrame = (v) => new Promise((r) => {
  const to = setTimeout(r, 120);
  if (v.requestVideoFrameCallback) v.requestVideoFrameCallback(() => { clearTimeout(to); r(); });
  else { clearTimeout(to); setTimeout(r, 40); }
});
const pts = (a, b, n) => Array.from({ length: n }, (_, i) => a + ((b - a) * i) / (n - 1));
const argmax = (r) => r.reduce((bi, x, i) => (x[1] > r[bi][1] ? i : bi), 0);

let ctx = null, timer = 0, job = null, state = 'idle', base = 0, dist = 0, low = 0, still = 0, cool = 0, manual = false, fd = null, dead = false;
const set = (s) => { state = s; ctx?.onState?.(s); };
const alive = () => { if (!ctx) throw new Error('stopped'); };

export const getFocusState = () => state;

export function startFocus(c) {
  stopFocus();
  ctx = c;
  const caps = c.track()?.getCapabilities?.() ?? {};
  fd = caps.focusDistance;
  manual = !!(caps.focusMode?.includes('manual') && fd && fd.max > fd.min);
  state = 'idle'; base = 0; dist = 0; low = 0; still = 0; cool = 0; dead = false;
  timer = setInterval(tick, 350);
}
export function stopFocus() { clearInterval(timer); ctx = null; job = null; state = 'idle'; }

function tick() {
  if (!ctx || job || dead || ctx.paused?.()) return;
  const steady = motion(ctx.video) < 4;
  still = steady ? still + 1 : 0;
  if (Date.now() < cool) return;
  if (state === 'idle') { if (still >= 3) launch(false); return; } // the phone is steady: find the focus
  if (state !== 'locked') return; // 'nodetail': the user taps a spot
  const s = focusScore(ctx.video, ctx.region());
  low = s < base * 0.65 && steady ? low + 1 : 0; // sharpness dropped while steady: the page / the distance changed
  if (low >= 3) launch(true);
}

// ---- manual focus distance: dist in DIOPTERS (1 / meters): equal steps give equal blur
function range() {
  let lo = Math.max(fd.min, 0.08), hi = Math.min(fd.max, 0.9); // metres: from 8 cm to 90 cm
  if (hi <= lo * 1.05) { lo = fd.min; hi = fd.max; }
  return [1 / hi, 1 / Math.max(lo, 1e-3)]; // [far, near]
}
async function setDist(d) {
  let m = Math.min(fd.max, Math.max(fd.min, 1 / d));
  if (fd.step > 0) m = fd.min + Math.round((m - fd.min) / fd.step) * fd.step;
  await ctx.track().applyConstraints({ advanced: [{ focusMode: 'manual', focusDistance: m }] });
}
async function measure(d, rect, wait) {
  await setDist(d); alive();
  await sleep(wait); await nextFrame(ctx.video); alive();
  const a = focusScore(ctx.video, rect);
  await nextFrame(ctx.video); alive();
  return (a + focusScore(ctx.video, rect)) / 2; // two frames: less noise
}
async function scan(ds, rect) {
  const out = [];
  for (let i = 0; i < ds.length; i++) out.push([ds[i], await measure(ds[i], rect, i === 0 ? 320 : 130)]); // the first step: the lens travels far
  return out;
}

async function sweepManual(rect, quick) {
  const [dFar, dNear] = range();
  let res = null, coarse = null;
  if (quick && dist) { // near the last distance only
    res = await scan(pts(Math.max(dFar, dist - 0.7), Math.min(dNear, dist + 0.7), 7), rect);
    const bi = argmax(res);
    if (bi === 0 || bi === res.length - 1) res = null; // the peak is at the edge: it is somewhere else
  }
  if (!res) {
    coarse = await scan(pts(dFar, dNear, 12), rect);
    const bi = argmax(coarse);
    res = await scan(pts(coarse[Math.max(0, bi - 1)][0], coarse[Math.min(coarse.length - 1, bi + 1)][0], 6), rect); // refine
  }
  if (coarse) {
    const sorted = coarse.map((x) => x[1]).sort((a, b) => a - b);
    if (sorted[sorted.length - 1] / Math.max(1e-6, sorted[sorted.length >> 1]) < 1.12) { // no peak: nothing to focus on
      await ctx.track().applyConstraints({ advanced: [{ focusMode: 'continuous' }] }).catch(() => {});
      cool = Date.now() + 4000; set('nodetail'); return;
    }
  }
  dist = res[argmax(res)][0];
  await setDist(dist); alive();
  await sleep(250);
  const a = focusScore(ctx.video, ctx.region());
  await nextFrame(ctx.video);
  base = (a + focusScore(ctx.video, ctx.region())) / 2;
  set('locked');
}

// ---- no manual distance: one hardware single-shot at the most detailed spot, then it stays
async function singleShot(rect, point) {
  const ok = await ctx.hw(point ?? detailSpot(ctx.video, rect), 'single-shot');
  alive();
  if (!ok) { dead = true; set('idle'); return; }
  await sleep(1000); alive();
  base = focusScore(ctx.video, ctx.region());
  set('locked');
}

function launch(quick, rect, point) {
  if (job) return job;
  set('seek');
  const r = rect ?? ctx.region();
  job = (async () => {
    try { await (manual ? sweepManual(r, quick) : singleShot(r, point)); } catch (e) {
      if (ctx) { console.error(e); if (manual) manual = false; cool = Date.now() + 3000; set('idle'); } // the lens refused manual focus: the hardware way next time
    } finally { job = null; low = 0; still = 0; }
    return state;
  })();
  return job;
}

/** The user tapped the spot p = [x, y] (normalised): a full search measured on that spot. */
export async function tapFocus(p) {
  if (!ctx) return 'idle';
  if (job) await job;
  const w = 0.14;
  return launch(false, [Math.max(0, p[0] - w), Math.max(0, p[1] - w), Math.min(1, p[0] + w), Math.min(1, p[1] + w)], p);
}

/** Before the shutter: wait for a search in progress; if the focus is not sure (not locked, or the sharpness fell), a quick one. */
export async function beforeShot() {
  if (!ctx || dead) return;
  if (job) await Promise.race([job, sleep(8000)]);
  if (!ctx || state === 'nodetail') return;
  if (state === 'locked' && focusScore(ctx.video, ctx.region()) >= base * 0.8) return;
  await Promise.race([launch(true), sleep(8000)]);
}
