// Scan tab: multi-page session. Pages are kept as JPEG blobs + quad/filter/turns; PDF is built on save.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { getFolder, folderInfo } from '../folder.js';
import { openSheet } from '../sheet.js';
import { loadImage } from './load.js';
import { detectQuad } from './detect.js';
import { openCamera } from './camera.js';
import { openResult } from './result.js';
import { warp } from './warp.js';
import { applyFilter, rotate90, scaled } from './filters.js';
import { canvasToJpeg, jpegsToPdf } from './pdf.js';
import { ensureFolder, savePdf } from './save.js';

const $ = (id) => document.getElementById(id);
const home = $('sc-home'), sess = $('sc-session'), grid = $('sc-grid'), count = $('sc-count'), target = $('sc-target');
const fallback = $('sc-cam'), gallery = $('sc-pick');
const pages = []; // {blob, quad, filter, turns, thumb}
let busy = false;
const tick = () => new Promise((r) => setTimeout(r, 0));

const thumbOf = (canvas, quad, filter, turns) =>
  applyFilter(rotate90(warp(canvas, quad, 360), turns), filter).toDataURL('image/jpeg', 0.7);

async function addPage(canvas, quad, filter = 'color') {
  const blob = await new Promise((r) => canvas.toBlob(r, 'image/jpeg', 0.92));
  pages.push({ blob, quad, filter, turns: 0, thumb: thumbOf(canvas, quad, filter, 0) });
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
  el.querySelector('img').src = p.thumb;
  el.querySelector('.num').textContent = i + 1;
  el.querySelector('.more').addEventListener('click', (e) => { e.stopPropagation(); menu(i); });
  el.addEventListener('click', () => edit(i));
  return el;
}

function render() {
  home.hidden = pages.length > 0;
  sess.hidden = pages.length === 0;
  count.textContent = `${t('sc.count')} ${pages.length}`;
  grid.replaceChildren(...pages.map(card));
}

async function edit(i) {
  if (busy) return;
  busy = true;
  try {
    const p = pages[i];
    const out = await openResult(await loadImage(p.blob), p.quad, { filter: p.filter, turns: p.turns, saveKey: 'ms.apply', retake: false });
    if (out) {
      Object.assign(p, { quad: out.quad, filter: out.filter, turns: out.turns, thumb: scaled(out.canvas, 360).toDataURL('image/jpeg', 0.7) });
      render();
    }
  } catch (e) { toast(t('scan.fail')); console.error(e); } finally { busy = false; }
}

function move(i, d) { [pages[i], pages[i + d]] = [pages[i + d], pages[i]]; render(); }
function menu(i) {
  const acts = [{ label: t('sc.edit'), run: () => edit(i) }];
  if (i > 0) acts.push({ label: t('sc.left'), run: () => move(i, -1) });
  if (i < pages.length - 1) acts.push({ label: t('sc.right'), run: () => move(i, 1) });
  acts.push({ label: t('sc.delete'), danger: true, run: () => { pages.splice(i, 1); render(); } });
  openSheet(`${t('sc.page')} ${i + 1}`, acts);
}

async function shoot() {
  if (busy || !ensureFolder()) return;
  try {
    await openCamera({ multi: true, start: pages.length, onShot: (f) => addPage(f.canvas, f.quad, f.filter) });
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click();
  }
}

async function fromFiles(files) {
  busy = true;
  try {
    for (const f of files) { const img = await loadImage(f); await addPage(img, detectQuad(img)); await tick(); }
  } catch (e) { toast(t('scan.fail')); console.error(e); } finally { busy = false; }
}

async function exportPdf() {
  if (busy || !pages.length || !ensureFolder()) return;
  busy = true;
  try {
    const out = [];
    for (let i = 0; i < pages.length; i++) {
      toast(`${t('scan.saving')} ${i + 1}/${pages.length}`, 60000);
      await tick();
      const p = pages[i];
      const f = applyFilter(rotate90(warp(await loadImage(p.blob), p.quad), p.turns), p.filter);
      out.push({ jpeg: await canvasToJpeg(f), w: f.width, h: f.height });
    }
    const r = await savePdf(jpegsToPdf(out));
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
