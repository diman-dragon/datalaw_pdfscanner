// System Back: closes the top overlay (sheet / page editor / text recognition / document editor / viewer / camera);
// else from any tab -> Library; on Library stays.
import { goTo, getTab } from './tabs.js';
import { isSheetOpen, closeSheet } from './sheet.js';
import { isResultOpen, closeResult } from './scan/result.js';
import { isOcrOpen, closeOcr } from './ocr.js';
import { isDocEditOpen, closeDocEdit } from './docedit.js';
import { isViewerOpen, closeViewer } from './viewer.js';
import { isCameraOpen, closeCamera } from './scan/camera.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (isSheetOpen()) return closeSheet();
  if (isResultOpen()) return closeResult();
  if (isOcrOpen()) return closeOcr();
  if (isDocEditOpen()) return closeDocEdit();
  if (isViewerOpen()) return closeViewer();
  if (isCameraOpen()) return closeCamera();
  if (getTab() !== 'library') goTo('library');
});
