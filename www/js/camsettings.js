// Camera settings: the frame mode.
import { store } from './storage.js';
import { FRAME_IDS } from './docTypes.js';
let fm = store.get('camframe');
if (!FRAME_IDS.includes(fm)) fm = 'auto';
export const getFrameMode = () => fm;
export function setFrameMode(v) { fm = FRAME_IDS.includes(v) ? v : 'auto'; store.set('camframe', fm); }
