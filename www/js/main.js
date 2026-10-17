// Точка входа: экраны, ввод, игровой цикл.
import { LEVELS } from './levels.js';
import { Game } from './game.js';
import { draw } from './render.js';
import * as G from './geometry.js';
import { playSound, unlockAudio, setSoundEnabled } from './audio.js';
import { haptic, onBackButton, exitApp } from './platform.js';
import { loadSave, writeSave } from './storage.js';
import { VERSION } from './version.js';

const STR = {
  ru: {
    sub: 'Высыпай шарики на конвейер — собирай коробки одного цвета.',
    play: 'Играть', cont: 'Продолжить', level: 'Уровень', pause: 'Пауза', resume: 'Продолжить',
    restart: 'Начать заново', menu: 'В меню', next: 'Дальше', again: 'Играть ещё раз', retry: 'Ещё раз',
    win: 'Уровень пройден!', lose: 'Конвейер забит!',
    winText: (n) => `Нажатий: ${n}.`, winLast: (n) => `Нажатий: ${n}. Новые уровни скоро появятся!`,
    loseText: 'Нет места для шариков, а подходящих коробок нет. Попробуй другой порядок лотков.',
    soundOn: 'Звук включён', soundOff: 'Звук выключен',
    hint: ['Нажимай на лотки сверху:', 'шарики попадут в коробки своего цвета'],
  },
  en: {
    sub: 'Drop beads on the belt and fill the boxes with matching colors.',
    play: 'Play', cont: 'Continue', level: 'Level', pause: 'Paused', resume: 'Resume',
    restart: 'Restart', menu: 'Menu', next: 'Next', again: 'Play again', retry: 'Try again',
    win: 'Level complete!', lose: 'Belt jammed!',
    winText: (n) => `Taps: ${n}.`, winLast: (n) => `Taps: ${n}. More levels are coming soon!`,
    loseText: 'The belt is full and no box matches. Try a different tray order.',
    soundOn: 'Sound on', soundOff: 'Sound off',
    hint: ['Tap the trays on top:', 'beads go into boxes of their color'],
  },
};
const lang = /^ru|^uk|^be/i.test(navigator.language || '') ? 'ru' : 'en';
const T = STR[lang];

const $ = (id) => document.getElementById(id);
const canvas = $('cv');
const ctx = canvas.getContext('2d');
const screens = { menu: $('screenMenu'), pause: $('screenPause'), end: $('screenEnd') };

const save = loadSave();
setSoundEnabled(save.sound);

let game = null;
let levelIndex = Math.min(Math.max(0, (save.level || 1) - 1), LEVELS.length - 1);
let mode = 'menu'; // menu | play | pause | end
let view = { k: 1, ox: 0, oy: 0, dpr: 1 };

function show(name) {
  for (const [k, el] of Object.entries(screens)) el.hidden = k !== name;
  mode = name || 'play';
}

function setText() {
  $('btnPlay').textContent = save.completed[1] || levelIndex > 0 ? T.cont : T.play;
  $('menuSub').textContent = T.sub;
  $('pauseTitle').textContent = T.pause;
  $('btnResume').textContent = T.resume;
  $('btnPauseRestart').textContent = T.restart;
  $('btnMenu').textContent = T.menu;
  $('btnEndMenu').textContent = T.menu;
  $('ver').textContent = 'v' + VERSION;
  document.documentElement.lang = lang;
}

// У SVG-элементов нет свойства hidden (оно есть только у HTML), поэтому переключаем атрибут.
function setHidden(el, hide) {
  if (hide) el.setAttribute('hidden', '');
  else el.removeAttribute('hidden');
}

function updateSoundIcon() {
  setHidden($('icoSoundOn'), !save.sound);
  setHidden($('icoSoundOff'), save.sound);
  $('btnSound').setAttribute('aria-pressed', String(!save.sound));
  $('btnSound').classList.toggle('on', save.sound);
  $('btnSound').classList.toggle('off', !save.sound);
}

let toastTimer = 0;
function showToast(text) {
  const el = $('toast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 1200);
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
  game.hintText = levelIndex === 0 && !save.seenHint ? T.hint : null;
  show(null);
}

function openMenu() {
  setText();
  show('menu');
}

function openPause() {
  if (mode !== 'play') return;
  $('pauseTitle').textContent = T.pause;
  show('pause');
}

function showEnd() {
  const won = game.state === 'won';
  const last = levelIndex >= LEVELS.length - 1;
  if (won) {
    save.completed[LEVELS[levelIndex].id] = true;
    if (!last) save.level = levelIndex + 2;
    writeSave(save);
  }
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
bind('btnPause', () => openPause());
bind('btnResume', () => show(null));
bind('btnPauseRestart', () => startLevel());
bind('btnRestart', () => { if (mode === 'play') startLevel(); });
bind('btnMenu', () => openMenu());
bind('btnEndMenu', () => openMenu());
bind('btnRetry', () => startLevel());
bind('btnNext', () => {
  levelIndex = levelIndex >= LEVELS.length - 1 ? 0 : levelIndex + 1; // после последнего уровня — сначала
  startLevel();
});
$('btnSound').addEventListener('click', () => {
  unlockAudio();
  save.sound = !save.sound;
  setSoundEnabled(save.sound);
  writeSave(save);
  updateSoundIcon();
  const b = $('btnSound');
  b.classList.remove('pop'); void b.offsetWidth; b.classList.add('pop');
  showToast(save.sound ? T.soundOn : T.soundOff);
  if (save.sound) playSound('soundOn'); // слышен только при включении — как подтверждение
});

// Кнопка «Назад» на Android: игра -> пауза -> меню -> выход
onBackButton(() => {
  if (mode === 'play') openPause();
  else if (mode === 'pause') show(null);
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
function frame(now) {
  const dt = (now - last) / 1000;
  last = now;
  if (game) {
    if (mode === 'play') {
      game.update(dt);
      if (game.state !== 'play' && game.endT > 0.9) showEnd(); // пауза, чтобы доиграли анимации
    }
    ctx.setTransform(view.dpr * view.k, 0, 0, view.dpr * view.k, view.dpr * view.ox, view.dpr * view.oy);
    draw(ctx, game);
  }
  requestAnimationFrame(frame);
}

// игра на фоне меню — чтобы под оверлеем не было пустоты
game = new Game(LEVELS[levelIndex], () => {});
game.lang = lang;
setText();
updateSoundIcon();
resize();
openMenu();
requestAnimationFrame((t) => { last = t; frame(t); });
