// Minimal PDF writer: one JPEG per page (DCTDecode), page width = A4 (595.28 pt).
const enc = new TextEncoder();

export function jpegsToPdf(pages) { // pages: [{jpeg:Uint8Array,w,h}]
  const parts = [];
  let len = 0;
  const offs = [];
  const put = (x) => { const u = typeof x === 'string' ? enc.encode(x) : x; parts.push(u); len += u.length; };
  const obj = (n, body) => { offs[n] = len; put(`${n} 0 obj\n`); put(body); put('\nendobj\n'); };
  const n = pages.length;
  put('%PDF-1.4\n');
  obj(1, '<< /Type /Catalog /Pages 2 0 R >>');
  obj(2, `<< /Type /Pages /Count ${n} /Kids [${pages.map((_, i) => `${3 + i * 3} 0 R`).join(' ')}] >>`);
  pages.forEach((p, i) => {
    const pw = 595.28, ph = (pw * p.h) / p.w, base = 3 + i * 3;
    obj(base, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pw.toFixed(2)} ${ph.toFixed(2)}] /Resources << /XObject << /Im0 ${base + 1} 0 R >> >> /Contents ${base + 2} 0 R >>`);
    offs[base + 1] = len;
    put(`${base + 1} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.length} >>\nstream\n`);
    put(p.jpeg);
    put('\nendstream\nendobj\n');
    const content = `q ${pw.toFixed(2)} 0 0 ${ph.toFixed(2)} 0 0 cm /Im0 Do Q`;
    obj(base + 2, `<< /Length ${content.length} >>\nstream\n${content}\nendstream`);
  });
  const total = 3 + n * 3, xref = len;
  put(`xref\n0 ${total}\n0000000000 65535 f \n`);
  for (let i = 1; i < total; i++) put(`${String(offs[i]).padStart(10, '0')} 00000 n \n`);
  put(`trailer\n<< /Size ${total} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
  const out = new Uint8Array(len);
  let p = 0;
  for (const u of parts) { out.set(u, p); p += u.length; }
  return out;
}

export const canvasToJpeg = (canvas, q = 0.85) => new Promise((res, rej) => canvas.toBlob(
  (b) => (b ? b.arrayBuffer().then((a) => res(new Uint8Array(a))) : rej(new Error('jpeg'))), 'image/jpeg', q));

export const bytesToBase64 = (bytes) => new Promise((res, rej) => {
  const r = new FileReader();
  r.onload = () => res(String(r.result).split(',')[1]);
  r.onerror = () => rej(r.error);
  r.readAsDataURL(new Blob([bytes]));
});
