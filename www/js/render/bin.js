// Накопитель (воронка со стеклом и затвором): статичные слои рисуются один раз, шарики, светодиод и шкала — каждый кадр.
import * as G from '../geometry.js';
import { TH } from './theme.js';
import { roundRect, vgrad, hgrad, fillCircle } from './draw-util.js';
import { drawMarble } from './marble.js';
import { binGauge } from '../viewmodel.js';

export function binPath(ctx, closeTop = true) {
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

// ---------- задний слой (за шариками)

function paintBinShadow(ctx) {
  ctx.save();
  ctx.translate(0, 7);
  binPath(ctx);
  ctx.fillStyle = 'rgba(0,0,0,0.35)';
  ctx.fill();
  ctx.restore();
}

// тонкая сетка на задней стенке
function paintGrid(ctx) {
  const b = G.BIN;
  ctx.strokeStyle = TH.grid;
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let gx = b.left + 14; gx < b.right; gx += 28) { ctx.moveTo(gx, b.top); ctx.lineTo(gx, b.floorY); }
  for (let gy = b.top + 14; gy < b.floorY; gy += 28) { ctx.moveTo(b.left, gy); ctx.lineTo(b.right, gy); }
  ctx.stroke();
}

function paintBackWall(ctx) {
  const b = G.BIN;
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
  paintGrid(ctx);
  ctx.restore();
}

// труба от затвора к конвейеру
function paintTube(ctx) {
  const tubeTop = G.BIN.floorY + 4, tubeBot = G.CY - G.R - 16;
  ctx.fillStyle = hgrad(ctx, G.CX - 15, G.CX + 15, [[0, TH.tube[0]], [0.25, TH.tube[1]], [0.5, TH.tube[2]], [1, TH.tube[3]]]);
  roundRect(ctx, G.CX - 15, tubeTop, 30, tubeBot - tubeTop, 4);
  ctx.fill();
  ctx.fillStyle = TH.tubeIn;
  roundRect(ctx, G.CX - 10, tubeTop - 1, 20, tubeBot - tubeTop + 1, 3);
  ctx.fill();
}

export function paintBinBack(ctx) {
  ctx.save();
  paintBinShadow(ctx);
  paintBackWall(ctx);
  paintTube(ctx);
  ctx.restore();
}

// ---------- передний слой (поверх шариков)

// стеклянная передняя панель с двумя бликами-полосами
function paintGlass(ctx) {
  const b = G.BIN;
  ctx.save();
  binPath(ctx);
  ctx.clip();
  ctx.fillStyle = vgrad(ctx, b.top, b.floorY, [[0, 'rgba(255,255,255,0.13)'], [0.5, 'rgba(255,255,255,0.03)'], [1, 'rgba(255,255,255,0.07)']]);
  ctx.fillRect(b.left, b.top, b.right - b.left, b.floorY - b.top);
  const stripe = (alpha, x0, x1, x2, x3) => {
    ctx.fillStyle = `rgba(255,255,255,${alpha})`;
    ctx.beginPath();
    ctx.moveTo(b.left + x0, b.top); ctx.lineTo(b.left + x1, b.top); ctx.lineTo(b.left + x2, b.floorY); ctx.lineTo(b.left + x3, b.floorY);
    ctx.closePath(); ctx.fill();
  };
  stripe(0.09, 30, 70, 20, -20);
  stripe(0.05, 90, 106, 56, 40);
  ctx.restore();
}

// металлическая рамка (без верхней кромки — накопитель открыт сверху)
function paintFrame(ctx) {
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  binPath(ctx, false);
  ctx.strokeStyle = TH.frameDark; ctx.lineWidth = 11; ctx.stroke();
  ctx.strokeStyle = TH.frameLight; ctx.lineWidth = 8; ctx.stroke();
  ctx.strokeStyle = TH.frameMid; ctx.lineWidth = 5.5; ctx.stroke();
  ctx.strokeStyle = TH.frameHi; ctx.lineWidth = 1.4;
  ctx.save(); ctx.translate(-1.5, -1.5); ctx.stroke(); ctx.restore();
}

function paintFlanges(ctx) {
  const b = G.BIN;
  for (const fx of [b.left, b.right]) {
    ctx.fillStyle = vgrad(ctx, b.top - 6, b.top + 4, [[0, TH.flange[0]], [1, TH.flange[1]]]);
    roundRect(ctx, fx - 9, b.top - 6, 18, 10, 4); ctx.fill();
    ctx.strokeStyle = TH.frameDark; ctx.lineWidth = 1.5; ctx.stroke();
  }
}

function paintGate(ctx) {
  const b = G.BIN;
  ctx.fillStyle = vgrad(ctx, b.floorY - 3, b.floorY + 8, [[0, TH.gate[0]], [0.4, TH.gate[1]], [1, TH.gate[2]]]);
  roundRect(ctx, b.gateL - 2, b.floorY - 3, b.gateR - b.gateL + 4, 11, 4); ctx.fill();
  ctx.strokeStyle = TH.frameDark; ctx.lineWidth = 1.5; ctx.stroke();
}

export function paintBinFront(ctx) {
  ctx.save();
  paintGlass(ctx);
  paintFrame(ctx);
  paintFlanges(ctx);
  paintGate(ctx);
  ctx.restore();
}

// ---------- динамика

export function drawBinMarbles(ctx, game) {
  const items = game.bin.items;
  for (let i = 0; i < items.length; i++) {
    const m = items[i];
    drawMarble(ctx, m.color, m.x, m.y, m.r, 1, true);
  }
}

// Светодиод затвора: вспыхивает, когда шарик уходит на конвейер.
function drawGateLed(ctx, glow) {
  const y = G.BIN.floorY + 3;
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  const lg = ctx.createRadialGradient(G.CX, y, 0, G.CX, y, 16);
  lg.addColorStop(0, `rgba(80,255,170,${0.15 + 0.7 * glow})`);
  lg.addColorStop(1, 'rgba(80,255,170,0)');
  ctx.fillStyle = lg;
  ctx.fillRect(G.CX - 18, G.BIN.floorY - 14, 36, 34);
  ctx.restore();
  ctx.fillStyle = glow > 0.05 ? '#b8ffe0' : '#2f9d77';
  fillCircle(ctx, G.CX, y, 2.6);
}

// Шкала заполнения накопителя.
function drawGauge(ctx, game) {
  const gx = 46, gy = G.BIN.floorY - 6, gw = 72, gh = 9;
  const { fill, hot, label } = binGauge(game);
  roundRect(ctx, gx, gy, gw, gh, 4.5);
  ctx.fillStyle = TH.gaugeBg; ctx.fill();
  ctx.strokeStyle = TH.gaugeEdge; ctx.lineWidth = 1; ctx.stroke();
  if (fill > 0.01) {
    ctx.fillStyle = hgrad(ctx, gx, gx + gw, [[0, '#35e0c0'], [0.65, '#ffd24d'], [1, hot ? '#ff4d5e' : '#ff9d4d']]);
    roundRect(ctx, gx + 1.5, gy + 1.5, Math.max(6, (gw - 3) * fill), gh - 3, 3); ctx.fill();
  }
  ctx.fillStyle = TH.gaugeText;
  ctx.font = '700 9px system-ui, sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  ctx.fillText(label, gx + gw + 6, gy + gh / 2 + 0.5);
}

export function drawBinHud(ctx, game) {
  ctx.save();
  drawGateLed(ctx, game.gateGlow);
  drawGauge(ctx, game);
  ctx.restore();
}
