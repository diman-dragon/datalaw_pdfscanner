import { getTheme, setTheme } from './theme.js';
import { getLang, setLang } from './i18n.js';
import { VERSION } from './version.js';
const get = { theme: getTheme, lang: getLang };
const set = { theme: setTheme, lang: setLang };
const segs = [...document.querySelectorAll('.seg')];
const paint = () => segs.forEach((s) => [...s.children].forEach((b) => b.classList.toggle('on', b.dataset.value === get[s.dataset.setting]())));
segs.forEach((s) => s.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  set[s.dataset.setting](b.dataset.value);
  paint();
}));
document.getElementById('ver').textContent = VERSION;
paint();
