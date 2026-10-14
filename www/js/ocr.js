// Text recognition screen (opened from the viewer menu): languages ru / en / it / sr, this page or the whole document.
// Recognition runs natively (Tesseract); language data is downloaded on first use.
import { Ocr } from './ocrplugin.js';
import { store } from './storage.js';
import { t } from './i18n.js';
import { toast } from './toast.js';

const $ = (id) => document.getElementById(id);
const root = $('ocr'), langsEl = $('ocr-langs'), statusEl = $('ocr-status'), textEl = $('ocr-text');
const LANGS = { ru: ['rus'], en: ['eng'], it: ['ita'], sr: ['srp', 'srp_latn'] }; // Serbian: Cyrillic + Latin
let sel = ['ru', 'en'];
try { const s = JSON.parse(store.get('ocrlangs')); if (Array.isArray(s)) sel = s.filter((k) => k in LANGS); } catch { /* default */ }
let ctx = null, run = 0;

export const isOcrOpen = () => !root.hidden;
export function closeOcr() {
  if (root.hidden) return;
  root.hidden = true; run++;
  Ocr.release().catch(() => {});
}

const paintLangs = () => [...langsEl.children].forEach((b) => b.classList.toggle('on', sel.includes(b.dataset.lang)));
const setBusy = (v) => { $('ocr-page').disabled = v; $('ocr-all').disabled = v; };

/** {file, page (index), pages (count)} */
export function openOcr(c) {
  ctx = c; run++;
  textEl.value = ''; statusEl.textContent = '';
  setBusy(false); paintLangs();
  root.hidden = false;
}

async function go(all) {
  if (!sel.length) { toast(t('ocr.nolang')); return; }
  const token = ++run, langs = sel.flatMap((k) => LANGS[k]);
  setBusy(true); textEl.value = '';
  try {
    statusEl.textContent = t('ocr.dl');
    await Ocr.ensure({ langs });
    const idx = all ? Array.from({ length: ctx.pages }, (_, i) => i) : [ctx.page];
    const out = [];
    for (let k = 0; k < idx.length; k++) {
      if (token !== run) return;
      statusEl.textContent = `${t('ocr.run')} ${k + 1}/${idx.length}`;
      const r = await Ocr.recognize({ uri: ctx.file.uri, page: idx[k], langs });
      if (token !== run) return;
      const txt = (r.text ?? '').trim();
      out.push(all ? `— ${idx[k] + 1} —\n${txt}` : txt);
      textEl.value = out.join('\n\n');
    }
    statusEl.textContent = textEl.value.replace(/[—\d\s]/g, '') ? '' : t('ocr.empty');
  } catch (e) {
    console.error(e);
    if (token === run) statusEl.textContent = t('ocr.fail');
  } finally {
    if (token === run) setBusy(false);
  }
}

langsEl.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  const k = b.dataset.lang;
  sel = sel.includes(k) ? sel.filter((x) => x !== k) : [...sel, k];
  store.set('ocrlangs', JSON.stringify(sel));
  paintLangs();
});
$('ocr-page').addEventListener('click', () => go(false));
$('ocr-all').addEventListener('click', () => go(true));
$('ocr-close').addEventListener('click', closeOcr);
$('ocr-copy').addEventListener('click', async () => {
  const v = textEl.value;
  if (!v) return;
  try { await navigator.clipboard.writeText(v); } catch { textEl.select(); document.execCommand('copy'); }
  toast(t('ocr.copied'));
});
document.addEventListener('langchange', () => { if (!root.hidden) paintLangs(); });
