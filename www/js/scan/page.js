// Image blob -> PDF page record, and thumbnails. JPEG originals are embedded as they are (no re-encoding).
import { jpegInfo, ORIENT_ROTATE } from './jpeg.js';
import { loadImage } from './load.js';
import { canvasToJpeg } from './pdf.js';

/** -> {jpeg: Uint8Array, w, h, comps, rot}. Non-JPEG (gallery PNG/WebP/...) is converted once at high quality. */
export async function pageFromBlob(blob) {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  const info = jpegInfo(bytes);
  if (info && (info.comps === 1 || info.comps === 3)) {
    return { jpeg: bytes, w: info.w, h: info.h, comps: info.comps, rot: ORIENT_ROTATE[info.orient] ?? 0 };
  }
  const c = await loadImage(blob, 8000);
  return { jpeg: await canvasToJpeg(c, 0.95), w: c.width, h: c.height, comps: 3, rot: 0 };
}

export async function thumbOf(blob) {
  return (await loadImage(blob, 360)).toDataURL('image/jpeg', 0.7);
}
