// Scan tab, document types other than a plain document: a card with two sides (one A4 sheet at real size), a business card,
// a passport, a receipt. Shoot (the camera of the Scan tab with the frame of the type), edit, name, save.
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

let busy = false;
const type = () => DOC_TYPES[getDocType()];
export const isTyped = () => getDocType() !== 'doc';

/** Paints the type chips into el; onChange() is called after a choice. */
export function paintTypes(el, onChange) {
  el.replaceChildren(...TYPE_IDS.map((id) => {
    const b = document.createElement('button');
    b.textContent = t('dt.' + id);
    b.classList.toggle('on', id === getDocType());
    b.addEventListener('click', () => { setDocType(id); paintTypes(el, onChange); onChange?.(); });
    return b;
  }));
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

/** One page of the chosen type: result screen (with Retake) -> name -> save. shot: {blob, quadN?, vr?}. */
async function runSingle(shot, fromCamera, ty) {
  let cur = shot, state = { look: defaultLook(ty.looks[ty.looks.length - 1]), turns: 0 };
  for (;;) {
    const img = await loadImage(cur.blob, MAXSIDE); // the original: the frame is set on it, the PDF is made from it
    const p = { qn: cur.quadN ?? null, vr: cur.vr };
    const out = await openResult(img, quadFor(img, p), { ...state, looks: ty.looks, retake: fromCamera });
    if (out === 'retake') {
      cur = await openCamera({ frame: ty.frame });
      if (!cur) return;
      state = { look: state.look, turns: 0 };
      continue;
    }
    if (!out) return;
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
  const pages = shots.map((sh) => ({ blob: sh.blob, qn: sh.quadN ?? null, vr: sh.vr, look: defaultLook(ty.looks[ty.looks.length - 1]), turns: 0 }));
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


/** The camera flow of the chosen type. Returns false if the camera is not available (the caller can offer the system camera). */
export async function shootTyped() {
  if (busy || !(await ensureFolder())) return true;
  const ty = type(), shots = [];
  try {
    for (let s = 0; s < ty.sides; s++) {
      const title = ty.sides > 1 ? `${t('dt.side')} ${s + 1} ${t('rs.of')} ${ty.sides}: ${t(s === 0 ? 'dt.front' : 'dt.back')}` : null;
      const shot = await openCamera({ frame: ty.frame, title });
      if (!shot) return true;
      shots.push(shot);
    }
  } catch (e) { console.error(e); toast(t('cam.denied')); return false; }
  process(shots, true);
  return true;
}

/** Files from the gallery / the system camera for the chosen type (a card takes two). */
export function typedFromFiles(files) {
  const ty = type(), list = files.slice(0, ty.sides);
  if (list.length < ty.sides) { toast(t('dt.need2')); return; }
  process(list.map((f) => ({ blob: f })), false);
}
