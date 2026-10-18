// Processing of a shot taken as an ORIGINAL: frame (quad) -> perspective warp -> rotation + filter.
// A page = {blob, qn (frame, normalized 0..1 | null = auto), vr (aspect the frame was made for), look (see enhance.js), turns}.
import { loadImage } from './load.js';
import { detectQuad } from './detect.js';
import { warp } from './warp.js';
import { rotate90 } from './filters.js';
import { applyLook, defaultLook, lookIsPlain } from './enhance.js';
import { canvasToJpeg, encodeToBudget } from './pdf.js';
import { pdfTarget, getPdfSearch } from '../pdfquality.js';
import { layered } from './layers.js';
import { wordsOf } from './textlayer.js';
import { sharpness } from './quality.js';
import { pageFromBlob } from './page.js';

export const MAXSIDE = 2800; // long side of the picture on the editing screens (frame / preview)
export const loadSide = () => Math.min(4200, Math.max(MAXSIDE, pdfTarget().side)); // the original is decoded at the size the PDF quality needs

export const FULL_QUAD = [[0, 0], [1, 0], [1, 1], [0, 1]];
export const isFull = (qn) => qn.every(([x, y], k) => Math.abs(x - FULL_QUAD[k][0]) < 0.003 && Math.abs(y - FULL_QUAD[k][1]) < 0.003);
export const normQuad = (q, img) => q.map(([x, y]) => [x / img.width, y / img.height]);

/** Frame in the pixel coordinates of img. If the camera frame does not fit this photo (other aspect), the edges are found automatically. */
export function quadFor(img, p) {
  const r = img.width / img.height;
  if (!p.qn || Math.abs(r - p.vr) > 0.05) p.qn = normQuad(detectQuad(img), img);
  p.vr = r;
  return p.qn.map(([x, y]) => [x * img.width, y * img.height]);
}

const tick = () => new Promise((r) => setTimeout(r, 0));

/** Stage 1 (heavy, can run in the background): decode the ORIGINAL, straighten it by the frame at the PDF quality size
 *  -> {jpeg, w, h} unfiltered (high-quality intermediate). */
export async function flatOf(p) {
  await tick();
  const { side } = pdfTarget();
  const img = await loadImage(p.blob, loadSide());
  let c = warp(img, quadFor(img, p), side);
  const r = sharpness(c, 99999); // edges at the page's own pixel scale: a soft shot carries less detail than it has pixels
  if (r < 0.85 && Math.max(c.width, c.height) > 1600) { // keep only the resolution the shot really has
    const k = Math.max(0.55, r * 1.15), s = document.createElement('canvas');
    s.width = Math.round(c.width * k); s.height = Math.round(c.height * k);
    s.getContext('2d').drawImage(c, 0, 0, s.width, s.height);
    c = s;
  }
  return { jpeg: await canvasToJpeg(c, 0.92), w: c.width, h: c.height };
}

/** Stage 2: rotation + look, then the PDF packing of the chosen quality -> PDF page record:
 *  Auto on a text page = layers (ink mask + paper), otherwise one JPEG within the page budget; optionally with the searchable text.
 *  If the device runs out of memory on the clean-up, a smaller render is tried, then the intermediate as it is. */
export async function finalOf(p) {
  if (p.keep) return pageFromBlob(p.blob); // untouched page of an existing PDF
  const { budget, side } = pdfTarget();
  const f = await (p.job ??= flatOf(p));
  const look = p.look ?? defaultLook('color'), search = getPdfSearch() === '1';
  const rot = (((p.turns || 0) % 4) + 4) % 4 * 90;
  if (lookIsPlain(look) && !search && f.jpeg.length <= budget) return { jpeg: f.jpeg, w: f.w, h: f.h, comps: 3, rot }; // the rotation is the PDF's job
  for (const s of [side, Math.round(side * 0.6)]) {
    try {
      const c = await loadImage(new Blob([f.jpeg], { type: 'image/jpeg' }), s);
      const out = applyLook(rotate90(c, p.turns || 0), look);
      let page = lookIsPlain(look) ? null : await layered(out, budget);
      page ??= await encodeToBudget(out, budget);
      if (search) { try { const w = await wordsOf(out); if (w?.length) page.words = w; } catch (e) { console.error('text layer', e); } }
      return page;
    } catch (e) { console.error('finalOf', s, e); await tick(); }
  }
  return { jpeg: f.jpeg, w: f.w, h: f.h, comps: 3, rot }; // last resort: the intermediate as it is
}

/** Small preview of the processed page (frame + rotation + filter). */
export async function thumbPage(p) {
  const img = await loadImage(p.blob, 360);
  const c = applyLook(rotate90(warp(img, quadFor(img, p), 360), p.turns || 0), p.look ?? defaultLook('color'));
  return c.toDataURL('image/jpeg', 0.7);
}
