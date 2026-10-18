import { getTheme, setTheme } from './theme.js';
import { getLang, setLang, t } from './i18n.js';
import { Folder, getFolder, setFolder, folderInfo } from './folder.js';
import { getShotDelay, setShotDelay } from './shotdelay.js';
import { getSeriesStr, setSeries } from './camsettings.js';
import { getPdfDpi, setPdfDpi, getPdfSearch, setPdfSearch } from './pdfquality.js';
import { VERSION } from './version.js';
const get = { theme: getTheme, lang: getLang, delay: () => String(getShotDelay()), pdfdpi: getPdfDpi, pdfsearch: getPdfSearch, series: getSeriesStr };
const set = { theme: setTheme, lang: setLang, delay: setShotDelay, pdfdpi: setPdfDpi, pdfsearch: setPdfSearch, series: setSeries };
const segs = [...document.querySelectorAll('.seg[data-setting]')];
const paint = () => segs.forEach((s) => [...s.children].forEach((b) => b.classList.toggle('on', b.dataset.value === get[s.dataset.setting]())));
segs.forEach((s) => s.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  set[s.dataset.setting](b.dataset.value);
  paint();
}));
const folderName = document.getElementById('folder-name');
const folderPath = document.getElementById('folder-path');
const paintFolder = () => {
  const info = folderInfo(getFolder());
  folderName.textContent = info ? info.name : t('set.nofolder');
  folderPath.textContent = info ? info.path : '';
};
document.getElementById('folder-pick').addEventListener('click', () => {
  Folder.pickFolder().then(setFolder).catch((e) => { if (String(e?.message ?? e) !== 'cancelled') alert(t('lib.fail')); });
});
document.addEventListener('folderchange', paintFolder);
document.addEventListener('langchange', paintFolder);
document.getElementById('ver').textContent = VERSION;
paint();
paintFolder();

// three screens: Shooting / PDF and files / App (+ About)
const home = document.getElementById('set-home'), screens = [...document.querySelectorAll('.set-screen')];
let sub = null;
function showSub(name) {
  sub = name;
  home.hidden = !!name;
  screens.forEach((el) => { el.hidden = el.dataset.screen !== name; });
}
home.addEventListener('click', (e) => { const b = e.target.closest('[data-open]'); if (b) showSub(b.dataset.open); });
document.querySelectorAll('[data-back]').forEach((b) => b.addEventListener('click', () => showSub(null)));
export const isSettingsSubOpen = () => !!sub;
export const closeSettingsSub = () => showSub(null);
