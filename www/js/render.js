// Отрисовка на canvas: объёмные шарики, лотки, накопитель, конвейер, коробки.
// Спрайты шариков и теней кэшируются в offscreen-canvas.
import * as G from './geometry.js';
import { BOX_SIZE } from './levels.js';
import { ENTER_DUR, POP_DUR, TRAY_LEAVE } from './game.js';

// hl — блик, lt — свет, base — основной тон, dk — тень, deep — самая тёмная кромка
// Неоновая палитра «под AMOLED»: чистые насыщенные оттенки, разнесённые по кругу (~55–75° друг от друга).
// Ключ O — бирюзовый (раньше был оранжевым: слишком похож на красный и жёлтый).
export const COLORS = {
  R: { hl: '#ff9aa6', lt: '#ff4560', base: '#ff0a2e', dk: '#b5001c', deep: '#5a0010' },
  Y: { hl: '#fff98a', lt: '#ffee2e', base: '#ffe100', dk: '#b8a800', deep: '#585000' },
  G: { hl: '#8affb0', lt: '#2dff7c', base: '#00e65a', dk: '#009a3a', deep: '#004d1c' },
  O: { hl: '#8af4ff', lt: '#2ee9ff', base: '#00d4f5', dk: '#0090b8', deep: '#00465c' },
  B: { hl: '#9db4ff', lt: '#4d73ff', base: '#1f45ff', dk: '#0d28c4', deep: '#060f66' },
  P: { hl: '#f59cff', lt: '#e84bff', base: '#d010ff', dk: '#8e00b8', deep: '#46005e' },
  // «туман»: скрытый лоток/коробка (цвет неизвестен)
  X: { hl: '#8d9bc4', lt: '#667399', base: '#46517a', dk: '#323b5e', deep: '#1a2040' },
};
const col = (k) => COLORS[k] || COLORS.R;

// Темы «станка» (холст). Тёмная — под AMOLED, светлая — серебристая.
const THEMES = {
  dark: {
    panel: ['#26396b', '#16254f', '#0b1431'], panelEdge: 'rgba(120,150,210,0.4)', panelInner: 'rgba(0,0,0,0.55)', panelGlow: 'rgba(80,130,230,0.18)',
    binWall: ['#0c1636', '#1c3162'], binGlow: ['rgba(110,170,255,0.2)', 'rgba(110,170,255,0)'], grid: 'rgba(140,180,255,0.06)',
    tube: ['#2a3552', '#8fa0c4', '#46557a', '#1c2640'], tubeIn: 'rgba(5,10,24,0.82)',
    frameDark: '#060a18', frameLight: '#8a9bc0', frameMid: '#475a86', frameHi: 'rgba(255,255,255,0.5)',
    flange: ['#b5c3e0', '#52638c'], gate: ['#c3cfe8', '#6f81aa', '#2b3860'],
    gaugeBg: 'rgba(4,8,20,0.7)', gaugeEdge: 'rgba(160,185,235,0.4)', gaugeText: 'rgba(190,205,240,0.85)',
    island: ['#060b1a', '#0f1833'], rail: ['#0a1020', '#aab8d6', '#5f7099', '#27345a', '#0d1426'],
    ribDark: 'rgba(0,0,0,0.55)', ribLight: 'rgba(150,180,255,0.14)', counter: 'rgba(200,215,245,0.9)',
    plat: ['#222f55', '#0f1834'], platEdge: 'rgba(160,185,235,0.32)', slot: ['rgba(2,5,16,0.88)', 'rgba(2,5,16,0.5)'], slotEdge: ['rgba(150,180,240,0.4)', 'rgba(150,180,240,0.15)'],
    screw: ['#9fb0cf', '#3a4766'], shade: '#040a1c', shadow: 0.35,
  },
  light: {
    panel: ['#f3f6ff', '#d6dff4', '#bccae8'], panelEdge: 'rgba(255,255,255,0.95)', panelInner: 'rgba(70,90,140,0.28)', panelGlow: 'rgba(255,255,255,0.55)',
    binWall: ['#c8d6f3', '#adc1ec'], binGlow: ['rgba(255,255,255,0.6)', 'rgba(255,255,255,0)'], grid: 'rgba(60,90,160,0.11)',
    tube: ['#94a3c6', '#f2f5ff', '#b4c0dd', '#7280a6'], tubeIn: 'rgba(40,55,95,0.78)',
    frameDark: '#56668f', frameLight: '#f6f8ff', frameMid: '#a7b6d8', frameHi: 'rgba(255,255,255,0.9)',
    flange: ['#ffffff', '#a7b6d8'], gate: ['#ffffff', '#b0bedb', '#7a8ab1'],
    gaugeBg: 'rgba(255,255,255,0.75)', gaugeEdge: 'rgba(60,80,130,0.5)', gaugeText: 'rgba(40,55,95,0.95)',
    island: ['#a9b7d8', '#c3cfea'], rail: ['#5f6f98', '#f5f7ff', '#c2cde8', '#9cadd3', '#7e8eb6'],
    ribDark: 'rgba(40,55,95,0.4)', ribLight: 'rgba(255,255,255,0.4)', counter: 'rgba(30,45,85,0.92)',
    plat: ['#dde5f7', '#bdcbe9'], platEdge: 'rgba(60,80,130,0.35)', slot: ['rgba(70,90,140,0.4)', 'rgba(70,90,140,0.2)'], slotEdge: ['rgba(60,80,130,0.5)', 'rgba(60,80,130,0.22)'],
    screw: ['#ffffff', '#8c9bc0'], shade: '#6f7ba0', shadow: 0.22,
  },
};
let TH = THEMES.dark;
export function setTheme(name) { TH = THEMES[name] || THEMES.dark; }

const easeOut = (t) => 1 - Math.pow(1 - t, 3);
const easeOutBack = (t) => {
  const c1 = 1.9;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
};
const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

// ---------------------------------------------------------------- спрайты

const SP = 128;       // размер спрайта шарика
const PAD = 10;
const spriteCache = new Map();
let shadowSprite = null;

// Едва заметный значок формы на шарике — для людей, не различающих цвета.
function glyph(x, key, cx, cy, r) {
  x.save();
  x.fillStyle = 'rgba(255,255,255,0.34)';
  x.strokeStyle = 'rgba(255,255,255,0.34)';
  x.lineWidth = r * 0.12;
  x.beginPath();
  switch (key) {
    case 'R': x.moveTo(cx, cy - r * 0.2); x.lineTo(cx + r * 0.22, cy + r * 0.18); x.lineTo(cx - r * 0.22, cy + r * 0.18); x.closePath(); x.fill(); break;
    case 'B': x.rect(cx - r * 0.16, cy - r * 0.16, r * 0.32, r * 0.32); x.fill(); break;
    case 'G': x.arc(cx, cy, r * 0.18, 0, Math.PI * 2); x.stroke(); break;
    case 'Y': x.moveTo(cx, cy - r * 0.24); x.lineTo(cx + r * 0.2, cy); x.lineTo(cx, cy + r * 0.24); x.lineTo(cx - r * 0.2, cy); x.closePath(); x.fill(); break;
    case 'P': x.moveTo(cx - r * 0.2, cy); x.lineTo(cx + r * 0.2, cy); x.moveTo(cx, cy - r * 0.2); x.lineTo(cx, cy + r * 0.2); x.stroke(); break;
    default: x.arc(cx, cy, r * 0.1, 0, Math.PI * 2); x.fill();
  }
  x.restore();
}

function sprite(key) {
  let c = spriteCache.get(key);
  if (c) return c;
  c = document.createElement('canvas');
  c.width = c.height = SP;
  const x = c.getContext('2d');
  const k = col(key);
  const m = SP / 2;
  const r = m - PAD;
  const disc = () => { x.beginPath(); x.arc(m, m, r, 0, Math.PI * 2); };

  // мягкое неоновое свечение вокруг шарика
  let g = x.createRadialGradient(m, m, r * 0.85, m, m, r + PAD * 0.95);
  g.addColorStop(0, k.base + '55');
  g.addColorStop(1, k.base + '00');
  x.fillStyle = g;
  x.fillRect(0, 0, SP, SP);

  // основное тело сферы
  g = x.createRadialGradient(m - r * 0.36, m - r * 0.42, r * 0.04, m + r * 0.04, m + r * 0.06, r * 1.06);
  g.addColorStop(0, k.hl);
  g.addColorStop(0.12, k.lt);
  g.addColorStop(0.5, k.base);
  g.addColorStop(0.86, k.dk);
  g.addColorStop(1, k.deep);
  x.fillStyle = g;
  disc(); x.fill();

  x.save();
  disc(); x.clip();

  // свет, прошедший сквозь «стекло» — тёплое пятно в нижней части
  g = x.createRadialGradient(m + r * 0.22, m + r * 0.42, r * 0.02, m + r * 0.22, m + r * 0.42, r * 0.95);
  g.addColorStop(0, k.lt);
  g.addColorStop(0.55, k.base + '66');
  g.addColorStop(1, k.base + '00');
  x.globalCompositeOperation = 'screen';
  x.globalAlpha = 0.6;
  x.fillStyle = g;
  x.fillRect(0, 0, SP, SP);
  x.globalAlpha = 1;
  x.globalCompositeOperation = 'source-over';

  // затемнённая кромка (эффект Френеля)
  g = x.createRadialGradient(m, m, r * 0.6, m, m, r);
  g.addColorStop(0, 'rgba(0,0,0,0)');
  g.addColorStop(1, 'rgba(0,0,0,0.34)');
  x.fillStyle = g;
  x.fillRect(0, 0, SP, SP);

  glyph(x, key, m + r * 0.03, m + r * 0.18, r);
  x.restore();

  // отражённый свет по нижнему правому краю
  x.save();
  disc(); x.clip();
  x.strokeStyle = k.hl;
  x.globalAlpha = 0.5;
  x.lineWidth = r * 0.12;
  x.lineCap = 'round';
  x.beginPath(); x.arc(m, m, r * 0.9, Math.PI * 0.08, Math.PI * 0.62); x.stroke();
  x.restore();

  // основной блик (окно) + мягкое свечение вокруг него
  x.save();
  x.translate(m - r * 0.37, m - r * 0.5);
  x.rotate(-0.62);
  g = x.createRadialGradient(0, 0, 0, 0, 0, r * 0.5);
  g.addColorStop(0, 'rgba(255,255,255,0.45)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  x.fillStyle = g;
  x.beginPath(); x.ellipse(0, 0, r * 0.5, r * 0.34, 0, 0, Math.PI * 2); x.fill();
  g = x.createLinearGradient(0, -r * 0.2, 0, r * 0.2);
  g.addColorStop(0, 'rgba(255,255,255,1)');
  g.addColorStop(1, 'rgba(255,255,255,0.3)');
  x.fillStyle = g;
  x.beginPath(); x.ellipse(0, 0, r * 0.3, r * 0.15, 0, 0, Math.PI * 2); x.fill();
  x.restore();

  // острая точка-искра и вторичный блик
  x.fillStyle = 'rgba(255,255,255,0.95)';
  x.beginPath(); x.arc(m - r * 0.12, m - r * 0.7, r * 0.055, 0, Math.PI * 2); x.fill();
  x.fillStyle = 'rgba(255,255,255,0.5)';
  x.beginPath(); x.ellipse(m + r * 0.46, m + r * 0.52, r * 0.1, r * 0.052, -0.8, 0, Math.PI * 2); x.fill();

  // тонкий контур
  x.strokeStyle = k.deep + 'aa';
  x.lineWidth = 1.3;
  x.beginPath(); x.arc(m, m, r - 0.5, 0, Math.PI * 2); x.stroke();

  spriteCache.set(key, c);
  return c;
}

function getShadowSprite() {
  if (shadowSprite) return shadowSprite;
  const c = document.createElement('canvas');
  c.width = 64; c.height = 32;
  const x = c.getContext('2d');
  x.translate(32, 16);
  x.scale(1, 0.5);
  const g = x.createRadialGradient(0, 0, 0, 0, 0, 32);
  g.addColorStop(0, 'rgba(0,0,0,0.55)');
  g.addColorStop(0.6, 'rgba(0,0,0,0.2)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  x.fillStyle = g;
  x.beginPath(); x.arc(0, 0, 32, 0, Math.PI * 2); x.fill();
  shadowSprite = c;
  return c;
}

function drawShadow(ctx, x, y, w, h, alpha = 1) {
  if (alpha !== 1) ctx.globalAlpha = alpha;
  ctx.drawImage(getShadowSprite(), x - w / 2, y - h / 2, w, h);
  if (alpha !== 1) ctx.globalAlpha = 1;
}

function drawMarble(ctx, key, x, y, r, alpha = 1, shadow = false, sx = 1, sy = 1) {
  const s = sprite(key);
  const d = r * 2 * (SP / (SP - 2 * PAD));
  if (shadow) drawShadow(ctx, x + 1.5, y + r * 0.9 + 1.5, r * 2.6, r * 1.4, alpha * 0.9);
  if (alpha !== 1) ctx.globalAlpha = alpha;
  if (sx === 1 && sy === 1) ctx.drawImage(s, x - d / 2, y - d / 2, d, d);
  else ctx.drawImage(s, x - (d * sx) / 2, y - (d * sy) / 2, d * sx, d * sy);
  if (alpha !== 1) ctx.globalAlpha = 1;
}

// ---------------------------------------------------------------- примитивы

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function vgrad(ctx, y0, y1, stops) {
  const g = ctx.createLinearGradient(0, y0, 0, y1);
  for (const [o, c] of stops) g.addColorStop(o, c);
  return g;
}

function hgrad(ctx, x0, x1, stops) {
  const g = ctx.createLinearGradient(x0, 0, x1, 0);
  for (const [o, c] of stops) g.addColorStop(o, c);
  return g;
}

function screw(ctx, x, y) {
  const g = ctx.createRadialGradient(x - 1, y - 1, 0.5, x, y, 4);
  g.addColorStop(0, TH.screw[0]);
  g.addColorStop(1, TH.screw[1]);
  ctx.fillStyle = g;
  ctx.beginPath(); ctx.arc(x, y, 3.6, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = 'rgba(10,16,30,0.7)';
  ctx.lineWidth = 1.2;
  ctx.beginPath(); ctx.moveTo(x - 2, y + 1); ctx.lineTo(x + 2, y - 1); ctx.stroke();
}

function sparkle(ctx, x, y, r) {
  ctx.beginPath();
  ctx.moveTo(x, y - r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.quadraticCurveTo(x, y, x, y + r);
  ctx.quadraticCurveTo(x, y, x - r, y);
  ctx.quadraticCurveTo(x, y, x, y - r);
  ctx.fill();
}

// ---------------------------------------------------------------- главная функция

export function draw(ctx, game) {
  ctx.clearRect(-200, -200, G.W + 400, G.H + 400);
  const t = game.time;

  drawTrays(ctx, game, t);
  drawPanel(ctx);
  drawBin(ctx, game, t);
  drawBeltBase(ctx, game, t);
  drawBeams(ctx, game);
  drawBeltMarbles(ctx, game);
  drawBoxes(ctx, game, t);
  drawFlyers(ctx, game);
  drawFx(ctx, game);
  drawConfetti(ctx, game);
  drawHint(ctx, game, t);
}

// Подсказка внутри пустого накопителя: исчезает после первого нажатия.
function drawHint(ctx, game, t) {
  if (!game.hintText || game.taps > 0 || game.state !== 'play') return;
  const lines = game.hintText;
  const cx = G.CX, cy = G.BIN.top + 52;
  const bob = Math.sin(t * 4) * 4;
  ctx.save();
  ctx.font = '700 14px system-ui, sans-serif';
  const w = Math.max(...lines.map((l) => ctx.measureText(l).width)) + 34;
  const h = 22 + lines.length * 19;
  roundRect(ctx, cx - w / 2, cy - h / 2 + 8, w, h, 16);
  ctx.fillStyle = 'rgba(6,12,30,0.78)';
  ctx.fill();
  ctx.strokeStyle = 'rgba(160,200,255,0.5)';
  ctx.lineWidth = 1.5;
  ctx.stroke();
  ctx.fillStyle = '#eaf2ff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  lines.forEach((l, i) => ctx.fillText(l, cx, cy + 8 + (i - (lines.length - 1) / 2) * 19));
  // стрелка вверх, к лоткам
  const ay = cy - h / 2 - 2 + bob;
  ctx.fillStyle = '#7dd3fc';
  ctx.beginPath();
  ctx.moveTo(cx, ay - 8); ctx.lineTo(cx + 11, ay + 6); ctx.lineTo(cx + 4, ay + 6); ctx.lineTo(cx + 4, ay + 13);
  ctx.lineTo(cx - 4, ay + 13); ctx.lineTo(cx - 4, ay + 6); ctx.lineTo(cx - 11, ay + 6);
  ctx.closePath(); ctx.fill();
  ctx.restore();
}

// ---------------------------------------------------------------- панель-«станок»

function drawPanel(ctx) {
  const x = 14, y = G.BIN.top - 8, w = 332, h = 264;
  ctx.save();
  ctx.fillStyle = 'rgba(0,0,0,0.35)';
  roundRect(ctx, x + 2, y + 7, w, h, 24); ctx.fill();
  ctx.fillStyle = vgrad(ctx, y, y + h, [[0, TH.panel[0]], [0.5, TH.panel[1]], [1, TH.panel[2]]]);
  roundRect(ctx, x, y, w, h, 24); ctx.fill();
  ctx.strokeStyle = TH.panelEdge;
  ctx.lineWidth = 2;
  roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 23); ctx.stroke();
  ctx.strokeStyle = TH.panelInner;
  ctx.lineWidth = 1.5;
  roundRect(ctx, x + 6, y + 6, w - 12, h - 12, 19); ctx.stroke();
  // мягкое свечение в центре
  const g = ctx.createRadialGradient(G.CX, G.CY - 10, 10, G.CX, G.CY - 10, 190);
  g.addColorStop(0, TH.panelGlow);
  g.addColorStop(1, 'rgba(80,130,230,0)');
  ctx.fillStyle = g;
  roundRect(ctx, x, y, w, h, 24); ctx.fill();
  screw(ctx, x + 14, y + 14); screw(ctx, x + w - 14, y + 14);
  screw(ctx, x + 14, y + h - 14); screw(ctx, x + w - 14, y + h - 14);
  ctx.restore();
}

// ---------------------------------------------------------------- лотки

function bucketPath(ctx, w, h) {
  const t = w / 2;
  const b = w * 0.44;
  const r = 15;
  ctx.beginPath();
  ctx.moveTo(-t + r, 0);
  ctx.lineTo(t - r, 0);
  ctx.quadraticCurveTo(t, 0, t - 1, r);
  ctx.lineTo(b + 3, h - r);
  ctx.quadraticCurveTo(b, h, b - r, h);
  ctx.lineTo(-b + r, h);
  ctx.quadraticCurveTo(-b, h, -b - 3, h - r);
  ctx.lineTo(-t + 1, r);
  ctx.quadraticCurveTo(-t, 0, -t + r, 0);
  ctx.closePath();
}

// Рисует лоток; начало координат — верхний центр лотка.
function paintTray(ctx, key, w, h, o) {
  const k = col(key);
  const t = w / 2;

  drawShadow(ctx, 0, h + 5, w * 1.0, 18, 0.9);

  // свечение-подсказка
  if (o.glow > 0) {
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    ctx.strokeStyle = k.lt;
    ctx.globalAlpha = 0.1 + 0.16 * o.glow;
    ctx.lineWidth = 4;
    bucketPath(ctx, w + 4, h + 2);
    ctx.translate(0, -1);
    ctx.stroke();
    ctx.restore();
  }

  // корпус
  bucketPath(ctx, w, h);
  ctx.fillStyle = vgrad(ctx, 0, h, [[0, k.lt], [0.3, k.base], [1, k.dk]]);
  ctx.fill();
  ctx.save();
  ctx.clip();
  ctx.fillStyle = hgrad(ctx, -t, t, [[0, 'rgba(255,255,255,0.14)'], [0.28, 'rgba(255,255,255,0)'], [0.72, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.26)']]);
  ctx.fillRect(-t, 0, w, h);
  // нижняя тень
  ctx.fillStyle = vgrad(ctx, h * 0.7, h, [[0, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.18)']]);
  ctx.fillRect(-t, h * 0.7, w, h * 0.3);
  ctx.restore();
  ctx.strokeStyle = k.deep;
  ctx.globalAlpha *= 0.85;
  ctx.lineWidth = 2.2;
  bucketPath(ctx, w, h);
  ctx.stroke();
  ctx.globalAlpha /= 0.85;

  // блик по верхней кромке
  ctx.strokeStyle = 'rgba(255,255,255,0.42)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(-t + 15, 1.6);
  ctx.lineTo(t - 15, 1.6);
  ctx.stroke();

  if (key === 'X') {
    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    ctx.font = '800 15px system-ui, sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText('?', 0, 12);
    return;
  }
  if (o.simple) return;

  // ёмкость: тёмная чаша с «бортиком»
  const big = o.n > 6; // три ряда (лоток на 9)
  const wx = -t + 10, wy = 10, ww = w - 20, wh = h * (big ? 0.68 : 0.58);
  roundRect(ctx, wx - 2, wy - 2, ww + 4, wh + 5, 13);
  ctx.fillStyle = vgrad(ctx, wy, wy + wh, [[0, 'rgba(255,255,255,0.3)'], [1, 'rgba(255,255,255,0.08)']]);
  ctx.fill();
  roundRect(ctx, wx, wy, ww, wh, 11);
  ctx.fillStyle = vgrad(ctx, wy, wy + wh, [[0, k.deep], [0.55, k.dk], [1, k.dk]]);
  ctx.fill();
  // внутренняя тень сверху
  ctx.save();
  roundRect(ctx, wx, wy, ww, wh, 11);
  ctx.clip();
  ctx.fillStyle = vgrad(ctx, wy, wy + 16, [[0, 'rgba(0,0,0,0.55)'], [1, 'rgba(0,0,0,0)']]);
  ctx.fillRect(wx, wy, ww, 16);
  ctx.restore();

  // шарики
  const n = o.n;
  const rows = Math.ceil(n / 3);
  const rowGap = big ? 17 : 19;
  const y0 = wy + wh / 2 - ((rows - 1) * rowGap) / 2 + 1;
  for (let i = 0; i < o.left; i++) {
    const r = Math.floor(i / 3);
    const inRow = Math.min(3, n - r * 3);
    const c = i % 3;
    const cx = (c - (inRow - 1) / 2) * 22;
    const cy = y0 + r * rowGap;
    drawMarble(ctx, key, cx, cy, big ? G.TRAY_R * 0.93 : G.TRAY_R, 1, false);
  }

  // передняя панель: декоративные полоски
  const fy = wy + wh + 8;
  ctx.strokeStyle = 'rgba(255,255,255,0.28)';
  ctx.lineWidth = 1.5;
  ctx.beginPath(); ctx.moveTo(-t + 14, fy - 3); ctx.lineTo(t - 14, fy - 3); ctx.stroke();
  ctx.strokeStyle = 'rgba(0,0,0,0.25)';
  ctx.beginPath(); ctx.moveTo(-t + 14, fy - 1); ctx.lineTo(t - 14, fy - 1); ctx.stroke();
}

function drawTrays(ctx, game, t) {
  game.cols.forEach((colTrays, ci) => {
    const adv = game.colAdv[ci];
    // дальние лотки первыми
    for (let d = Math.min(colTrays.length - 1, 3); d >= 0; d--) {
      const tray = colTrays[d];
      const depth = d + adv;
      const r = G.trayRect(ci, depth);
      ctx.save();
      const front = d === 0;
      let rot = 0, dx = 0, dy = 0, sx = 1, sy = 1, alpha = front ? 1 : (d === 1 ? 1 : 0.94);
      let glow = 0;
      if (front) {
        if (tray.state === 'pouring') {
          const p = Math.max(0, tray.pulse);
          rot = Math.sin(t * 38 + ci) * 0.035 * (0.4 + p);
          sx = 1 + 0.045 * p;
          sy = 1 - 0.06 * p;
          dy = 2 * p;
        } else if (tray.state === 'leaving') {
          const p = clamp01(tray.t / TRAY_LEAVE);
          rot = (ci - 1) * 0.35 * p;
          dy = p * p * 46;
          dx = (ci - 1) * 10 * p;
          sx = sy = 1 - 0.3 * p;
          alpha = 1 - p * p;
        } else {
          dy = Math.sin(t * 2.4 + ci * 1.7) * 1.3;
          glow = 0.5 + 0.5 * Math.sin(t * 4 + ci);
          if (tray.pulse < 0) dx = Math.sin(t * 60) * 5 * -tray.pulse; // отказ
        }
      }
      ctx.globalAlpha = alpha;
      ctx.translate(r.cx + dx, r.y + r.h / 2 + dy);
      ctx.rotate(rot);
      ctx.scale(sx * r.scale, sy * r.scale);
      ctx.translate(0, -G.TRAY_H / 2);
      paintTray(ctx, (d === 0 || d <= game.fog.top) ? tray.color : 'X', G.TRAY_W, G.TRAY_H, {
        left: tray.left, n: tray.n, front, simple: !front && depth > 1.2, glow,
      });
      if (!front) {
        // дальние лотки чуть затемнены, чтобы лоток спереди выделялся
        ctx.globalAlpha = d === 1 ? 0.08 : 0.2;
        ctx.fillStyle = TH.shade;
        bucketPath(ctx, G.TRAY_W, G.TRAY_H);
        ctx.fill();
      }
      ctx.restore();
    }
  });
}

// ---------------------------------------------------------------- накопитель

function binPath(ctx, closeTop = true) {
  const b = G.BIN;
  ctx.beginPath();
  ctx.moveTo(b.left, b.top);
  ctx.lineTo(b.left, b.wallBottom);
  ctx.lineTo(b.gateL, b.floorY);
  ctx.lineTo(b.gateR, b.floorY);
  ctx.lineTo(b.right, b.wallBottom);
  ctx.lineTo(b.right, b.top);
  if (closeTop) ctx.closePath();
}

function drawBin(ctx, game, t) {
  const b = G.BIN;
  ctx.save();

  // тень
  ctx.save();
  ctx.translate(0, 7);
  binPath(ctx);
  ctx.fillStyle = 'rgba(0,0,0,0.35)';
  ctx.fill();
  ctx.restore();

  // задняя стенка
  binPath(ctx);
  ctx.fillStyle = vgrad(ctx, b.top, b.floorY, [[0, TH.binWall[0]], [1, TH.binWall[1]]]);
  ctx.fill();
  ctx.save();
  binPath(ctx);
  ctx.clip();
  const gl = ctx.createRadialGradient(G.CX, b.top + 40, 6, G.CX, b.top + 40, 150);
  gl.addColorStop(0, TH.binGlow[0]);
  gl.addColorStop(1, TH.binGlow[1]);
  ctx.fillStyle = gl;
  ctx.fillRect(b.left, b.top, b.right - b.left, b.floorY - b.top);
  // тонкая сетка на задней стенке
  ctx.strokeStyle = TH.grid;
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let gx = b.left + 14; gx < b.right; gx += 28) { ctx.moveTo(gx, b.top); ctx.lineTo(gx, b.floorY); }
  for (let gy = b.top + 14; gy < b.floorY; gy += 28) { ctx.moveTo(b.left, gy); ctx.lineTo(b.right, gy); }
  ctx.stroke();
  ctx.restore();

  // труба от затвора к конвейеру
  const tubeTop = b.floorY + 4, tubeBot = G.CY - G.R - 16;
  ctx.fillStyle = hgrad(ctx, G.CX - 15, G.CX + 15, [[0, TH.tube[0]], [0.25, TH.tube[1]], [0.5, TH.tube[2]], [1, TH.tube[3]]]);
  roundRect(ctx, G.CX - 15, tubeTop, 30, tubeBot - tubeTop, 4);
  ctx.fill();
  ctx.fillStyle = TH.tubeIn;
  roundRect(ctx, G.CX - 10, tubeTop - 1, 20, tubeBot - tubeTop + 1, 3);
  ctx.fill();

  // шарики накопителя
  for (const m of game.bin.items) drawMarble(ctx, m.color, m.x, m.y, m.r, 1, true);

  // стеклянная передняя панель
  ctx.save();
  binPath(ctx);
  ctx.clip();
  ctx.fillStyle = vgrad(ctx, b.top, b.floorY, [[0, 'rgba(255,255,255,0.13)'], [0.5, 'rgba(255,255,255,0.03)'], [1, 'rgba(255,255,255,0.07)']]);
  ctx.fillRect(b.left, b.top, b.right - b.left, b.floorY - b.top);
  ctx.fillStyle = 'rgba(255,255,255,0.09)';
  ctx.beginPath();
  ctx.moveTo(b.left + 30, b.top); ctx.lineTo(b.left + 70, b.top); ctx.lineTo(b.left + 20, b.floorY); ctx.lineTo(b.left - 20, b.floorY);
  ctx.closePath(); ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,0.05)';
  ctx.beginPath();
  ctx.moveTo(b.left + 90, b.top); ctx.lineTo(b.left + 106, b.top); ctx.lineTo(b.left + 56, b.floorY); ctx.lineTo(b.left + 40, b.floorY);
  ctx.closePath(); ctx.fill();
  ctx.restore();

  // металлическая рамка (без верхней кромки — накопитель открыт сверху)
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  binPath(ctx, false);
  ctx.strokeStyle = TH.frameDark; ctx.lineWidth = 11; ctx.stroke();
  ctx.strokeStyle = TH.frameLight; ctx.lineWidth = 8; ctx.stroke();
  ctx.strokeStyle = TH.frameMid; ctx.lineWidth = 5.5; ctx.stroke();
  ctx.strokeStyle = TH.frameHi; ctx.lineWidth = 1.4;
  ctx.save(); ctx.translate(-1.5, -1.5); ctx.stroke(); ctx.restore();

  // верхние фланцы
  for (const fx of [b.left, b.right]) {
    ctx.fillStyle = vgrad(ctx, b.top - 6, b.top + 4, [[0, TH.flange[0]], [1, TH.flange[1]]]);
    roundRect(ctx, fx - 9, b.top - 6, 18, 10, 4); ctx.fill();
    ctx.strokeStyle = TH.frameDark; ctx.lineWidth = 1.5; ctx.stroke();
  }

  // затвор
  ctx.fillStyle = vgrad(ctx, b.floorY - 3, b.floorY + 8, [[0, TH.gate[0]], [0.4, TH.gate[1]], [1, TH.gate[2]]]);
  roundRect(ctx, b.gateL - 2, b.floorY - 3, b.gateR - b.gateL + 4, 11, 4); ctx.fill();
  ctx.strokeStyle = TH.frameDark; ctx.lineWidth = 1.5; ctx.stroke();
  const glow = game.gateGlow;
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  const lg = ctx.createRadialGradient(G.CX, b.floorY + 3, 0, G.CX, b.floorY + 3, 16);
  lg.addColorStop(0, `rgba(80,255,170,${0.15 + 0.7 * glow})`);
  lg.addColorStop(1, 'rgba(80,255,170,0)');
  ctx.fillStyle = lg;
  ctx.fillRect(G.CX - 18, b.floorY - 14, 36, 34);
  ctx.restore();
  ctx.fillStyle = glow > 0.05 ? '#b8ffe0' : '#2f9d77';
  ctx.beginPath(); ctx.arc(G.CX, b.floorY + 3, 2.6, 0, Math.PI * 2); ctx.fill();

  // шкала заполнения накопителя
  const gx = 46, gy = G.BIN.floorY - 6, gw = 72, gh = 9;
  const fill = clamp01(game.bin.count / G.BIN_CAP);
  roundRect(ctx, gx, gy, gw, gh, 4.5);
  ctx.fillStyle = TH.gaugeBg; ctx.fill();
  ctx.strokeStyle = TH.gaugeEdge; ctx.lineWidth = 1; ctx.stroke();
  if (fill > 0.01) {
    const hot = fill > 0.8;
    ctx.fillStyle = hgrad(ctx, gx, gx + gw, [[0, '#35e0c0'], [0.65, '#ffd24d'], [1, hot ? '#ff4d5e' : '#ff9d4d']]);
    roundRect(ctx, gx + 1.5, gy + 1.5, Math.max(6, (gw - 3) * fill), gh - 3, 3); ctx.fill();
  }
  ctx.fillStyle = TH.gaugeText;
  ctx.font = '700 9px system-ui, sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  ctx.fillText(`${game.bin.count}/${G.BIN_CAP}`, gx + gw + 6, gy + gh / 2 + 0.5);

  ctx.restore();
}

// ---------------------------------------------------------------- конвейер

function trackPath(ctx) {
  ctx.beginPath();
  ctx.moveTo(G.CX - G.HALF, G.CY - G.R);
  ctx.lineTo(G.CX + G.HALF, G.CY - G.R);
  ctx.arc(G.CX + G.HALF, G.CY, G.R, -Math.PI / 2, Math.PI / 2);
  ctx.lineTo(G.CX - G.HALF, G.CY + G.R);
  ctx.arc(G.CX - G.HALF, G.CY, G.R, Math.PI / 2, Math.PI * 1.5);
  ctx.closePath();
}

const RIB = 11;

function drawBeltBase(ctx, game, t) {
  const danger = game.occupied >= game.n - 3;
  const pulse = 0.5 + 0.5 * Math.sin(t * 9);
  ctx.save();
  ctx.lineJoin = 'round';

  // тень от конвейера
  ctx.save();
  ctx.translate(0, 5);
  trackPath(ctx);
  ctx.lineWidth = 46;
  ctx.strokeStyle = 'rgba(0,0,0,0.38)';
  ctx.stroke();
  ctx.restore();

  // внутренний «остров»
  trackPath(ctx);
  ctx.fillStyle = vgrad(ctx, G.CY - G.R, G.CY + G.R, [[0, TH.island[0]], [1, TH.island[1]]]);
  ctx.fill();

  // рельсы: несколько слоёв — получается скруглённый металлический профиль
  const layers = [
    [42, TH.rail[0]],
    [40, danger ? `rgba(255,${90 + 60 * pulse | 0},${90 + 40 * pulse | 0},1)` : TH.rail[1]],
    [37.5, danger ? '#8a2432' : TH.rail[2]],
    [34, TH.rail[3]],
    [30, TH.rail[4]],
  ];
  for (const [lw, c] of layers) {
    trackPath(ctx);
    ctx.lineWidth = lw;
    ctx.strokeStyle = c;
    ctx.stroke();
  }

  // рёбра ленты — движутся вместе с шариками, создают ощущение скорости
  const rib0 = G.mod(game.offset, RIB);
  const count = Math.floor(G.L / RIB);
  ctx.lineCap = 'butt';
  ctx.beginPath();
  for (let i = 0; i < count; i++) {
    const s = i * RIB + rib0;
    const p = G.pos(s);
    const n = G.normal(s);
    ctx.moveTo(p.x - n.x * 14, p.y - n.y * 14);
    ctx.lineTo(p.x + n.x * 14, p.y + n.y * 14);
  }
  ctx.strokeStyle = TH.ribDark;
  ctx.lineWidth = 2.4;
  ctx.stroke();
  ctx.beginPath();
  for (let i = 0; i < count; i++) {
    const s = i * RIB + rib0 + 2.6;
    const p = G.pos(s);
    const n = G.normal(s);
    ctx.moveTo(p.x - n.x * 14, p.y - n.y * 14);
    ctx.lineTo(p.x + n.x * 14, p.y + n.y * 14);
  }
  ctx.strokeStyle = TH.ribLight;
  ctx.lineWidth = 1.6;
  ctx.stroke();

  // блик на верхнем рельсе
  ctx.beginPath();
  ctx.moveTo(G.CX - G.HALF, G.CY - G.R - 16.5);
  ctx.lineTo(G.CX + G.HALF, G.CY - G.R - 16.5);
  ctx.strokeStyle = 'rgba(255,255,255,0.45)';
  ctx.lineWidth = 1.4;
  ctx.stroke();

  // счётчик в «острове»
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = '800 15px system-ui, sans-serif';
  ctx.fillStyle = danger ? `rgba(255,${150 + 80 * pulse | 0},${150 + 60 * pulse | 0},1)` : TH.counter;
  ctx.fillText(`${game.occupied} / ${game.n}`, G.CX, G.CY + 0.5);
  ctx.restore();
}

// Лучи от конвейера к активным коробкам + светящиеся «захваты» на нижнем рельсе.
function drawBeams(ctx, game) {
  for (const b of game.boxes) {
    if (!b || b.done) continue;
    const p = b.fromStack ? 1 : Math.min(1, Math.max(0, b.enterT / ENTER_DUR));
    if (p <= 0) continue;
    const k = col(b.color);
    const cx = G.BOX_CX(b.i);
    const pp = G.pos(G.pickupS(cx));
    const y0 = pp.y + 20;
    const y1 = G.BOX_TOP + 4;
    ctx.save();
    ctx.globalAlpha = p;
    const g = ctx.createLinearGradient(0, y0, 0, y1);
    g.addColorStop(0, k.lt);
    g.addColorStop(1, k.base);
    ctx.globalAlpha = 0.34 * p;
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.moveTo(cx - 5, y0); ctx.lineTo(cx + 5, y0); ctx.lineTo(cx + 15, y1); ctx.lineTo(cx - 15, y1);
    ctx.closePath(); ctx.fill();
    ctx.globalAlpha = p;
    // захват на рельсе
    ctx.fillStyle = vgrad(ctx, y0 - 4, y0 + 5, [[0, k.hl], [1, k.dk]]);
    roundRect(ctx, cx - 9, y0 - 4, 18, 8, 4); ctx.fill();
    ctx.strokeStyle = k.deep; ctx.lineWidth = 1.4; ctx.stroke();
    ctx.restore();
  }
}

function drawBeltMarbles(ctx, game) {
  // входная труба сверху — шарик падает по ней, поэтому рисуем её как «козырёк»
  for (let i = 0; i < game.n; i++) {
    const m = game.slots[i];
    if (!m) continue;
    const s = game.slotS(i);
    const p = G.pos(s);
    let x = p.x, y = p.y, a = 1;
    if (m.drop > 0) {
      const e = G.pos(G.ENTRY_S);
      x += (m.fx - e.x) * m.drop;
      y += (m.fy - e.y) * m.drop * m.drop;
      a = 1;
    }
    drawMarble(ctx, m.color, x, y, G.MARBLE_R, a, m.drop <= 0.05);
  }
}

// ---------------------------------------------------------------- коробки

function paintBox(ctx, key, b, t, simple = false) {
  const k = col(key);
  const w = G.BOX_W, h = G.BOX_H;
  const x0 = -w / 2;

  if (!simple) drawShadow(ctx, 0, h + 5, w * 1.05, 20, 0.95);

  // корпус
  roundRect(ctx, x0, 0, w, h, 17);
  ctx.fillStyle = vgrad(ctx, 0, h, [[0, k.lt], [0.28, k.base], [1, k.dk]]);
  ctx.fill();
  ctx.save();
  roundRect(ctx, x0, 0, w, h, 17);
  ctx.clip();
  ctx.fillStyle = hgrad(ctx, x0, x0 + w, [[0, 'rgba(255,255,255,0.15)'], [0.25, 'rgba(255,255,255,0)'], [0.75, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.26)']]);
  ctx.fillRect(x0, 0, w, h);
  ctx.fillStyle = vgrad(ctx, h * 0.72, h, [[0, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.2)']]);
  ctx.fillRect(x0, h * 0.72, w, h * 0.28);
  // диагональные полосы внизу — «индустриальный» декор
  ctx.fillStyle = 'rgba(255,255,255,0.1)';
  for (let sx = x0 - 20; sx < x0 + w + 10; sx += 14) {
    ctx.beginPath();
    ctx.moveTo(sx, h - 14); ctx.lineTo(sx + 7, h - 14); ctx.lineTo(sx - 1, h); ctx.lineTo(sx - 8, h);
    ctx.closePath(); ctx.fill();
  }
  ctx.restore();
  ctx.strokeStyle = k.deep;
  ctx.lineWidth = 2.4;
  roundRect(ctx, x0, 0, w, h, 17); ctx.stroke();
  ctx.strokeStyle = 'rgba(255,255,255,0.3)';
  ctx.lineWidth = 1.6;
  roundRect(ctx, x0 + 2.2, 2.2, w - 4.4, h - 4.4, 15); ctx.stroke();
  if (key === 'X') {
    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    ctx.font = '800 13px system-ui, sans-serif';
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText('?', 0, h - 8);
  }
  if (simple) return;

  // панель с гнёздами
  const px = x0 + 6, py = 10, pw = w - 12, ph = 52;
  roundRect(ctx, px - 1.5, py - 1.5, pw + 3, ph + 4, 13);
  ctx.fillStyle = vgrad(ctx, py, py + ph, [[0, 'rgba(0,0,0,0.5)'], [1, 'rgba(255,255,255,0.22)']]);
  ctx.fill();
  roundRect(ctx, px, py, pw, ph, 11.5);
  ctx.fillStyle = vgrad(ctx, py, py + ph, [[0, k.deep], [1, k.dk]]);
  ctx.fill();
  ctx.save();
  roundRect(ctx, px, py, pw, ph, 11.5); ctx.clip();
  ctx.fillStyle = vgrad(ctx, py, py + 14, [[0, 'rgba(0,0,0,0.5)'], [1, 'rgba(0,0,0,0)']]);
  ctx.fillRect(px, py, pw, 14);
  ctx.restore();

  for (let s = 0; s < BOX_SIZE; s++) {
    const sx = (s - 1) * 22;
    const sy = 36;
    // гнездо
    const g = ctx.createRadialGradient(sx, sy - 1, 1, sx, sy, 12);
    g.addColorStop(0, 'rgba(0,0,0,0.78)');
    g.addColorStop(0.75, 'rgba(0,0,0,0.5)');
    g.addColorStop(1, 'rgba(0,0,0,0.2)');
    ctx.fillStyle = g;
    ctx.beginPath(); ctx.arc(sx, sy, 11.5, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.3)';
    ctx.lineWidth = 1.3;
    ctx.beginPath(); ctx.arc(sx, sy, 11.5, 0.15 * Math.PI, 0.85 * Math.PI); ctx.stroke();
    if (s < b.arrived) {
      const bump = b.bump[s];
      const sq = 1 + 0.2 * Math.sin(bump * Math.PI);
      drawMarble(ctx, key, sx, sy + 0.5, G.BOX_R, 1, false, 1 / sq, sq);
    }
  }

  // индикаторы прогресса на передней панели
  const ly = 78;
  roundRect(ctx, -22, ly - 7, 44, 14, 7);
  ctx.fillStyle = 'rgba(6,10,24,0.55)'; ctx.fill();
  ctx.strokeStyle = 'rgba(255,255,255,0.3)'; ctx.lineWidth = 1; ctx.stroke();
  for (let s = 0; s < BOX_SIZE; s++) {
    const on = s < b.arrived;
    ctx.fillStyle = on ? k.hl : 'rgba(255,255,255,0.18)';
    ctx.beginPath(); ctx.arc((s - 1) * 13, ly, 3.4, 0, Math.PI * 2); ctx.fill();
    if (on) {
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      const lg = ctx.createRadialGradient((s - 1) * 13, ly, 0, (s - 1) * 13, ly, 9);
      lg.addColorStop(0, k.lt); lg.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = lg;
      ctx.fillRect((s - 1) * 13 - 9, ly - 9, 18, 18);
      ctx.restore();
    }
  }

  // глянцевый блик по верху корпуса
  ctx.save();
  roundRect(ctx, x0, 0, w, h, 17); ctx.clip();
  const gg = ctx.createLinearGradient(0, 0, 0, 22);
  gg.addColorStop(0, 'rgba(255,255,255,0.26)');
  gg.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = gg;
  ctx.beginPath();
  ctx.moveTo(x0 + 6, 20); ctx.quadraticCurveTo(x0 + 10, 2, x0 + 32, 2); ctx.lineTo(x0 + w - 12, 2); ctx.lineTo(x0 + w - 12, 8);
  ctx.quadraticCurveTo(0, 6, x0 + 6, 20);
  ctx.fill();
  ctx.restore();

  // вспышка при заполнении
  if (b.flash > 0) {
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    ctx.globalAlpha = Math.min(1, b.flash) * 0.75;
    ctx.fillStyle = k.hl;
    roundRect(ctx, x0, 0, w, h, 17); ctx.fill();
    ctx.restore();
  }
}

const STACK_DY = 17;      // на сколько каждая следующая коробка стопки выглядывает вниз
const STACK_SC = 0.94;
const DUMMY_BOX = { arrived: 0, bump: [0, 0, 0], flash: 0 };

// «Магазины» под стопки коробок: утопленные металлические пазы с подсветкой.
function drawBoxSlots(ctx, game) {
  const x0 = -G.BOX_W / 2 - 1;
  const w = G.BOX_W + 2;
  const y0 = G.BOX_TOP - 1;
  const h = G.BOX_H + 2 * STACK_DY + 20;
  // общая платформа
  const px = G.BOX_CX(0) - G.BOX_W / 2 - 7, pw = G.BOX_CX(3) + G.BOX_W / 2 + 7 - px;
  roundRect(ctx, px, y0 - 5, pw, h + 10, 18);
  ctx.fillStyle = vgrad(ctx, y0 - 5, y0 + h + 5, [[0, TH.plat[0]], [1, TH.plat[1]]]);
  ctx.fill();
  ctx.strokeStyle = TH.platEdge; ctx.lineWidth = 1.5; ctx.stroke();
  for (let i = 0; i < 4; i++) {
    const cx = G.BOX_CX(i);
    const st = game.stacks[i];
    const has = !!game.boxes[i] || st.length > 0;
    ctx.save();
    ctx.translate(cx, 0);
    roundRect(ctx, x0, y0, w, h, 16);
    ctx.fillStyle = vgrad(ctx, y0, y0 + h, [[0, TH.slot[0]], [1, TH.slot[1]]]);
    ctx.fill();
    ctx.strokeStyle = has ? TH.slotEdge[0] : TH.slotEdge[1];
    ctx.lineWidth = 1.5; ctx.stroke();
    // блик по нижнему краю паза
    ctx.strokeStyle = 'rgba(255,255,255,0.12)';
    ctx.beginPath(); ctx.moveTo(x0 + 14, y0 + h - 1); ctx.lineTo(x0 + w - 14, y0 + h - 1); ctx.stroke();
    ctx.restore();
  }
}

function drawOneBox(ctx, game, b, i, t) {
  const cx = G.BOX_CX(i);
  const adv = game.stackAdv[i] || 0;
  const p = b.fromStack ? 1 : clamp01(b.enterT / ENTER_DUR);
  let alpha = 1, dy = 0, sc = 1, sy = 1;
  if (b.fromStack && !b.done) {
    // выдвигается вперёд из стопки
    dy = STACK_DY * adv;
    sc = Math.pow(STACK_SC, adv);
  } else if (!b.fromStack && p < 1) {
    const e = easeOutBack(p);
    dy = (1 - e) * 70;
    sc = 0.78 + 0.22 * Math.min(1, e);
    sy = 1 + (e > 1 ? (e - 1) * 0.5 : 0);
    alpha = clamp01(p * 3.2);
  }
  if (b.done) {
    const q = clamp01(b.popT / POP_DUR);
    if (q < 0.3) {
      sc = 1 + 0.15 * Math.sin((q / 0.3) * Math.PI * 0.5);
    } else {
      const r = (q - 0.3) / 0.7;
      sc = 1.15 - 0.4 * r;
      dy = r * r * 80;
      alpha = 1 - r * r;
    }
  }
  // кольцо-вспышка при посадке коробки
  if (b.flash > 0 && !b.done) {
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    ctx.strokeStyle = col(b.color).lt;
    ctx.globalAlpha = b.flash * 0.7;
    ctx.lineWidth = 3;
    const rr = 30 + (1 - b.flash) * 30;
    ctx.beginPath(); ctx.ellipse(cx, G.BOX_TOP + G.BOX_H + 4, rr, rr * 0.28, 0, 0, Math.PI * 2); ctx.stroke();
    ctx.restore();
  }
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.translate(cx, G.BOX_TOP + G.BOX_H / 2 + dy);
  ctx.scale(sc, sc * sy);
  ctx.translate(0, -G.BOX_H / 2);
  paintBox(ctx, b.color, b, t);
  ctx.restore();
}

function drawBoxes(ctx, game, t) {
  drawBoxSlots(ctx, game);
  game.boxes.forEach((b, i) => {
    const cx = G.BOX_CX(i);
    const adv = game.stackAdv[i] || 0;
    const p = !b || b.fromStack ? 1 : clamp01(b.enterT / ENTER_DUR);
    if (b && b.enterT < 0) return; // коробка ещё ждёт своей очереди на появление

    // стопка за активной коробкой: две следующие, дальние первыми. За туманом — «?»
    const st = game.stacks[i];
    for (let d = 2; d >= 1; d--) {
      const real = st[d - 1];
      if (!real) continue;
      const color = d <= game.fog.bottom ? real : 'X';
      const depth = d + adv;
      const a = (depth > 2 ? clamp01(3 - depth) : 1) * p;
      if (a <= 0) continue;
      const sc = Math.pow(STACK_SC, depth);
      ctx.save();
      ctx.globalAlpha = a;
      ctx.translate(cx, G.BOX_TOP + G.BOX_H / 2 + STACK_DY * depth);
      ctx.scale(sc, sc);
      ctx.translate(0, -G.BOX_H / 2);
      paintBox(ctx, color, DUMMY_BOX, t, true);
      ctx.globalAlpha = a * (0.03 + 0.06 * depth);
      ctx.fillStyle = TH.shade;
      roundRect(ctx, -G.BOX_W / 2, 0, G.BOX_W, G.BOX_H, 17);
      ctx.fill();
      ctx.restore();
    }
    if (b) drawOneBox(ctx, game, b, i, t);
  });
  // уезжающие заполненные коробки — поверх новых
  for (const b of game.leaving) drawOneBox(ctx, game, b, b.i, t);
}

function drawFlyers(ctx, game) {
  for (const f of game.flyers) {
    const at = (p) => {
      const e = easeOut(p);
      return {
        x: f.x0 + (f.x1 - f.x0) * e,
        y: f.y0 + (f.y1 - f.y0) * e - Math.sin(p * Math.PI) * 12,
        r: G.MARBLE_R + (G.BOX_R - G.MARBLE_R) * p,
      };
    };
    const p = Math.min(1, f.t / f.dur);
    // короткий светящийся хвост
    for (let k = 3; k >= 1; k--) {
      const q = p - k * 0.07;
      if (q <= 0) continue;
      const g = at(q);
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      ctx.globalAlpha = 0.16 * (4 - k);
      ctx.fillStyle = col(f.color).lt;
      ctx.beginPath(); ctx.arc(g.x, g.y, g.r * (1 - k * 0.12), 0, Math.PI * 2); ctx.fill();
      ctx.restore();
    }
    const c = at(p);
    drawMarble(ctx, f.color, c.x, c.y, c.r, 1, false);
  }
}

function drawConfetti(ctx, game) {
  for (const c of game.confetti) {
    const k = col(c.color);
    ctx.save();
    ctx.translate(c.x, c.y);
    ctx.rotate(c.rot);
    ctx.scale(1, Math.abs(Math.cos(c.phase)) * 0.8 + 0.2);
    ctx.fillStyle = k.base;
    ctx.fillRect(-c.w / 2, -c.h / 2, c.w, c.h);
    ctx.fillStyle = 'rgba(255,255,255,0.35)';
    ctx.fillRect(-c.w / 2, -c.h / 2, c.w, c.h / 2.5);
    ctx.restore();
  }
}

// ---------------------------------------------------------------- очередь коробок и эффекты

function drawFx(ctx, game) {
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  for (const p of game.fx) {
    const a = Math.max(0, p.life / p.max);
    const k = col(p.color);
    ctx.globalAlpha = Math.min(1, a * 1.5);
    ctx.fillStyle = a > 0.5 ? k.hl : k.lt;
    if (p.star) sparkle(ctx, p.x, p.y, 3 + 5 * a);
    else { ctx.beginPath(); ctx.arc(p.x, p.y, 1.6 + 2.4 * a, 0, Math.PI * 2); ctx.fill(); }
  }
  ctx.restore();
}
