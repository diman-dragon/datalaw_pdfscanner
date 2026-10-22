// System Back: closes the top overlay (question over the camera / popup menu / sheet / page editor / text recognition / document editor / viewer / camera);
// else from any tab -> Library; on Library stays.
import { goTo, getTab } from './tabs.js';
import { isSheetOpen, closeSheet } from './sheet.js';
import { isMenuOpen, closeMenu } from './menu.js';
import { isResultOpen, closeResult } from './scan/result.js';
import { isOcrOpen, closeOcr } from './ocr.js';
import { isDocEditOpen, closeDocEdit } from './docedit.js';
import { isViewerOpen, closeViewer } from './viewer.js';
import { isQcamOpen, closeQcam } from './scan/qcamera.js';
import { isChoiceOpen, closeChoice } from './scan/choice.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (isChoiceOpen()) return closeChoice();
  if (isQcamOpen()) return closeQcam();
  if (isMenuOpen()) return closeMenu();
  if (isSheetOpen()) return closeSheet();
  if (isResultOpen()) return closeResult();
  if (isOcrOpen()) return closeOcr();
  if (isDocEditOpen()) return closeDocEdit();
  if (isViewerOpen()) return closeViewer();
  if (getTab() !== 'library') goTo('library');
});
