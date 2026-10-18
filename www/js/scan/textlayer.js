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

/** jpeg: the upright page (made in the render worker, already enlarged for the recognizer). Returns [{t, x0, y0, x1, y1}] (0..1). */
export async function wordsFromJpeg(jpeg) {
  const l = langs();
  await Ocr.ensure({ langs: l });
  const { hocr } = await Ocr.recognizeImage({ data: await bytesToBase64(jpeg), langs: l });
  const doc = new DOMParser().parseFromString(hocr || '', 'text/html');
  const pg = /bbox \d+ \d+ (\d+) (\d+)/.exec(doc.querySelector('.ocr_page')?.getAttribute('title') || '');
  if (!pg) return [];
  const W = +pg[1], H = +pg[2], out = [];
  for (const el of doc.querySelectorAll('.ocrx_word')) {
    const m = /bbox (\d+) (\d+) (\d+) (\d+)/.exec(el.getAttribute('title') || ''), conf = /x_wconf (\d+)/.exec(el.getAttribute('title') || '');
    const text = (el.textContent || '').trim();
    if (!m || !text || (conf && +conf[1] < 35)) continue;
    out.push({ t: text, x0: +m[1] / W, y0: +m[2] / H, x1: +m[3] / W, y1: +m[4] / H });
  }
  return out;
}
