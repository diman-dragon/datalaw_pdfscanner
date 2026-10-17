// Quick quality checks used for warnings: how crooked the sheet was photographed, and how sharp the picture is.

/** 0 = the sheet is parallel to the lens; 0.1 = opposite sides differ by 10% (the phone was tilted). quad: TL, TR, BR, BL. */
export function skewOf(quad) {
  const d = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
  const [tl, tr, br, bl] = quad, top = d(tl, tr), bot = d(bl, br), l = d(tl, bl), r = d(tr, br);
  return Math.max(Math.abs(top - bot) / Math.max(top, bot, 1), Math.abs(l - r) / Math.max(l, r, 1));
}

/**
 * Edge crispness 0..1 that does not depend on how much text the page has: for the contrasty edges, how steep they are
 * relative to their own contrast (a step edge = 1; the wider the edge is smeared, the lower). Measured on a ~1280 px grey copy.
 */
export function sharpness(canvas) {
  const k = Math.min(1, 1280 / Math.max(canvas.width, canvas.height));
  const w = Math.max(16, Math.round(canvas.width * k)), h = Math.max(16, Math.round(canvas.height * k));
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(canvas, 0, 0, w, h);
  const d = x.getImageData(0, 0, w, h).data, g = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) g[i] = d[i * 4] * 0.299 + d[i * 4 + 1] * 0.587 + d[i * 4 + 2] * 0.114;
  const ratios = [], R = 8;
  for (let yy = R; yy < h - R; yy += 2) for (let xx = R; xx < w - R; xx += 2) {
    const i = yy * w + xx, gx = Math.abs(g[i + 1] - g[i - 1]), gy = Math.abs(g[i + w] - g[i - w]), gm = Math.max(gx, gy);
    if (gm < 5) continue; // only real edges
    let lo = 255, hi = 0;
    for (let q = -R; q <= R; q++) { const v = gx >= gy ? g[i + q] : g[i + q * w]; if (v < lo) lo = v; if (v > hi) hi = v; }
    const contrast = hi - lo;
    if (contrast >= 25) ratios.push(Math.min(1, (gm * 2) / contrast));
  }
  if (ratios.length < 40) return 1; // not enough edges to judge: no warning
  ratios.sort((a, b) => a - b);
  return ratios[Math.floor(ratios.length * 0.6)]; // upper-middle: the sharpest edges of the page represent it
}
