// System Back: closes the edge editor / action sheet first; else from any tab -> Library; on Library stays.
import { goTo, getTab } from './tabs.js';
import { isSheetOpen, closeSheet } from './sheet.js';
import { isEditorOpen, closeEditor } from './scan/editor.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (isEditorOpen()) return closeEditor();
  if (isSheetOpen()) return closeSheet();
  if (getTab() !== 'library') goTo('library');
});
