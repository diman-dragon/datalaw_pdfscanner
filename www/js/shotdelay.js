// Self-timer delay (seconds) for the camera shutter. Setting: 3 | 5 | 10.
import { store } from './storage.js';
const OPTIONS = [3, 5, 10];
let cur = Number(store.get('delay'));
if (!OPTIONS.includes(cur)) cur = 5;
export const getShotDelay = () => cur;
export function setShotDelay(v) {
  cur = Number(v);
  store.set('delay', String(cur));
  document.dispatchEvent(new Event('delaychange'));
}
