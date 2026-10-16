// Игровая симуляция без DOM: лотки -> воронка -> конвейер -> коробки.
import { BOX_SIZE, ACTIVE_BOXES } from './levels.js';
import * as G from './geometry.js';

export const BELT_SPEED = 120;     // пикселей в секунду
const EMIT_INTERVAL = 0.07;        // пауза между шариками из лотка
const FLY_TO_CHUTE = 0.28;
const FLY_TO_BOX = 0.22;
const BOX_POP = 0.4;
const LOSE_DELAY = 0.7;

export class Game {
  constructor(level, onEvent) {
    this.level = level;
    this.emit = onEvent || (() => {});
    this.n = level.belt;
    this.spacing = G.L / this.n;
    this.offset = 0;
    this.time = 0;
    this.state = 'play';           // play | won | lost
    this.slots = new Array(this.n).fill(null);
    this.cols = level.columns.map((col) =>
      col.map((t) => ({ color: t.c, n: t.n, left: t.n, emitting: false, slide: 0 })));
    this.queue = level.boxes.slice(ACTIVE_BOXES);
    this.boxes = [];
    for (let i = 0; i < ACTIVE_BOXES; i++) {
      const c = level.boxes[i];
      this.boxes.push(c ? this.makeBox(c, i) : null);
    }
    this.flyers = [];
    this.emitters = [];
    this.fx = [];
    this.chute = [];               // цвета шариков в воронке, ждущих свободной ячейки
    this.taps = 0;
    this.stuckT = 0;
    this.endT = 0;
    this.nextId = 1;
  }

  makeBox(color, i) {
    return { color, i, f: 0, arrived: 0, done: false, popT: 0, enterT: 0 };
  }

  get pending() {
    return this.chute.length;
  }

  get occupied() {
    let c = 0;
    for (const s of this.slots) if (s) c++;
    return c;
  }

  slotS(i) {
    return G.mod(i * this.spacing + this.offset, G.L);
  }

  trayAt(x, y) {
    for (let ci = 0; ci < this.cols.length; ci++) {
      const t = this.cols[ci][0];
      if (!t) continue;
      const r = G.trayRect(ci, 0);
      const pad = 6;
      if (x >= r.x - pad && x <= r.x + r.w + pad && y >= r.y - pad && y <= r.y + r.h + pad) return ci;
    }
    return -1;
  }

  tap(x, y) {
    if (this.state !== 'play') return false;
    const ci = this.trayAt(x, y);
    if (ci < 0) return false;
    const tray = this.cols[ci][0];
    if (tray.emitting) return false;
    tray.emitting = true;
    this.emitters.push({ tray, ci, timer: 0 });
    this.taps++;
    this.emit('tap', { ci });
    return true;
  }

  update(dtRaw) {
    const dt = Math.min(dtRaw, 0.05);
    this.time += dt;

    if (this.state === 'play') {
      this.updateEmitters(dt);
      this.updateBelt(dt);
    }
    this.updateFlyers(dt);
    this.updateBoxes(dt);
    this.updateTrays(dt);
    this.updateFx(dt);
    if (this.state === 'play') this.checkEnd(dt);
    else this.endT += dt;
  }

  updateEmitters(dt) {
    for (let k = this.emitters.length - 1; k >= 0; k--) {
      const e = this.emitters[k];
      e.timer -= dt;
      while (e.timer <= 0 && e.tray.left > 0) {
        const r = G.trayRect(e.ci, 0);
        this.flyers.push({
          kind: 'chute', color: e.tray.color,
          x0: r.cx + (Math.random() - 0.5) * 30, y0: r.y + r.h - 10,
          x1: G.CHUTE.x, y1: G.CHUTE.y, t: 0, dur: FLY_TO_CHUTE,
        });
        e.tray.left--;
        e.timer += EMIT_INTERVAL;
      }
      if (e.tray.left === 0) {
        const col = this.cols[e.ci];
        col.shift();
        if (col[0]) col[0].slide = 1;
        this.emitters.splice(k, 1);
      }
    }
  }

  updateBelt(dt) {
    const step = G.L > 0 ? BELT_SPEED * dt : 0;
    const oldOffset = this.offset;
    this.offset += step;
    const sp = this.spacing;

    // 1) коробки забирают подходящие шарики, проезжающие мимо точки забора
    for (const b of this.boxes) {
      if (!b || b.done) continue;
      const t = G.pickupS(G.BOX_CX(b.i));
      for (let i = 0; i < this.n && b.f < BOX_SIZE; i++) {
        const m = this.slots[i];
        if (!m || m.color !== b.color) continue;
        const sOld = G.mod(i * sp + oldOffset, G.L);
        const d = G.mod(t - sOld, G.L);
        if (d < step) {
          this.slots[i] = null;
          const p = G.pos(t);
          const dest = G.boxSlotPos(b.i, b.f);
          b.f++;
          this.flyers.push({
            kind: 'box', color: m.color, box: b,
            x0: p.x, y0: p.y, x1: dest.x, y1: dest.y, t: 0, dur: FLY_TO_BOX,
          });
        }
      }
    }

    // 2) из воронки на свободную ячейку, проходящую точку входа
    if (this.pending > 0) {
      for (let i = 0; i < this.n && this.pending > 0; i++) {
        if (this.slots[i]) continue;
        const sOld = G.mod(i * sp + oldOffset, G.L);
        const d = G.mod(G.ENTRY_S - sOld, G.L);
        if (d < step) {
          this.slots[i] = { id: this.nextId++, color: this.chute.shift(), drop: 1 };
          this.emit('insert', {});
        }
      }
    }
    for (const m of this.slots) if (m && m.drop > 0) m.drop = Math.max(0, m.drop - dt / 0.14);
  }

  updateFlyers(dt) {
    for (let k = this.flyers.length - 1; k >= 0; k--) {
      const f = this.flyers[k];
      f.t += dt;
      if (f.t < f.dur) continue;
      this.flyers.splice(k, 1);
      if (f.kind === 'chute') {
        this.chute.push(f.color);
      } else if (f.kind === 'box') {
        const b = f.box;
        b.arrived++;
        this.burst(f.x1, f.y1, f.color, 5, 60);
        this.emit('capture', {});
        if (b.arrived >= BOX_SIZE && !b.done) {
          b.done = true;
          b.popT = 0;
          this.burst(G.BOX_CX(b.i), G.BOX_TOP + 40, b.color, 16, 130);
          this.emit('boxDone', {});
        }
      }
    }
  }

  updateBoxes(dt) {
    for (let i = 0; i < this.boxes.length; i++) {
      const b = this.boxes[i];
      if (!b) continue;
      if (b.enterT < 1) b.enterT = Math.min(1, b.enterT + dt / 0.28);
      if (b.done) {
        b.popT += dt;
        if (b.popT >= BOX_POP) {
          this.boxes[i] = this.queue.length ? this.makeBox(this.queue.shift(), i) : null;
        }
      }
    }
  }

  updateTrays(dt) {
    for (const col of this.cols) if (col[0] && col[0].slide > 0) col[0].slide = Math.max(0, col[0].slide - dt / 0.2);
  }

  updateFx(dt) {
    for (let k = this.fx.length - 1; k >= 0; k--) {
      const p = this.fx[k];
      p.life -= dt;
      if (p.life <= 0) { this.fx.splice(k, 1); continue; }
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.vy += 260 * dt;
    }
  }

  burst(x, y, color, n, speed) {
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2;
      const v = speed * (0.4 + Math.random() * 0.6);
      this.fx.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 40, life: 0.5 + Math.random() * 0.3, max: 0.8, color });
    }
  }

  canTake() {
    for (const m of this.slots) {
      if (!m) continue;
      for (const b of this.boxes) {
        if (b && !b.done && b.f < BOX_SIZE && b.color === m.color) return true;
      }
    }
    return false;
  }

  hasTrays() {
    return this.cols.some((c) => c.length > 0);
  }

  checkEnd(dt) {
    const boxesLeft = this.boxes.some(Boolean) || this.queue.length > 0;
    if (!boxesLeft && this.flyers.length === 0) {
      this.state = 'won';
      this.endT = 0;
      this.emit('win', {});
      return;
    }

    const full = this.occupied >= this.n;
    const capturing = this.flyers.some((f) => f.kind === 'box') || this.boxes.some((b) => b && b.done);
    const take = this.canTake();
    let stuck = false;
    if (!capturing && !take) {
      if (full) stuck = true; // конвейер забит, подходящих коробок нет
      else if (!this.hasTrays() && this.pending === 0 && this.emitters.length === 0 &&
        !this.flyers.some((f) => f.kind === 'chute') && this.occupied > 0) stuck = true; // тупик
    }
    this.stuckT = stuck ? this.stuckT + dt : 0;
    if (this.stuckT >= LOSE_DELAY) {
      this.state = 'lost';
      this.endT = 0;
      this.emit('lose', {});
    }
  }
}
