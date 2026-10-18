// Quick scan tab: one page. Camera (frame, original photo) or gallery -> result (filter, rotation, edges) -> name -> PDF.
import { getFolder, folderInfo } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { goTo } from '../tabs.js';
import { openCamera } from './camera.js';
import { loadImage } from './load.js';
import { openResult } from './result.js';
import { pdfStream } from './pdf.js';
import { MAXSIDE, quadFor, normQuad, finalOf } from './shot.js';
import { defaultLook } from './enhance.js';
import { ensureFolder, savePdf, askName } from './save.js';

const $ = (id) => document.getElementById(id);
const target = $('quick-target'), fallback = $('quick-cam'), gallery = $('quick-pick');
let busy = false;

function paintTarget() {
  const info = folderInfo(getFolder());
  target.textContent = info ? `${t('scan.to')} ${info.path}` : t('scan.nofolder.label');
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

/** shot: {blob, quadN?, vr?}; fromCamera enables "Retake". */
async function run(shot, fromCamera) {
  busy = true;
  try {
    let cur = shot, state = { look: defaultLook('auto'), turns: 0 };
    for (;;) {
      const img = await loadImage(cur.blob, MAXSIDE); // the original: the frame is set on it, the PDF is made from it
      const p = { qn: cur.quadN ?? null, vr: cur.vr };
      const out = await openResult(img, quadFor(img, p), { ...state, retake: fromCamera, alts: cur.alts, pick: cur.pick ?? 0, best: cur.best ?? cur.pick ?? 0 });
      if (out === 'retake') {
        cur = await openCamera();
        if (!cur) return;
        state = { look: state.look, turns: 0 };
        continue;
      }
      if (!out) return;
      if (cur.alts && out.pick != null) cur = { ...cur, blob: cur.alts[out.pick].blob, pick: out.pick }; // the chosen picture of the series
      const job = finalOf({ blob: cur.blob, qn: normQuad(out.quad, img), vr: img.width / img.height, look: out.look, turns: out.turns }); // the page is made while the name is typed
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
  } catch (e) {
    toast(`${t('scan.fail')}: ${e?.message ?? e}`, 6000);
    console.error(e);
  } finally {
    busy = false;
  }
}

$('quick-shoot').addEventListener('click', async () => {
  if (busy || !(await ensureFolder())) return;
  try {
    const shot = await openCamera();
    if (shot) run(shot, true);
  } catch (e) {
    console.error(e);
    toast(t('cam.denied'));
    fallback.click(); // system camera as a fallback
  }
});
$('quick-gallery').addEventListener('click', async () => { if (busy) return; if (getFolder()) gallery.click(); else await ensureFolder(); }); // the file chooser needs the tap itself, so after choosing a folder tap again
for (const input of [fallback, gallery]) {
  input.addEventListener('change', () => { const f = input.files[0]; input.value = ''; if (f) run({ blob: f }, false); });
}
document.addEventListener('folderchange', paintTarget);
document.addEventListener('langchange', paintTarget);
paintTarget();
