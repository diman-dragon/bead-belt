// Конвейер-«стадион»: статичный металлический профиль рисуется один раз, ребра ленты, тревога и шарики — каждый кадр.
import * as G from '../geometry.js';
import { TH } from './theme.js';
import { vgrad, line } from './draw-util.js';
import { drawMarble } from './marble.js';
import { beltDanger, beltLabel, beltMarblePose } from '../viewmodel.js';

const RIB = 11;           // шаг ребер ленты
const LUT_STEP = 0.5;     // шаг таблицы точек пути
const pulseOf = (t) => 0.5 + 0.5 * Math.sin(t * 9);

function trackPath(ctx) {
  ctx.beginPath();
  ctx.moveTo(G.CX - G.HALF, G.CY - G.R);
  ctx.lineTo(G.CX + G.HALF, G.CY - G.R);
  ctx.arc(G.CX + G.HALF, G.CY, G.R, -Math.PI / 2, Math.PI / 2);
  ctx.lineTo(G.CX - G.HALF, G.CY + G.R);
  ctx.arc(G.CX - G.HALF, G.CY, G.R, Math.PI / 2, Math.PI * 1.5);
  ctx.closePath();
}

// Несколько слоёв разной толщины — получается скруглённый металлический профиль.
function strokeLayers(ctx, layers) {
  for (const [lw, c] of layers) {
    trackPath(ctx);
    ctx.lineWidth = lw;
    ctx.strokeStyle = c;
    ctx.stroke();
  }
}

// ---------- статичная часть

function paintBeltShadow(ctx) {
  ctx.save();
  ctx.translate(0, 5);
  trackPath(ctx);
  ctx.lineWidth = 46;
  ctx.strokeStyle = 'rgba(0,0,0,0.38)';
  ctx.stroke();
  ctx.restore();
}

// внутренний «остров»
function paintIsland(ctx) {
  trackPath(ctx);
  ctx.fillStyle = vgrad(ctx, G.CY - G.R, G.CY + G.R, [[0, TH.island[0]], [1, TH.island[1]]]);
  ctx.fill();
}

function paintRails(ctx) {
  strokeLayers(ctx, [[42, TH.rail[0]], [40, TH.rail[1]], [37.5, TH.rail[2]], [34, TH.rail[3]], [30, TH.rail[4]]]);
  ctx.strokeStyle = 'rgba(255,255,255,0.45)'; // блик на верхнем рельсе
  ctx.lineWidth = 1.4;
  line(ctx, G.CX - G.HALF, G.CY - G.R - 16.5, G.CX + G.HALF, G.CY - G.R - 16.5);
}

export function paintBelt(ctx) {
  ctx.save();
  ctx.lineJoin = 'round';
  paintBeltShadow(ctx);
  paintIsland(ctx);
  paintRails(ctx);
  ctx.restore();
}

// ---------- динамика

// Таблица точек пути — чтобы не считать тригонометрию и не создавать объекты на каждом кадре.
let ribLUT = null;
function getRibLUT() {
  if (ribLUT) return ribLUT;
  const n = Math.ceil(G.L / LUT_STEP);
  const px = new Float32Array(n), py = new Float32Array(n), nx = new Float32Array(n), ny = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const p = G.pos(i * LUT_STEP);
    const q = G.normal(i * LUT_STEP);
    px[i] = p.x; py[i] = p.y; nx[i] = q.x; ny[i] = q.y;
  }
  ribLUT = { n, px, py, nx, ny };
  return ribLUT;
}

// тревога: рельсы мигают красным
function paintDangerRails(ctx, pulse) {
  strokeLayers(ctx, [
    [40, `rgba(255,${90 + 60 * pulse | 0},${90 + 40 * pulse | 0},1)`],
    [37.5, '#8a2432'], [34, TH.rail[3]], [30, TH.rail[4]],
  ]);
}

// Рёбра ленты — движутся вместе с шариками, создают ощущение скорости.
function paintRibs(ctx, offset) {
  const L = getRibLUT();
  const rib0 = G.mod(offset, RIB);
  const count = Math.floor(G.L / RIB);
  ctx.lineCap = 'butt';
  for (let pass = 0; pass < 2; pass++) {
    const shift = pass ? 2.6 : 0;
    ctx.beginPath();
    for (let i = 0; i < count; i++) {
      const idx = Math.floor((i * RIB + rib0 + shift) / LUT_STEP) % L.n;
      const x = L.px[idx], y = L.py[idx], nx = L.nx[idx] * 14, ny = L.ny[idx] * 14;
      ctx.moveTo(x - nx, y - ny);
      ctx.lineTo(x + nx, y + ny);
    }
    ctx.strokeStyle = pass ? TH.ribLight : TH.ribDark;
    ctx.lineWidth = pass ? 1.6 : 2.4;
    ctx.stroke();
  }
}

// счётчик «занято / всего» в «острове»
function paintCounter(ctx, game, danger, pulse) {
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = '800 15px system-ui, sans-serif';
  ctx.fillStyle = danger ? `rgba(255,${150 + 80 * pulse | 0},${150 + 60 * pulse | 0},1)` : TH.counter;
  ctx.fillText(beltLabel(game), G.CX, G.CY + 0.5);
}

export function drawBeltDynamic(ctx, game, t) {
  const danger = beltDanger(game);
  const pulse = pulseOf(t);
  ctx.save();
  ctx.lineJoin = 'round';
  if (danger) paintDangerRails(ctx, pulse);
  paintRibs(ctx, game.offset);
  paintCounter(ctx, game, danger, pulse);
  ctx.restore();
}

const marblePose = { x: 0, y: 0, color: 'R', onBelt: true };

export function drawBeltMarbles(ctx, game) {
  for (let i = 0; i < game.n; i++) {
    if (!beltMarblePose(marblePose, game, i)) continue;
    drawMarble(ctx, marblePose.color, marblePose.x, marblePose.y, G.MARBLE_R, 1, marblePose.onBelt);
  }
}
