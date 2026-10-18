import { mk } from './canvasx.js';
// Automatic document edge detection (own engine, no libraries).
// Idea: Otsu split into bright/dark, largest blob per polarity, its 4 extreme corners;
// the polarity whose blob is most rectangular wins. Fallback: inset frame.
const W_MAX = 480;

function boxBlur(src, w, h, r) {
  const tmp = new Float32Array(w * h), out = new Float32Array(w * h);
  for (let y = 0; y < h; y++) {
    let s = 0;
    for (let x = -r; x <= r; x++) s += src[y * w + Math.min(w - 1, Math.max(0, x))];
    for (let x = 0; x < w; x++) {
      tmp[y * w + x] = s / (2 * r + 1);
      s += src[y * w + Math.min(w - 1, x + r + 1)] - src[y * w + Math.max(0, x - r)];
    }
  }
  for (let x = 0; x < w; x++) {
    let s = 0;
    for (let y = -r; y <= r; y++) s += tmp[Math.min(h - 1, Math.max(0, y)) * w + x];
    for (let y = 0; y < h; y++) {
      out[y * w + x] = s / (2 * r + 1);
      s += tmp[Math.min(h - 1, y + r + 1) * w + x] - tmp[Math.max(0, y - r) * w + x];
    }
  }
  return out;
}

// Separable grey max/min filter (radius r). closing = min(max(g)) removes dark text on paper, opening = the opposite.
function rank(src, w, h, r, useMax) {
  const pick = useMax ? Math.max : Math.min;
  const tmp = new Float32Array(w * h), out = new Float32Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let v = src[y * w + x];
    for (let k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++) v = pick(v, src[y * w + k]);
    tmp[y * w + x] = v;
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let v = tmp[y * w + x];
    for (let k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++) v = pick(v, tmp[k * w + x]);
    out[y * w + x] = v;
  }
  return out;
}
const closing = (g, w, h, r) => rank(rank(g, w, h, r, true), w, h, r, false);
const opening = (g, w, h, r) => rank(rank(g, w, h, r, false), w, h, r, true);

function otsu(g) {
  const hist = new Float64Array(256);
  for (const v of g) hist[Math.max(0, Math.min(255, Math.round(v)))]++;
  const total = g.length;
  let sum = 0;
  for (let i = 0; i < 256; i++) sum += i * hist[i];
  let wB = 0, sB = 0, best = 0, thr = 128;
  for (let t = 0; t < 256; t++) {
    wB += hist[t];
    if (!wB) continue;
    const wF = total - wB;
    if (!wF) break;
    sB += t * hist[t];
    const d = sB / wB - (sum - sB) / wF;
    const v = wB * wF * d * d;
    if (v > best) { best = v; thr = t; }
  }
  return thr;
}

// Largest connected blob of mask==1: area + 4 extreme corners.
function largestBlob(m, w, h) {
  const seen = new Uint8Array(w * h), stack = new Int32Array(w * h);
  let best = null;
  for (let i = 0; i < w * h; i++) {
    if (!m[i] || seen[i]) continue;
    let sp = 0, area = 0;
    const e = { tl: [0, 0, 1e9], tr: [0, 0, -1e9], br: [0, 0, -1e9], bl: [0, 0, 1e9] };
    stack[sp++] = i; seen[i] = 1;
    while (sp) {
      const p = stack[--sp], x = p % w, y = (p / w) | 0;
      area++;
      const a = x + y, b = x - y;
      if (a < e.tl[2]) e.tl = [x, y, a];
      if (a > e.br[2]) e.br = [x, y, a];
      if (b > e.tr[2]) e.tr = [x, y, b];
      if (b < e.bl[2]) e.bl = [x, y, b];
      if (x > 0 && m[p - 1] && !seen[p - 1]) { seen[p - 1] = 1; stack[sp++] = p - 1; }
      if (x < w - 1 && m[p + 1] && !seen[p + 1]) { seen[p + 1] = 1; stack[sp++] = p + 1; }
      if (y > 0 && m[p - w] && !seen[p - w]) { seen[p - w] = 1; stack[sp++] = p - w; }
      if (y < h - 1 && m[p + w] && !seen[p + w]) { seen[p + w] = 1; stack[sp++] = p + w; }
    }
    if (!best || area > best.area) best = { area, quad: [e.tl, e.tr, e.br, e.bl].map((q) => [q[0], q[1]]) };
  }
  return best;
}

const quadArea = (q) => {
  let s = 0;
  for (let i = 0; i < 4; i++) { const a = q[i], b = q[(i + 1) % 4]; s += a[0] * b[1] - b[0] * a[1]; }
  return Math.abs(s) / 2;
};

export function insetQuad(w, h, f = 0.06) {
  return [[w * f, h * f], [w * (1 - f), h * f], [w * (1 - f), h * (1 - f)], [w * f, h * (1 - f)]];
}

/** @returns {number[][]} 4 points TL,TR,BR,BL in src pixel coords */
export function detectQuad(src, maxSide = W_MAX) {
  const k = Math.min(1, maxSide / Math.max(src.width, src.height));
  const w = Math.max(8, Math.round(src.width * k)), h = Math.max(8, Math.round(src.height * k));
  const c = mk(w, h);
  const ctx = c.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(src, 0, 0, w, h);
  const d = ctx.getImageData(0, 0, w, h).data;
  const g = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) g[i] = 0.299 * d[i * 4] + 0.587 * d[i * 4 + 1] + 0.114 * d[i * 4 + 2];
  const rr = Math.max(2, Math.round((4 * Math.max(w, h)) / 480));
  let best = null;
  for (const bright of [true, false]) {
    // remove text first: dark text on bright paper (closing) / light text on dark paper (opening)
    const b = boxBlur(bright ? closing(g, w, h, rr) : opening(g, w, h, rr), w, h, 2), thr = otsu(b);
    const m = new Uint8Array(w * h);
    for (let i = 0; i < w * h; i++) m[i] = (b[i] > thr) === bright ? 1 : 0;
    const blob = largestBlob(m, w, h);
    if (!blob || blob.area < 0.1 * w * h) continue;
    const qa = quadArea(blob.quad);
    const score = qa > 0 ? blob.area / qa : 0;
    if (!best || score > best.score) best = { score, quad: blob.quad };
  }
  if (!best || best.score < 0.6) return insetQuad(src.width, src.height);
  return best.quad.map(([x, y]) => [x / k, y / k]);
}
