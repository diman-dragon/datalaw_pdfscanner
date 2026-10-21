import { getTheme, setTheme } from './theme.js';
import { getLang, setLang, t } from './i18n.js';
import { Folder, getFolder, setFolder, folderInfo } from './folder.js';
import { goTo } from './tabs.js';
import { getPdfDpi, setPdfDpi, getPdfSearch, setPdfSearch } from './pdfquality.js';
import { VERSION } from './version.js';
const get = { theme: getTheme, lang: getLang, pdfdpi: getPdfDpi, pdfsearch: getPdfSearch };
const set = { theme: setTheme, lang: setLang, pdfdpi: setPdfDpi, pdfsearch: setPdfSearch };
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

// two screens - PDF and files / App (+ About) - switched by swipe or by the tabs on top
const pager = document.getElementById('set-pager'), track = document.getElementById('set-track'), tabsEl = document.getElementById('set-tabs');
let idx = 0, dxs = 0, sg = null;
function applySet(anim = true) {
  track.style.transition = anim ? '' : 'none';
  track.style.transform = `translateX(calc(${-idx * 100}% + ${dxs}px))`;
  [...tabsEl.children].forEach((b, i) => b.classList.toggle('on', i === idx));
}
tabsEl.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { idx = +b.dataset.i; dxs = 0; applySet(); } });
pager.addEventListener('pointerdown', (e) => { sg = { x: e.clientX, y: e.clientY, t: Date.now(), drag: false, id: e.pointerId }; });
pager.addEventListener('pointermove', (e) => {
  if (!sg) return;
  const dx = e.clientX - sg.x, dy = e.clientY - sg.y;
  if (!sg.drag && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.2) { sg.drag = true; pager.setPointerCapture(sg.id); } // a taps on the buttons stay taps
  if (!sg.drag) return;
  const edge = (dx > 0 && idx === 0) || (dx < 0 && idx === 1);
  dxs = edge ? dx * 0.25 : dx;
  applySet(false);
});
const endSet = (e) => {
  if (!sg) return;
  const s = sg; sg = null;
  if (!s.drag) return;
  const dx = e.clientX - s.x, W = pager.clientWidth, fast = Math.abs(dx / Math.max(1, Date.now() - s.t)) > 0.45;
  if ((Math.abs(dx) > W * 0.2 || fast) && dx < 0 && idx < 1) idx++;
  else if ((Math.abs(dx) > W * 0.2 || fast) && dx > 0) { if (idx > 0) idx--; else goTo('scan'); } // past the first screen: the previous tab
  dxs = 0; applySet();
};
pager.addEventListener('pointerup', endSet);
pager.addEventListener('pointercancel', endSet);
applySet(false);
