import { store } from './storage.js';
let cur = store.get('theme') ?? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
export const getTheme = () => cur;
export const applyTheme = () => { document.documentElement.dataset.theme = cur; };
export function setTheme(v) { cur = v; store.set('theme', v); applyTheme(); }
