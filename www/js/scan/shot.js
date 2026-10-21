// A page of a scan = {blob (the ORIGINAL photo), qn (frame, normalised 0..1 | null = auto), vr (aspect the frame was made for),
// look (see enhance.js), turns}. The heavy work (decode, straighten, Auto, layers, JPEG) runs in render workers (render.js).
import { loadImage } from './load.js';
import { warp } from './warp.js';
import { rotate90 } from './filters.js';
import { applyLook, defaultLook } from './enhance.js';
import { pageFromBlob } from './page.js';
import { quadFor } from './frame.js';
import { render } from './pool.js';
import { wordsFromJpeg } from './textlayer.js';
import { pdfTarget, getPdfSearch } from '../pdfquality.js';
export { FULL_QUAD, isFull, normQuad, quadFor } from './frame.js';

export const MAXSIDE = 2800; // long side of the picture on the editing screens (frame / preview)
const loadSide = () => Math.round(Math.min(4200, Math.max(1800, pdfTarget().side * 1.3))); // the original is decoded at the size the PDF quality needs

/** Starts making the PDF page in the background (k < 1: a smaller render, after a failure). -> Promise<{page, ocr?}> */
export function startRender(p, k = 1) {
  const { side, budget } = pdfTarget();
  const job = render({
    blob: p.blob, qn: p.qn, vr: p.vr, look: p.look ?? defaultLook('color'), turns: p.turns || 0, mm: p.mm,
    side: Math.round(side * k), budget, loadSide: Math.round(loadSide() * k), search: getPdfSearch() === '1',
  });
  job.catch(() => {}); // the failure is handled where the result is awaited
  return job;
}

/** The PDF page record of a page (waits for the background render, starts it if needed). */
export async function finalOf(p) {
  if (p.keep) return pageFromBlob(p.blob); // untouched page of an existing PDF
  let r;
  try { r = await (p.job ??= startRender(p)); } catch (e) { console.error('render', e); r = await startRender(p, 0.6); } // low memory: a smaller render
  const page = r.page;
  if (r.ocr) { try { const w = await wordsFromJpeg(r.ocr.jpeg); if (w?.length) page.words = w; } catch (e) { console.error('text layer', e); } }
  return page;
}

/** Small preview of the processed page (frame + rotation + look). */
export async function thumbPage(p) {
  const img = await loadImage(p.blob, 360);
  const c = applyLook(rotate90(warp(img, quadFor(img, p), 360), p.turns || 0), p.look ?? defaultLook('color'));
  return c.toDataURL('image/jpeg', 0.7);
}

/** One A4 sheet with the sides (pages) of a card at real size -> PDF page record. */
export async function finalSheet(pages, mm) {
  const { side, budget } = pdfTarget();
  const run = (k) => render({
    sheet: { sides: pages.map((p) => ({ blob: p.blob, qn: p.qn, vr: p.vr, look: p.look ?? defaultLook('color'), turns: p.turns || 0 })), mm },
    side: Math.round(side * k), budget, loadSide: Math.round(loadSide() * k),
  });
  try { return (await run(1)).page; } catch (e) { console.error('sheet', e); return (await run(0.6)).page; } // low memory: smaller
}

/** Quick scan: the photo WITHOUT any processing, pressed into the PDF quality of the Settings (size of the page and its weight). */
export async function finalRaw(blob, crop = null, vr = 1) {
  const { side, budget } = pdfTarget();
  const run = (k) => render({ raw: true, blob, crop, vr, side: Math.round(side * k), budget });
  try { return (await run(1)).page; } catch (e) { console.error('raw', e); return (await run(0.6)).page; }
}
