// Летящие шарики, искры и конфетти — всё, что живёт недолго.
import { col } from './theme.js';
import { sparkle, fillCircle, glowing } from './draw-util.js';
import { drawMarble } from './marble.js';
import { flyerAt, flyerProgress } from '../viewmodel.js';

const TRAIL = 3;          // сколько «следов» тянется за шариком
const TRAIL_GAP = 0.07;   // расстояние между следами по времени полёта
const at = { x: 0, y: 0, r: 0 };

// короткий светящийся хвост за летящим шариком
function drawTrail(ctx, f, p) {
  glowing(ctx, 1, () => {
    ctx.fillStyle = col(f.color).lt;
    for (let k = TRAIL; k >= 1; k--) {
      const q = p - k * TRAIL_GAP;
      if (q <= 0) continue;
      flyerAt(at, f, q);
      ctx.globalAlpha = 0.16 * (TRAIL + 1 - k);
      fillCircle(ctx, at.x, at.y, at.r * (1 - k * 0.12));
    }
  });
}

export function drawFlyers(ctx, game) {
  for (const f of game.flyers) {
    const p = flyerProgress(f);
    drawTrail(ctx, f, p);
    flyerAt(at, f, p);
    drawMarble(ctx, f.color, at.x, at.y, at.r, 1, false);
  }
}

export function drawConfetti(ctx, game) {
  for (const c of game.effects.confetti) {
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

// Искры от захвата, посадки коробки и заполнения.
export function drawSparks(ctx, game) {
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  for (const p of game.effects.sparks) {
    const a = Math.max(0, p.life / p.max);
    const k = col(p.color);
    ctx.globalAlpha = Math.min(1, a * 1.5);
    ctx.fillStyle = a > 0.5 ? k.hl : k.lt;
    if (p.star) sparkle(ctx, p.x, p.y, 3 + 5 * a);
    else fillCircle(ctx, p.x, p.y, 1.6 + 2.4 * a);
  }
  ctx.restore();
}

