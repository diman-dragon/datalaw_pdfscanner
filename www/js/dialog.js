// Dialogs in the app's own style (instead of the system confirm / prompt / alert). One dialog at a time.
// Tap outside / System Back = the "safe" answer (the first option of askChoice, false, null).
import { t } from './i18n.js';
let el = null, done = null, safe = null;
export const isDialogOpen = () => el !== null;
export function closeDialog(answer = safe) { el?.remove(); el = null; const d = done; done = null; d?.(answer); }

function show(safeAnswer, build) {
  closeDialog();
  return new Promise((res) => {
    done = res; safe = safeAnswer;
    el = document.createElement('div');
    el.className = 'dlg';
    const box = document.createElement('div');
    box.className = 'dlg-box';
    build(box);
    el.append(box);
    el.addEventListener('click', (e) => { if (e.target === el) closeDialog(); });
    document.body.append(el);
  });
}
const text = (cls, s) => { const p = document.createElement('p'); p.className = cls; p.textContent = s; return p; };
function button(label, kind, onClick) {
  const b = document.createElement('button');
  b.className = 'dlg-btn' + (kind ? ' ' + kind : '');
  b.textContent = label;
  b.addEventListener('click', onClick);
  return b;
}
function buttons(row, list) {
  const d = document.createElement('div');
  d.className = 'dlg-btns' + (row ? ' dlg-row' : '');
  d.append(...list);
  return d;
}

/** message, [{id, label, danger?}, ...] -> Promise<id>. The first option is the main (filled) one and the answer of a tap outside. */
export function askChoice(message, options) {
  return show(options[0].id, (box) => {
    box.append(text('dlg-msg', message), buttons(false, options.map((o, i) => button(o.label, o.danger ? 'danger' : i === 0 ? 'pri' : '', () => closeDialog(o.id)))));
  });
}

/** message, {ok, cancel, detail?, danger?} -> Promise<boolean> */
export function askConfirm(message, { ok = t('dlg.ok'), cancel = t('dlg.cancel'), detail = '', danger = false } = {}) {
  return show(false, (box) => {
    box.append(text('dlg-msg', message));
    if (detail) box.append(text('dlg-detail', detail));
    box.append(buttons(true, [button(cancel, '', () => closeDialog(false)), button(ok, danger ? 'danger' : 'pri', () => closeDialog(true))]));
  });
}

/** message, ready value, {ok, cancel} -> Promise<string | null> (null = cancelled) */
export function askText(message, value, { ok = t('dlg.ok'), cancel = t('dlg.cancel') } = {}) {
  return show(null, (box) => {
    const inp = document.createElement('input');
    inp.className = 'dlg-input';
    inp.type = 'text'; inp.value = value; inp.autocomplete = 'off'; inp.spellcheck = false;
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') closeDialog(inp.value); });
    box.append(text('dlg-msg', message), inp, buttons(true, [button(cancel, '', () => closeDialog(null)), button(ok, 'pri', () => closeDialog(inp.value))]));
    setTimeout(() => { inp.focus(); inp.select(); }, 0);
  });
}

/** A notice with one button. */
export function askAlert(message, ok = t('dlg.ok')) {
  return show(undefined, (box) => { box.append(text('dlg-msg', message), buttons(false, [button(ok, 'pri', () => closeDialog())])); });
}
