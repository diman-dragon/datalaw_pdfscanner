// QUICK SCAN: nothing to choose. One tap: the camera opens with a fixed A4 frame; a tap on the shutter takes ONE photo (see qcamera.js),
// the photo is cut to the frame, pressed into the PDF quality of the Settings WITHOUT any other processing and saved
// into the chosen folder under the name qscan_YYYYMMDD_HHMM.pdf (the window with the name is filled in, it can be edited). Then the Library opens.
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { goTo } from '../tabs.js';
import { getFolder, folderInfo } from '../folder.js';
import { pdfStream } from './pdf.js';
import { finalRaw } from './shot.js';
import { openQcam, qcamStatus } from './qcamera.js';
import { ensureFolder, savePdf, autoName, askFileName } from './save.js';

const $ = (id) => document.getElementById(id);
const target = $('quick-target'), A4 = 210 / 297;

async function onShot({ blob, crop, vr }) {
  const name = await askFileName(autoName('qscan'));
  if (name === null) return undefined; // cancelled: back to the camera
  qcamStatus(t('scan.saving'));
  const page = await finalRaw(blob, crop, vr);
  const r = await savePdf(pdfStream(1, async () => page), name);
  toast(`${t('scan.saved')} ${r.name ?? ''}`);
  document.dispatchEvent(new Event('libchange'));
  goTo('library');
  return 'close';
}

$('quick-shoot').addEventListener('click', async () => {
  if (await ensureFolder()) openQcam({ ratio: (land) => (land ? 1 / A4 : A4), label: () => 'A4', hint: () => t('qs.hint'), fallback: $('quick-cam'), onShot });
});
const paintTarget = () => { const info = folderInfo(getFolder()); target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label'); };
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', paintTarget);
paintTarget();
