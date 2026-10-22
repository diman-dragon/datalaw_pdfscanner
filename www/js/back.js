// System Back: closes the top overlay (dialog / popup menu / sheet / page editor / text recognition / document editor / viewer / camera);
// else from any tab -> Library; on Library stays.
import { goTo, getTab } from './tabs.js';
import { isSheetOpen, closeSheet } from './sheet.js';
import { isMenuOpen, closeMenu } from './menu.js';
import { isResultOpen, closeResult } from './scan/result.js';
import { isOcrOpen, closeOcr } from './ocr.js';
import { isDocEditOpen, closeDocEdit } from './docedit.js';
import { isViewerOpen, closeViewer } from './viewer.js';
import { isQcamOpen, closeQcam } from './scan/qcamera.js';
import { isDialogOpen, closeDialog } from './dialog.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (isDialogOpen()) return closeDialog();
  if (isQcamOpen()) return closeQcam();
  if (isMenuOpen()) return closeMenu();
  if (isSheetOpen()) return closeSheet();
  if (isResultOpen()) return closeResult();
  if (isOcrOpen()) return closeOcr();
  if (isDocEditOpen()) return closeDocEdit();
  if (isViewerOpen()) return closeViewer();
  if (getTab() !== 'library') goTo('library');
});
