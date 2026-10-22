// Measures how sharp the camera preview is, from the picture itself (used by autofocus.js).
// Works on a page with very little text: only the sharpest 1% of the edges counts, so empty paper does not dilute the score.
const K = 0.3; // working scale: pixels of the measured picture per pixel of the video
const cv = document.createElement('canvas');
const cx = cv.getContext('2d', { willReadFrequently: true });
const tiny = document.createElement('canvas'); tiny.width = 32; tiny.height = 24;
const tx = tiny.getContext('2d', { willReadFrequently: true });
let prev = null;
const size = (v) => [v.videoWidth ?? v.width, v.videoHeight ?? v.height];

/** The part rect = [x0, y0, x1, y1] (normalised to the video) as gray levels. */
function gray(video, rect) {
  const [vw, vh] = size(video);
  const sx = rect[0] * vw, sy = rect[1] * vh, sw = Math.max(8, (rect[2] - rect[0]) * vw), sh = Math.max(8, (rect[3] - rect[1]) * vh);
  const w = Math.max(24, Math.round(sw * K)), h = Math.max(24, Math.round(sh * K));
  cv.width = w; cv.height = h;
  cx.drawImage(video, sx, sy, sw, sh, 0, 0, w, h);
  const d = cx.getImageData(0, 0, w, h).data, g = new Float32Array(w * h);
  let sum = 0;
  for (let i = 0, j = 0; j < g.length; i += 4, j++) { g[j] = d[i] * 0.3 + d[i + 1] * 0.59 + d[i + 2] * 0.11; sum += g[j]; }
  return { g, w, h, mean: sum / g.length };
}

/** Sharpness score of the part (higher = sharper). Comparable between frames of one scene (the brightness is divided out). */
export function focusScore(video, rect = [0, 0, 1, 1]) {
  const { g, w, h, mean } = gray(video, rect);
  const hist = new Uint32Array(512);
  let n = 0;
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x, a = Math.abs(g[i + 1] - g[i - 1]) + Math.abs(g[i + w] - g[i - w]);
    hist[a > 511 ? 511 : a | 0]++; n++;
  }
  const need = Math.max(20, n * 0.01);
  let acc = 0, cnt = 0;
  for (let b = 511; b >= 0 && cnt < need; b--) { const take = Math.min(hist[b], need - cnt); acc += take * b; cnt += take; }
  return acc / Math.max(1, cnt) / (mean + 20);
}

/** The spot with the most detail (text) inside the part -> [x, y] normalised to the video. */
export function detailSpot(video, rect = [0, 0, 1, 1]) {
  const { g, w, h } = gray(video, rect), GX = 8, GY = 6, cell = new Float32Array(GX * GY);
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x;
    cell[Math.floor((y / h) * GY) * GX + Math.floor((x / w) * GX)] += Math.abs(g[i + 1] - g[i - 1]) + Math.abs(g[i + w] - g[i - w]);
  }
  let best = 0;
  for (let c = 1; c < cell.length; c++) if (cell[c] > cell[best]) best = c;
  const cxn = ((best % GX) + 0.5) / GX, cyn = (Math.floor(best / GX) + 0.5) / GY;
  return [rect[0] + cxn * (rect[2] - rect[0]), rect[1] + cyn * (rect[3] - rect[1])];
}

/** How much the picture moved since the previous call (mean gray difference of a tiny frame, 0..255). 99 on the first call. */
export function motion(video) {
  tx.drawImage(video, 0, 0, 32, 24);
  const d = tx.getImageData(0, 0, 32, 24).data, cur = new Float32Array(768);
  for (let i = 0, j = 0; j < 768; i += 4, j++) cur[j] = d[i] * 0.3 + d[i + 1] * 0.59 + d[i + 2] * 0.11;
  let m = 99;
  if (prev) { m = 0; for (let j = 0; j < 768; j++) m += Math.abs(cur[j] - prev[j]); m /= 768; }
  prev = cur;
  return m;
}
