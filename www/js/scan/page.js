// Image blob -> PDF page record, and thumbnails.
import { jpegInfo, ORIENT_ROTATE } from './jpeg.js';
import { loadImage } from './load.js';
import { encodeToBudget } from './pdf.js';
import { pdfTarget } from '../pdfquality.js';

/** -> {jpeg, w, h, comps, rot}. A JPEG that already fits the page budget is embedded as it is (EXIF orientation -> /Rotate);
 *  anything bigger (or another format) is decoded and re-encoded to the budget of the chosen PDF quality. */
export async function pageFromBlob(blob) {
  const { budget, side } = pdfTarget();
  if (blob.size <= budget * 1.5) {
    const bytes = new Uint8Array(await blob.arrayBuffer());
    const info = jpegInfo(bytes);
    if (info && (info.comps === 1 || info.comps === 3)) return { jpeg: bytes, w: info.w, h: info.h, comps: info.comps, rot: ORIENT_ROTATE[info.orient] ?? 0 };
  }
  return encodeToBudget(await loadImage(blob, side), budget);
}

export async function thumbOf(blob) {
  return (await loadImage(blob, 360)).toDataURL('image/jpeg', 0.7);
}
