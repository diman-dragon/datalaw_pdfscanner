// Scan tab: multi-page capture. Pages are ORIGINAL photos with their frame; on "Done" the filter is chosen for all pages,
// each page can still be adjusted (edges / filter / rotation) from its menu. Save writes one PDF.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { getFolder, folderInfo } from '../folder.js';
import { openSheet } from '../sheet.js';
import { openCamera } from './camera.js';
import { loadImage } from './load.js';
import { openResult } from './result.js';
import { pdfStream } from './pdf.js';
import { FILTERS, MAXSIDE, flatOf, finalOf, thumbPage, quadFor, normQuad } from './shot.js';
import { ensureFolder, savePdf, askName } from './save.js';

const $ = (id) => document.getElementById(id);
const home = $('sc-home'), sess = $('sc-session'), grid = $('sc-grid'), count = $('sc-count'), target = $('sc-target');
const fallback = $('sc-cam'), gallery = $('sc-pick');
const pages = []; // {blob, qn, vr, filter, turns, thumb, job}
let busy = false, chain = Promise.resolve();

// heavy stage (decode + warp by the frame) runs in the background, one page at a time, so Save only writes the file
function prepare(p) {
  const job = chain.then(() => flatOf(p));
  chain = job.catch(() => {});
  p.job = job;
}
const refreshThumb = async (p) => { try { p.thumb = await thumbPage(p); } catch (e) { console.error(e); } render(); };

async function addPage(shot) {
  const p = { blob: shot.blob, qn: shot.quadN ?? null, vr: shot.vr, filter: 'color', turns: 0, thumb: '', job: null };
  pages.push(p); // order is fixed at once, the thumbnail arrives later
  render();
  prepare(p);
  await refreshThumb(p);
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
  el.addEventListener('click', () => adjust(p));
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
    { label: t('sc.edit'), run: () => adjust(p) },
    { label: t('sc.delete'), danger: true, run: () => { pages.splice(pages.indexOf(p), 1); render(); } },
  ]);
}

/** Edges / filter / rotation of one page (parameters only; the full-size render happens on save). */
async function adjust(p) {
  if (busy) return;
  busy = true;
  try {
    const img = await loadImage(p.blob, MAXSIDE);
    const out = await openResult(img, quadFor(img, p), { filter: p.filter, turns: p.turns, saveKey: 'ms.apply', light: true });
    if (out) {
      Object.assign(p, { qn: normQuad(out.quad, img), vr: img.width / img.height, filter: out.filter, turns: out.turns });
      prepare(p);
      await refreshThumb(p);
    }
  } catch (e) { toast(t('scan.fail')); console.error(e); } finally { busy = false; }
}

// "Done": the filter for all pages
function pickFilter() {
  if (!pages.length) return;
  openSheet(t('sc.filterq'), FILTERS.map((f) => ({
    label: t(`fx.${f}`),
    run: async () => {
      for (const p of pages) p.filter = f;
      for (const p of pages.slice()) await refreshThumb(p);
    },
  })));
}

async function shoot() {
  if (busy || !ensureFolder()) return;
  const before = pages.length;
  try {
    await openCamera({ multi: true, start: pages.length, onShot: addPage });
    if (pages.length > before) pickFilter();
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click();
  }
}

async function fromFiles(files) {
  for (const f of files) await addPage({ blob: f });
  pickFilter();
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
      return finalOf(list[i]);
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
