// Отображение настроек: значок звука, переключатели, сегменты, кнопка сброса. Только читает save и рисует.
import { $, setHidden, replayAnimation } from './dom.js';

export function renderSoundIcon(save) {
  setHidden($('icoSoundOn'), !save.sound);
  setHidden($('icoSoundOff'), save.sound);
  $('btnSound').setAttribute('aria-pressed', String(!save.sound));
  $('btnSound').classList.toggle('on', save.sound);
  $('btnSound').classList.toggle('off', !save.sound);
  $('swSound').setAttribute('aria-checked', String(save.sound));
}

export function popSoundButton() {
  replayAnimation($('btnSound'), 'pop');
}

export function renderSettings(save) {
  $('swSound').setAttribute('aria-checked', String(save.sound));
  $('swVibe').setAttribute('aria-checked', String(save.vibration));
  for (const b of $('segTheme').children) b.setAttribute('aria-pressed', String(b.dataset.v === save.theme));
  for (const b of $('segLang').children) b.setAttribute('aria-pressed', String(b.dataset.v === save.lang));
}

export function renderResetButton(T, armed) {
  $('btnReset').classList.toggle('armed', armed);
  $('btnReset').textContent = armed ? T.resetSure : T.reset;
}
