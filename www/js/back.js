// System Back: from any tab -> Library; on Library stays (app not closed).
import { goTo, getTab } from './tabs.js';
window.Capacitor?.Plugins?.App?.addListener('backButton', () => {
  if (getTab() !== 'library') goTo('library');
});
