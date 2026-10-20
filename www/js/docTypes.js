// What is being scanned: the type decides the camera frame, how many sides are shot, the physical size and the layout in the PDF.
import { store } from './storage.js';

// Frame presets of the camera. orient: 'video' = follow the phone's orientation, 'land' / 'port' = always landscape / portrait.
export const FRAMES = {
  auto: {},
  a4: { ratio: 210 / 297, orient: 'video' },
  card: { ratio: 85.6 / 54, orient: 'land' },
  passport: { ratio: 250 / 176, orient: 'land' },
  receipt: { ratio: 80 / 200, orient: 'port' },
  free: {},
};
export const FRAME_IDS = Object.keys(FRAMES);

// mm = physical size (the page is straightened to exactly this proportion); layout 'sheet' = real size on an A4 sheet
export const DOC_TYPES = {
  doc: { sides: 1, frame: 'auto', looks: ['color', 'auto'] },
  card2: { sides: 2, frame: 'card', mm: [85.6, 54], layout: 'sheet', looks: ['color', 'enhance'] },
  card1: { sides: 1, frame: 'card', mm: [90, 50], layout: 'sheet', looks: ['color', 'enhance'] },
  passport: { sides: 1, frame: 'passport', mm: [250, 176], layout: 'page', looks: ['color', 'enhance'] },
  receipt: { sides: 1, frame: 'receipt', layout: 'page', looks: ['color', 'auto'] },
};
export const TYPE_IDS = Object.keys(DOC_TYPES);
let cur = store.get('doctype');
if (!DOC_TYPES[cur]) cur = 'doc';
export const getDocType = () => cur;
export function setDocType(v) { cur = DOC_TYPES[v] ? v : 'doc'; store.set('doctype', cur); }
