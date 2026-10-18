// Shared by the Quick and Scan tabs: folder guard, file name, chunked PDF save.
import { Folder, getFolder, setFolder } from '../folder.js';
import { t } from '../i18n.js';
import { toast } from '../toast.js';
import { bytesToBase64 } from './pdf.js';

/** true if a folder is selected; otherwise explains, opens the folder picker (it has a "New folder" button) and waits for the choice. */
export async function ensureFolder() {
  if (getFolder()) return true;
  if (!confirm(t('scan.nofolder'))) return false;
  try {
    const f = await Folder.pickFolder();
    setFolder(f);
    toast(t('scan.folderset'));
    return true;
  } catch (e) {
    if (String(e?.message ?? e) !== 'cancelled') alert(t('lib.fail'));
    return false;
  }
}

export const stamp = () => {
  const d = new Date(), p = (n) => String(n).padStart(2, '0');
  return `${t('scan.prefix')}_${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}_${p(d.getHours())}-${p(d.getMinutes())}-${p(d.getSeconds())}`;
};

const clean = (s) => s.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim().replace(/\.pdf$/i, '').trim();

/** Asks for the file name (suggests `def`, the time stamp by default). null = user cancelled. */
export function askName(def = stamp()) {
  const n = prompt(t('scan.nameq'), def);
  return n === null ? null : clean(n) || stamp();
}

const CHUNK = 1500000;

// If the folder we save to is gone (deleted, moved, card removed) the user picks another one and the save goes on.
async function openOut(name) {
  try {
    return await Folder.saveOpen({ tree: getFolder().uri, name });
  } catch (e) {
    if (!/folder_missing|Missing file/i.test(String(e?.message ?? e)) || !confirm(t('save.folderlost'))) throw e;
    const f = await Folder.pickFolder();
    setFolder(f);
    return Folder.saveOpen({ tree: f.uri, name });
  }
}

/** Writes a PDF (async iterable of Uint8Array parts) into the selected folder in ~1.5 MB chunks. Returns {uri, name}. */
export async function savePdf(parts, name = stamp()) {
  const { id } = await openOut(name);
  let buf = [], size = 0;
  const flush = async () => {
    if (!size) return;
    const u = new Uint8Array(size);
    let o = 0;
    for (const p of buf) { u.set(p, o); o += p.length; }
    buf = []; size = 0;
    await Folder.saveWrite({ id, data: await bytesToBase64(u) });
  };
  try {
    for await (const part of parts) {
      for (let o = 0; o < part.length; o += CHUNK) {
        const piece = part.subarray(o, Math.min(part.length, o + CHUNK));
        buf.push(piece); size += piece.length;
        if (size >= CHUNK) await flush();
      }
    }
    await flush();
    return await Folder.saveClose({ id });
  } catch (e) {
    await Folder.saveAbort({ id }).catch(() => {});
    throw e;
  }
}
