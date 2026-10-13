// First-page previews: sequential queue + in-memory cache.
import { Folder } from './folder.js';
const cache = new Map(); // key -> dataURL, '' = failed
const queue = [];
let busy = false;

export function loadPreview(file, img) {
  const key = `${file.uri}|${file.modified}|${file.size}`;
  if (cache.has(key)) {
    if (cache.get(key)) img.src = cache.get(key);
    return;
  }
  queue.push({ key, uri: file.uri, img });
  run();
}

async function run() {
  if (busy) return;
  busy = true;
  while (queue.length) {
    const job = queue.shift();
    if (!job.img.isConnected) continue;
    try {
      const r = await Folder.preview({ uri: job.uri, width: 360 });
      cache.set(job.key, r.data);
      job.img.src = r.data;
    } catch {
      cache.set(job.key, '');
    }
  }
  busy = false;
}
