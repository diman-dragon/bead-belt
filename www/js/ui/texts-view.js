// Подстановка строк в статичные элементы интерфейса.
import { $ } from './dom.js';
import { VERSION } from '../version.js';

export function renderDataStrings(T) {
  document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = T[el.dataset.i18n]; });
}

export function renderLevelLabel(T, levelId) {
  $('levelLabel').textContent = `${T.level} ${levelId}`;
}

export function renderMenuTexts(T, { started, levelId }) {
  $('playLabel').textContent = started ? T.cont : T.play;
  $('playLevel').textContent = `${T.level} ${levelId}`.toUpperCase();
  $('menuSub').textContent = T.sub;
  $('ver').textContent = 'v' + VERSION;
}

export function renderPauseTexts(T) {
  $('pauseTitle').textContent = T.pause;
  $('btnResume').textContent = T.resume;
  $('btnPauseRestart').textContent = T.restart;
  $('btnPauseSettings').textContent = T.settings;
  $('btnMenu').textContent = T.menu;
  $('btnEndMenu').textContent = T.menu;
}

export function renderAboutTexts(T, levelCount) {
  $('aboutVer').textContent = `${T.version} ${VERSION}`;
  $('aboutText').textContent = T.aboutText;
  $('aboutPrivacy').textContent = T.aboutPrivacy;
  $('aboutMade').textContent = T.aboutMade(levelCount);
}
