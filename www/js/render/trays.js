// Лотки сверху: корпус рисуется один раз и кэшируется, каждый кадр — поза, свечение и шарики внутри.
import * as G from '../geometry.js';
import { TH, col } from './theme.js';
import { makeCache, cached, offscreen, blit } from './cache.js';
import { roundRect, vgrad, hgrad, line, applyPose, questionMark } from './draw-util.js';
import { drawShadow } from './shadow.js';
import { drawMarble } from './marble.js';
import {
  makePose, trayPose, trayShownColor, trayIsSimple, trayDim, trayCup, trayMarbleSlot, MAX_TRAY_DEPTH,
} from '../viewmodel.js';

const bodyCache = makeCache();
const pose = makePose();
const slot = { x: 0, y: 0, r: 0 };

export function bucketPath(ctx, w, h) {
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

// ---------- статичный корпус (начало координат — верхний центр лотка)

function paintShell(ctx, k, w, h) {
  const t = w / 2;
  bucketPath(ctx, w, h);
  ctx.fillStyle = vgrad(ctx, 0, h, [[0, k.lt], [0.3, k.base], [1, k.dk]]);
  ctx.fill();
  ctx.save();
  ctx.clip();
  ctx.fillStyle = hgrad(ctx, -t, t, [[0, 'rgba(255,255,255,0.14)'], [0.28, 'rgba(255,255,255,0)'], [0.72, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.26)']]);
  ctx.fillRect(-t, 0, w, h);
  ctx.fillStyle = vgrad(ctx, h * 0.7, h, [[0, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.18)']]);
  ctx.fillRect(-t, h * 0.7, w, h * 0.3);
  ctx.restore();
  ctx.strokeStyle = k.deep;
  ctx.globalAlpha = 0.85;
  ctx.lineWidth = 2.2;
  bucketPath(ctx, w, h);
  ctx.stroke();
  ctx.globalAlpha = 1;
}

// блик по верхней кромке
function paintRimShine(ctx, w) {
  const t = w / 2;
  ctx.strokeStyle = 'rgba(255,255,255,0.42)';
  ctx.lineWidth = 2;
  line(ctx, -t + 15, 1.6, t - 15, 1.6);
}

// ёмкость: тёмная чаша с «бортиком» и тенью сверху
function paintCup(ctx, k, w, cup) {
  const t = w / 2;
  const wx = -t + 10, wy = cup.y, ww = w - 20, wh = cup.h;
  roundRect(ctx, wx - 2, wy - 2, ww + 4, wh + 5, 13);
  ctx.fillStyle = vgrad(ctx, wy, wy + wh, [[0, 'rgba(255,255,255,0.3)'], [1, 'rgba(255,255,255,0.08)']]);
  ctx.fill();
  roundRect(ctx, wx, wy, ww, wh, 11);
  ctx.fillStyle = vgrad(ctx, wy, wy + wh, [[0, k.deep], [0.55, k.dk], [1, k.dk]]);
  ctx.fill();
  ctx.save();
  roundRect(ctx, wx, wy, ww, wh, 11);
  ctx.clip();
  ctx.fillStyle = vgrad(ctx, wy, wy + 16, [[0, 'rgba(0,0,0,0.55)'], [1, 'rgba(0,0,0,0)']]);
  ctx.fillRect(wx, wy, ww, 16);
  ctx.restore();
}

// передняя панель: декоративные полоски
function paintFrontStripes(ctx, w, cup) {
  const t = w / 2;
  const fy = cup.y + cup.h + 8;
  ctx.strokeStyle = 'rgba(255,255,255,0.28)';
  ctx.lineWidth = 1.5;
  line(ctx, -t + 14, fy - 3, t - 14, fy - 3);
  ctx.strokeStyle = 'rgba(0,0,0,0.25)';
  line(ctx, -t + 14, fy - 1, t - 14, fy - 1);
}

// Дальние лотки чуть затемнены, чтобы лоток спереди выделялся: затемнение запекается в корпус (d — глубина лотка).
function paintDim(ctx, d) {
  if (d === 0) return;
  ctx.save();
  ctx.globalAlpha = trayDim(d);
  ctx.fillStyle = TH.shade;
  bucketPath(ctx, G.TRAY_W, G.TRAY_H);
  ctx.fill();
  ctx.restore();
}

function trayStatic(ctx, key, n, simple, d) {
  const w = G.TRAY_W, h = G.TRAY_H;
  const k = col(key);
  drawShadow(ctx, 0, h + 5, w, 18, 0.9);
  paintShell(ctx, k, w, h);
  paintRimShine(ctx, w);
  if (key === 'X') questionMark(ctx, 12, 15);
  else if (!simple) {
    const cup = trayCup(n);
    paintCup(ctx, k, w, cup);
    paintFrontStripes(ctx, w, cup);
  }
  paintDim(ctx, d);
}

// Три варианта по глубине: передний, второй и все дальше (затемнение у них разное).
function trayBody(key, n, simple, d) {
  const dim = Math.min(d, 2);
  return cached(bodyCache, `${key}|${simple ? 1 : 0}|${n > 6 ? 1 : 0}|${dim}`, () =>
    offscreen(-G.TRAY_W / 2 - 12, -12, G.TRAY_W + 24, G.TRAY_H + 40, (x) => trayStatic(x, key, n, simple, dim)));
}

// ---------- кадр

// свечение-подсказка вокруг ближнего лотка
function paintTrayGlow(ctx, key, glow) {
  if (glow <= 0) return;
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  ctx.strokeStyle = col(key).lt;
  ctx.globalAlpha = 0.1 + 0.16 * glow;
  ctx.lineWidth = 4;
  bucketPath(ctx, G.TRAY_W + 4, G.TRAY_H + 2);
  ctx.stroke();
  ctx.restore();
}

function paintTrayMarbles(ctx, key, tray) {
  for (let i = 0; i < tray.left; i++) {
    trayMarbleSlot(slot, tray.n, i);
    drawMarble(ctx, key, slot.x, slot.y, slot.r, 1, false);
  }
}

function paintTray(ctx, key, tray, simple, glow, d) {
  paintTrayGlow(ctx, key, glow);
  blit(ctx, trayBody(key, tray.n, simple, d));
  if (key === 'X' || simple) return;
  paintTrayMarbles(ctx, key, tray);
}

function drawTrayAt(ctx, game, ci, d, t) {
  const tray = game.cols[ci][d];
  const adv = game.colAdv[ci];
  trayPose(pose, tray, ci, d, adv, t);
  ctx.save();
  ctx.globalAlpha = pose.alpha;
  applyPose(ctx, pose, G.TRAY_H / 2);
  paintTray(ctx, trayShownColor(game, d, tray), tray, trayIsSimple(d, d + adv), pose.glow, d);
  ctx.restore();
}

export function drawTrays(ctx, game, t) {
  for (let ci = 0; ci < game.cols.length; ci++) {
    // дальние лотки первыми
    for (let d = Math.min(game.cols[ci].length - 1, MAX_TRAY_DEPTH); d >= 0; d--) drawTrayAt(ctx, game, ci, d, t);
  }
}
