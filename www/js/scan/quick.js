// Quick scan tab: one page. Camera (or gallery) -> name -> the ORIGINAL photo is written into a PDF. No processing.
import { getFolder, folderInfo } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { openCamera } from './camera.js';
import { pdfStream } from './pdf.js';
import { pageFromBlob } from './page.js';
import { ensureFolder, savePdf, askName } from './save.js';

const $ = (id) => document.getElementById(id);
const target = $('quick-target'), fallback = $('quick-cam'), gallery = $('quick-pick');
let busy = false;

function paintTarget() {
  const info = folderInfo(getFolder());
  target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label');
}

async function save(blob) {
  if (busy) return;
  const name = askName();
  if (name === null) return;
  busy = true;
  try {
    toast(t('scan.saving'), 60000);
    const page = await pageFromBlob(blob);
    const r = await savePdf(pdfStream(1, async () => page), name);
    toast(`${t('scan.saved')} ${r.name ?? ''}`);
    document.dispatchEvent(new Event('libchange'));
  } catch (e) {
    toast(t('scan.fail'));
    console.error(e);
  } finally {
    busy = false;
  }
}

$('quick-shoot').addEventListener('click', async () => {
  if (busy || !ensureFolder()) return;
  try {
    const shot = await openCamera();
    if (shot) save(shot.blob);
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click(); // system camera as a fallback
  }
});
$('quick-gallery').addEventListener('click', () => { if (!busy && ensureFolder()) gallery.click(); });
for (const input of [fallback, gallery]) {
  input.addEventListener('change', () => { const f = input.files[0]; input.value = ''; if (f) save(f); });
}
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', paintTarget);
paintTarget();
