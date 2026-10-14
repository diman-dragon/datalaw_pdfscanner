// Rotation + document filters (own engine).
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };

export function rotate90(src, turns) {
  const t = ((turns % 4) + 4) % 4;
  if (!t) return src;
  const sw = src.width, sh = src.height, swap = t % 2 === 1;
  const c = mk(swap ? sh : sw, swap ? sw : sh), x = c.getContext('2d');
  x.translate(c.width / 2, c.height / 2);
  x.rotate((t * Math.PI) / 2);
  x.drawImage(src, -sw / 2, -sh / 2);
  return c;
}

export function scaled(src, maxSide) {
  const k = Math.min(1, maxSide / Math.max(src.width, src.height));
  if (k === 1) return src;
  const c = mk(Math.round(src.width * k), Math.round(src.height * k));
  c.getContext('2d').drawImage(src, 0, 0, c.width, c.height);
  return c;
}

/** mode: color (unchanged) | contrast (per-channel auto levels) | gray (grey + auto levels) | bw (adaptive black & white) */
export function applyFilter(src, mode) {
  if (mode === 'color') return src;
  const w = src.width, h = src.height, c = mk(w, h), x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(src, 0, 0);
  const id = x.getImageData(0, 0, w, h), d = id.data, n = w * h;
  if (mode === 'contrast') {
    for (let ch = 0; ch < 3; ch++) {
      const hist = new Uint32Array(256);
      for (let i = 0; i < n; i++) hist[d[i * 4 + ch]]++;
      let acc = 0, lo = 0, hi = 255;
      for (let v = 0; v < 256; v++) { acc += hist[v]; if (acc >= n * 0.01) { lo = v; break; } }
      acc = 0;
      for (let v = 255; v >= 0; v--) { acc += hist[v]; if (acc >= n * 0.01) { hi = v; break; } }
      const span = Math.max(1, hi - lo);
      for (let i = 0; i < n; i++) d[i * 4 + ch] = ((d[i * 4 + ch] - lo) * 255) / span;
    }
    x.putImageData(id, 0, 0);
    return c;
  }
  const g = new Float32Array(n);
  for (let i = 0; i < n; i++) g[i] = 0.299 * d[i * 4] + 0.587 * d[i * 4 + 1] + 0.114 * d[i * 4 + 2];
  const out = new Uint8ClampedArray(n);
  if (mode === 'gray') {
    const hist = new Uint32Array(256);
    for (let i = 0; i < n; i++) hist[g[i] | 0]++;
    let acc = 0, lo = 0, hi = 255;
    for (let v = 0; v < 256; v++) { acc += hist[v]; if (acc >= n * 0.01) { lo = v; break; } }
    acc = 0;
    for (let v = 255; v >= 0; v--) { acc += hist[v]; if (acc >= n * 0.01) { hi = v; break; } }
    const span = Math.max(1, hi - lo);
    for (let i = 0; i < n; i++) out[i] = ((g[i] - lo) * 255) / span;
  } else {
    // local mean via integral image; pixel is ink if clearly darker than its surroundings
    const W1 = w + 1, I = new Float64Array(W1 * (h + 1));
    for (let y = 0; y < h; y++) {
      let row = 0;
      for (let xx = 0; xx < w; xx++) { row += g[y * w + xx]; I[(y + 1) * W1 + xx + 1] = I[y * W1 + xx + 1] + row; }
    }
    const r = Math.max(8, Math.round(Math.min(w, h) / 24));
    for (let y = 0; y < h; y++) {
      const y0 = Math.max(0, y - r), y1 = Math.min(h, y + r + 1);
      for (let xx = 0; xx < w; xx++) {
        const x0 = Math.max(0, xx - r), x1 = Math.min(w, xx + r + 1);
        const mean = (I[y1 * W1 + x1] - I[y0 * W1 + x1] - I[y1 * W1 + x0] + I[y0 * W1 + x0]) / ((x1 - x0) * (y1 - y0));
        out[y * w + xx] = g[y * w + xx] > mean * 0.9 ? 255 : 0;
      }
    }
  }
  for (let i = 0; i < n; i++) { d[i * 4] = d[i * 4 + 1] = d[i * 4 + 2] = out[i]; d[i * 4 + 3] = 255; }
  x.putImageData(id, 0, 0);
  return c;
}
