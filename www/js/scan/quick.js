// Quick scan tab: pick WHAT you scan (document, card with two sides, passport, business card, receipt), shoot, edit, name, save.
import { getFolder, folderInfo } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { goTo } from '../tabs.js';
import { DOC_TYPES, TYPE_IDS, getDocType, setDocType } from '../docTypes.js';
import { openCamera } from './camera.js';
import { loadImage } from './load.js';
import { openResult } from './result.js';
import { pdfStream } from './pdf.js';
import { editPages } from './pagesedit.js';
import { MAXSIDE, quadFor, normQuad, finalOf, finalSheet } from './shot.js';
import { defaultLook } from './enhance.js';
import { ensureFolder, savePdf, askName } from './save.js';

const $ = (id) => document.getElementById(id);
const target = $('quick-target'), fallback = $('quick-cam'), gallery = $('quick-pick'), typesEl = $('quick-types');
let busy = false;

const type = () => DOC_TYPES[getDocType()];
function paintTarget() {
  const info = folderInfo(getFolder());
  target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label');
}
function paintTypes() {
  typesEl.replaceChildren(...TYPE_IDS.map((id) => {
    const b = document.createElement('button');
    b.textContent = t('dt.' + id);
    b.classList.toggle('on', id === getDocType());
    b.addEventListener('click', () => { setDocType(id); paintTypes(); });
    return b;
  }));
  gallery.multiple = type().sides > 1; // a card: both sides in one go
}

async function save(page, name) {
  busy = true;
  try {
    toast(t('scan.saving'), 60000);
    const r = await savePdf(pdfStream(1, async () => page), name);
    toast(`${t('scan.saved')} ${r.name ?? ''}`);
    document.dispatchEvent(new Event('libchange'));
    goTo('library'); // show the saved file
  } catch (e) {
    toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000);
    console.error(e);
  } finally {
    busy = false;
  }
}

const fail = (e) => { toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000); console.error(e); };

/** One page of the chosen type: result screen (with Retake) -> name -> save. shot: {blob, quadN?, vr?, alts?}. */
async function runSingle(shot, fromCamera, ty) {
  let cur = shot, state = { look: defaultLook(ty.looks[ty.looks.length - 1]), turns: 0 };
  for (;;) {
    const img = await loadImage(cur.blob, MAXSIDE); // the original: the frame is set on it, the PDF is made from it
    const p = { qn: cur.quadN ?? null, vr: cur.vr };
    const out = await openResult(img, quadFor(img, p), { ...state, looks: ty.looks, retake: fromCamera, alts: cur.alts, pick: cur.pick ?? 0, best: cur.best ?? cur.pick ?? 0 });
    if (out === 'retake') {
      cur = await openCamera({ frame: ty.frame });
      if (!cur) return;
      state = { look: state.look, turns: 0 };
      continue;
    }
    if (!out) return;
    if (cur.alts && out.pick != null) cur = { ...cur, blob: cur.alts[out.pick].blob, pick: out.pick }; // the chosen picture of the series
    const job = finalOf({ blob: cur.blob, qn: normQuad(out.quad, img), vr: img.width / img.height, look: out.look, turns: out.turns, mm: ty.mm }); // made while the name is typed
    job.catch(() => {});
    const name = askName();
    if (name === null) { // back to the result with the same settings
      cur = { ...cur, quadN: normQuad(out.quad, img), vr: img.width / img.height };
      state = { look: out.look, turns: out.turns };
      continue;
    }
    toast(t('scan.saving'), 60000);
    await save(await job, name);
    return;
  }
}

/** A card (one or two sides) on an A4 sheet at real size: the sides are edited one after another, then one sheet is made. */
async function runSheet(shots, ty) {
  const pages = shots.map((sh) => ({ blob: sh.blob, alts: sh.alts, pick: sh.pick, best: sh.best, qn: sh.quadN ?? null, vr: sh.vr, look: defaultLook(ty.looks[ty.looks.length - 1]), turns: 0 }));
  if (await editPages(pages, { lastKey: 'sc.save', looks: ty.looks, word: 'dt.side' }) !== 'done') return;
  const job = finalSheet(pages, ty.mm);
  job.catch(() => {});
  const name = askName();
  if (name === null) return;
  toast(t('scan.saving'), 60000);
  await save(await job, name);
}

async function process(shots, fromCamera) {
  const ty = type();
  busy = true;
  try {
    if (ty.layout === 'sheet') await runSheet(shots, ty); else await runSingle(shots[0], fromCamera, ty);
  } catch (e) { fail(e); } finally { busy = false; }
}

$('quick-shoot').addEventListener('click', async () => {
  if (busy || !(await ensureFolder())) return;
  const ty = type(), shots = [];
  try {
    for (let s = 0; s < ty.sides; s++) {
      const title = ty.sides > 1 ? `${t('dt.side')} ${s + 1} ${t('rs.of')} ${ty.sides}: ${t(s === 0 ? 'dt.front' : 'dt.back')}` : null;
      const shot = await openCamera({ frame: ty.frame, title });
      if (!shot) return;
      shots.push(shot);
    }
    process(shots, true);
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click(); // system camera as a fallback
  }
});
$('quick-gallery').addEventListener('click', async () => { if (busy) return; if (getFolder()) gallery.click(); else await ensureFolder(); }); // the file chooser needs the tap itself, so after choosing a folder tap again
for (const input of [fallback, gallery]) {
  input.addEventListener('change', () => {
    const files = [...input.files].slice(0, type().sides);
    input.value = '';
    if (!files.length) return;
    if (files.length < type().sides) { toast(t('dt.need2')); return; } // a card needs both sides
    process(files.map((f) => ({ blob: f })), false);
  });
}
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', () => { paintTarget(); paintTypes(); });
paintTarget();
paintTypes();
