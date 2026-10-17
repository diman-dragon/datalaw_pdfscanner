// "Auto" clean-up of a photographed page: removes shadows, fold shading, uneven light, paper grain and pale creases
// (flat-field by the estimated paper colour + white point) and sharpens a little. Works in horizontal strips, so even a
// 10 MP page needs little memory. look = {filter: 'color' (original) | 'auto'}.
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);

export const FILTERS = ['color', 'auto'];
export const defaultLook = (filter = 'auto') => ({ filter });
export const lookIsPlain = (l) => l.filter !== 'auto';

const FIX = 0.9;   // strength of the shadow / crease removal
const SHARP = 0.4; // unsharp amount

// ---- paper estimate on a small copy: max filter drops text and thin creases, blur smooths it
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

const bgCache = new WeakMap(); // source canvas -> small paper-colour canvas
function paperOf(src) {
  const hit = bgCache.get(src);
  if (hit) return hit;
  const k = Math.min(1, 160 / Math.max(src.width, src.height));
  const sw = Math.max(4, Math.round(src.width * k)), sh = Math.max(4, Math.round(src.height * k));
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
  bgCache.set(src, sm);
  return sm;
}

/** look: {filter}. Returns src itself for the original. */
export function applyLook(src, look) {
  if (lookIsPlain(look)) return src;
  const w = src.width, h = src.height, out = mk(w, h), ox = out.getContext('2d', { willReadFrequently: true });
  const paper = paperOf(src), strip = Math.max(64, Math.floor(1500000 / w));
  const tc = mk(w, strip), tx = tc.getContext('2d', { willReadFrequently: true });
  const bc = mk(w, strip), bx = bc.getContext('2d', { willReadFrequently: true });
  bx.imageSmoothingQuality = 'high';
  const wp = 255 - 45 * FIX, lut = new Uint8ClampedArray(256); // white point: pale creases / grain -> white
  for (let v = 0; v < 256; v++) lut[v] = (v * 255) / wp;
  const ky = paper.height / h;
  for (let y0 = 0; y0 < h; y0 += strip) {
    const sh = Math.min(strip, h - y0);
    tx.clearRect(0, 0, w, strip); tx.drawImage(src, 0, y0, w, sh, 0, 0, w, sh);
    bx.clearRect(0, 0, w, strip); bx.drawImage(paper, 0, y0 * ky, paper.width, sh * ky, 0, 0, w, sh); // paper colour, up-scaled by the browser
    const id = tx.getImageData(0, 0, w, sh), d = id.data, B = bx.getImageData(0, 0, w, sh).data;
    for (let i = 0, n = w * sh * 4; i < n; i += 4) {
      for (let k = 0; k < 3; k++) {
        const v = d[i + k], flat = Math.min(255, (v * 255) / Math.max(60, B[i + k]));
        d[i + k] = lut[(v + (flat - v) * FIX) | 0];
      }
    }
    ox.putImageData(id, 0, y0);
  }
  // unsharp mask with the browser's blur
  const bl = mk(w, h), blx = bl.getContext('2d', { willReadFrequently: true });
  blx.filter = `blur(${Math.max(0.8, Math.min(w, h) / 800).toFixed(2)}px)`;
  blx.drawImage(out, 0, 0);
  const a = SHARP * 1.3;
  for (let y0 = 0; y0 < h; y0 += strip) {
    const sh = Math.min(strip, h - y0);
    const o = ox.getImageData(0, y0, w, sh), od = o.data, bd = blx.getImageData(0, y0, w, sh).data;
    for (let i = 0, n = w * sh * 4; i < n; i += 4) for (let k = 0; k < 3; k++) od[i + k] = od[i + k] + a * (od[i + k] - bd[i + k]);
    ox.putImageData(o, 0, y0);
  }
  return out;
}
