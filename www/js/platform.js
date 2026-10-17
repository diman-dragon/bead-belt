// Мост к Capacitor. Всё защищено try/catch: в обычном браузере игра работает без плагинов.
const cap = () => (typeof window !== 'undefined' ? window.Capacitor : undefined);

export const isNative = () => {
  try { return !!cap()?.isNativePlatform?.(); } catch { return false; }
};

function plugin(name) {
  try { return cap()?.Plugins?.[name]; } catch { return undefined; }
}

let hapticsOn = true;
export function setHapticsEnabled(v) { hapticsOn = !!v; }

export function haptic(kind = 'light') {
  if (!hapticsOn) return;
  try {
    const h = plugin('Haptics');
    if (h) {
      if (kind === 'success' || kind === 'error') {
        h.notification?.({ type: kind === 'success' ? 'SUCCESS' : 'ERROR' });
      } else {
        h.impact?.({ style: kind === 'medium' ? 'MEDIUM' : 'LIGHT' });
      }
      return;
    }
    navigator.vibrate?.(kind === 'light' ? 10 : 25);
  } catch { /* ignore */ }
}

export function onBackButton(handler) {
  try {
    plugin('App')?.addListener?.('backButton', handler);
  } catch { /* ignore */ }
}

export function exitApp() {
  try { plugin('App')?.exitApp?.(); } catch { /* ignore */ }
}
