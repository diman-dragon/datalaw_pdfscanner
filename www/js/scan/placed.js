// A4 sheet with one item (a page, a card with one or two sides...) at its REAL size. Safe for a Web Worker.
// job.placed = {sides: [{blob, crop, vr}], mm: [w, h] (real size of one side), rotate: bool (a landscape item wider than the sheet is turned)}
import { loadImage } from './load.js';
import { rotate90 } from './filters.js';
import { encodeToBudget } from './pdf.js';
import { mk } from './canvasx.js';

const SW = 210, SH = 297, GAP = 12; // the sheet and the gap between the sides, mm

/** The part of the photo that is the item: the camera frame if the photo matches the preview, else the centre with the item's proportion. */
function region(img, { crop, vr }, aspect) {
  if (crop && Math.abs(img.width / img.height - vr) < 0.04 * vr) {
    const [x0, y0, x1, y1] = crop;
    return [x0 * img.width, y0 * img.height, (x1 - x0) * img.width, (y1 - y0) * img.height];
  }
  const w = Math.min(img.width, img.height * aspect), h = w / aspect;
  return [(img.width - w) / 2, (img.height - h) / 2, w, h];
}

export async function renderPlaced(job) {
  const { sides, mm, rotate } = job.placed, { side, budget } = job;
  const ppm = Math.max(side, 2339) / SH, W = Math.round(SW * ppm), H = Math.round(SH * ppm); // at least 200 dpi: the small print of a card
  const [iw, ih] = rotate ? [mm[1], mm[0]] : mm; // size on the sheet
  const full = iw >= SW - 0.5 && ih >= SH - 0.5;
  const sheet = mk(W, H), x = sheet.getContext('2d');
  x.fillStyle = '#fff'; x.fillRect(0, 0, W, H);
  const total = sides.length * ih + (sides.length - 1) * GAP, y0 = full ? 0 : Math.max(15, (SH - total) / 3);
  for (let i = 0; i < sides.length; i++) {
    const s = sides[i], dw = Math.round(mm[0] * ppm), dh = Math.round(mm[1] * ppm);
    const c0 = s.crop ? Math.max(s.crop[2] - s.crop[0], s.crop[3] - s.crop[1]) : 1;
    const img = await loadImage(s.blob, Math.min(5200, Math.round(Math.max(dw, dh) / c0)));
    const [sx, sy, sw, sh] = region(img, s, mm[0] / mm[1]);
    let item = mk(dw, dh);
    item.getContext('2d').drawImage(img, sx, sy, sw, sh, 0, 0, dw, dh);
    if (rotate) item = rotate90(item, 1);
    const px = (SW - iw) / 2 * ppm, py = (y0 + i * (ih + GAP)) * ppm;
    x.drawImage(item, px, py, iw * ppm, ih * ppm);
    if (!full) { x.strokeStyle = '#9a9a9a'; x.lineWidth = Math.max(1, ppm * 0.25); x.strokeRect(px, py, iw * ppm, ih * ppm); } // the outline helps to cut it out
  }
  return { page: await encodeToBudget(sheet, full ? budget : budget * 2), ocr: null };
}
