// Popup menu anchored to a button (the three dots). One menu at a time.
let el = null;
export const isMenuOpen = () => el !== null;
export function closeMenu() { el?.remove(); el = null; }

// actions: [{ label, icon (svg markup), danger, run }]
export function openMenu(anchor, actions) {
  closeMenu();
  el = document.createElement('div');
  el.className = 'pop';
  const box = document.createElement('div');
  box.className = 'pop-box';
  for (const a of actions) {
    const b = document.createElement('button');
    if (a.danger) b.className = 'danger';
    const ic = document.createElement('span');
    ic.className = 'pop-ic';
    ic.innerHTML = a.icon ?? '';
    const tx = document.createElement('span');
    tx.textContent = a.label;
    b.append(ic, tx);
    b.addEventListener('click', (e) => { e.stopPropagation(); closeMenu(); a.run?.(); });
    box.append(b);
  }
  el.append(box);
  el.addEventListener('click', closeMenu);
  document.body.append(el);
  // place under the button, right edges aligned; flip above when there is no room below
  const r = anchor.getBoundingClientRect(), bw = box.offsetWidth, bh = box.offsetHeight, m = 8;
  const left = Math.min(Math.max(m, r.right - bw), window.innerWidth - bw - m);
  const below = r.bottom + 4 + bh <= window.innerHeight - m;
  box.style.left = left + 'px';
  box.style.top = (below ? r.bottom + 4 : Math.max(m, r.top - 4 - bh)) + 'px';
}
