// Small questions over the camera: askChoice (2-3 answers; the FIRST is the safe default: tap outside / System Back = it) and
// askText (a text field with a ready value; tap outside / System Back = null, i.e. cancel).
let el = null, done = null, first = null;
export const isChoiceOpen = () => el !== null;
export function closeChoice(answer = first) { el?.remove(); el = null; const d = done; done = null; d?.(answer); }

/** message, [{id, label}, ...] -> Promise<id> */
export function askChoice(message, options) {
  closeChoice();
  return new Promise((res) => {
    done = res; first = options[0].id;
    el = document.createElement('div');
    el.className = 'sheet';
    const box = document.createElement('div');
    box.className = 'sheet-box';
    const msg = document.createElement('p');
    msg.className = 'sheet-title sheet-msg';
    msg.textContent = message;
    box.append(msg);
    for (const o of options) {
      const b = document.createElement('button');
      b.textContent = o.label;
      b.addEventListener('click', () => closeChoice(o.id));
      box.append(b);
    }
    el.append(box);
    el.addEventListener('click', (e) => { if (e.target === el) closeChoice(); });
    document.body.append(el);
  });
}

/** message, ready value, {ok, cancel} labels -> Promise<string | null> (null = cancelled) */
export function askText(message, value, { ok, cancel }) {
  closeChoice();
  return new Promise((res) => {
    done = res; first = null;
    el = document.createElement('div');
    el.className = 'sheet';
    const box = document.createElement('div');
    box.className = 'sheet-box';
    const msg = document.createElement('p');
    msg.className = 'sheet-title sheet-msg';
    msg.textContent = message;
    const inp = document.createElement('input');
    inp.className = 'sheet-input';
    inp.type = 'text'; inp.value = value; inp.autocomplete = 'off'; inp.spellcheck = false;
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') closeChoice(inp.value); });
    const yes = document.createElement('button'), no = document.createElement('button');
    yes.textContent = ok; no.textContent = cancel;
    yes.addEventListener('click', () => closeChoice(inp.value));
    no.addEventListener('click', () => closeChoice(null));
    box.append(msg, inp, yes, no);
    el.append(box);
    el.addEventListener('click', (e) => { if (e.target === el) closeChoice(null); });
    document.body.append(el);
    inp.focus(); inp.select();
  });
}
