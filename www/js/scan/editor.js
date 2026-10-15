// Edge editor: photo + 4 draggable corner handles.
import { detectQuad } from './detect.js';
const root = document.getElementById('editor');
const stage = document.getElementById('ed-stage');
const cv = document.getElementById('ed-canvas');
const ctx = cv.getContext('2d');
let img, quad, scale = 1, drag = -1, resolveFn = null;

export const isEditorOpen = () => !root.hidden;

function draw() {
  const dpr = window.devicePixelRatio || 1;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cv.width, cv.height);
  ctx.drawImage(img, 0, 0, img.width * scale, img.height * scale);
  const pts = quad.map(([x, y]) => [x * scale, y * scale]);
  ctx.beginPath();
  ctx.rect(0, 0, img.width * scale, img.height * scale);
  ctx.moveTo(...pts[0]); pts.slice(1).forEach((p) => ctx.lineTo(...p)); ctx.closePath();
  ctx.fillStyle = 'rgba(0,0,0,.45)'; ctx.fill('evenodd');
  ctx.beginPath(); ctx.moveTo(...pts[0]); pts.slice(1).forEach((p) => ctx.lineTo(...p)); ctx.closePath();
  ctx.lineWidth = 2; ctx.strokeStyle = '#38D9F5'; ctx.stroke();
  for (const [x, y] of pts) {
    ctx.beginPath(); ctx.arc(x, y, 12, 0, 7);
    ctx.fillStyle = 'rgba(56,217,245,.35)'; ctx.fill(); ctx.lineWidth = 2.5; ctx.strokeStyle = '#38D9F5'; ctx.stroke();
  }
}

function layout() {
  const sw = stage.clientWidth - 24, sh = stage.clientHeight - 24; // minus the stage padding
  const dpr = window.devicePixelRatio || 1;
  scale = Math.min(sw / img.width, sh / img.height);
  const cw = img.width * scale, ch = img.height * scale;
  cv.style.width = cw + 'px'; cv.style.height = ch + 'px';
  cv.width = Math.round(cw * dpr); cv.height = Math.round(ch * dpr);
  draw();
}

const pos = (e) => { const r = cv.getBoundingClientRect(); return [(e.clientX - r.left) / scale, (e.clientY - r.top) / scale]; };
cv.addEventListener('pointerdown', (e) => {
  const [x, y] = pos(e);
  let best = -1, bd = 44 / scale; // 44 css px grab radius
  quad.forEach((q, i) => { const d = Math.hypot(q[0] - x, q[1] - y); if (d < bd) { bd = d; best = i; } });
  drag = best;
  if (drag >= 0) cv.setPointerCapture(e.pointerId);
});
cv.addEventListener('pointermove', (e) => {
  if (drag < 0) return;
  const [x, y] = pos(e);
  quad[drag] = [Math.min(img.width, Math.max(0, x)), Math.min(img.height, Math.max(0, y))];
  draw();
});
cv.addEventListener('pointerup', () => { drag = -1; });
cv.addEventListener('pointercancel', () => { drag = -1; });

function finish(result) { root.hidden = true; const r = resolveFn; resolveFn = null; r?.(result); }
export const closeEditor = () => finish(null);
document.getElementById('ed-cancel').addEventListener('click', closeEditor);
document.getElementById('ed-done').addEventListener('click', () => finish(quad.map((q) => q.slice())));
document.getElementById('ed-auto').addEventListener('click', () => { quad = detectQuad(img); draw(); });

/** Resolves with the corrected quad (TL,TR,BR,BL in image px) or null if cancelled. */
export function openEditor(image, initialQuad) {
  img = image; quad = initialQuad.map((q) => q.slice());
  root.hidden = false;
  requestAnimationFrame(layout);
  return new Promise((res) => { resolveFn = res; });
}
