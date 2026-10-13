// Native Folder plugin (Android SAF) with an in-memory mock for browser dev.
import { store } from './storage.js';

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
