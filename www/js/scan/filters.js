import { mk } from './canvasx.js';
// Rotation and scaling helpers (the filters themselves are in enhance.js).

export function rotate90(src, turns) {
  const t = ((turns % 4) + 4) % 4;
  if (!t) return src;
  const sw = src.width, sh = src.height, swap = t % 2 === 1;
  const c = mk(swap ? sh : sw, swap ? sw : sh), x = c.getContext('2d');
  x.translate(c.width / 2, c.height / 2);
  x.rotate((t * Math.PI) / 2);
  x.drawImage(src, -sw / 2, -sh / 2);
  return c;
}

export function scaled(src, maxSide) {
  const k = Math.min(1, maxSide / Math.max(src.width, src.height));
  if (k === 1) return src;
  const c = mk(Math.round(src.width * k), Math.round(src.height * k));
  c.getContext('2d').drawImage(src, 0, 0, c.width, c.height);
  return c;
}
