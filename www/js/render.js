// Отрисовка на canvas. Все спрайты шариков кэшируются в offscreen-canvas.
import * as G from './geometry.js';
import { BOX_SIZE } from './levels.js';

export const COLORS = {
  R: { base: '#ef4444', light: '#fca5a5', dark: '#991b1b' },
  B: { base: '#3b82f6', light: '#93c5fd', dark: '#1e3a8a' },
  G: { base: '#22c55e', light: '#86efac', dark: '#14532d' },
  Y: { base: '#facc15', light: '#fef08a', dark: '#854d0e' },
  P: { base: '#a855f7', light: '#d8b4fe', dark: '#581c87' },
  O: { base: '#fb923c', light: '#fed7aa', dark: '#9a3412' },
};

const spriteCache = new Map();
const SPRITE_PX = 64;

// Метка формы на шарике — для людей, не различающих цвета.
function glyph(ctx, key, cx, cy, r) {
  ctx.save();
  ctx.fillStyle = 'rgba(255,255,255,0.55)';
  ctx.strokeStyle = 'rgba(255,255,255,0.55)';
  ctx.lineWidth = r * 0.16;
  ctx.beginPath();
  switch (key) {
    case 'R': // треугольник
      ctx.moveTo(cx, cy - r * 0.42); ctx.lineTo(cx + r * 0.42, cy + r * 0.32); ctx.lineTo(cx - r * 0.42, cy + r * 0.32); ctx.closePath(); ctx.fill(); break;
    case 'B': // квадрат
      ctx.rect(cx - r * 0.3, cy - r * 0.3, r * 0.6, r * 0.6); ctx.fill(); break;
    case 'G': // кольцо
      ctx.arc(cx, cy, r * 0.32, 0, Math.PI * 2); ctx.stroke(); break;
    case 'Y': // ромб
      ctx.moveTo(cx, cy - r * 0.42); ctx.lineTo(cx + r * 0.36, cy); ctx.lineTo(cx, cy + r * 0.42); ctx.lineTo(cx - r * 0.36, cy); ctx.closePath(); ctx.fill(); break;
    case 'P': // плюс
      ctx.moveTo(cx - r * 0.34, cy); ctx.lineTo(cx + r * 0.34, cy); ctx.moveTo(cx, cy - r * 0.34); ctx.lineTo(cx, cy + r * 0.34); ctx.stroke(); break;
    default: // точка
      ctx.arc(cx, cy, r * 0.16, 0, Math.PI * 2); ctx.fill();
  }
  ctx.restore();
}

function sprite(key) {
  let c = spriteCache.get(key);
  if (c) return c;
  c = document.createElement('canvas');
  c.width = c.height = SPRITE_PX;
  const x = c.getContext('2d');
  const col = COLORS[key] || COLORS.R;
  const r = SPRITE_PX / 2 - 3;
  const m = SPRITE_PX / 2;
  x.shadowColor = 'rgba(0,0,0,0.35)';
  x.shadowBlur = 4;
  x.shadowOffsetY = 2;
  const g = x.createRadialGradient(m - r * 0.35, m - r * 0.4, r * 0.1, m, m, r);
  g.addColorStop(0, col.light);
  g.addColorStop(0.45, col.base);
  g.addColorStop(1, col.dark);
  x.fillStyle = g;
  x.beginPath();
  x.arc(m, m, r, 0, Math.PI * 2);
  x.fill();
  x.shadowColor = 'transparent';
  x.fillStyle = 'rgba(255,255,255,0.65)';
  x.beginPath();
  x.ellipse(m - r * 0.32, m - r * 0.42, r * 0.28, r * 0.17, -0.6, 0, Math.PI * 2);
  x.fill();
  glyph(x, key, m, m + r * 0.08, r);
  spriteCache.set(key, c);
  return c;
}

function drawMarble(ctx, key, x, y, r, alpha = 1) {
  const s = sprite(key);
  const d = r * 2 * (SPRITE_PX / (SPRITE_PX - 6));
  if (alpha !== 1) ctx.globalAlpha = alpha;
  ctx.drawImage(s, x - d / 2, y - d / 2, d, d);
  if (alpha !== 1) ctx.globalAlpha = 1;
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

const easeOut = (t) => 1 - Math.pow(1 - t, 3);

function trackPath(ctx) {
  ctx.beginPath();
  ctx.moveTo(G.CX - G.HALF, G.CY - G.R);
  ctx.lineTo(G.CX + G.HALF, G.CY - G.R);
  ctx.arc(G.CX + G.HALF, G.CY, G.R, -Math.PI / 2, Math.PI / 2);
  ctx.lineTo(G.CX - G.HALF, G.CY + G.R);
  ctx.arc(G.CX - G.HALF, G.CY, G.R, Math.PI / 2, Math.PI * 1.5);
  ctx.closePath();
}

export function draw(ctx, game) {
  const t = game.time;
  ctx.clearRect(-100, -100, G.W + 200, G.H + 200);

  drawTrays(ctx, game);
  drawBelt(ctx, game, t);
  drawBoxes(ctx, game);
  drawFlyers(ctx, game);
  drawFx(ctx, game);
}

function drawTrays(ctx, game) {
  game.cols.forEach((col, ci) => {
    // дальние лотки рисуем первыми
    for (let d = Math.min(col.length - 1, 3); d >= 0; d--) {
      const tray = col[d];
      const slide = d === 0 ? tray.slide : 0;
      const r = G.trayRect(ci, d);
      const y = r.y;
      const k = d === 0 && slide > 0 ? 0.93 + 0.07 * (1 - slide) : 1;
      const w = r.w * k, h = r.h * k;
      const x = r.cx - w / 2;
      const yy = y + (r.h - h);
      const col2 = COLORS[tray.color];
      ctx.save();
      ctx.globalAlpha = d === 0 ? 1 : Math.max(0.5, 0.85 - d * 0.12);
      // тень
      ctx.fillStyle = 'rgba(0,0,0,0.28)';
      roundRect(ctx, x + 2, yy + 5, w, h, 14); ctx.fill();
      // корпус
      const g = ctx.createLinearGradient(0, yy, 0, yy + h);
      g.addColorStop(0, col2.base);
      g.addColorStop(1, col2.dark);
      ctx.fillStyle = g;
      roundRect(ctx, x, yy, w, h, 14); ctx.fill();
      ctx.strokeStyle = 'rgba(255,255,255,0.35)';
      ctx.lineWidth = 2;
      roundRect(ctx, x + 1, yy + 1, w - 2, h - 2, 13); ctx.stroke();
      if (d === 0) {
        // внутренняя «чаша» и шарики
        ctx.fillStyle = 'rgba(0,0,0,0.25)';
        roundRect(ctx, x + 8, yy + 10, w - 16, h - 22, 10); ctx.fill();
        const rows = Math.ceil(tray.n / 3);
        for (let i = 0; i < tray.left; i++) {
          const cx = x + w / 2 + ((i % 3) - 1) * 26;
          const cy = yy + h / 2 + (Math.floor(i / 3) - (rows - 1) / 2) * 25;
          drawMarble(ctx, tray.color, cx, cy, G.SMALL_R);
        }
        if (!tray.emitting) {
          // мягкая подсказка «нажми»
          const pulse = 0.5 + 0.5 * Math.sin(game.time * 4 + ci);
          ctx.strokeStyle = `rgba(255,255,255,${0.15 + 0.2 * pulse})`;
          ctx.lineWidth = 3;
          roundRect(ctx, x - 3, yy - 3, w + 6, h + 6, 17); ctx.stroke();
        }
      } else {
        ctx.fillStyle = 'rgba(255,255,255,0.85)';
        ctx.font = `700 ${Math.round(15 * r.scale)}px system-ui, sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(String(tray.n), r.cx, yy + h / 2);
      }
      ctx.restore();
    }
  });
}

function drawBelt(ctx, game, t) {
  const danger = game.occupied >= game.n - 2;
  const pulse = 0.5 + 0.5 * Math.sin(t * 8);

  // воронка над точкой входа
  const ch = G.CHUTE;
  ctx.fillStyle = 'rgba(15,23,42,0.55)';
  ctx.beginPath();
  ctx.moveTo(ch.x - 26, ch.y - 16);
  ctx.lineTo(ch.x + 26, ch.y - 16);
  ctx.lineTo(ch.x + 9, G.CY - G.R - 14);
  ctx.lineTo(ch.x - 9, G.CY - G.R - 14);
  ctx.closePath();
  ctx.fill();
  for (let i = 0; i < Math.min(game.chute.length, 4); i++) {
    drawMarble(ctx, game.chute[i], ch.x, ch.y - 4 - i * 2, 9);
  }
  if (game.chute.length > 1) {
    ctx.fillStyle = '#fff';
    ctx.font = '700 12px system-ui, sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText('×' + game.chute.length, ch.x + 30, ch.y - 6);
  }

  // дорожка
  ctx.save();
  ctx.lineJoin = 'round';
  trackPath(ctx);
  ctx.lineWidth = 34;
  ctx.strokeStyle = 'rgba(8,15,35,0.55)';
  ctx.stroke();
  trackPath(ctx);
  ctx.lineWidth = 28;
  ctx.strokeStyle = danger ? `rgba(127,29,29,${0.55 + 0.25 * pulse})` : 'rgba(51,65,85,0.9)';
  ctx.stroke();
  // бегущие метки ячеек — дают ощущение движения
  for (let i = 0; i < game.n; i++) {
    const p = G.pos(game.slotS(i));
    ctx.fillStyle = 'rgba(255,255,255,0.14)';
    ctx.beginPath();
    ctx.arc(p.x, p.y, 3, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.restore();

  // счётчик в центре
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillStyle = danger ? '#fecaca' : 'rgba(226,232,240,0.9)';
  ctx.font = '800 34px system-ui, sans-serif';
  ctx.fillText(`${game.occupied}/${game.n}`, G.CX, G.CY - 4);
  ctx.fillStyle = 'rgba(148,163,184,0.9)';
  ctx.font = '600 11px system-ui, sans-serif';
  ctx.fillText(game.lang === 'ru' ? 'НА КОНВЕЙЕРЕ' : 'ON BELT', G.CX, G.CY + 22);

  // шарики
  for (let i = 0; i < game.n; i++) {
    const m = game.slots[i];
    if (!m) continue;
    const p = G.pos(game.slotS(i));
    const dy = -m.drop * 22;
    drawMarble(ctx, m.color, p.x, p.y + dy, G.MARBLE_R, 1 - m.drop * 0.4);
  }
}

function drawBoxes(ctx, game) {
  game.boxes.forEach((b, i) => {
    const cx = G.BOX_CX(i);
    if (!b) return;
    const col = COLORS[b.color];
    let sc = 1, alpha = 1, dy = 0;
    if (b.enterT < 1) {
      const e = easeOut(b.enterT);
      dy = (1 - e) * 50;
      alpha = e;
    }
    if (b.done) {
      const p = Math.min(1, b.popT / 0.4);
      sc = 1 + 0.18 * Math.sin(p * Math.PI) - p * 0.1;
      alpha = 1 - p * p;
    }

    // линия от коробки к точке забора на конвейере
    const ps = G.pickupS(cx);
    const pp = G.pos(ps);
    ctx.save();
    ctx.globalAlpha = 0.5 * alpha;
    ctx.strokeStyle = col.base;
    ctx.lineWidth = 3;
    ctx.setLineDash([3, 6]);
    ctx.beginPath();
    ctx.moveTo(cx, G.BOX_TOP + dy);
    ctx.lineTo(pp.x, pp.y + 16);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = col.base;
    ctx.beginPath();
    ctx.arc(pp.x, pp.y + 16, 4, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();

    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.translate(cx, G.BOX_TOP + G.BOX_H / 2 + dy);
    ctx.scale(sc, sc);
    ctx.translate(-cx, -(G.BOX_TOP + G.BOX_H / 2));
    const x = cx - G.BOX_W / 2, y = G.BOX_TOP;
    ctx.fillStyle = 'rgba(0,0,0,0.3)';
    roundRect(ctx, x + 2, y + 5, G.BOX_W, G.BOX_H, 12); ctx.fill();
    const g = ctx.createLinearGradient(0, y, 0, y + G.BOX_H);
    g.addColorStop(0, col.light);
    g.addColorStop(0.18, col.base);
    g.addColorStop(1, col.dark);
    ctx.fillStyle = g;
    roundRect(ctx, x, y, G.BOX_W, G.BOX_H, 12); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,0.4)';
    ctx.lineWidth = 2;
    roundRect(ctx, x + 1, y + 1, G.BOX_W - 2, G.BOX_H - 2, 11); ctx.stroke();
    // ячейки
    for (let k = 0; k < BOX_SIZE; k++) {
      const p = G.boxSlotPos(i, k);
      ctx.fillStyle = 'rgba(0,0,0,0.35)';
      ctx.beginPath();
      ctx.arc(p.x, p.y, G.SMALL_R + 2, 0, Math.PI * 2);
      ctx.fill();
      if (k < b.arrived) drawMarble(ctx, b.color, p.x, p.y, G.SMALL_R);
    }
    ctx.restore();
  });

  // очередь: сколько коробок осталось
  const left = game.queue.length;
  if (left > 0) {
    ctx.fillStyle = 'rgba(203,213,225,0.85)';
    ctx.font = '600 12px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText((game.lang === 'ru' ? 'Ещё коробок: ' : 'Boxes left: ') + left, G.W / 2, G.BOX_TOP + G.BOX_H + 18);
  }
}

function drawFlyers(ctx, game) {
  for (const f of game.flyers) {
    const p = Math.min(1, f.t / f.dur);
    const e = f.kind === 'chute' ? p * p : easeOut(p);
    const x = f.x0 + (f.x1 - f.x0) * e;
    let y = f.y0 + (f.y1 - f.y0) * e;
    if (f.kind === 'box') y -= Math.sin(p * Math.PI) * 10;
    drawMarble(ctx, f.color, x, y, f.kind === 'chute' ? G.SMALL_R : G.MARBLE_R - 1 * p);
  }
}

function drawFx(ctx, game) {
  for (const p of game.fx) {
    const a = Math.max(0, p.life / p.max);
    ctx.globalAlpha = Math.min(1, a * 1.4);
    ctx.fillStyle = (COLORS[p.color] || COLORS.R).light;
    ctx.beginPath();
    ctx.arc(p.x, p.y, 2 + 2 * a, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.globalAlpha = 1;
}
