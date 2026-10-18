// Invisible text layer of a page: native recognition (Tesseract) -> word boxes, normalised to 0..1 of the page.
import { Ocr } from '../ocrplugin.js';
import { store } from '../storage.js';
import { bytesToBase64 } from './pdf.js';

const LANGS = { ru: ['rus'], en: ['eng'], it: ['ita'], sr: ['srp', 'srp_latn'] }; // as on the recognition screen
function langs() {
  let sel = ['ru', 'en'];
  try { const s = JSON.parse(store.get('ocrlangs')); if (Array.isArray(s)) sel = s.filter((k) => k in LANGS); } catch { /* default */ }
  return (sel.length ? sel : ['ru', 'en']).flatMap((k) => LANGS[k]);
}

/** canvas: the upright page. Returns [{t, x0, y0, x1, y1}] or null. Letters are small at 150 dpi, so the picture is enlarged for the recognizer. */
export async function wordsOf(canvas) {
  const l = langs();
  await Ocr.ensure({ langs: l });
  const k = Math.min(2, Math.max(1, 2480 / Math.max(canvas.width, canvas.height)));
  const c = document.createElement('canvas');
  c.width = Math.round(canvas.width * k); c.height = Math.round(canvas.height * k);
  c.getContext('2d').drawImage(canvas, 0, 0, c.width, c.height);
  const jpeg = new Uint8Array(await (await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.85))).arrayBuffer());
  const { hocr } = await Ocr.recognizeImage({ data: await bytesToBase64(jpeg), langs: l });
  const doc = new DOMParser().parseFromString(hocr || '', 'text/html');
  const out = [];
  for (const el of doc.querySelectorAll('.ocrx_word')) {
    const m = /bbox (\d+) (\d+) (\d+) (\d+)/.exec(el.getAttribute('title') || ''), conf = /x_wconf (\d+)/.exec(el.getAttribute('title') || '');
    const text = (el.textContent || '').trim();
    if (!m || !text || (conf && +conf[1] < 35)) continue;
    out.push({ t: text, x0: +m[1] / c.width, y0: +m[2] / c.height, x1: +m[3] / c.width, y1: +m[4] / c.height });
  }
  return out;
}
