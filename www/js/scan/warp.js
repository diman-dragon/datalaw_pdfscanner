import { mk } from './canvasx.js';
// Perspective correction: quad -> upright rectangle (homography + bilinear sampling).
function solve(A, b) { // Gaussian elimination, 8x8
  const n = b.length;
  for (let i = 0; i < n; i++) {
    let p = i;
    for (let r = i + 1; r < n; r++) if (Math.abs(A[r][i]) > Math.abs(A[p][i])) p = r;
    [A[i], A[p]] = [A[p], A[i]]; [b[i], b[p]] = [b[p], b[i]];
    for (let r = i + 1; r < n; r++) {
      const f = A[r][i] / A[i][i];
      for (let c = i; c < n; c++) A[r][c] -= f * A[i][c];
      b[r] -= f * b[i];
    }
  }
  const x = new Array(n).fill(0);
  for (let i = n - 1; i >= 0; i--) {
    let s = b[i];
    for (let c = i + 1; c < n; c++) s -= A[i][c] * x[c];
    x[i] = s / A[i][i];
  }
  return x;
}

// h maps (x,y) of `from` to (u,v) of `to`.
function homography(from, to) {
  const A = [], b = [];
  for (let i = 0; i < 4; i++) {
    const [x, y] = from[i], [u, v] = to[i];
    A.push([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.push(u);
    A.push([0, 0, 0, x, y, 1, -v * x, -v * y]); b.push(v);
  }
  return solve(A, b);
}

const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

/**
 * Real width / height of the rectangle that is seen as `quad` (TL, TR, BR, BL) in a picture of cx*2 x cy*2 px.
 * The focal length is recovered from the orthogonality of the sheet's sides (Zhang & He, principal point = picture centre);
 * when that is impossible (the phone is tilted about one axis only) or implausible, a typical phone lens is assumed
 * (f = 0.75 x the long side of the picture, ~26 mm equivalent).
 */
export function trueAspect(quad, cx, cy) {
  const m = (p) => [p[0] - cx, p[1] - cy, 1];
  const m1 = m(quad[0]), m2 = m(quad[1]), m3 = m(quad[3]), m4 = m(quad[2]);
  const k2 = dot(cross(m1, m4), m3) / dot(cross(m2, m4), m3);
  const k3 = dot(cross(m1, m4), m2) / dot(cross(m3, m4), m2);
  if (!Number.isFinite(k2) || !Number.isFinite(k3)) return null;
  const n2 = m2.map((v, i) => k2 * v - m1[i]), n3 = m3.map((v, i) => k3 * v - m1[i]);
  const L = Math.max(cx, cy) * 2, prior = 0.75 * L;
  let f2 = -(n2[0] * n3[0] + n2[1] * n3[1]) / (n2[2] * n3[2]);
  const f = Math.sqrt(f2);
  // the focal length is only trustworthy when the sheet converges in BOTH directions (tilted about two axes)
  const wellPosed = Math.abs(k2 - 1) > 0.05 && Math.abs(k3 - 1) > 0.05;
  if (!wellPosed || !(f2 > 0) || !Number.isFinite(f) || f < 0.35 * L || f > 2 * L) f2 = prior * prior; // otherwise: typical lens
  const len2 = (n) => (n[0] * n[0] + n[1] * n[1]) / f2 + n[2] * n[2];
  const r = Math.sqrt(len2(n2) / len2(n3));
  return Number.isFinite(r) && r > 0.2 && r < 5 ? r : null;
}

/** Output size of the straightened page: the right proportions, the detail of the nearest side kept, long side <= maxSide. */
export function pageSize(quad, srcW, srcH, maxSide) {
  const [tl, tr, br, bl] = quad;
  const wMax = Math.max(dist(tl, tr), dist(bl, br)), hMax = Math.max(dist(tl, bl), dist(tr, br));
  const a = trueAspect(quad, srcW / 2, srcH / 2) ?? wMax / hMax; // w / h
  let W = wMax, H = wMax / a;
  if (H < hMax) { H = hMax; W = hMax * a; }
  const k = Math.min(1, maxSide / Math.max(W, H));
  return [Math.max(16, Math.round(W * k)), Math.max(16, Math.round(H * k))];
}

export function warp(src, quad, maxSide = 2200) {
  const [W, H] = pageSize(quad, src.width, src.height, maxSide);
  const h = homography([[0, 0], [W, 0], [W, H], [0, H]], quad);
  const sw = src.width, sh = src.height;
  const sd = src.getContext('2d', { willReadFrequently: true }).getImageData(0, 0, sw, sh).data;
  const out = mk(W, H);
  const octx = out.getContext('2d');
  const od = octx.createImageData(W, H), o = od.data;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const den = h[6] * x + h[7] * y + 1;
      let u = (h[0] * x + h[1] * y + h[2]) / den - 0.5, v = (h[3] * x + h[4] * y + h[5]) / den - 0.5;
      u = Math.min(sw - 1.001, Math.max(0, u)); v = Math.min(sh - 1.001, Math.max(0, v));
      const x0 = u | 0, y0 = v | 0, fx = u - x0, fy = v - y0;
      const i00 = (y0 * sw + x0) * 4, i10 = i00 + 4, i01 = i00 + sw * 4, i11 = i01 + 4;
      const j = (y * W + x) * 4;
      for (let c = 0; c < 3; c++) {
        o[j + c] = (sd[i00 + c] * (1 - fx) + sd[i10 + c] * fx) * (1 - fy) + (sd[i01 + c] * (1 - fx) + sd[i11 + c] * fx) * fy;
      }
      o[j + 3] = 255;
    }
  }
  octx.putImageData(od, 0, 0);
  return out;
}
