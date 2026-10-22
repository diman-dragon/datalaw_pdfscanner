// Editor of an existing PDF (Library menu / viewer): the same single-scan editor over the pages
// (edges / filter / rotation, Back / Next, caption), plus page order and deletion. Save replaces the original or writes a new file.
import { Folder } from './folder.js';
import { t } from './i18n.js';
import { toast } from './toast.js';
import { openSheet } from './sheet.js';
import { pdfStream } from './scan/pdf.js';
import { editPages } from './scan/pagesedit.js';
import { FULL_QUAD, finalOf, thumbPage } from './scan/shot.js';
import { defaultLook } from './scan/enhance.js';
import { savePdf, askName } from './scan/save.js';
import { pdfTarget } from './pdfquality.js';
import { askConfirm, askAlert } from './dialog.js';

const $ = (id) => document.getElementById(id);
const root = $('docedit'), grid = $('de-grid'), head = $('de-title');
let pages = [];   // {i (index in the PDF), blob|null, qn, vr, filter, turns, keep, orig, thumb}
let file = null, doc = null, opts = {}, busy = false, dirty = false;

export const isDocEditOpen = () => !root.hidden;

function reset() {
  if (doc) Folder.viewClose({ id: doc.id }).catch(() => {});
  doc = null; pages = []; file = null; opts = {}; dirty = false;
  root.hidden = true; grid.replaceChildren();
}
export async function closeDocEdit() {
  if (busy) return;
  if (dirty && !(await askConfirm(t('de.discardq'), { ok: t('de.discard'), cancel: t('dlg.stay'), danger: true }))) return;
  reset();
}

// a page of the PDF is rendered when it is needed (editing / saving), not all at once
async function ensure(p) {
  if (p.blob) return;
  const r = await Folder.viewPage({ id: doc.id, index: p.i, width: Math.min(2400, pdfTarget().side), quality: 88 });
  p.blob = await (await fetch(r.data)).blob();
}

const refreshThumb = async (p) => { try { await ensure(p); p.thumb = await thumbPage(p); } catch (e) { console.error(e); } render(); };

function card(p, n) {
  const el = document.createElement('div');
  el.className = 'pg';
  el.innerHTML = '<img alt=""><span class="num"></span><button class="more">⋮</button>';
  if (p.thumb) el.querySelector('img').src = p.thumb;
  el.querySelector('.num').textContent = n + 1;
  el.querySelector('.more').addEventListener('click', (e) => { e.stopPropagation(); menu(p); });
  el.addEventListener('click', () => edit(pages.indexOf(p), { single: true, lastKey: 'ms.apply' }));
  return el;
}
const render = () => grid.replaceChildren(...pages.map(card));

/** Runs the editor; returns 'done' / 'cancel'. */
async function edit(start, o = {}) {
  if (busy) return 'cancel';
  busy = true;
  let res = 'cancel';
  try {
    res = await editPages(pages, {
      start, del: true, lastKey: 'ed.done', ...o, ensure,
      onChange: (p) => { dirty = true; refreshThumb(p); },
      onDelete: () => { dirty = true; render(); },
    });
  } catch (e) { toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000); console.error(e); } finally { busy = false; }
  if (!root.hidden) render();
  return res;
}

function move(i, d) { [pages[i], pages[i + d]] = [pages[i + d], pages[i]]; dirty = true; render(); }
function menu(p) {
  const i = pages.indexOf(p);
  const acts = [{ label: t('sc.edit'), run: () => edit(i, { single: true, lastKey: 'ms.apply' }) }];
  if (i > 0) acts.push({ label: t('sc.left'), run: () => move(i, -1) });
  if (i < pages.length - 1) acts.push({ label: t('sc.right'), run: () => move(i, 1) });
  if (pages.length > 1) acts.push({ label: t('sc.delete'), danger: true, run: () => { pages.splice(i, 1); dirty = true; render(); } });
  openSheet(`${t('sc.page')} ${i + 1}`, acts);
}

/** f: library file {uri, name}. o: {page, single} - edit one page right away (then save); onSaved(file). */
export async function openDocEdit(f, o = {}) {
  if (busy) return;
  busy = true;
  try {
    const d = await Folder.viewOpen({ uri: f.uri });
    doc = { id: d.id };
    pages = d.ratios.map((ratio, i) => ({ i, blob: null, qn: FULL_QUAD.map((q) => q.slice()), vr: 1 / ratio, look: defaultLook('color'), turns: 0, keep: true, orig: true, thumb: '' }));
    file = f; opts = o; dirty = false;
    head.textContent = f.name;
    if (o.single) { // one page: no grid, edit it and save
      busy = false;
      const res = await edit(o.page ?? 0, { single: true, lastKey: 'sc.save' });
      if (res === 'done') await save(); else reset();
      return;
    }
    for (let k = 0; k < pages.length; k++) { // the grid needs thumbnails
      toast(`${t('de.loading')} ${k + 1}/${pages.length}`, 60000);
      await ensure(pages[k]);
      pages[k].thumb = await thumbPage(pages[k]);
    }
    toast(t('ed.done'), 1000);
    root.hidden = false;
    render();
  } catch (e) { toast(t('de.fail')); console.error(e); reset(); } finally { busy = false; }
}

async function save() {
  if (busy || !pages.length) return;
  const base = file.name.replace(/\.pdf$/i, '');
  const name = await askName(base);
  if (name === null) { if (opts.single) reset(); return; }
  const same = name.toLowerCase() === base.toLowerCase();
  if (same && !(await askConfirm(t('de.replaceq'), { ok: t('de.replace') }))) { if (opts.single) reset(); return; }
  busy = true;
  try {
    const list = pages.slice(), orig = file, onSaved = opts.onSaved;
    const r = await savePdf(pdfStream(list.length, async (i) => {
      toast(`${t('scan.saving')} ${i + 1}/${list.length}`, 60000);
      await ensure(list[i]);
      return finalOf(list[i]);
    }), same ? `${base}~new` : name);
    let saved = { uri: r.uri, name: r.name ?? `${name}.pdf` };
    if (doc) { Folder.viewClose({ id: doc.id }).catch(() => {}); doc = null; }
    if (same) { // replace the original in place (it is never deleted or renamed); if that fails the new file stays as it is
      try {
        await Folder.replaceWith({ uri: orig.uri, from: r.uri });
        saved = { uri: orig.uri, name: orig.name };
      } catch (e1) {
        console.error(e1);
        try { // the original is gone (deleted elsewhere): the new file simply takes its name
          const rn = await Folder.rename({ uri: r.uri, name: `${base}.pdf` });
          saved = { uri: rn?.uri ?? r.uri, name: `${base}.pdf` };
        } catch (e2) { console.error(e2); askAlert(`${t('de.keptnew')} ${saved.name}`); }
      }
    }
    toast(`${t('scan.saved')} ${saved.name}`);
    dirty = false; busy = false;
    reset();
    document.dispatchEvent(new Event('libchange'));
    onSaved?.(saved);
  } catch (e) { toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000); console.error(e); if (opts.single) reset(); } finally { busy = false; }
}

$('de-cancel').addEventListener('click', closeDocEdit);
$('de-all').addEventListener('click', () => edit(0));
$('de-save').addEventListener('click', save);
