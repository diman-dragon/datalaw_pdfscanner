// Camera capture mode: "frame" = the exact video frame at the moment of the press (instant, what you see is what you get);
// "sensor" = ImageCapture.takePhoto at the full sensor size (sharper but slower, the scene may move meanwhile).
import { store } from './storage.js';
let cur = store.get('shotmode') === 'sensor' ? 'sensor' : 'frame';
export const getShotMode = () => cur;
export function setShotMode(v) { cur = v === 'sensor' ? 'sensor' : 'frame'; store.set('shotmode', cur); }
