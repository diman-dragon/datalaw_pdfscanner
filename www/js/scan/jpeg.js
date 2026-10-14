// JPEG header reader: size, components, EXIF orientation. Lets the original camera file go into the PDF untouched.
function exifOrientation(b, t, end) {
  const le = b[t] === 0x49;
  const u16 = (o) => (le ? b[o] | (b[o + 1] << 8) : (b[o] << 8) | b[o + 1]);
  const u32 = (o) => (le ? (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) >>> 0
    : ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0);
  const ifd = t + u32(t + 4);
  if (ifd + 2 > end) return 1;
  const n = u16(ifd);
  for (let k = 0; k < n; k++) {
    const e = ifd + 2 + k * 12;
    if (e + 12 > end) break;
    if (u16(e) === 0x0112) return u16(e + 8);
  }
  return 1;
}

/** bytes: Uint8Array. Returns {w, h, comps, orient} for baseline/progressive JPEG, else null. */
export function jpegInfo(b) {
  if (b.length < 4 || b[0] !== 0xff || b[1] !== 0xd8) return null;
  let i = 2, orient = 1;
  while (i + 4 < b.length) {
    if (b[i] !== 0xff) { i++; continue; }
    const m = b[i + 1];
    if (m === 0xff) { i++; continue; }
    if (m === 0xd8 || m === 0x01 || (m >= 0xd0 && m <= 0xd7)) { i += 2; continue; }
    const len = (b[i + 2] << 8) | b[i + 3];
    if (m === 0xe1 && len > 14 && b[i + 4] === 0x45 && b[i + 5] === 0x78 && b[i + 6] === 0x69 && b[i + 7] === 0x66) {
      orient = exifOrientation(b, i + 10, i + 2 + len);
    }
    if (m === 0xc0 || m === 0xc1 || m === 0xc2) {
      return { h: (b[i + 5] << 8) | b[i + 6], w: (b[i + 7] << 8) | b[i + 8], comps: b[i + 9], orient };
    }
    if (m === 0xda) return null;
    i += 2 + len;
  }
  return null;
}

// EXIF orientation -> /Rotate (degrees clockwise). Mirrored variants are treated as their rotation.
export const ORIENT_ROTATE = { 1: 0, 2: 0, 3: 180, 4: 180, 5: 90, 6: 90, 7: 270, 8: 270 };
