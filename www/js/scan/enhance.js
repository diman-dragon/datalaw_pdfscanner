// Document enhancement: removes shadows, fold shading, uneven light and paper grain (flat-field + white point),
// brightness / contrast, sharpening, grey and black & white. Used by the result screen, thumbnails and the final render.
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);

// fix = shadows & creases removal 0..100, sharp = sharpening 0..100
export const PRESETS = {
  color: { fix: 0, sharp: 0 },    // original
  auto: { fix: 55, sharp: 25 },   // gentle clean-up
  clean: { fix: 100, sharp: 35 }, // clean sheet: no shadows, creases or grain
  gray: { fix: 85, sharp: 30 },
  bw: { fix: 85, sharp: 0 },
};
export const FILTERS = Object.keys(PRESETS);
export const defaultLook = (filter = 'auto') => ({ filter, bright: 0, contrast: 0, ...PRESETS[filter] });
export const lookIsPlain = (l) => l.filter === 'color' && !l.bright && !l.contrast && !l.fix && !l.sharp;

// ---- paper (background) estimate on a small copy: max filter drops text and thin creases, blur smooths it
function dilate(p, w, h, r) {
  const t = new Float32Array(p.length), o = new Float32Array(p.length);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let m = 0;
    for (let k = -r; k <= r; k++) { const v = p[y * w + clamp(x + k, 0, w - 1)]; if (v > m) m = v; }
    t[y * w + x] = m;
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let m = 0;
    for (let k = -r; k <= r; k++) { const v = t[clamp(y + k, 0, h - 1) * w + x]; if (v > m) m = v; }
    o[y * w + x] = m;
  }
  return o;
}
function blur(p, w, h, r) {
  const t = new Float32Array(p.length), o = new Float32Array(p.length), n = 2 * r + 1;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let s = 0;
    for (let k = -r; k <= r; k++) s += p[y * w + clamp(x + k, 0, w - 1)];
    t[y * w + x] = s / n;
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let s = 0;
    for (let k = -r; k <= r; k++) s += t[clamp(y + k, 0, h - 1) * w + x];
    o[y * w + x] = s / n;
  }
  return o;
}

const bgCache = new WeakMap(); // source canvas -> background pixels (sliders do not recompute it)
function background(src) {
  const hit = bgCache.get(src);
  if (hit) return hit;
  const w = src.width, h = src.height, k = Math.min(1, 160 / Math.max(w, h));
  const sw = Math.max(4, Math.round(w * k)), sh = Math.max(4, Math.round(h * k));
  const sm = mk(sw, sh), sx = sm.getContext('2d', { willReadFrequently: true });
  sx.drawImage(src, 0, 0, sw, sh);
  const id = sx.getImageData(0, 0, sw, sh), d = id.data, n = sw * sh;
  for (let ch = 0; ch < 3; ch++) {
    let p = new Float32Array(n);
    for (let i = 0; i < n; i++) p[i] = d[i * 4 + ch];
    p = blur(blur(dilate(p, sw, sh, 2), sw, sh, 3), sw, sh, 3);
    for (let i = 0; i < n; i++) d[i * 4 + ch] = p[i];
  }
  sx.putImageData(id, 0, 0);
  const full = mk(w, h), fx = full.getContext('2d', { willReadFrequently: true });
  fx.imageSmoothingQuality = 'high';
  fx.drawImage(sm, 0, 0, w, h); // bilinear up-scale by the browser
  const bg = fx.getImageData(0, 0, w, h).data;
  bgCache.set(src, bg);
  return bg;
}

function otsu(hist, total) {
  let sum = 0;
  for (let i = 0; i < 256; i++) sum += i * hist[i];
  let sb = 0, wb = 0, best = 0, thr = 128;
  for (let i = 0; i < 256; i++) {
    wb += hist[i];
    if (!wb) continue;
    const wf = total - wb;
    if (!wf) break;
    sb += i * hist[i];
    const mb = sb / wb, mf = (sum - sb) / wf, v = wb * wf * (mb - mf) * (mb - mf);
    if (v > best) { best = v; thr = i; }
  }
  return thr;
}

/** look: {filter, bright, contrast, fix, sharp}. Returns src itself when nothing is to be done. */
export function applyLook(src, look) {
  if (lookIsPlain(look)) return src;
  const w = src.width, h = src.height, n = w * h, c = mk(w, h), x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(src, 0, 0);
  const id = x.getImageData(0, 0, w, h), d = id.data, f = look.fix / 100;
  if (f > 0) { // flat-field: divide by the paper estimate, blended by strength
    const B = background(src);
    for (let i = 0; i < n * 4; i += 4) {
      for (let k = 0; k < 3; k++) {
        const v = d[i + k], flat = Math.min(255, (v * 255) / Math.max(60, B[i + k]));
        d[i + k] = v + (flat - v) * f;
      }
    }
  }
  // tone: white point (pale creases / grain -> white), contrast, brightness
  const wp = 255 - 45 * f * (look.filter === 'color' ? 0.5 : 1);
  const cf = 1 + look.contrast / 50, bs = look.bright * 2.2, lut = new Uint8ClampedArray(256);
  for (let v = 0; v < 256; v++) lut[v] = ((v * 255) / wp - 128) * cf + 128 + bs;
  const gray = look.filter === 'gray' || look.filter === 'bw', hist = new Uint32Array(256);
  for (let i = 0; i < n * 4; i += 4) {
    const r = lut[d[i]], g = lut[d[i + 1]], b = lut[d[i + 2]];
    if (gray) { const y = (0.299 * r + 0.587 * g + 0.114 * b) | 0; d[i] = d[i + 1] = d[i + 2] = y; hist[y]++; } else { d[i] = r; d[i + 1] = g; d[i + 2] = b; }
  }
  if (look.filter === 'bw') { // soft threshold: clean letters with smooth edges
    const t = clamp(otsu(hist, n) * 0.92 + look.bright * 0.8, 70, 225), lo = t - 16;
    for (let i = 0; i < n * 4; i += 4) { const v = clamp(((d[i] - lo) * 255) / 32, 0, 255); d[i] = d[i + 1] = d[i + 2] = v; }
  }
  x.putImageData(id, 0, 0);
  if (look.sharp > 0 && look.filter !== 'bw') { // unsharp mask with the browser's blur
    const bl = mk(w, h), bx = bl.getContext('2d', { willReadFrequently: true });
    bx.filter = `blur(${Math.max(0.8, Math.min(w, h) / 800).toFixed(2)}px)`;
    bx.drawImage(c, 0, 0);
    const bd = bx.getImageData(0, 0, w, h).data, o = x.getImageData(0, 0, w, h), od = o.data, a = (look.sharp / 100) * 1.3;
    for (let i = 0; i < n * 4; i += 4) for (let k = 0; k < 3; k++) od[i + k] = od[i + k] + a * (od[i + k] - bd[i + k]);
    x.putImageData(o, 0, 0);
  }
  return c;
}
