// Точка входа: связывает экраны, ввод, настройки и игровой цикл. Логика каждой части живёт в своём модуле.
import { LEVELS } from './levels.js';
import { Game } from './game.js';
import { draw, setTheme } from './render/index.js';
import { playSound, unlockAudio, setSoundEnabled } from './audio.js';
import { haptic, setHapticsEnabled, onBackButton, exitApp } from './platform.js';
import { loadSave, writeSave } from './storage.js';
import { resolveLang, strings } from './i18n.js';
import { Navigator } from './navigator.js';
import { AdaptiveQuality } from './quality.js';
import { FramePacer, clampFrameDt } from './frame-pacer.js';
import { createViewport } from './viewport.js';
import { $, showToast } from './ui/dom.js';
import { renderSoundIcon, popSoundButton, renderSettings, renderResetButton } from './ui/settings-view.js';
import { renderLevelGrid } from './ui/levels-view.js';
import { renderEnd } from './ui/end-view.js';
import { renderDataStrings, renderLevelLabel, renderMenuTexts, renderPauseTexts, renderAboutTexts } from './ui/texts-view.js';
import { setProgress, resetProgress } from './ui/progress-bar.js';

const END_DELAY = 1.1; // пауза перед экраном конца уровня: доигрывают анимации и конфетти

// ---------- состояние
const save = loadSave();
let lang = resolveLang(save.lang);
let T = strings(lang);
let game = null;
let levelIndex = Math.min(Math.max(0, (save.level || 1) - 1), LEVELS.length - 1);
let resetArmed = false;
let resetTimer = 0;

const canvas = $('cv');
const ctx = canvas.getContext('2d');
const pacer = new FramePacer();
const quality = new AdaptiveQuality();
const viewport = createViewport(canvas, quality, () => pacer.invalidate());
const nav = new Navigator({
  menu: $('screenMenu'), levels: $('screenLevels'), settings: $('screenSettings'), about: $('screenAbout'),
  pause: $('screenPause'), end: $('screenEnd'),
}, () => pacer.invalidate());

const click = () => { unlockAudio(); playSound('click'); };

// ---------- тема и язык
const darkQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

function resolveTheme() {
  if (save.theme !== 'auto') return save.theme;
  return darkQuery && !darkQuery.matches ? 'light' : 'dark';
}

function applyTheme() {
  const resolved = resolveTheme();
  document.documentElement.dataset.theme = resolved;
  setTheme(resolved);
  const meta = document.querySelector('meta[name=theme-color]');
  if (meta) meta.setAttribute('content', resolved === 'dark' ? '#000000' : '#dfe7f8');
}

function renderTexts() {
  const levelId = LEVELS[levelIndex].id;
  renderDataStrings(T);
  renderMenuTexts(T, { started: save.completed[1] || levelIndex > 0, levelId });
  renderPauseTexts(T);
  renderAboutTexts(T, LEVELS.length);
  renderResetButton(T, resetArmed);
  if (nav.mode === 'play' || nav.mode === 'pause') renderLevelLabel(T, levelId);
}

function applyLang() {
  lang = resolveLang(save.lang);
  T = strings(lang);
  document.documentElement.lang = lang;
  renderTexts();
  renderGrid();
  renderSettings(save);
}

function renderGrid() {
  renderLevelGrid(LEVELS, { completed: save.completed, current: levelIndex, onPick: pickLevel }, T);
}

// ---------- уровень
function onGameEvent(type) {
  switch (type) {
    case 'tap': playSound('tap'); haptic('light'); hideHint(); break;
    case 'insert': playSound('insert'); break;
    case 'capture': playSound('capture'); break;
    case 'boxDone': playSound('boxDone'); haptic('medium'); break;
    case 'win': playSound('win'); haptic('success'); break;
    case 'lose': playSound('lose'); haptic('error'); break;
    default:
  }
}

function hideHint() {
  if (!game || !game.hintText) return;
  game.hintText = null;
  save.seenHint = true;
  writeSave(save);
}

function startLevel() {
  const level = LEVELS[levelIndex];
  game = new Game(level, onGameEvent);
  game.hintText = levelIndex === 0 && !save.seenHint ? T.hint : null;
  renderLevelLabel(T, level.id);
  resetProgress();
  nav.clear();
  nav.show(null);
}

function pickLevel(index) {
  click();
  levelIndex = index;
  startLevel();
}

function nextLevelIndex() {
  return levelIndex >= LEVELS.length - 1 ? 0 : levelIndex + 1; // после последнего уровня — сначала
}

function recordWin() {
  save.completed[LEVELS[levelIndex].id] = true;
  if (levelIndex < LEVELS.length - 1) save.level = Math.max(save.level || 1, levelIndex + 2);
  writeSave(save);
}

function showEnd() {
  const won = game.state === 'won';
  if (won) recordWin();
  renderEnd(T, { won, last: levelIndex >= LEVELS.length - 1, taps: game.taps });
  nav.show('end');
}

// ---------- экраны
function openMenu() {
  nav.clear();
  renderTexts();
  renderGrid();
  nav.show('menu');
}

function openSub(name) {
  if (name === 'levels') renderGrid();
  if (name === 'settings') renderSettings(save);
  nav.open(name);
}

function goBack() {
  const prev = nav.back();
  if (prev === 'menu') openMenu();
  else nav.show(prev);
}

function openPause() {
  if (nav.mode === 'play') nav.show('pause');
}

// ---------- настройки
function setSound(on, toast = true) {
  save.sound = on;
  setSoundEnabled(on);
  writeSave(save);
  renderSoundIcon(save);
  popSoundButton();
  if (toast) showToast(on ? T.soundOn : T.soundOff);
  if (on) playSound('soundOn'); // слышен только при включении — как подтверждение
}

function toggleVibration() {
  save.vibration = !save.vibration;
  setHapticsEnabled(save.vibration);
  writeSave(save);
  renderSettings(save);
  playSound('click');
  haptic('medium');
}

function chooseTheme(value) {
  save.theme = value;
  writeSave(save);
  applyTheme();
  renderSettings(save);
}

function chooseLang(value) {
  save.lang = value;
  writeSave(save);
  applyLang();
}

function disarmReset() {
  resetArmed = false;
  clearTimeout(resetTimer);
  renderResetButton(T, false);
}

function armReset() {
  resetArmed = true;
  renderResetButton(T, true);
  playSound('click');
  clearTimeout(resetTimer);
  resetTimer = setTimeout(disarmReset, 3500);
}

function eraseProgress() {
  save.completed = {};
  save.level = 1;
  save.seenHint = false;
  writeSave(save);
  levelIndex = 0;
  game = new Game(LEVELS[0], () => {});
  renderTexts();
  renderGrid();
  showToast(T.resetDone);
  haptic('medium');
}

function onResetClick() {
  unlockAudio();
  if (!resetArmed) { armReset(); return; }
  disarmReset();
  eraseProgress();
}

// Кнопка сегмента (тема/язык): вызывает fn с data-v нажатой кнопки.
function onSegment(fn) {
  return (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    click();
    fn(b.dataset.v);
  };
}

// ---------- привязка событий
function bind(id, fn) {
  $(id).addEventListener('click', () => { click(); fn(); });
}

function bindControls() {
  canvas.addEventListener('pointerdown', (e) => {
    unlockAudio();
    if (nav.mode !== 'play' || !game) return;
    const p = viewport.toLogical(e);
    game.tap(p.x, p.y);
    e.preventDefault();
  });
  bind('btnPlay', startLevel);
  bind('btnLevels', () => openSub('levels'));
  bind('btnSettings', () => openSub('settings'));
  bind('btnAbout', () => openSub('about'));
  bind('btnPause', openPause);
  bind('btnResume', () => nav.show(null));
  bind('btnPauseRestart', startLevel);
  bind('btnPauseSettings', () => openSub('settings'));
  bind('btnRestart', () => { if (nav.mode === 'play') startLevel(); });
  bind('btnMenu', openMenu);
  bind('btnEndMenu', openMenu);
  bind('btnRetry', startLevel);
  bind('btnNext', () => { levelIndex = nextLevelIndex(); startLevel(); });
  document.querySelectorAll('[data-back]').forEach((b) => b.addEventListener('click', () => { click(); goBack(); }));
  $('btnSound').addEventListener('click', () => { unlockAudio(); setSound(!save.sound); });
  $('swSound').addEventListener('click', () => { unlockAudio(); setSound(!save.sound, false); });
  $('swVibe').addEventListener('click', () => { unlockAudio(); toggleVibration(); });
  $('segTheme').addEventListener('click', onSegment(chooseTheme));
  $('segLang').addEventListener('click', onSegment(chooseLang));
  $('btnReset').addEventListener('click', onResetClick);
  if (darkQuery) darkQuery.addEventListener?.('change', () => { if (save.theme === 'auto') applyTheme(); });
  document.addEventListener('visibilitychange', () => { if (document.hidden) openPause(); });
}

// Кнопка «Назад» на Android: игра -> пауза -> меню -> выход
onBackButton(() => {
  const m = nav.mode;
  if (m === 'play') openPause();
  else if (m === 'pause') nav.show(null);
  else if (m === 'levels' || m === 'settings' || m === 'about') goBack();
  else if (m === 'end') openMenu();
  else exitApp();
});

// ---------- цикл
function progressPercent() {
  return Math.round((game.state === 'won' ? 1 : game.doneBoxes / game.totalBoxes) * 100);
}

function render() {
  viewport.apply(ctx);
  draw(ctx, game);
}

function afterPlayUpdate(dt) {
  if (game.state !== 'play' && game.endT > END_DELAY) showEnd();
  setProgress(progressPercent());
  if (quality.sample(dt)) viewport.resize();
}

let last = performance.now();
function frame(now) {
  requestAnimationFrame(frame);
  const dt = clampFrameDt((now - last) / 1000);
  last = now;
  if (!game) return;
  const plan = pacer.plan(nav.mode, dt);
  if (plan.update > 0) game.update(plan.update);
  if (nav.mode === 'play') afterPlayUpdate(dt);
  if (plan.render) render();
}

// игра на фоне меню — чтобы под оверлеем не было пустоты
game = new Game(LEVELS[levelIndex], () => {});
setSoundEnabled(save.sound);
setHapticsEnabled(save.vibration);
bindControls();
applyTheme();
applyLang();
renderSoundIcon(save);
viewport.resize();
openMenu();
requestAnimationFrame((t) => { last = t; frame(t); });
