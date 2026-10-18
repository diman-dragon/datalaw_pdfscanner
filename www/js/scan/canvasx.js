// Canvas helpers that work on the page and inside a Web Worker (OffscreenCanvas).
export const mk = (w, h) => {
  if (typeof document !== 'undefined') { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
  return new OffscreenCanvas(w, h);
};
export const toBlob = (c, q = 0.92) => (c.convertToBlob
  ? c.convertToBlob({ type: 'image/jpeg', quality: q })
  : new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', q)));
