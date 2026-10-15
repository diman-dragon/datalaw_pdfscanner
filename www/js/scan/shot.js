// Processing of a shot taken as an ORIGINAL: frame (quad) -> perspective warp -> rotation + filter.
// A page = {blob, qn (frame, normalized 0..1 | null = auto), vr (aspect the frame was made for), filter, turns}.
import { loadImage } from './load.js';
import { detectQuad } from './detect.js';
import { warp } from './warp.js';
import { applyFilter, rotate90 } from './filters.js';
import { canvasToJpeg } from './pdf.js';
import { pageFromBlob } from './page.js';

export const MAXSIDE = 4200; // long side cap of the working image (a 12 MP photo is used in full)
export const FILTERS = ['color', 'contrast', 'gray', 'bw'];

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

/** Stage 1 (heavy, can run in the background): decode the original, warp by the frame -> {jpeg, w, h} unfiltered. */
export async function flatOf(p) {
  await tick();
  const img = await loadImage(p.blob, MAXSIDE);
  const c = warp(img, quadFor(img, p), MAXSIDE);
  return { jpeg: await canvasToJpeg(c, 0.95), w: c.width, h: c.height };
}

/** Stage 2: rotation + filter -> PDF page record. p.job = promise of stage 1 (made if missing). */
export async function finalOf(p) {
  if (p.keep) return pageFromBlob(p.blob); // untouched page of an existing PDF: as it is
  const f = await (p.job ??= flatOf(p));
  if (!p.turns && (p.filter ?? 'color') === 'color') return { jpeg: f.jpeg, w: f.w, h: f.h, comps: 3, rot: 0 };
  const c = await loadImage(new Blob([f.jpeg], { type: 'image/jpeg' }), MAXSIDE);
  const out = applyFilter(rotate90(c, p.turns || 0), p.filter ?? 'color');
  return { jpeg: await canvasToJpeg(out, 0.93), w: out.width, h: out.height, comps: 3, rot: 0 };
}

/** Small preview of the processed page (frame + rotation + filter). */
export async function thumbPage(p) {
  const img = await loadImage(p.blob, 360);
  const c = applyFilter(rotate90(warp(img, quadFor(img, p), 360), p.turns || 0), p.filter ?? 'color');
  return c.toDataURL('image/jpeg', 0.7);
}
