// Sharpness of a photo (a Blob), measured on the page area only (the part inside the frame), and the verdict.
import { mk } from './canvasx.js';
import { sharpness } from './quality.js';

const LIMIT = 0.75; // the same limit as the "may be blurry" warning of the editor (result.js)
export const isBlurry = (s) => s < LIMIT;

/** blob (photo), crop (normalised [x0, y0, x1, y1] | null) -> 0..1 edge crispness (1 when it cannot be measured: no false alarm). */
export async function blobSharpness(blob, crop = null) {
  try {
    const bmp = await createImageBitmap(blob, { imageOrientation: 'from-image' });
    const [x0, y0, x1, y1] = crop ?? [0, 0, 1, 1];
    const sx = x0 * bmp.width, sy = y0 * bmp.height, sw = (x1 - x0) * bmp.width, sh = (y1 - y0) * bmp.height;
    const k = Math.min(1, 1600 / Math.max(sw, sh)), c = mk(Math.max(16, Math.round(sw * k)), Math.max(16, Math.round(sh * k)));
    c.getContext('2d').drawImage(bmp, sx, sy, sw, sh, 0, 0, c.width, c.height);
    bmp.close?.();
    return sharpness(c);
  } catch (e) { console.error(e); return 1; }
}
