import { getTheme, setTheme } from './theme.js';
import { getLang, setLang, t } from './i18n.js';
import { Folder, getFolder, setFolder } from './folder.js';
import { VERSION } from './version.js';
const get = { theme: getTheme, lang: getLang };
const set = { theme: setTheme, lang: setLang };
const segs = [...document.querySelectorAll('.seg[data-setting]')];
const paint = () => segs.forEach((s) => [...s.children].forEach((b) => b.classList.toggle('on', b.dataset.value === get[s.dataset.setting]())));
segs.forEach((s) => s.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  set[s.dataset.setting](b.dataset.value);
  paint();
}));
const folderName = document.getElementById('folder-name');
const paintFolder = () => { folderName.textContent = getFolder()?.name || t('set.nofolder'); };
document.getElementById('folder-pick').addEventListener('click', () => {
  Folder.pickFolder().then(setFolder).catch(() => {});
});
document.addEventListener('folderchange', paintFolder);
document.addEventListener('langchange', paintFolder);
document.getElementById('ver').textContent = VERSION;
paint();
paintFolder();
