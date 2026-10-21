// Flash mode of the cameras: automatic (the light comes on only when the scene is dark), always on, off. Remembered.
import { store } from './storage.js';
const MODES = ['auto', 'on', 'off'];
let cur = store.get('flash');
if (!MODES.includes(cur)) cur = 'auto';
export const getFlash = () => cur;
export function nextFlash() { cur = MODES[(MODES.indexOf(cur) + 1) % MODES.length]; store.set('flash', cur); return cur; }
export const BOLT = '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true"><path d="M13 2L5 14h5l-1 8 8-12h-5z"/></svg>';
/** Is the scene dark? Mean brightness of a tiny frame of the preview. */
export function isDark(video) {
  const c = document.createElement('canvas'); c.width = 32; c.height = 24;
  const x = c.getContext('2d', { willReadFrequently: true });
  x.drawImage(video, 0, 0, 32, 24);
  const d = x.getImageData(0, 0, 32, 24).data;
  let s = 0;
  for (let i = 0; i < d.length; i += 4) s += d[i] * 0.3 + d[i + 1] * 0.59 + d[i + 2] * 0.11;
  return s / (d.length / 4) < 55;
}
