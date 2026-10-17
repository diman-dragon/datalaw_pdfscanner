// PDF quality (Settings): the resolution of a saved page and its size budget.
// Pixels are never invented: a page is never enlarged beyond what the camera actually captured.
import { store } from './storage.js';
const OPTIONS = [150, 300, 600];
let cur = Number(store.get('pdfdpi'));
if (!OPTIONS.includes(cur)) cur = 150;
export const getPdfDpi = () => String(cur);
export function setPdfDpi(v) { cur = Number(v); store.set('pdfdpi', String(cur)); }

// side = long side of a page in px (A4: 11.69 in; 600 dpi would be 7000 px, capped for phone memory), budget = bytes per page
const TARGETS = { 150: { side: 1754, budget: 300000 }, 300: { side: 3508, budget: 800000 }, 600: { side: 4800, budget: 2200000 } };
export const pdfTarget = () => TARGETS[cur];
