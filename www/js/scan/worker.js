// Web Worker: renders PDF pages off the main thread, so the screen stays smooth and several pages are made in parallel.
import { renderPage } from './render.js';
self.onmessage = async (e) => {
  const { id, job } = e.data;
  try {
    const r = await renderPage(job), tr = [];
    const p = r.page;
    if (p.jpeg) tr.push(p.jpeg.buffer);
    if (p.layers) tr.push(p.layers.bg.jpeg.buffer, p.layers.fg.jpeg.buffer, p.layers.mask.data.buffer);
    if (r.ocr) tr.push(r.ocr.jpeg.buffer);
    self.postMessage({ id, ok: true, r }, tr);
  } catch (err) {
    self.postMessage({ id, ok: false, error: String(err?.stack || err) });
  }
};
