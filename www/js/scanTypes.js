// What the Scan tab shoots: every type has a frame of its REAL size (mm). Everything is saved on an A4 sheet at that real size.
// orient: 'video' = the frame follows the phone's orientation, 'land' / 'port' = always landscape / portrait. sides: 2 = the back is asked for.
// parts: true = the item may be longer than the frame: "Next part" adds a part, the parts stand side by side on the sheet. eg: true = the description lists examples.
import { store } from './storage.js';

export const SCAN_TYPES = {
  a4: { mm: [210, 297], orient: 'video' },
  a5: { mm: [148, 210], orient: 'video' },
  id: { mm: [85.6, 54], orient: 'land', sides: 2, eg: true }, // "Card": ID, driving licence, residence permit, bank card
  card: { mm: [90, 50], orient: 'land' }, // business card
  passport: { mm: [176, 125], orient: 'land' }, // the whole spread: 176 wide, 125 high
  receipt: { mm: [80, 200], orient: 'port', parts: true },
};
export const SCAN_IDS = Object.keys(SCAN_TYPES);

/** Real size of the item [w, h] in mm for a phone held landscape (land) or portrait. */
export const itemMm = (id, land) => { const { mm, orient } = SCAN_TYPES[id]; return orient === 'video' && land ? [mm[1], mm[0]] : [...mm]; };

let cur = store.get('scantype');
if (!SCAN_TYPES[cur]) cur = 'a4';
export const getScanType = () => cur;
export function setScanType(v) { cur = SCAN_TYPES[v] ? v : 'a4'; store.set('scantype', cur); }
