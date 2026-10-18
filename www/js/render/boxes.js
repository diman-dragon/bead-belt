// Коробки внизу: корпус рисуется один раз и кэшируется; каждый кадр — поза, шарики в гнёздах, огоньки и вспышки.
import * as G from '../geometry.js';
import { BOX_SIZE } from '../levels.js';
import { TH, col } from './theme.js';
import { makeCache, cached, offscreen, blit } from './cache.js';
import { roundRect, vgrad, hgrad, fillCircle, glowing, applyPose, questionMark } from './draw-util.js';
import { drawShadow } from './shadow.js';
import { drawMarble } from './marble.js';
import {
  makePose, boxPose, boxEnter, stackedBoxPose, stackedBoxShade, boxShownColor, boxSocket, boxMarbleSquash,
  BOX_MARBLE_DY, STACK_DY,
} from '../viewmodel.js';

const bodyCache = makeCache();
const beamCache = makeCache();
const pose = makePose();
const CORNER = 17;
const LED_Y = 78;       // ряд огоньков прогресса
const LED_STEP = 13;

// ---------- статичный корпус (начало координат — верхний центр коробки)

function paintShell(ctx, k, x0, w, h) {
  roundRect(ctx, x0, 0, w, h, CORNER);
  ctx.fillStyle = vgrad(ctx, 0, h, [[0, k.lt], [0.28, k.base], [1, k.dk]]);
  ctx.fill();
  ctx.save();
  roundRect(ctx, x0, 0, w, h, CORNER);
  ctx.clip();
  ctx.fillStyle = hgrad(ctx, x0, x0 + w, [[0, 'rgba(255,255,255,0.15)'], [0.25, 'rgba(255,255,255,0)'], [0.75, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.26)']]);
  ctx.fillRect(x0, 0, w, h);
  ctx.fillStyle = vgrad(ctx, h * 0.72, h, [[0, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.2)']]);
  ctx.fillRect(x0, h * 0.72, w, h * 0.28);
  paintHazardStripes(ctx, x0, w, h);
  ctx.restore();
  ctx.strokeStyle = k.deep;
  ctx.lineWidth = 2.4;
  roundRect(ctx, x0, 0, w, h, CORNER); ctx.stroke();
  ctx.strokeStyle = 'rgba(255,255,255,0.3)';
  ctx.lineWidth = 1.6;
  roundRect(ctx, x0 + 2.2, 2.2, w - 4.4, h - 4.4, CORNER - 2); ctx.stroke();
}

// диагональные полосы внизу — «индустриальный» декор
function paintHazardStripes(ctx, x0, w, h) {
  ctx.fillStyle = 'rgba(255,255,255,0.1)';
  for (let sx = x0 - 20; sx < x0 + w + 10; sx += 14) {
    ctx.beginPath();
    ctx.moveTo(sx, h - 14); ctx.lineTo(sx + 7, h - 14); ctx.lineTo(sx - 1, h); ctx.lineTo(sx - 8, h);
    ctx.closePath(); ctx.fill();
  }
}

// одно гнездо под шарик
function paintSocket(ctx, s) {
  const { x, y } = boxSocket(s);
  const g = ctx.createRadialGradient(x, y - 1, 1, x, y, 12);
  g.addColorStop(0, 'rgba(0,0,0,0.78)');
  g.addColorStop(0.75, 'rgba(0,0,0,0.5)');
  g.addColorStop(1, 'rgba(0,0,0,0.2)');
  ctx.fillStyle = g;
  fillCircle(ctx, x, y, 11.5);
  ctx.strokeStyle = 'rgba(255,255,255,0.3)';
  ctx.lineWidth = 1.3;
  ctx.beginPath(); ctx.arc(x, y, 11.5, 0.15 * Math.PI, 0.85 * Math.PI); ctx.stroke();
}

// панель с гнёздами
function paintSocketPanel(ctx, k, x0, w) {
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
  for (let s = 0; s < BOX_SIZE; s++) paintSocket(ctx, s);
}

// «окошко» индикаторов прогресса
function paintProgressWindow(ctx) {
  roundRect(ctx, -22, LED_Y - 7, 44, 14, 7);
  ctx.fillStyle = 'rgba(6,10,24,0.55)'; ctx.fill();
  ctx.strokeStyle = 'rgba(255,255,255,0.3)'; ctx.lineWidth = 1; ctx.stroke();
}

// глянцевый блик по верху корпуса
function paintTopGloss(ctx, x0, w, h) {
  ctx.save();
  roundRect(ctx, x0, 0, w, h, CORNER); ctx.clip();
  const gg = ctx.createLinearGradient(0, 0, 0, 22);
  gg.addColorStop(0, 'rgba(255,255,255,0.26)');
  gg.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = gg;
  ctx.beginPath();
  ctx.moveTo(x0 + 6, 20); ctx.quadraticCurveTo(x0 + 10, 2, x0 + 32, 2); ctx.lineTo(x0 + w - 12, 2); ctx.lineTo(x0 + w - 12, 8);
  ctx.quadraticCurveTo(0, 6, x0 + 6, 20);
  ctx.fill();
  ctx.restore();
}

// Коробка из стопки чуть затемнена: чем глубже, тем сильнее (запекается в корпус, d — глубина 1 или 2).
function paintStackShade(ctx, d) {
  ctx.save();
  ctx.globalAlpha = stackedBoxShade(d);
  ctx.fillStyle = TH.shade;
  roundRect(ctx, -G.BOX_W / 2, 0, G.BOX_W, G.BOX_H, CORNER);
  ctx.fill();
  ctx.restore();
}

// simple — «упрощённая» коробка из стопки (глубина d): без гнёзд и тени
function boxStatic(ctx, key, simple, d) {
  const k = col(key);
  const w = G.BOX_W, h = G.BOX_H, x0 = -w / 2;
  if (!simple) drawShadow(ctx, 0, h + 5, w * 1.05, 20, 0.95);
  paintShell(ctx, k, x0, w, h);
  if (key === 'X') questionMark(ctx, h - 8, 13);
  if (simple) { paintStackShade(ctx, d); return; }
  paintSocketPanel(ctx, k, x0, w);
  paintProgressWindow(ctx);
  paintTopGloss(ctx, x0, w, h);
}

// d — глубина в стопке (0 — активная коробка).
function boxBody(key, d) {
  return cached(bodyCache, `${key}|${d}`, () =>
    offscreen(-G.BOX_W / 2 - 10, -6, G.BOX_W + 20, G.BOX_H + 36, (x) => boxStatic(x, key, d > 0, d)));
}

// ---------- платформа с пазами под стопки (статичный слой)

export function paintBoxPlatform(ctx) {
  const x0 = -G.BOX_W / 2 - 1;
  const w = G.BOX_W + 2;
  const y0 = G.BOX_TOP - 1;
  const h = G.BOX_H + 2 * STACK_DY + 20;
  const px = G.BOX_CX(0) - G.BOX_W / 2 - 7, pw = G.BOX_CX(3) + G.BOX_W / 2 + 7 - px;
  roundRect(ctx, px, y0 - 5, pw, h + 10, 18);
  ctx.fillStyle = vgrad(ctx, y0 - 5, y0 + h + 5, [[0, TH.plat[0]], [1, TH.plat[1]]]);
  ctx.fill();
  ctx.strokeStyle = TH.platEdge; ctx.lineWidth = 1.5; ctx.stroke();
  for (let i = 0; i < 4; i++) {
    ctx.save();
    ctx.translate(G.BOX_CX(i), 0);
    roundRect(ctx, x0, y0, w, h, 16);
    ctx.fillStyle = vgrad(ctx, y0, y0 + h, [[0, TH.slot[0]], [1, TH.slot[1]]]);
    ctx.fill();
    ctx.strokeStyle = TH.slotEdge[0];
    ctx.lineWidth = 1.5; ctx.stroke();
    ctx.strokeStyle = 'rgba(255,255,255,0.12)'; // блик по нижнему краю паза
    ctx.beginPath(); ctx.moveTo(x0 + 14, y0 + h - 1); ctx.lineTo(x0 + w - 14, y0 + h - 1); ctx.stroke();
    ctx.restore();
  }
}

// ---------- кадр: коробка

function paintBoxMarbles(ctx, key, b) {
  for (let s = 0; s < b.arrived; s++) {
    const sq = boxMarbleSquash(b.bump[s]);
    const { x, y } = boxSocket(s);
    drawMarble(ctx, key, x, y + BOX_MARBLE_DY, G.BOX_R, 1, false, 1 / sq, sq);
  }
}

// индикаторы прогресса на передней панели
function paintLeds(ctx, k, arrived) {
  for (let s = 0; s < BOX_SIZE; s++) {
    const on = s < arrived;
    const x = (s - 1) * LED_STEP;
    ctx.fillStyle = on ? k.hl : 'rgba(255,255,255,0.18)';
    fillCircle(ctx, x, LED_Y, 3.4);
    if (on) {
      glowing(ctx, 0.7, () => { ctx.fillStyle = k.lt; fillCircle(ctx, x, LED_Y, 6.5); });
    }
  }
}

// вспышка при заполнении
function paintFillFlash(ctx, k, flash) {
  glowing(ctx, Math.min(1, flash) * 0.75, () => {
    ctx.fillStyle = k.hl;
    roundRect(ctx, -G.BOX_W / 2, 0, G.BOX_W, G.BOX_H, CORNER);
    ctx.fill();
  });
}

function paintBox(ctx, key, b) {
  blit(ctx, boxBody(key, 0));
  const k = col(key);
  paintBoxMarbles(ctx, key, b);
  paintLeds(ctx, k, b.arrived);
  if (b.flash > 0) paintFillFlash(ctx, k, b.flash);
}

// кольцо-вспышка на платформе при посадке коробки
function paintLandingRing(ctx, b) {
  glowing(ctx, b.flash * 0.7, () => {
    ctx.strokeStyle = col(b.color).lt;
    ctx.lineWidth = 3;
    const rr = 30 + (1 - b.flash) * 30;
    ctx.beginPath();
    ctx.ellipse(G.BOX_CX(b.i), G.BOX_TOP + G.BOX_H + 4, rr, rr * 0.28, 0, 0, Math.PI * 2);
    ctx.stroke();
  });
}

function drawBox(ctx, game, b) {
  boxPose(pose, b, game.stackAdv[b.i] || 0);
  if (b.flash > 0 && !b.done) paintLandingRing(ctx, b);
  ctx.save();
  ctx.globalAlpha = pose.alpha;
  applyPose(ctx, pose, G.BOX_H / 2);
  paintBox(ctx, b.color, b);
  ctx.restore();
}

// ---------- кадр: стопка за активной коробкой

// Две следующие коробки, дальние первыми. За туманом — «?».
function drawStack(ctx, game, i, enter) {
  const adv = game.stackAdv[i] || 0;
  const stack = game.stacks[i];
  for (let d = 2; d >= 1; d--) {
    const real = stack[d - 1];
    if (!real) continue;
    const depth = d + adv;
    stackedBoxPose(pose, i, depth, enter);
    if (pose.alpha <= 0) continue;
    ctx.save();
    ctx.globalAlpha = pose.alpha;
    applyPose(ctx, pose, G.BOX_H / 2);
    blit(ctx, boxBody(boxShownColor(game, d, real), d));
    ctx.restore();
  }
}

export function drawBoxes(ctx, game) {
  for (let i = 0; i < game.boxes.length; i++) {
    const b = game.boxes[i];
    if (b && b.enterT < 0) continue; // коробка ещё ждёт своей очереди на появление
    drawStack(ctx, game, i, b ? boxEnter(b) : 1);
    if (b) drawBox(ctx, game, b);
  }
  // уезжающие заполненные коробки — поверх новых
  for (const b of game.leaving) drawBox(ctx, game, b);
}

// ---------- лучи от конвейера к активным коробкам

function beamImage(color, i) {
  return cached(beamCache, color + i, () => {
    const k = col(color);
    const cx = G.BOX_CX(i);
    const y0 = G.pos(G.pickupS(cx)).y + 20;
    const y1 = G.BOX_TOP + 4;
    return offscreen(cx - 22, y0 - 10, 44, y1 - y0 + 14, (c) => {
      const g = c.createLinearGradient(0, y0, 0, y1);
      g.addColorStop(0, k.lt);
      g.addColorStop(1, k.base);
      c.globalAlpha = 0.34;
      c.fillStyle = g;
      c.beginPath();
      c.moveTo(cx - 5, y0); c.lineTo(cx + 5, y0); c.lineTo(cx + 15, y1); c.lineTo(cx - 15, y1);
      c.closePath(); c.fill();
      c.globalAlpha = 1;
      c.fillStyle = vgrad(c, y0 - 4, y0 + 5, [[0, k.hl], [1, k.dk]]); // захват на рельсе
      roundRect(c, cx - 9, y0 - 4, 18, 8, 4); c.fill();
      c.strokeStyle = k.deep; c.lineWidth = 1.4; c.stroke();
    });
  });
}

export function drawBeams(ctx, game) {
  for (const b of game.boxes) {
    if (!b || b.done) continue;
    const p = boxEnter(b);
    if (p <= 0) continue;
    if (p !== 1) ctx.globalAlpha = p;
    blit(ctx, beamImage(b.color, b.i));
    if (p !== 1) ctx.globalAlpha = 1;
  }
}
