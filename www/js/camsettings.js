// Camera settings: how many frames a shot takes (the best one is chosen), and the frame mode.
import { store } from './storage.js';
let series = Number(store.get('series'));
if (![1, 3, 5].includes(series)) series = 3;
export const getSeries = () => series;
export const getSeriesStr = () => String(series);
export function setSeries(v) { series = Number(v); store.set('series', String(series)); }

export const FRAME_RATIOS = { a4p: 210 / 297, a4l: 297 / 210, card: 85.6 / 54, receipt: 80 / 200 }; // width / height
export const FRAME_MODES = ['auto', 'a4p', 'a4l', 'card', 'receipt', 'free'];
let fm = store.get('camframe');
if (!FRAME_MODES.includes(fm)) fm = 'auto';
export const getFrameMode = () => fm;
export function setFrameMode(v) { fm = FRAME_MODES.includes(v) ? v : 'auto'; store.set('camframe', fm); }
