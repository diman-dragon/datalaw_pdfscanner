// "The shot is soft" question of the quick scan: retake or save as it is. Tap outside / System Back = retake.
import { t } from '../i18n.js';

let el = null, done = null;
export const isBlurOpen = () => el !== null;
export function closeBlur(answer = 'retake') { el?.remove(); el = null; done?.(answer); done = null; }

/** -> Promise<'retake' | 'save'> */
export function askBlur() {
  closeBlur();
  return new Promise((res) => {
    done = res;
    el = document.createElement('div');
    el.className = 'sheet';
    const box = document.createElement('div');
    box.className = 'sheet-box';
    const msg = document.createElement('p');
    msg.className = 'sheet-title sheet-msg';
    msg.textContent = t('qs.blur');
    box.append(msg);
    for (const [id, key] of [['retake', 'qs.blur.retake'], ['save', 'qs.blur.save']]) {
      const b = document.createElement('button');
      b.textContent = t(key);
      b.addEventListener('click', () => closeBlur(id));
      box.append(b);
    }
    el.append(box);
    el.addEventListener('click', (e) => { if (e.target === el) closeBlur('retake'); });
    document.body.append(el);
  });
}
