// The frame of a page (4 corners, TL TR BR BL) normalised to 0..1 of the photo; automatic detection when there is none.
import { detectQuad } from './detect.js';
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
