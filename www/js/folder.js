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
  async preview() { throw new Error('no preview in browser'); },
  async add() {
    mockFiles.push({ uri: 'mock://' + Date.now(), name: 'Новый файл.pdf', size: 100000, modified: Date.now() });
    return { added: 1 };
  },
  async save({ name }) {
    mockFiles.push({ uri: 'mock://' + Date.now(), name: name + '.pdf', size: 300000, modified: Date.now() });
    return { uri: 'mock://x', name: name + '.pdf' };
  },
  async remove({ uri }) { mockFiles = mockFiles.filter((f) => f.uri !== uri); },
  async rename({ uri, name }) { mockFiles.forEach((f) => { if (f.uri === uri) f.name = name; }); return { uri }; },
  async open() { alert('open (browser mock)'); },
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
  const volLabel = vol === 'primary' || vol === '' ? t('set.internal') : vol;
  const path = rest ? `${volLabel}/${rest}` : volLabel;
  const name = f.name || rest.split('/').filter(Boolean).pop() || volLabel;
  return { name, path };
}
