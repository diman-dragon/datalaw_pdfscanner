// System Back: closes the open sheet; else from any tab -> Library; on Library stays (app not closed).
import { goTo, getTab } from './tabs.js';
import { isSheetOpen, closeSheet } from './sheet.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (isSheetOpen()) return closeSheet();
  if (getTab() !== 'library') goTo('library');
});
