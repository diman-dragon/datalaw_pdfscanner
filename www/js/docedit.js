// Document editor (opened from the Library or the viewer): pages of an existing PDF ->
// per page edges / filter / rotation, reorder, delete -> save (replace the original or save as a new file).
import { Folder } from './folder.js';
import { t } from './i18n.js';
import { toast } from './toast.js';
import { openSheet } from './sheet.js';
import { loadImage } from './scan/load.js';
import { detectQuad } from './scan/detect.js';
import { openResult } from './scan/result.js';
import { scaled } from './scan/filters.js';
import { canvasToJpeg, pdfStream } from './scan/pdf.js';
import { pageFromBlob, thumbOf } from './scan/page.js';
import { savePdf, askName } from './scan/save.js';

const $ = (id) => document.getElementById(id);
const root = $('docedit'), grid = $('de-grid'), head = $('de-title');
let pages = []; // {blob (JPEG of the page), thumb, quad?, filter?, turns?, out? (edited page record)}
let file = null, busy = false, dirty = false;

export const isDocEditOpen = () => !root.hidden;
export function closeDocEdit() {
  if (busy) return;
  if (dirty && !confirm(t('de.discardq'))) return;
  root.hidden = true; pages = []; file = null; dirty = false;
  grid.replaceChildren();
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
const render = () => grid.replaceChildren(...pages.map(card));

async function edit(i) {
  if (busy) return;
  busy = true;
  try {
    const p = pages[i];
    const img = await loadImage(p.blob, 4000);
    const out = await openResult(img, p.quad ?? detectQuad(img), { filter: p.filter ?? 'color', turns: p.turns ?? 0, saveKey: 'ms.apply' });
    if (out) {
      const c = out.canvas;
      Object.assign(p, {
        quad: out.quad, filter: out.filter, turns: out.turns,
        thumb: scaled(c, 360).toDataURL('image/jpeg', 0.7),
        out: { jpeg: await canvasToJpeg(c, 0.92), w: c.width, h: c.height, comps: 3, rot: 0 },
      });
      dirty = true;
      render();
    }
  } catch (e) { toast(t('scan.fail')); console.error(e); } finally { busy = false; }
}

function move(i, d) { [pages[i], pages[i + d]] = [pages[i + d], pages[i]]; dirty = true; render(); }
function menu(i) {
  const acts = [{ label: t('sc.edit'), run: () => edit(i) }];
  if (i > 0) acts.push({ label: t('sc.left'), run: () => move(i, -1) });
  if (i < pages.length - 1) acts.push({ label: t('sc.right'), run: () => move(i, 1) });
  if (pages.length > 1) acts.push({ label: t('sc.delete'), danger: true, run: () => { pages.splice(i, 1); dirty = true; render(); } });
  openSheet(`${t('sc.page')} ${i + 1}`, acts);
}

export async function openDocEdit(f) {
  if (busy) return;
  busy = true;
  try {
    const d = await Folder.viewOpen({ uri: f.uri });
    const list = [];
    try {
      for (let i = 0; i < d.ratios.length; i++) {
        toast(`${t('de.loading')} ${i + 1}/${d.ratios.length}`, 60000);
        const r = await Folder.viewPage({ id: d.id, index: i, width: 3000, quality: 95 });
        const blob = await (await fetch(r.data)).blob();
        list.push({ blob, thumb: await thumbOf(blob) });
      }
    } finally { Folder.viewClose({ id: d.id }).catch(() => {}); }
    pages = list; file = f; dirty = false;
    head.textContent = f.name;
    root.hidden = false;
    render();
    toast(t('ed.done'), 1200);
  } catch (e) { toast(t('de.fail')); console.error(e); } finally { busy = false; }
}

async function save() {
  if (busy || !pages.length) return;
  const base = file.name.replace(/\.pdf$/i, '');
  const name = askName(base);
  if (name === null) return;
  const same = name.toLowerCase() === base.toLowerCase();
  if (same && !confirm(t('de.replaceq'))) return;
  busy = true;
  try {
    const list = pages.slice(), orig = file;
    const r = await savePdf(pdfStream(list.length, async (i) => {
      toast(`${t('scan.saving')} ${i + 1}/${list.length}`, 60000);
      return list[i].out ?? pageFromBlob(list[i].blob);
    }), same ? `${base}~new` : name);
    let shown = r.name ?? '';
    if (same) { // replace: delete the original, give the new file its name
      await Folder.remove({ uri: orig.uri });
      await Folder.rename({ uri: r.uri, name: `${base}.pdf` });
      shown = `${base}.pdf`;
    }
    toast(`${t('scan.saved')} ${shown}`);
    dirty = false; busy = false;
    closeDocEdit();
    document.dispatchEvent(new Event('libchange'));
  } catch (e) { toast(t('scan.fail')); console.error(e); } finally { busy = false; }
}

$('de-cancel').addEventListener('click', closeDocEdit);
$('de-save').addEventListener('click', save);
