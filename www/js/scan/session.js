// Scan tab: multi-page capture. Pages are the ORIGINAL photos (blobs); editing lives in the Library / viewer.
// Here a page can only be deleted (a bad shot). Save writes all originals into one PDF.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { getFolder, folderInfo } from '../folder.js';
import { openSheet } from '../sheet.js';
import { openCamera } from './camera.js';
import { pdfStream } from './pdf.js';
import { pageFromBlob, thumbOf } from './page.js';
import { ensureFolder, savePdf, askName } from './save.js';

const $ = (id) => document.getElementById(id);
const home = $('sc-home'), sess = $('sc-session'), grid = $('sc-grid'), count = $('sc-count'), target = $('sc-target');
const fallback = $('sc-cam'), gallery = $('sc-pick');
const pages = []; // {blob, thumb}
let busy = false;

async function addPage(blob) {
  const p = { blob, thumb: '' };
  pages.push(p); // order is fixed at once, the thumbnail arrives later
  render();
  try { p.thumb = await thumbOf(blob); } catch (e) { console.error(e); }
  render();
}

function paintTarget() {
  const info = folderInfo(getFolder());
  target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label');
}

function card(p, i) {
  const el = document.createElement('div');
  el.className = 'pg';
  el.innerHTML = '<img alt=""><span class="num"></span><button class="more">⋮</button>';
  if (p.thumb) el.querySelector('img').src = p.thumb;
  el.querySelector('.num').textContent = i + 1;
  el.querySelector('.more').addEventListener('click', (e) => { e.stopPropagation(); menu(p); });
  el.addEventListener('click', () => menu(p));
  return el;
}

function render() {
  home.hidden = pages.length > 0;
  sess.hidden = pages.length === 0;
  count.textContent = `${t('sc.count')} ${pages.length}`;
  grid.replaceChildren(...pages.map(card));
}

function menu(p) {
  openSheet(`${t('sc.page')} ${pages.indexOf(p) + 1}`, [
    { label: t('sc.delete'), danger: true, run: () => { pages.splice(pages.indexOf(p), 1); render(); } },
  ]);
}

async function shoot() {
  if (busy || !ensureFolder()) return;
  try {
    await openCamera({ multi: true, start: pages.length, onShot: (f) => addPage(f.blob) });
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click();
  }
}

async function fromFiles(files) {
  for (const f of files) await addPage(f);
}

async function exportPdf() {
  if (busy || !pages.length || !ensureFolder()) return;
  const name = askName();
  if (name === null) return;
  busy = true;
  try {
    const list = pages.slice();
    const r = await savePdf(pdfStream(list.length, async (i) => {
      toast(`${t('scan.saving')} ${i + 1}/${list.length}`, 60000);
      return pageFromBlob(list[i].blob);
    }), name);
    pages.length = 0; render();
    toast(`${t('scan.saved')} ${r.name ?? ''}`);
    document.dispatchEvent(new Event('libchange'));
  } catch (e) { toast(t('scan.fail')); console.error(e); } finally { busy = false; }
}

for (const id of ['sc-shoot', 'sc-add']) $(id).addEventListener('click', shoot);
for (const id of ['sc-gallery', 'sc-add-gallery']) $(id).addEventListener('click', () => { if (!busy && ensureFolder()) gallery.click(); });
$('sc-save').addEventListener('click', exportPdf);
$('sc-clear').addEventListener('click', () => { if (confirm(t('sc.clearq'))) { pages.length = 0; render(); } });
gallery.addEventListener('change', () => { const fs = [...gallery.files]; gallery.value = ''; if (fs.length) fromFiles(fs); });
fallback.addEventListener('change', () => { const fs = [...fallback.files]; fallback.value = ''; if (fs.length) fromFiles(fs); });
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', () => { paintTarget(); render(); });
paintTarget();
render();
