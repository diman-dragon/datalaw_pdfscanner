// Camera focus mode (Settings): 'smart' = the app finds the focus from the picture and locks it (autofocus.js); 'system' = the phone's own autofocus.
import { store } from './storage.js';
const OPTIONS = ['smart', 'system'];
let cur = store.get('focusmode');
if (!OPTIONS.includes(cur)) cur = 'smart';
export const getFocusMode = () => cur;
export function setFocusMode(v) { cur = OPTIONS.includes(v) ? v : 'smart'; store.set('focusmode', cur); }
