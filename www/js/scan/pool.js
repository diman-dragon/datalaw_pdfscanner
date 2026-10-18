// A small pool of render workers (falls back to the main thread if workers are not available).
const N = Math.max(1, Math.min(2, (navigator.hardwareConcurrency || 4) - 1));
const workers = [], idle = [], queue = [], pending = new Map();
let seq = 0, inited = false, broken = false;

async function onMain(job) { return (await import('./render.js')).renderPage(job); }

function pump() {
  while (idle.length && queue.length) {
    const w = idle.pop(), it = queue.shift();
    w._id = it.id;
    w.postMessage({ id: it.id, job: it.job });
  }
}
function init() {
  if (inited) return;
  inited = true;
  try {
    for (let i = 0; i < N; i++) {
      const w = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
      w.onmessage = (e) => {
        const { id, ok, r, error } = e.data, it = pending.get(id);
        pending.delete(id);
        idle.push(w);
        if (it) (ok ? it.res(r) : it.rej(new Error(error)));
        pump();
      };
      w.onerror = (ev) => { // the worker could not start / crashed: everything goes on on the main thread
        broken = true;
        console.error('render worker', ev.message);
        const it = pending.get(w._id);
        pending.delete(w._id);
        if (it) onMain(it.job).then(it.res, it.rej);
        for (const q of queue.splice(0)) onMain(q.job).then(q.res, q.rej).finally(() => pending.delete(q.id));
      };
      workers.push(w); idle.push(w);
    }
  } catch (e) { broken = true; console.error(e); }
}

/** job -> Promise<{page, ocr?}> */
export function render(job) {
  if (!inited) init();
  if (broken || !workers.length) return onMain(job);
  return new Promise((res, rej) => {
    const id = ++seq, it = { id, job, res, rej };
    pending.set(id, it);
    queue.push(it);
    pump();
  });
}
