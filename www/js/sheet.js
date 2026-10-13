// Bottom action sheet.
import { t } from './i18n.js';
let el = null;
export const isSheetOpen = () => el !== null;
export function closeSheet() { el?.remove(); el = null; }
export function openSheet(title, actions) {
  closeSheet();
  el = document.createElement('div');
  el.className = 'sheet';
  const box = document.createElement('div');
  box.className = 'sheet-box';
  const h = document.createElement('p');
  h.className = 'sheet-title';
  h.textContent = title;
  box.append(h);
  for (const a of [...actions, { label: t('lib.cancel') }]) {
    const b = document.createElement('button');
    b.textContent = a.label;
    if (a.danger) b.className = 'danger';
    b.addEventListener('click', () => { closeSheet(); a.run?.(); });
    box.append(b);
  }
  el.append(box);
  el.addEventListener('click', (e) => { if (e.target === el) closeSheet(); });
  document.body.append(el);
}
