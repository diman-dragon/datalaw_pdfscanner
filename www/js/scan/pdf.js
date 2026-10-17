// Streaming PDF writer: one JPEG per page (DCTDecode), page width = A4 (595.28 pt). Page = {jpeg,w,h,comps?,rot?}.
// Yields Uint8Array parts, so only one page is in memory at a time. /Rotate carries the EXIF orientation of originals.
const enc = new TextEncoder();

/** getPage(i) -> page (may be async). */
export async function* pdfStream(n, getPage) {
  let len = 0;
  const offs = [];
  const out = (x) => { const u = typeof x === 'string' ? enc.encode(x) : x; len += u.length; return u; };
  yield out('%PDF-1.4\n');
  offs[1] = len; yield out('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n');
  const kids = Array.from({ length: n }, (_, i) => `${3 + i * 3} 0 R`).join(' ');
  offs[2] = len; yield out(`2 0 obj\n<< /Type /Pages /Count ${n} /Kids [${kids}] >>\nendobj\n`);
  for (let i = 0; i < n; i++) {
    const p = await getPage(i);
    const rot = p.rot || 0, swap = rot % 180 !== 0;
    const dw = swap ? p.h : p.w, dh = swap ? p.w : p.h; // size as displayed
    const pw = 595.28, ph = (pw * dh) / dw;
    const mw = swap ? ph : pw, mh = swap ? pw : ph; // media box = unrotated image box
    const base = 3 + i * 3;
    offs[base] = len;
    yield out(`${base} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${mw.toFixed(2)} ${mh.toFixed(2)}] /Rotate ${rot} /Resources << /XObject << /Im0 ${base + 1} 0 R >> >> /Contents ${base + 2} 0 R >>\nendobj\n`);
    offs[base + 1] = len;
    yield out(`${base + 1} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} /ColorSpace /${p.comps === 1 ? 'DeviceGray' : 'DeviceRGB'} /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.length} >>\nstream\n`);
    yield out(p.jpeg);
    yield out('\nendstream\nendobj\n');
    const content = `q ${mw.toFixed(2)} 0 0 ${mh.toFixed(2)} 0 0 cm /Im0 Do Q`;
    offs[base + 2] = len;
    yield out(`${base + 2} 0 obj\n<< /Length ${content.length} >>\nstream\n${content}\nendstream\nendobj\n`);
  }
  const total = 3 + n * 3, xref = len;
  let x = `xref\n0 ${total}\n0000000000 65535 f \n`;
  for (let i = 1; i < total; i++) x += `${String(offs[i]).padStart(10, '0')} 00000 n \n`;
  yield out(`${x}trailer\n<< /Size ${total} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

const blobOf = (c, q) => new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', q));
const pack = async (b, c) => ({ jpeg: new Uint8Array(await b.arrayBuffer()), w: c.width, h: c.height, comps: 3, rot: 0 });

/** The best JPEG of at most `max` bytes: quality is searched; if even the lowest does not fit, the picture is scaled down. */
export async function encodeToBudget(canvas, max) {
  let c = canvas;
  for (let round = 0; round < 4; round++) {
    const first = await blobOf(c, 0.82);
    if (first.size <= max) return pack(first, c);
    let lo = 0.3, hi = 0.82, best = null;
    for (let i = 0; i < 4; i++) {
      const q = (lo + hi) / 2, b = await blobOf(c, q);
      if (b.size <= max) { best = b; lo = q; } else hi = q;
    }
    if (best) return pack(best, c);
    const n = document.createElement('canvas');
    n.width = Math.round(c.width * 0.85); n.height = Math.round(c.height * 0.85);
    n.getContext('2d').drawImage(c, 0, 0, n.width, n.height);
    c = n;
  }
  return pack(await blobOf(c, 0.3), c);
}

export const canvasToJpeg = (canvas, q = 0.92) => new Promise((res, rej) => canvas.toBlob(
  (b) => (b ? b.arrayBuffer().then((a) => res(new Uint8Array(a))) : rej(new Error('jpeg'))), 'image/jpeg', q));

export const bytesToBase64 = (bytes) => new Promise((res, rej) => {
  const r = new FileReader();
  r.onload = () => res(String(r.result).split(',')[1]);
  r.onerror = () => rej(r.error);
  r.readAsDataURL(new Blob([bytes]));
});
