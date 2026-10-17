// Сохранение прогресса в localStorage (в WebView Capacitor он сохраняется между запусками).
const KEY = 'beadbelt.save.v1';

const DEFAULTS = { sound: true, vibration: true, theme: 'dark', lang: 'auto', level: 1, completed: {}, seenHint: false };

export function loadSave() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULTS, completed: {} };
    const data = JSON.parse(raw);
    return { ...DEFAULTS, ...data, completed: { ...(data.completed || {}) } };
  } catch {
    return { ...DEFAULTS, completed: {} };
  }
}

export function writeSave(save) {
  try { localStorage.setItem(KEY, JSON.stringify(save)); } catch { /* ignore */ }
}
