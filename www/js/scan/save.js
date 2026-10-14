// Shared by the Quick and Scan tabs: folder guard, file name, chunked PDF save.
import { Folder, getFolder, setFolder } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { bytesToBase64 } from './pdf.js';

/** true if a folder is selected; otherwise offers to choose one and returns false. */
export function ensureFolder() {
  if (getFolder()) return true;
  if (confirm(t('scan.nofolder'))) {
    Folder.pickFolder().then((f) => { setFolder(f); toast(t('scan.folderset')); })
      .catch((e) => { if (String(e?.message ?? e) !== 'cancelled') alert(t('lib.fail')); });
  }
  return false;
}

export const stamp = () => {
  const d = new Date(), p = (n) => String(n).padStart(2, '0');
  return `${t('scan.prefix')}_${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}_${p(d.getHours())}-${p(d.getMinutes())}-${p(d.getSeconds())}`;
};

const clean = (s) => s.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim().replace(/\.pdf$/i, '').trim();

/** Asks for the file name (suggests the time stamp). null = user cancelled. */
export function askName() {
  const n = prompt(t('scan.nameq'), stamp());
  return n === null ? null : clean(n) || stamp();
}

const CHUNK = 1500000;
/** Writes PDF bytes into the selected folder. Returns {uri, name}. */
export async function savePdf(bytes, name = stamp()) {
  const { id } = await Folder.saveOpen({ tree: getFolder().uri, name });
  try {
    for (let o = 0; o < bytes.length; o += CHUNK) {
      await Folder.saveWrite({ id, data: await bytesToBase64(bytes.subarray(o, o + CHUNK)) });
    }
    return await Folder.saveClose({ id });
  } catch (e) {
    await Folder.saveAbort({ id }).catch(() => {});
    throw e;
  }
}
