// Streaming PDF writer, page width = A4 (595.28 pt). Page = plain {jpeg, w, h, comps?, rot?}
// or layered {layers: {bg, fg, mask}, w, h}; optional words [{t, x0, y0, x1, y1}] (0..1 of the page) = invisible searchable text.
// Yields Uint8Array parts, so only one page is in memory at a time. /Rotate carries the EXIF orientation of originals.
import { mk, toBlob } from './canvasx.js';
const enc = new TextEncoder();
const hex2 = (n) => n.toString(16).padStart(2, '0');
const utf16hex = (ch) => {
  const c = ch.codePointAt(0);
  if (c < 0x10000) return c.toString(16).padStart(4, '0');
  const v = c - 0x10000;
  return (0xd800 + (v >> 10)).toString(16) + (0xdc00 + (v & 0x3ff)).toString(16);
};

/** getPage(i) -> page (may be async). */
export async function* pdfStream(n, getPage) {
  let len = 0, next = 3 + n + 3; // objects: 1 catalog, 2 pages, 3.. pages, then 3 for the text font, then the rest as they come
  const offs = [], FONT = 3 + n, TOUNI = FONT + 1, GLYPH = FONT + 2;
  const codes = new Map(), chars = [];
  const codeOf = (ch) => {
    let c = codes.get(ch);
    if (c === undefined && codes.size < 255) { c = codes.size + 1; codes.set(ch, c); chars.push(ch); }
    return c;
  };
  const out = (x) => { const u = typeof x === 'string' ? enc.encode(x) : x; len += u.length; return u; };
  function* obj(num, dict, bytes) {
    offs[num] = len;
    yield out(`${num} 0 obj\n<< ${dict} /Length ${bytes.length} >>\nstream\n`);
    yield out(bytes);
    yield out('\nendstream\nendobj\n');
  }
  yield out('%PDF-1.4\n');
  offs[1] = len; yield out('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n');
  offs[2] = len; yield out(`2 0 obj\n<< /Type /Pages /Count ${n} /Kids [${Array.from({ length: n }, (_, i) => `${3 + i} 0 R`).join(' ')}] >>\nendobj\n`);
  for (let i = 0; i < n; i++) {
    const p = await getPage(i), L = p.layers;
    const rot = L ? 0 : (p.rot || 0), swap = rot % 180 !== 0;
    const dw = swap ? p.h : p.w, dh = swap ? p.w : p.h; // size as displayed
    const pw = 595.28, ph = (pw * dh) / dw;
    const mw = swap ? ph : pw, mh = swap ? pw : ph; // media box = unrotated image box
    const cont = next++, box = `${mw.toFixed(2)} 0 0 ${mh.toFixed(2)} 0 0 cm`;
    let xobj, draw;
    if (L) {
      const bg = next++, fg = next++, mk = next++;
      yield* obj(bg, `/Type /XObject /Subtype /Image /Width ${L.bg.w} /Height ${L.bg.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode`, L.bg.jpeg);
      yield* obj(mk, `/Type /XObject /Subtype /Image /Width ${L.mask.w} /Height ${L.mask.h} /ImageMask true /BitsPerComponent 1 /Decode [1 0] /Filter /FlateDecode`, L.mask.data);
      yield* obj(fg, `/Type /XObject /Subtype /Image /Width ${L.fg.w} /Height ${L.fg.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Mask ${mk} 0 R`, L.fg.jpeg);
      xobj = `/Bg ${bg} 0 R /Fg ${fg} 0 R`;
      draw = `q ${box} /Bg Do Q q ${box} /Fg Do Q`; // paper, then the ink colour through the mask
    } else {
      const im = next++;
      yield* obj(im, `/Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} /ColorSpace /${p.comps === 1 ? 'DeviceGray' : 'DeviceRGB'} /BitsPerComponent 8 /Filter /DCTDecode`, p.jpeg);
      xobj = `/Im0 ${im} 0 R`;
      draw = `q ${box} /Im0 Do Q`;
    }
    const parts = [];
    if (p.words?.length && !rot) { // invisible text (render mode 3) fitted to the recognised word boxes
      for (const wd of p.words) {
        const bytes = [];
        for (const ch of wd.t) { const c = codeOf(ch); if (c) bytes.push(c); }
        if (!bytes.length) continue;
        const hPt = (wd.y1 - wd.y0) * mh, wPt = (wd.x1 - wd.x0) * mw, fs = Math.max(2, hPt * 0.85), tz = (wPt / (bytes.length * fs)) * 100;
        parts.push(`/F1 ${fs.toFixed(2)} Tf ${tz.toFixed(1)} Tz 1 0 0 1 ${(wd.x0 * mw).toFixed(2)} ${(mh - wd.y1 * mh + hPt * 0.2).toFixed(2)} Tm <${bytes.map(hex2).join('')}> Tj`);
      }
    }
    const content = parts.length ? `${draw}\nBT 3 Tr\n${parts.join('\n')}\nET` : draw;
    offs[3 + i] = len;
    yield out(`${3 + i} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${mw.toFixed(2)} ${mh.toFixed(2)}] /Rotate ${rot} /Resources << /XObject << ${xobj} >>${parts.length ? ` /Font << /F1 ${FONT} 0 R >>` : ''} >> /Contents ${cont} 0 R >>\nendobj\n`);
    yield* obj(cont, '', enc.encode(content));
  }
  if (codes.size) { // the text font: nothing to draw, only the code -> Unicode map, so viewers can search and copy the text
    const N = chars.length;
    yield* obj(GLYPH, '', enc.encode('1000 0 d0'));
    offs[FONT] = len;
    yield out(`${FONT} 0 obj\n<< /Type /Font /Subtype /Type3 /FontBBox [0 0 1000 1000] /FontMatrix [0.001 0 0 0.001 0 0] /CharProcs << ${chars.map((_, k) => `/g${k + 1} ${GLYPH} 0 R`).join(' ')} >> /Encoding << /Type /Encoding /Differences [1 ${chars.map((_, k) => `/g${k + 1}`).join(' ')}] >> /FirstChar 1 /LastChar ${N} /Widths [${chars.map(() => 1000).join(' ')}] /Resources << >> /ToUnicode ${TOUNI} 0 R >>\nendobj\n`);
    let map = '';
    for (let s = 0; s < N; s += 100) {
      const part = chars.slice(s, s + 100);
      map += `${part.length} beginbfchar\n${part.map((ch, k) => `<${hex2(s + k + 1)}> <${utf16hex(ch)}>`).join('\n')}\nendbfchar\n`;
    }
    yield* obj(TOUNI, '', enc.encode(`/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n1 begincodespacerange\n<00> <FF>\nendcodespacerange\n${map}endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend`));
  }
  const total = next, xref = len;
  let x = `xref\n0 ${total}\n0000000000 65535 f \n`;
  for (let i = 1; i < total; i++) x += offs[i] === undefined ? '0000000000 65535 f \n' : `${String(offs[i]).padStart(10, '0')} 00000 n \n`;
  yield out(`${x}trailer\n<< /Size ${total} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

// ---- JPEG helpers
const blobOf = (c, q) => toBlob(c, q);
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
    const nc = mk(Math.round(c.width * 0.85), Math.round(c.height * 0.85));
    nc.getContext('2d').drawImage(c, 0, 0, nc.width, nc.height);
    c = nc;
  }
  return pack(await blobOf(c, 0.3), c);
}

export const canvasToJpeg = async (canvas, q = 0.92) => new Uint8Array(await (await toBlob(canvas, q)).arrayBuffer());

export const bytesToBase64 = (bytes) => new Promise((res, rej) => {
  const r = new FileReader();
  r.onload = () => res(String(r.result).split(',')[1]);
  r.onerror = () => rej(r.error);
  r.readAsDataURL(new Blob([bytes]));
});
