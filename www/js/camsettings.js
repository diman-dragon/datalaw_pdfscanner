// Camera settings: how many frames a shot takes (the best one is chosen), and the frame mode.
import { store } from './storage.js';
let series = Number(store.get('series'));
if (![1, 3, 5].includes(series)) series = 3;
export const getSeries = () => series;
export const getSeriesStr = () => String(series);
export function setSeries(v) { series = Number(v); store.set('series', String(series)); }

import { FRAME_IDS } from './docTypes.js';
let fm = store.get('camframe');
if (!FRAME_IDS.includes(fm)) fm = 'auto';
export const getFrameMode = () => fm;
export function setFrameMode(v) { fm = FRAME_IDS.includes(v) ? v : 'auto'; store.set('camframe', fm); }
