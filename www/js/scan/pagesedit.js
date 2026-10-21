// The single-scan editor over a list of pages: edges / filter / rotation of each page, "Back / Next",
// the caption "Page i of n" at the bottom, optional page deletion. Used by the Scan tab, the Library editor and the viewer.
import { t } from '../i18n.js';
import { loadImage } from './load.js';
import { openResult, closeResult } from './result.js';
import { MAXSIDE, quadFor, normQuad, isFull } from './shot.js';
import { defaultLook, lookIsPlain } from './enhance.js';

/**
 * pages: [{blob, qn, vr, filter, turns, orig?}] edited in place (blob must be loaded: await opts.ensure(page) is called before a page opens).
 * opts: start, single (one page only), lastKey (label of the last button), del (allow deleting), ensure(page), onChange(page), onDelete().
 * Resolves 'done' (last page confirmed) or 'cancel'.
 */
export async function editPages(pages, opts = {}) {
  let i = opts.start ?? 0, result = 'cancel';
  try {
    while (pages.length && i >= 0 && i < pages.length) {
      const p = pages[i], last = opts.single || i === pages.length - 1;
      await opts.ensure?.(p);
      const img = await loadImage(p.blob, MAXSIDE);
      const out = await openResult(img, quadFor(img, p), {
        look: p.look ?? defaultLook('color'), looks: opts.looks, turns: p.turns ?? 0, stay: true,
        saveKey: last ? (opts.lastKey ?? 'ms.apply') : 'rs.next',
        caption: opts.single || pages.length === 1 ? '' : `${t(opts.word ?? 'sc.page')} ${i + 1} ${t('rs.of')} ${pages.length}`,
        back: !opts.single && i > 0, del: !!opts.del && pages.length > 1, swipeNext: !last,
      });
      if (!out) return 'cancel';
      if (out === 'delete') {
        pages.splice(i, 1);
        opts.onDelete?.();
        if (opts.single) { result = 'done'; break; }
        if (i >= pages.length) i = pages.length - 1;
        continue;
      }
      const qn = normQuad(out.quad, img);
      const unchanged = lookIsPlain(out.look) && !out.turns && isFull(qn);
      Object.assign(p, { qn, vr: img.width / img.height, look: out.look, turns: out.turns, touched: true, job: null, keep: !!p.orig && unchanged });
      opts.onChange?.(p);
      if (out.back) { i -= 1; continue; }
      if (last) { result = 'done'; break; }
      i += 1;
      if (!pages[i].touched) pages[i].look = { ...out.look }; // the next page starts with the same look
    }
    if (!pages.length) result = 'cancel';
  } finally {
    closeResult(); // the screen was kept open between pages
  }
  return result;
}
