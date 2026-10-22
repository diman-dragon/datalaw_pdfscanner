// First-page previews + page count: sequential queue + in-memory cache.
import { Folder } from './folder.js';
const cache = new Map(); // key -> { data, pages } | null (failed)
const queue = [];
let busy = false;

// host: the element the result is for (skipped if it left the DOM); apply(info): info = { data, pages } or null.
export function loadPreview(file, host, apply) {
  const key = `${file.uri}|${file.modified}|${file.size}`;
  if (cache.has(key)) { apply(cache.get(key)); return; }
  queue.push({ key, uri: file.uri, host, apply });
  run();
}

async function run() {
  if (busy) return;
  busy = true;
  while (queue.length) {
    const job = queue.shift();
    if (!job.host.isConnected) continue;
    let info = null;
    try {
      const r = await Folder.preview({ uri: job.uri, width: 360 });
      info = { data: r.data, pages: r.pages ?? 0 };
    } catch { /* leave null */ }
    cache.set(job.key, info);
    job.apply(info);
  }
  busy = false;
}
