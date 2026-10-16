// Звуки синтезируются на лету (WebAudio) — файлов нет, лицензионных вопросов тоже.
let ctx = null;
let enabled = true;
let lastInsert = 0;

export function setSoundEnabled(v) { enabled = !!v; }
export function isSoundEnabled() { return enabled; }

// AudioContext можно создавать только после касания пользователя.
export function unlockAudio() {
  try {
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      ctx = new AC();
    }
    if (ctx.state === 'suspended') ctx.resume();
  } catch { /* звук необязателен */ }
}

function tone(freq, dur, { type = 'sine', vol = 0.12, slide = 0, delay = 0 } = {}) {
  if (!enabled || !ctx) return;
  try {
    const t0 = ctx.currentTime + delay;
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.type = type;
    o.frequency.setValueAtTime(freq, t0);
    if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, freq + slide), t0 + dur);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(vol, t0 + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g).connect(ctx.destination);
    o.start(t0);
    o.stop(t0 + dur + 0.02);
  } catch { /* ignore */ }
}

export function playSound(name) {
  switch (name) {
    case 'tap':
      tone(240, 0.12, { type: 'triangle', vol: 0.16, slide: 120 });
      break;
    case 'insert': {
      const now = performance.now();
      if (now - lastInsert < 55) return;
      lastInsert = now;
      tone(520 + Math.random() * 160, 0.06, { type: 'sine', vol: 0.06 });
      break;
    }
    case 'capture':
      tone(780 + Math.random() * 120, 0.08, { type: 'triangle', vol: 0.09 });
      break;
    case 'boxDone':
      tone(660, 0.14, { type: 'sine', vol: 0.14 });
      tone(990, 0.2, { type: 'sine', vol: 0.14, delay: 0.1 });
      break;
    case 'win':
      [523, 659, 784, 1047].forEach((f, i) => tone(f, 0.28, { type: 'triangle', vol: 0.16, delay: i * 0.12 }));
      break;
    case 'lose':
      [392, 330, 262].forEach((f, i) => tone(f, 0.3, { type: 'sawtooth', vol: 0.09, delay: i * 0.16, slide: -20 }));
      break;
    case 'click':
      tone(420, 0.05, { type: 'square', vol: 0.05 });
      break;
    default:
  }
}
