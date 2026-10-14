// Quick scan tab: one page. Camera (or gallery) -> result (filters/rotation/edges) -> PDF into the folder.
import { Folder, getFolder, setFolder, folderInfo } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { loadImage } from './load.js';
import { detectQuad } from './detect.js';
import { openCamera } from './camera.js';
import { openResult } from './result.js';
import { canvasToJpeg, jpegsToPdf, bytesToBase64 } from './pdf.js';

const $ = (id) => document.getElementById(id);
const target = $('quick-target'), fallback = $('quick-cam'), gallery = $('quick-pick');
let busy = false;

function paintTarget() {
  const info = folderInfo(getFolder());
  target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label');
}

function ensureFolder() {
  if (getFolder()) return true;
  if (confirm(t('scan.nofolder'))) {
    Folder.pickFolder().then((f) => { setFolder(f); toast(t('scan.folderset')); })
      .catch((e) => { if (String(e?.message ?? e) !== 'cancelled') alert(t('lib.fail')); });
  }
  return false;
}

const stamp = () => {
  const d = new Date(), p = (n) => String(n).padStart(2, '0');
  return `Scan_${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}_${p(d.getHours())}-${p(d.getMinutes())}-${p(d.getSeconds())}`;
};

async function save(canvas) {
  toast(t('scan.saving'), 60000);
  const jpeg = await canvasToJpeg(canvas);
  const pdf = jpegsToPdf([{ jpeg, w: canvas.width, h: canvas.height }]);
  const r = await Folder.save({ tree: getFolder().uri, name: stamp(), data: await bytesToBase64(pdf) });
  toast(`${t('scan.saved')} ${r.name ?? ''}`);
  document.dispatchEvent(new Event('libchange'));
}

// Source: {canvas, quad} from the camera or a gallery file. Loops while the user presses "Retake".
async function run(first) {
  if (busy) return;
  busy = true;
  try {
    let cur = first;
    while (cur) {
      const out = await openResult(cur.canvas, cur.quad, cur.filter);
      if (out === 'retake') { cur = await openCamera(); continue; }
      if (out) await save(out);
      return;
    }
  } catch (e) {
    toast(t('scan.fail'));
    console.error(e);
  } finally {
    busy = false;
  }
}

async function fromFile(file) {
  const img = await loadImage(file);
  run({ canvas: img, quad: detectQuad(img) });
}

$('quick-shoot').addEventListener('click', async () => {
  if (busy || !ensureFolder()) return;
  try {
    const first = await openCamera();
    if (first) run(first);
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click(); // system camera as a fallback
  }
});
$('quick-gallery').addEventListener('click', () => { if (!busy && ensureFolder()) gallery.click(); });
for (const input of [fallback, gallery]) {
  input.addEventListener('change', () => { const f = input.files[0]; input.value = ''; if (f) fromFile(f).catch(() => toast(t('scan.fail'))); });
}
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', paintTarget);
paintTarget();
