let el, timer;
export function toast(text, ms = 3000) {
  if (!el) { el = document.createElement('div'); el.className = 'toast'; document.body.append(el); }
  el.textContent = text; el.classList.add('show');
  clearTimeout(timer); timer = setTimeout(() => el.classList.remove('show'), ms);
}
