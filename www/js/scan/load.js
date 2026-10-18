// File -> canvas (EXIF orientation applied), long side capped. Works on the page and in a worker.
import { mk } from './canvasx.js';
export async function loadImage(file, maxSide = 3000) {
  const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const k = Math.min(1, maxSide / Math.max(bmp.width, bmp.height));
  const c = mk(Math.round(bmp.width * k), Math.round(bmp.height * k));
  c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
  bmp.close?.();
  return c;
}
