// Точка входа: экраны, ввод, игровой цикл.
import { LEVELS } from './levels.js';
import { Game } from './game.js';
import { draw, setTheme } from './render.js';
import * as G from './geometry.js';
import { playSound, unlockAudio, setSoundEnabled } from './audio.js';
import { haptic, setHapticsEnabled, onBackButton, exitApp } from './platform.js';
import { loadSave, writeSave } from './storage.js';
import { VERSION } from './version.js';

const STR = {
  ru: {
    sub: 'Высыпай шарики на конвейер — собирай коробки одного цвета.',
    play: 'Играть', cont: 'Продолжить', level: 'Уровень', levels: 'Уровни', settings: 'Настройки', about: 'О нас',
    pause: 'Пауза', resume: 'Продолжить', restart: 'Начать заново', menu: 'В меню', next: 'Дальше', again: 'Играть ещё раз', retry: 'Ещё раз',
    win: 'Уровень пройден!', lose: 'Конвейер забит!',
    winText: (n) => `Нажатий: ${n}.`, winLast: (n) => `Нажатий: ${n}. Это был последний уровень — дальше всё сначала!`,
    loseText: 'Нет места для шариков, а подходящих коробок нет. Попробуй другой порядок лотков.',
    soundOn: 'Звук включён', soundOff: 'Звук выключен',
    sound: 'Звук', vibration: 'Вибрация', theme: 'Тема', themeDark: 'Тёмная', themeLight: 'Светлая', themeAuto: 'Авто',
    language: 'Язык', langAuto: 'Авто', reset: 'Сбросить прогресс', resetSure: 'Нажми ещё раз — сбросить всё', resetDone: 'Прогресс сброшен',
    legDone: 'пройден', legFog: 'с туманом',
    version: 'Версия', levelsCount: (a, b) => `${a} / ${b}`,
    aboutText: 'Bead Belt — головоломка про шарики и конвейер. Высыпай лотки в накопитель, лови нужные цвета в коробки и не дай конвейеру забиться.',
    aboutPrivacy: 'Игра не собирает и не передаёт никаких данных: прогресс хранится только на этом устройстве.',
    aboutMade: (n) => `${n} уровней · сделано на Capacitor`,
    hint: ['Нажимай на лотки сверху:', 'шарики попадут в коробки своего цвета'],
  },
  en: {
    sub: 'Drop beads on the belt and fill the boxes with matching colors.',
    play: 'Play', cont: 'Continue', level: 'Level', levels: 'Levels', settings: 'Settings', about: 'About',
    pause: 'Paused', resume: 'Resume', restart: 'Restart', menu: 'Menu', next: 'Next', again: 'Play again', retry: 'Try again',
    win: 'Level complete!', lose: 'Belt jammed!',
    winText: (n) => `Taps: ${n}.`, winLast: (n) => `Taps: ${n}. That was the last level — starting over!`,
    loseText: 'The belt is full and no box matches. Try a different tray order.',
    soundOn: 'Sound on', soundOff: 'Sound off',
    sound: 'Sound', vibration: 'Vibration', theme: 'Theme', themeDark: 'Dark', themeLight: 'Light', themeAuto: 'Auto',
    language: 'Language', langAuto: 'Auto', reset: 'Reset progress', resetSure: 'Tap again to erase everything', resetDone: 'Progress reset',
    legDone: 'completed', legFog: 'fog',
    version: 'Version', levelsCount: (a, b) => `${a} / ${b}`,
    aboutText: 'Bead Belt is a puzzle about beads and a conveyor belt. Pour the trays into the hopper, catch the right colors with the boxes and keep the belt from jamming.',
    aboutPrivacy: 'The game collects and sends no data: progress is stored only on this device.',
    aboutMade: (n) => `${n} levels · made with Capacitor`,
    hint: ['Tap the trays on top:', 'beads go into boxes of their color'],
  },
};
const systemLang = /^ru|^uk|^be/i.test(navigator.language || '') ? 'ru' : 'en';
let lang = systemLang;
let T = STR[lang];

const $ = (id) => document.getElementById(id);
const canvas = $('cv');
const ctx = canvas.getContext('2d');
const screens = {
  menu: $('screenMenu'), levels: $('screenLevels'), settings: $('screenSettings'), about: $('screenAbout'),
  pause: $('screenPause'), end: $('screenEnd'),
};

const save = loadSave();
setSoundEnabled(save.sound);
setHapticsEnabled(save.vibration);

let game = null;
let levelIndex = Math.min(Math.max(0, (save.level || 1) - 1), LEVELS.length - 1);
let mode = 'menu'; // menu | levels | settings | about | play | pause | end
const backStack = []; // куда вернуться из «Настроек» / «О нас» / «Уровней»
let view = { k: 1, ox: 0, oy: 0, dpr: 1 };

function show(name) {
  for (const [k, el] of Object.entries(screens)) el.hidden = k !== name;
  mode = name || 'play';
}

// У SVG-элементов нет свойства hidden (оно есть только у HTML), поэтому переключаем атрибут.
function setHidden(el, hide) {
  if (hide) el.setAttribute('hidden', '');
  else el.removeAttribute('hidden');
}

// ---------- тема ----------
const darkQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
function applyTheme() {
  const resolved = save.theme === 'auto' ? (darkQuery && !darkQuery.matches ? 'light' : 'dark') : save.theme;
  document.documentElement.dataset.theme = resolved;
  setTheme(resolved);
  const meta = document.querySelector('meta[name=theme-color]');
  if (meta) meta.setAttribute('content', resolved === 'dark' ? '#000000' : '#dfe7f8');
}
if (darkQuery) darkQuery.addEventListener?.('change', () => { if (save.theme === 'auto') applyTheme(); });

// ---------- тексты ----------
function applyLang() {
  lang = save.lang === 'auto' ? systemLang : save.lang;
  T = STR[lang];
  document.documentElement.lang = lang;
  document.querySelectorAll('[data-i18n]').forEach((el) => { el.textContent = T[el.dataset.i18n]; });
  if (game) game.lang = lang;
  setText();
  buildLevels();
  updateSettingsUI();
  if (mode === 'play' || mode === 'pause') $('levelLabel').textContent = `${T.level} ${LEVELS[levelIndex].id}`;
}

function setText() {
  const started = save.completed[1] || levelIndex > 0;
  $('playLabel').textContent = started ? T.cont : T.play;
  $('playLevel').textContent = `${T.level} ${LEVELS[levelIndex].id}`.toUpperCase();
  $('menuSub').textContent = T.sub;
  $('pauseTitle').textContent = T.pause;
  $('btnResume').textContent = T.resume;
  $('btnPauseRestart').textContent = T.restart;
  $('btnPauseSettings').textContent = T.settings;
  $('btnMenu').textContent = T.menu;
  $('btnEndMenu').textContent = T.menu;
  $('ver').textContent = 'v' + VERSION;
  $('aboutVer').textContent = `${T.version} ${VERSION}`;
  $('aboutText').textContent = T.aboutText;
  $('aboutPrivacy').textContent = T.aboutPrivacy;
  $('aboutMade').textContent = T.aboutMade(LEVELS.length);
  if (!resetArmed) $('btnReset').textContent = T.reset;
}

function updateSoundIcon() {
  setHidden($('icoSoundOn'), !save.sound);
  setHidden($('icoSoundOff'), save.sound);
  $('btnSound').setAttribute('aria-pressed', String(!save.sound));
  $('btnSound').classList.toggle('on', save.sound);
  $('btnSound').classList.toggle('off', !save.sound);
  $('swSound').setAttribute('aria-checked', String(save.sound));
}

function updateSettingsUI() {
  $('swSound').setAttribute('aria-checked', String(save.sound));
  $('swVibe').setAttribute('aria-checked', String(save.vibration));
  for (const b of $('segTheme').children) b.setAttribute('aria-pressed', String(b.dataset.v === save.theme));
  for (const b of $('segLang').children) b.setAttribute('aria-pressed', String(b.dataset.v === save.lang));
}

let toastTimer = 0;
function showToast(text) {
  const el = $('toast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 1200);
}

// ---------- уровни ----------
function buildLevels() {
  const grid = $('lvGrid');
  grid.textContent = '';
  let done = 0;
  LEVELS.forEach((lv, i) => {
    const b = document.createElement('button');
    b.className = 'lv';
    b.textContent = String(lv.id);
    if (save.completed[lv.id]) { b.classList.add('done'); done++; }
    if (i === levelIndex) b.classList.add('cur');
    if (lv.fog && (lv.fog.top < 2 || lv.fog.bottom < 2)) b.classList.add('fog');
    b.addEventListener('click', () => { unlockAudio(); playSound('click'); levelIndex = i; startLevel(); });
    grid.appendChild(b);
  });
  $('lvCount').textContent = T.levelsCount(done, LEVELS.length);
}

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
  if (game && game.hintText) {
    game.hintText = null;
    save.seenHint = true;
    writeSave(save);
  }
}

function startLevel() {
  const level = LEVELS[levelIndex];
  game = new Game(level, onGameEvent);
  game.lang = lang;
  $('levelLabel').textContent = `${T.level} ${level.id}`;
  $('progBar').style.width = '0%';
  game.hintText = levelIndex === 0 && !save.seenHint ? T.hint : null;
  backStack.length = 0;
  show(null);
}

function openMenu() {
  backStack.length = 0;
  setText();
  buildLevels();
  show('menu');
}

function openSub(name) {
  backStack.push(mode);
  if (name === 'levels') buildLevels();
  if (name === 'settings') updateSettingsUI();
  show(name);
}

function goBack() {
  const prev = backStack.pop();
  if (!prev || prev === 'menu') { openMenu(); return; }
  show(prev);
}

function openPause() {
  if (mode !== 'play') return;
  show('pause');
}

function showEnd() {
  const won = game.state === 'won';
  const last = levelIndex >= LEVELS.length - 1;
  if (won) {
    save.completed[LEVELS[levelIndex].id] = true;
    if (!last) save.level = Math.max(save.level || 1, levelIndex + 2);
    writeSave(save);
  }
  const icon = $('endIcon');
  icon.className = 'end-icon ' + (won ? 'win' : 'lose');
  icon.innerHTML = won
    ? '<svg viewBox="0 0 24 24" width="42" height="42"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/></svg>'
    : '<svg viewBox="0 0 24 24" width="38" height="38"><path d="M6 6l12 12M18 6L6 18" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round"/></svg>';
  $('endTitle').textContent = won ? T.win : T.lose;
  $('endText').textContent = won ? (last ? T.winLast(game.taps) : T.winText(game.taps)) : T.loseText;
  $('btnNext').hidden = !won;
  $('btnNext').textContent = last ? T.again : T.next;
  $('btnRetry').hidden = won;
  $('btnRetry').textContent = T.retry;
  show('end');
}

// ---------- ввод ----------
function toLogical(e) {
  const r = canvas.getBoundingClientRect();
  return { x: (e.clientX - r.left - view.ox) / view.k, y: (e.clientY - r.top - view.oy) / view.k };
}

canvas.addEventListener('pointerdown', (e) => {
  unlockAudio();
  if (mode !== 'play' || !game) return;
  const p = toLogical(e);
  game.tap(p.x, p.y);
  e.preventDefault();
});

function bind(id, fn) {
  $(id).addEventListener('click', () => { unlockAudio(); playSound('click'); fn(); });
}

bind('btnPlay', () => startLevel());
bind('btnLevels', () => openSub('levels'));
bind('btnSettings', () => openSub('settings'));
bind('btnAbout', () => openSub('about'));
bind('btnPause', () => openPause());
bind('btnResume', () => show(null));
bind('btnPauseRestart', () => startLevel());
bind('btnPauseSettings', () => openSub('settings'));
bind('btnRestart', () => { if (mode === 'play') startLevel(); });
bind('btnMenu', () => openMenu());
bind('btnEndMenu', () => openMenu());
bind('btnRetry', () => startLevel());
bind('btnNext', () => {
  levelIndex = levelIndex >= LEVELS.length - 1 ? 0 : levelIndex + 1; // после последнего уровня — сначала
  startLevel();
});
document.querySelectorAll('[data-back]').forEach((b) => b.addEventListener('click', () => { unlockAudio(); playSound('click'); goBack(); }));

function setSound(v, toast = true) {
  save.sound = v;
  setSoundEnabled(v);
  writeSave(save);
  updateSoundIcon();
  const b = $('btnSound');
  b.classList.remove('pop'); void b.offsetWidth; b.classList.add('pop');
  if (toast) showToast(v ? T.soundOn : T.soundOff);
  if (v) playSound('soundOn'); // слышен только при включении — как подтверждение
}
$('btnSound').addEventListener('click', () => { unlockAudio(); setSound(!save.sound); });
$('swSound').addEventListener('click', () => { unlockAudio(); setSound(!save.sound, false); });
$('swVibe').addEventListener('click', () => {
  unlockAudio();
  save.vibration = !save.vibration;
  setHapticsEnabled(save.vibration);
  writeSave(save);
  updateSettingsUI();
  playSound('click');
  haptic('medium');
});
$('segTheme').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  unlockAudio(); playSound('click');
  save.theme = b.dataset.v;
  writeSave(save);
  applyTheme();
  updateSettingsUI();
});
$('segLang').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  unlockAudio(); playSound('click');
  save.lang = b.dataset.v;
  writeSave(save);
  applyLang();
});

let resetArmed = false;
let resetTimer = 0;
$('btnReset').addEventListener('click', () => {
  unlockAudio();
  if (!resetArmed) {
    resetArmed = true;
    $('btnReset').classList.add('armed');
    $('btnReset').textContent = T.resetSure;
    playSound('click');
    clearTimeout(resetTimer);
    resetTimer = setTimeout(disarmReset, 3500);
    return;
  }
  disarmReset();
  save.completed = {};
  save.level = 1;
  save.seenHint = false;
  writeSave(save);
  levelIndex = 0;
  game = new Game(LEVELS[0], () => {});
  game.lang = lang;
  setText();
  buildLevels();
  showToast(T.resetDone);
  haptic('medium');
});
function disarmReset() {
  resetArmed = false;
  clearTimeout(resetTimer);
  $('btnReset').classList.remove('armed');
  $('btnReset').textContent = T.reset;
}

// Кнопка «Назад» на Android: игра -> пауза -> меню -> выход
onBackButton(() => {
  if (mode === 'play') openPause();
  else if (mode === 'pause') show(null);
  else if (mode === 'levels' || mode === 'settings' || mode === 'about') goBack();
  else if (mode === 'end') openMenu();
  else exitApp();
});

document.addEventListener('visibilitychange', () => {
  if (document.hidden && mode === 'play') openPause();
});

// ---------- размеры ----------
function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 3);
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  canvas.width = Math.round(w * dpr);
  canvas.height = Math.round(h * dpr);
  const k = Math.min(w / G.W, h / G.H);
  view = { k, ox: (w - G.W * k) / 2, oy: (h - G.H * k) / 2, dpr };
}
new ResizeObserver(resize).observe(canvas);
window.addEventListener('resize', resize);

// ---------- цикл ----------
let last = performance.now();
let shownProg = -1;
function frame(now) {
  const dt = (now - last) / 1000;
  last = now;
  if (game) {
    if (mode === 'play') {
      game.update(dt);
      if (game.state !== 'play' && game.endT > 1.1) showEnd(); // пауза, чтобы доиграли анимации и конфетти
      const pr = Math.round((game.state === 'won' ? 1 : game.doneBoxes / game.totalBoxes) * 100);
      if (pr !== shownProg) { shownProg = pr; $('progBar').style.width = pr + '%'; }
    } else if (mode === 'menu' || mode === 'levels' || mode === 'settings' || mode === 'about') {
      game.update(dt); // фоновая «жизнь»: анимации за размытием
    }
    ctx.setTransform(view.dpr * view.k, 0, 0, view.dpr * view.k, view.dpr * view.ox, view.dpr * view.oy);
    draw(ctx, game);
  }
  requestAnimationFrame(frame);
}

// игра на фоне меню — чтобы под оверлеем не было пустоты
game = new Game(LEVELS[levelIndex], () => {});
applyTheme();
applyLang();
updateSoundIcon();
resize();
openMenu();
requestAnimationFrame((t) => { last = t; frame(t); });
