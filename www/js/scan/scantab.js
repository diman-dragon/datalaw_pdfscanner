// SCAN TAB: choose what you scan (A4, A5, ID card, business card, passport, receipt), tap Shoot: the same bare camera as the Quick scan with
// the frame of that type. After every shot: "Shoot more" or "Finish". An ID card asks for the back side (both sides go on ONE sheet with a gap).
// Every item is saved on an A4 sheet at its REAL size. "Finish" asks for the file name (filled in: scan_YYYYMMDD_HHMM, can be edited), saves the PDF into the chosen folder and opens the Library.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { goTo } from '../tabs.js';
import { getFolder, folderInfo } from '../folder.js';
import { SCAN_TYPES, SCAN_IDS, itemMm, getScanType, setScanType } from '../scanTypes.js';
import { openQcam, qcamStatus, qcamBadge, qcamTexts } from './qcamera.js';
import { askChoice } from './choice.js';
import { pdfStream } from './pdf.js';
import { finalPlaced } from './shot.js';
import { ensureFolder, savePdf, autoName, askFileName } from './save.js';

const $ = (id) => document.getElementById(id);
const types = $('sc-types'), desc = $('sc-desc'), target = $('sc-target');
let items = [], cur = null; // items: finished pages {sides, mm, rotate, job}; cur: the item being shot (the front is taken, the back is awaited)
const type = () => SCAN_TYPES[getScanType()];
const name = (id) => t('st.' + id);
const sizeText = (id) => { const [w, h] = SCAN_TYPES[id].mm; return `${w} × ${h} mm`; };

function paintTypes() {
  types.replaceChildren(...SCAN_IDS.map((id) => {
    const b = document.createElement('button');
    b.textContent = name(id);
    b.classList.toggle('on', id === getScanType());
    b.addEventListener('click', () => { setScanType(id); paintTypes(); });
    return b;
  }));
  const id = getScanType();
  desc.textContent = `${sizeText(id)}${(SCAN_TYPES[id].sides ?? 1) === 2 ? ` · ${t('sc.twosides')}` : ''}`;
}
const paintTarget = () => { const info = folderInfo(getFolder()); target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label'); };

const reset = () => { items = []; cur = null; };

/** A finished item: its A4 sheet is made in the background while the user decides what is next. */
function complete(item) {
  item.job = finalPlaced(item);
  item.job.catch(() => {});
  items.push(item);
  qcamBadge(`${t('sc.count')} ${items.length}`);
}

async function finish(name) {
  qcamStatus(t('scan.saving'));
  const list = items.slice();
  const r = await savePdf(pdfStream(list.length, async (i) => list[i].job), name);
  toast(`${t('scan.saved')} ${r.name ?? ''}`);
  document.dispatchEvent(new Event('libchange'));
  goTo('library');
}

async function onShot({ blob, crop, vr, land }) {
  const id = getScanType(), ty = type(), mm = itemMm(id, land);
  cur ??= { sides: [], mm, rotate: mm[0] > 210.5 }; // an A4 held landscape does not fit the sheet: it is turned
  cur.sides.push({ blob, crop, vr });
  if ((ty.sides ?? 1) === 2 && cur.sides.length === 1) {
    const a = await askChoice(t('sc.askback'), [{ id: 'back', label: t('sc.back') }, { id: 'skip', label: t('sc.noback') }]);
    if (a === 'back') { qcamTexts(() => t('sc.hint.back'), `${name(id)}: ${t('sc.backlabel')}`); return undefined; }
  }
  complete(cur); cur = null;
  qcamTexts(null, null);
  for (;;) {
    const a = await askChoice(`${t('sc.count')} ${items.length}`, [{ id: 'more', label: t('sc.more') }, { id: 'finish', label: t('sc.finish') }]);
    if (a === 'more') return undefined;
    const fname = await askFileName(autoName('scan')); // the name by the parameters of the scan, ready to be saved or changed
    if (fname === null) continue; // cancelled: the question again
    await finish(fname);
    return 'close';
  }
}

$('sc-shoot').addEventListener('click', async () => {
  if (!(await ensureFolder())) return;
  const id = getScanType(), orient = SCAN_TYPES[id].orient;
  reset();
  openQcam({
    ratio: (land) => { const m = itemMm(id, land); return m[0] / m[1]; },
    label: () => name(id),
    hint: () => t(orient === 'video' ? 'sc.hint.page' : 'sc.hint.item'),
    fallback: $('sc-cam'),
    onShot,
    canClose: () => (!items.length && !cur) || confirm(t('sc.discardq')),
    onClose: reset,
  });
});
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', () => { paintTarget(); paintTypes(); });
paintTarget();
paintTypes();
