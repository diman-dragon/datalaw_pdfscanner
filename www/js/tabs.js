import { t } from './i18n.js';
export const TABS = ['library', 'quick', 'scan', 'settings'];
const pager = document.getElementById('pager');
const btns = [...document.querySelectorAll('#tabbar button')];
const title = document.getElementById('title');
let current = 'library';
let locked = false, timer;
export const getTab = () => current;
function mark(name) {
  current = name;
  btns.forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
  title.textContent = t('tab.' + name);
}
export function goTo(name, smooth = true) {
  mark(name);
  locked = true; clearTimeout(timer); timer = setTimeout(() => { locked = false; }, 500);
  pager.scrollTo({ left: TABS.indexOf(name) * pager.clientWidth, behavior: smooth ? 'smooth' : 'auto' });
}
pager.addEventListener('scroll', () => {
  if (locked) return;
  const n = TABS[Math.round(pager.scrollLeft / pager.clientWidth)];
  if (n && n !== current) mark(n);
}, { passive: true });
btns.forEach((b) => b.addEventListener('click', () => goTo(b.dataset.tab)));
document.addEventListener('langchange', () => mark(current));
