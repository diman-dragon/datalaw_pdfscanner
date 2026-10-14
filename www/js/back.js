// System Back: closes the top overlay (edge editor / result / camera / sheet); else from any tab -> Library; on Library stays.
import { goTo, getTab } from './tabs.js';
import { isSheetOpen, closeSheet } from './sheet.js';
import { isEditorOpen, closeEditor } from './scan/editor.js';
import { isResultOpen, closeResult } from './scan/result.js';
import { isCameraOpen, closeCamera } from './scan/camera.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (isEditorOpen()) return closeEditor();
  if (isResultOpen()) return closeResult();
  if (isCameraOpen()) return closeCamera();
  if (isSheetOpen()) return closeSheet();
  if (getTab() !== 'library') goTo('library');
});
