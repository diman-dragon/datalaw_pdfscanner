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
  if (job.sheet) return renderSheet(job);
  if (job.raw) return { page: await encodeToBudget(await loadImage(job.blob, job.side), job.budget), ocr: null }; // quick scan: the photo as it is, only pressed into the PDF quality
  const { blob, look, turns, side, budget, loadSide, search, mm } = job;
  const img = await loadImage(blob, loadSide);
  const quad = quadFor(img, job);
  let size = null; // a document of a known physical size is straightened to exactly its proportion
  if (mm) { const a = (turns || 0) % 2 ? mm[1] / mm[0] : mm[0] / mm[1]; size = a >= 1 ? [side, side / a] : [side * a, side]; }
  let c = warp(img, quad, side, size);
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

/** An A4 sheet with the sides of a card at REAL size, one under another, centred. job.sheet = {sides: [{blob, qn, vr, look, turns}], mm: [w, h]}. */
async function renderSheet(job) {
  const { sides, mm } = job.sheet, { side, budget, loadSide } = job;
  const long = Math.max(side, 2339), W = Math.round(long * 210 / 297), H = long, ppm = W / 210; // at least 200 dpi: the small print of a card
  const sheet = mk(W, H), x = sheet.getContext('2d');
  x.fillStyle = '#fff'; x.fillRect(0, 0, W, H);
  const cw = Math.round(mm[0] * ppm), ch = Math.round(mm[1] * ppm), gap = 12 * ppm, total = sides.length * ch + (sides.length - 1) * gap, y0 = Math.max(20 * ppm, (H - total) / 3);
  for (let i = 0; i < sides.length; i++) {
    const sd = sides[i], img = await loadImage(sd.blob, loadSide), quad = quadFor(img, sd);
    const odd = (sd.turns || 0) % 2 !== 0;
    let card = warp(img, quad, 99999, odd ? [ch, cw] : [cw, ch]);
    card = applyLook(rotate90(card, sd.turns || 0), sd.look);
    const px = (W - cw) / 2, py = y0 + i * (ch + gap);
    x.drawImage(card, px, py, cw, ch);
    x.strokeStyle = '#9a9a9a'; x.lineWidth = Math.max(1, ppm * 0.25); x.strokeRect(px, py, cw, ch);
  }
  return { page: await encodeToBudget(sheet, budget * 2), ocr: null };
}
