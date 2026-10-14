// Scan tab: shoot / pick -> edit edges -> warp -> PDF -> save into the chosen folder.
import { Folder, getFolder, setFolder, folderInfo } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { loadImage } from './load.js';
import { detectQuad } from './detect.js';
import { openEditor } from './editor.js';
import { warp } from './warp.js';
import { canvasToJpeg, jpegsToPdf, bytesToBase64 } from './pdf.js';

const $ = (id) => document.getElementById(id);
const cam = $('scan-cam'), pick = $('scan-pick'), target = $('scan-target');
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

async function process(file) {
  if (busy) return;
  busy = true;
  try {
    const img = await loadImage(file);
    const quad = await openEditor(img, detectQuad(img));
    if (!quad) return;
    toast(t('scan.saving'), 60000);
    const out = warp(img, quad);
    const jpeg = await canvasToJpeg(out);
    const pdf = jpegsToPdf([{ jpeg, w: out.width, h: out.height }]);
    const r = await Folder.save({ tree: getFolder().uri, name: stamp(), data: await bytesToBase64(pdf) });
    toast(`${t('scan.saved')} ${r.name ?? ''}`);
    document.dispatchEvent(new Event('libchange'));
  } catch (e) {
    toast(t('scan.fail'));
    console.error(e);
  } finally {
    busy = false;
  }
}

for (const [btn, input] of [[$('scan-shoot'), cam], [$('scan-gallery'), pick]]) {
  btn.addEventListener('click', () => { if (ensureFolder()) input.click(); });
  input.addEventListener('change', () => { const f = input.files[0]; input.value = ''; if (f) process(f); });
}
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', paintTarget);
paintTarget();
