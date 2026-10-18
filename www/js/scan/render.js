// The whole heavy part of making a PDF page from the ORIGINAL photo, in one pass, safe for a Web Worker:
// decode -> straighten by the frame -> (Auto) clean-up -> layers or one JPEG within the page budget.
import { loadImage } from './load.js';
import { quadFor } from './frame.js';
import { warp } from './warp.js';
import { rotate90 } from './filters.js';
import { applyLook, lookIsPlain } from './enhance.js';
import { layered } from './layers.js';
import { encodeToBudget } from './pdf.js';
import { sharpness } from './quality.js';
import { mk, toBlob } from './canvasx.js';

/** job: {blob, qn, vr, look, turns, side, budget, loadSide, search} -> {page, ocr?: {jpeg}} */
export async function renderPage(job) {
  const { blob, look, turns, side, budget, loadSide, search } = job;
  const img = await loadImage(blob, loadSide);
  const quad = quadFor(img, job);
  let c = warp(img, quad, side);
  const r = sharpness(c, 99999); // edges at the page's own pixel scale: a soft shot carries less detail than it has pixels
  if (r < 0.85 && Math.max(c.width, c.height) > 1600) {
    const k = Math.max(0.55, r * 1.15), s = mk(Math.round(c.width * k), Math.round(c.height * k));
    s.getContext('2d').drawImage(c, 0, 0, s.width, s.height);
    c = s;
  }
  const out = applyLook(rotate90(c, turns || 0), look);
  let page = lookIsPlain(look) ? null : await layered(out, budget);
  page ??= await encodeToBudget(out, budget);
  let ocr = null;
  if (search) { // the recognizer works on a bigger picture: letters of a 150 dpi page are small
    const k = Math.min(2, Math.max(1, 2480 / Math.max(out.width, out.height))), o = mk(Math.round(out.width * k), Math.round(out.height * k));
    o.getContext('2d').drawImage(out, 0, 0, o.width, o.height);
    ocr = { jpeg: new Uint8Array(await (await toBlob(o, 0.85)).arrayBuffer()) };
  }
  return { page, ocr };
}
