// Native Folder plugin (Android SAF) with an in-memory mock for browser dev.
import { store } from './storage.js';
import { t } from './i18n.js';

let mockFiles = [
  { uri: 'mock://1', name: 'Договор.pdf', size: 245000, modified: Date.now() - 86400000 },
  { uri: 'mock://2', name: 'Invoice 2026-10.pdf', size: 1820000, modified: Date.now() - 3600000 },
  { uri: 'mock://3', name: 'Паспорт скан.pdf', size: 530000, modified: Date.now() - 864000000 },
];
const mock = {
  async pickFolder() { return { uri: 'mock://folder', name: 'Demo' }; },
  async list() { return { files: mockFiles.slice() }; },
  async preview({ width }) {
    const c = document.createElement('canvas'); c.width = width; c.height = Math.round(width * 1.414);
    const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height);
    x.fillStyle = '#bbb'; for (let i = 0; i < 14; i++) x.fillRect(width * .1, width * (.18 + i * .09), width * (i % 4 === 3 ? .5 : .8), width * .03);
    return { data: c.toDataURL('image/jpeg', 0.8), pages: 1 + (c.width % 7) + Math.floor(Math.random() * 6) };
  },
  async add() {
    mockFiles.push({ uri: 'mock://' + Date.now(), name: 'Новый файл.pdf', size: 100000, modified: Date.now() });
    return { added: 1 };
  },
  _streams: {},
  async saveOpen({ name }) { const id = String(Date.now()); this._streams[id] = { name, size: 0 }; return { id }; },
  async saveWrite({ id, data }) { this._streams[id].size += data.length * 0.75; },
  async saveClose({ id }) {
    const st = this._streams[id]; delete this._streams[id];
    mockFiles.push({ uri: 'mock://' + id, name: st.name + '.pdf', size: st.size, modified: Date.now() });
    return { uri: 'mock://' + id, name: st.name + '.pdf' };
  },
  async saveAbort({ id }) { delete this._streams[id]; },
  async replaceWith({ uri, from }) { const src = mockFiles.find((f) => f.uri === from), dst = mockFiles.find((f) => f.uri === uri); if (!dst) throw new Error('Missing file'); if (src) { dst.size = src.size; dst.modified = Date.now(); } mockFiles = mockFiles.filter((f) => f.uri !== from); return { uri }; },
  async remove({ uri }) { mockFiles = mockFiles.filter((f) => f.uri !== uri); },
  async rename({ uri, name }) { mockFiles.forEach((f) => { if (f.uri === uri) f.name = name; }); return { uri }; },
  async open() { alert('open (browser mock)'); },
  async viewOpen() { return { id: 'mock', ratios: [1.414, 1.414, 1.414] }; },
  async viewPage({ index, width }) {
    const c = document.createElement('canvas'); c.width = width; c.height = Math.round(width * 1.414);
    const x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height);
    x.fillStyle = '#888'; x.font = `${width / 5}px sans-serif`; x.textAlign = 'center'; x.fillText(String(index + 1), width / 2, c.height / 2);
    return { data: c.toDataURL('image/jpeg', 0.8) };
  },
  async viewClose() {},
};

export const Folder = window.Capacitor?.Plugins?.Folder ?? mock;

export function getFolder() {
  try { return JSON.parse(store.get('folder')); } catch { return null; }
}
export function setFolder(f) {
  store.set('folder', JSON.stringify(f));
  document.dispatchEvent(new Event('folderchange'));
}

// Human-readable name + path from the SAF tree URI (works even if the provider gave no display name).
export function folderInfo(f) {
  if (!f) return null;
  let id = '';
  try { id = decodeURIComponent((f.uri.split('/tree/')[1] || '').split('/document/')[0]); } catch { id = f.uri; }
  const i = id.indexOf(':');
  const vol = i < 0 ? '' : id.slice(0, i);
  const rest = i < 0 ? id : id.slice(i + 1);
  const sd = /^[0-9a-f]{4}-[0-9a-f]{4}$/i.test(vol); // removable card volume id looks like 1A2B-3C4D
  const volLabel = vol === 'primary' || vol === '' ? t('set.internal') : sd ? t('set.sd') : vol;
  const path = rest ? (sd ? `${volLabel}\\${rest.split('/').join('\\')}` : `${volLabel}/${rest}`) : volLabel;
  const name = f.name || rest.split('/').filter(Boolean).pop() || volLabel;
  return { name, path };
}
