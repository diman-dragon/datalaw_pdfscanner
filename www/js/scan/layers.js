// Layered ("MRC") page: ink as a 1-bit mask in high resolution + colour of the ink + the paper as low-resolution layers.
// A text page then weighs about a quarter of a single JPEG at the same sharpness of the letters. Returns null when the page
// is not a text document (photos, big coloured areas) or the result would not be smaller - the caller then makes a plain JPEG.
const mk = (w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; };
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const blobOf = (c, q) => new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', q));
const bytesOf = async (b) => new Uint8Array(await b.arrayBuffer());

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

export async function layered(canvas, budget) {
  if (typeof CompressionStream === 'undefined') return null;
  const w = canvas.width, h = canvas.height, n = w * h;
  const d = canvas.getContext('2d', { willReadFrequently: true }).getImageData(0, 0, w, h).data;
  const G = new Uint8Array(n), hist = new Uint32Array(256);
  for (let i = 0; i < n; i++) { const g = (d[i * 4] * 77 + d[i * 4 + 1] * 150 + d[i * 4 + 2] * 29) >> 8; G[i] = g; hist[g]++; }
  const thr = clamp(otsu(hist, n), 110, 190);

  // block statistics: colour of ink and of paper per block
  const bs = Math.max(2, Math.round(Math.max(w, h) / 700)), bw = Math.ceil(w / bs), bh = Math.ceil(h / bs), nb = bw * bh;
  const fgS = new Float32Array(nb * 3), bgS = new Float32Array(nb * 3), fgN = new Uint32Array(nb), bgN = new Uint32Array(nb);
  let ink = 0;
  for (let y = 0; y < h; y++) {
    const row = (y / bs | 0) * bw;
    for (let x = 0; x < w; x++) {
      const i = y * w + x, bi = row + (x / bs | 0), j = i * 4;
      if (G[i] < thr) { ink++; fgN[bi]++; fgS[bi * 3] += d[j]; fgS[bi * 3 + 1] += d[j + 1]; fgS[bi * 3 + 2] += d[j + 2]; } else { bgN[bi]++; bgS[bi * 3] += d[j]; bgS[bi * 3 + 1] += d[j + 1]; bgS[bi * 3 + 2] += d[j + 2]; }
    }
  }
  const inkFrac = ink / n;
  if (inkFrac < 0.002 || inkFrac > 0.3) return null; // blank or not a text page
  let blocks = 0, nonPaper = 0;
  for (let b = 0; b < nb; b++) {
    if (!bgN[b]) continue;
    const r = bgS[b * 3] / bgN[b], g = bgS[b * 3 + 1] / bgN[b], bl = bgS[b * 3 + 2] / bgN[b];
    blocks++;
    if (r * 0.3 + g * 0.59 + bl * 0.11 < 190 || Math.max(r, g, bl) - Math.min(r, g, bl) > 40) nonPaper++; // photo / coloured area
  }
  if (blocks && nonPaper / blocks > 0.08) return null;

  // low-resolution layers
  const bgc = mk(bw, bh), bgx = bgc.getContext('2d'), bgd = bgx.createImageData(bw, bh);
  const fgc = mk(bw, bh), fgx = fgc.getContext('2d'), fgd = fgx.createImageData(bw, bh);
  for (let b = 0; b < nb; b++) {
    for (let k = 0; k < 3; k++) {
      bgd.data[b * 4 + k] = bgN[b] ? bgS[b * 3 + k] / bgN[b] : fgN[b] ? fgS[b * 3 + k] / fgN[b] : 255;
      fgd.data[b * 4 + k] = fgN[b] ? fgS[b * 3 + k] / fgN[b] : 0;
    }
    bgd.data[b * 4 + 3] = fgd.data[b * 4 + 3] = 255;
  }
  bgx.putImageData(bgd, 0, 0); fgx.putImageData(fgd, 0, 0);

  // the mask: up to 2x the page resolution, bilinear sampling = smooth letter edges
  const km = Math.min(2, 5000 / Math.max(w, h)), mw = Math.round(w * km), mh = Math.round(h * km), rb = (mw + 7) >> 3, bits = new Uint8Array(rb * mh);
  const x0 = new Int32Array(mw), x1 = new Int32Array(mw), fx = new Float32Array(mw);
  for (let mx = 0; mx < mw; mx++) { const sx = clamp((mx + 0.5) / km - 0.5, 0, w - 1), a = Math.floor(sx); x0[mx] = a; x1[mx] = Math.min(w - 1, a + 1); fx[mx] = sx - a; }
  for (let my = 0; my < mh; my++) {
    const sy = clamp((my + 0.5) / km - 0.5, 0, h - 1), y0 = Math.floor(sy), y1 = Math.min(h - 1, y0 + 1), fy = sy - y0, r0 = y0 * w, r1 = y1 * w, ro = my * rb;
    for (let mx = 0; mx < mw; mx++) {
      const a = x0[mx], b = x1[mx], f = fx[mx];
      const v = (G[r0 + a] * (1 - f) + G[r0 + b] * f) * (1 - fy) + (G[r1 + a] * (1 - f) + G[r1 + b] * f) * fy;
      if (v < thr) bits[ro + (mx >> 3)] |= 0x80 >> (mx & 7);
    }
  }
  const z = new Uint8Array(await new Response(new Blob([bits]).stream().pipeThrough(new CompressionStream('deflate'))).arrayBuffer());
  const bgJ = await bytesOf(await blobOf(bgc, 0.5)), fgJ = await bytesOf(await blobOf(fgc, 0.6));
  if (bgJ.length + fgJ.length + z.length > budget) return null;
  return { layers: { bg: { jpeg: bgJ, w: bw, h: bh }, fg: { jpeg: fgJ, w: bw, h: bh }, mask: { data: z, w: mw, h: mh } }, w, h, comps: 3, rot: 0 };
}
