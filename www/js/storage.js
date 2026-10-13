export const store = {
  get(k) { try { return localStorage.getItem('dpdf.' + k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem('dpdf.' + k, v); } catch { /* ignore */ } },
};
