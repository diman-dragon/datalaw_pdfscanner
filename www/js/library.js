import { Folder, getFolder } from './folder.js';
import { t, resolve } from './i18n.js';
import { store } from './storage.js';
import { goTo } from './tabs.js';
import { openSheet } from './sheet.js';
import { loadPreview } from './previews.js';
import { openViewer, openExternal } from './viewer.js';
import { openDocEdit } from './docedit.js';

const $ = (id) => document.getElementById(id);
const bar = $('lib-bar'), items = $('lib-items');
const empty = $('lib-empty'), eTitle = $('lib-empty-title'), eHint = $('lib-empty-hint'), eBtn = $('lib-choose');
const viewSeg = $('lib-view');

let files = [];
let state = 'nofolder'; // nofolder | error | ok
let view = store.get('libview') ?? 'list';

const observer = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    observer.unobserve(e.target);
    const pv = e.target.querySelector('.pv');
    pv.onload = () => pv.classList.add('ready');
    loadPreview(e.target._file, pv);
  }
}, { rootMargin: '200px' });

const fmtSize = (b) => (b < 1048576 ? Math.max(1, Math.round(b / 1024)) + ' KB' : (b / 1048576).toFixed(1) + ' MB');
const fmtDate = (ms) => new Date(ms).toLocaleDateString(resolve());

function card(f) {
  const el = document.createElement('div');
  el._file = f;
  el.className = 'item';
  el.innerHTML = '<div class="thumb"><img class="ph" alt="" src="img/emoji/1f4c4.svg"><img class="pv" alt=""></div>'
    + '<div class="meta"><div class="fname"></div><div class="fsub"></div></div>'
    + '<button class="more">⋮</button>';
  el.querySelector('.fname').textContent = f.name;
  el.querySelector('.fsub').textContent = `${fmtSize(f.size)} · ${fmtDate(f.modified)}`;
  const more = el.querySelector('.more');
  more.setAttribute('aria-label', t('lib.menu'));
  more.addEventListener('click', (e) => { e.stopPropagation(); menu(f); });
  el.addEventListener('click', () => openViewer(f));
  observer.observe(el);
  return el;
}

function paintView() {
  items.className = view;
  [...viewSeg.children].forEach((b) => b.classList.toggle('on', b.dataset.value === view));
}

function render() {
  const folder = getFolder();
  const ok = state === 'ok';
  bar.hidden = !folder || state === 'error';
  items.hidden = !(ok && files.length);
  empty.hidden = ok && files.length > 0;
  eBtn.hidden = ok;
  if (state === 'nofolder') { eTitle.textContent = t('lib.nofolder'); eHint.textContent = t('lib.nofolder.hint'); }
  else if (state === 'error') { eTitle.textContent = t('lib.error'); eHint.textContent = t('lib.error.hint'); }
  else { eTitle.textContent = t('lib.empty'); eHint.textContent = t('lib.empty.hint'); }
  paintView();
  items.replaceChildren(...(ok ? files.map(card) : []));
}

export async function refresh() {
  const folder = getFolder();
  if (!folder) { state = 'nofolder'; files = []; return render(); }
  try {
    const r = await Folder.list({ tree: folder.uri });
    files = r.files.sort((a, b) => b.modified - a.modified);
    state = 'ok';
  } catch { state = 'error'; files = []; }
  render();
}

const fail = (e) => { if (String(e?.message ?? e) !== 'cancelled') alert(t('lib.fail')); };

async function rename(f) {
  const base = f.name.replace(/\.pdf$/i, '');
  const n = prompt(t('lib.renameq'), base)?.trim();
  if (!n || n === base) return;
  try { await Folder.rename({ uri: f.uri, name: n + '.pdf' }); } catch (e) { fail(e); }
  refresh();
}
async function remove(f) {
  if (!confirm(`${t('lib.delq')}\n${f.name}`)) return;
  try { await Folder.remove({ uri: f.uri }); } catch (e) { fail(e); }
  refresh();
}
function menu(f) {
  openSheet(f.name, [
    { label: t('lib.open'), run: () => openViewer(f) },
    { label: t('lib.edit'), run: () => openDocEdit(f) },
    { label: t('lib.openext'), run: () => openExternal(f) },
    { label: t('lib.rename'), run: () => rename(f) },
    { label: t('lib.delete'), danger: true, run: () => remove(f) },
  ]);
}

$('lib-add').addEventListener('click', async () => {
  try { await Folder.add({ tree: getFolder().uri }); } catch (e) { fail(e); }
  refresh();
});
eBtn.addEventListener('click', () => goTo('settings'));
viewSeg.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  view = b.dataset.value; store.set('libview', view); paintView();
});
document.addEventListener('folderchange', refresh);
document.addEventListener('libchange', refresh);
document.addEventListener('langchange', render);
window.Capacitor?.Plugins?.App?.addListener('appStateChange', (s) => { if (s.isActive) refresh(); });
refresh();
