// Подсказка внутри пустого накопителя: пока игрок не нажал на лоток.
import * as G from '../geometry.js';
import { roundRect } from './draw-util.js';
import { hintVisible } from '../viewmodel.js';

const FONT = '700 14px system-ui, sans-serif';
const LINE_H = 19;
let measured = { lines: null, w: 0 };

// Ширина самой длинной строки — считается один раз для набора строк, а не на каждом кадре.
function hintWidth(ctx, lines) {
  if (measured.lines !== lines) {
    ctx.font = FONT;
    measured = { lines, w: Math.max(...lines.map((l) => ctx.measureText(l).width)) + 34 };
  }
  return measured.w;
}

function paintBubble(ctx, cx, top, w, h) {
  roundRect(ctx, cx - w / 2, top, w, h, 16);
  ctx.fillStyle = 'rgba(6,12,30,0.78)';
  ctx.fill();
  ctx.strokeStyle = 'rgba(160,200,255,0.5)';
  ctx.lineWidth = 1.5;
  ctx.stroke();
}

function paintLines(ctx, lines, cx, cy) {
  ctx.fillStyle = '#eaf2ff';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  lines.forEach((l, i) => ctx.fillText(l, cx, cy + (i - (lines.length - 1) / 2) * LINE_H));
}

// стрелка вверх, к лоткам
function paintArrow(ctx, cx, ay) {
  ctx.fillStyle = '#7dd3fc';
  ctx.beginPath();
  ctx.moveTo(cx, ay - 8); ctx.lineTo(cx + 11, ay + 6); ctx.lineTo(cx + 4, ay + 6); ctx.lineTo(cx + 4, ay + 13);
  ctx.lineTo(cx - 4, ay + 13); ctx.lineTo(cx - 4, ay + 6); ctx.lineTo(cx - 11, ay + 6);
  ctx.closePath(); ctx.fill();
}

export function drawHint(ctx, game, t) {
  if (!hintVisible(game)) return;
  const lines = game.hintText;
  const cx = G.CX, cy = G.BIN.top + 52;
  const h = 22 + lines.length * LINE_H;
  ctx.save();
  ctx.font = FONT;
  const w = hintWidth(ctx, lines);
  paintBubble(ctx, cx, cy - h / 2 + 8, w, h);
  paintLines(ctx, lines, cx, cy + 8);
  paintArrow(ctx, cx, cy - h / 2 - 2 + Math.sin(t * 4) * 4);
  ctx.restore();
}
