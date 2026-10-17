// Игровая симуляция без DOM: лотки -> накопитель -> конвейер -> коробки.
import { BOX_SIZE, ACTIVE_BOXES, boxStacks } from './levels.js';
import { Bin } from './bin.js';
import * as G from './geometry.js';

export const BELT_SPEED = 190;     // пикселей в секунду
const EMIT_INTERVAL = 0.075;       // пауза между шариками из лотка
const FLY_TO_BOX = 0.24;
const DROP_TIME = 0.17;            // падение шарика из накопителя на конвейер
export const ENTER_DUR = 0.55;     // появление коробки
export const POP_DUR = 0.62;       // исчезновение заполненной коробки
export const TRAY_LEAVE = 0.38;
const ADVANCE_DUR = 0.32;
const LOSE_DELAY = 0.8;

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
      col.map((t) => ({ color: t.c, n: t.n, left: t.n, state: 'idle', t: 0, pulse: 0 })));
    this.colAdv = this.cols.map(() => 0);   // 1 -> 0: лотки выдвигаются вперёд
    this.bin = new Bin();
    this.stacks = boxStacks(level);          // за активной коробкой в каждой стопке лежат следующие
    this.stackAdv = this.stacks.map(() => 0);
    this.leaving = [];                       // заполненные коробки, которые уезжают (на их месте уже стоит следующая)
    this.fog = { top: 2, bottom: 2, ...(level.fog || {}) }; // сколько следующих лотков/коробок видно заранее // 1 -> 0: стопка выдвигается вперёд
    this.boxes = [];
    for (let i = 0; i < ACTIVE_BOXES; i++) {
      const c = this.stacks[i].shift();
      this.boxes.push(c ? this.makeBox(c, i, 0.25 + i * 0.16) : null);
    }
    this.flyers = [];
    this.emitters = [];
    this.fx = [];
    this.taps = 0;
    this.stuckT = 0;
    this.endT = 0;
    this.gateGlow = 0;
    this.nextId = 1;
  }

  // fromStack: коробка выдвигается из стопки (а не выезжает снизу, как в начале уровня)
  makeBox(color, i, delay = 0.05, fromStack = false) {
    return { color, i, f: 0, arrived: 0, done: false, popT: 0, enterT: fromStack ? 0 : -delay, landed: fromStack, flash: 0, bump: [0, 0, 0], fromStack };
  }

  boxReady(b) {
    // коробка из стопки готова брать шарики сразу: пока старая уезжает, новая уже работает
    return b && !b.done && (b.fromStack || b.enterT >= ENTER_DUR * 0.7);
  }

  get pending() {
    return this.bin.count;
  }

  get occupied() {
    let c = 0;
    for (const s of this.slots) if (s) c++;
    return c;
  }

  // Шарики, которые уже в накопителе или ещё сыплются из лотков.
  get reserved() {
    let r = this.bin.count;
    for (const e of this.emitters) r += e.tray.left;
    return r;
  }

  slotS(i) {
    return G.mod(i * this.spacing + this.offset, G.L);
  }

  trayAt(x, y) {
    for (let ci = 0; ci < this.cols.length; ci++) {
      if (!this.cols[ci][0]) continue;
      const r = G.trayRect(ci, 0);
      const pad = 8;
      if (x >= r.x - pad && x <= r.x + r.w + pad && y >= r.y - pad && y <= r.y + r.h + pad) return ci;
    }
    return -1;
  }

  tap(x, y) {
    if (this.state !== 'play') return false;
    const ci = this.trayAt(x, y);
    if (ci < 0) return false;
    const tray = this.cols[ci][0];
    if (tray.state !== 'idle' || this.colAdv[ci] > 0.5) return false;
    if (this.reserved + tray.left > G.BIN_CAP) {
      this.emit('blocked', { ci });
      tray.pulse = -1; // «отказ»: лоток дёргается
      return false;
    }
    tray.state = 'pouring';
    tray.t = 0;
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
    this.bin.step(dt);
    this.updateFlyers(dt);
    this.updateBoxes(dt);
    this.updateTrays(dt);
    this.updateFx(dt);
    if (this.gateGlow > 0) this.gateGlow = Math.max(0, this.gateGlow - dt * 4);
    for (let i = 0; i < this.stackAdv.length; i++) {
      if (this.stackAdv[i] > 0) this.stackAdv[i] = Math.max(0, this.stackAdv[i] - dt / 0.34);
    }
    if (this.state === 'play') this.checkEnd(dt);
    else this.endT += dt;
  }

  updateEmitters(dt) {
    for (let k = this.emitters.length - 1; k >= 0; k--) {
      const e = this.emitters[k];
      const tray = e.tray;
      e.timer -= dt;
      while (e.timer <= 0 && tray.left > 0) {
        const r = G.trayRect(e.ci, 0);
        const x = Math.min(G.BIN.right - 26, Math.max(G.BIN.left + 26, r.cx + (Math.random() - 0.5) * 30));
        const vx = (G.CX - x) * 0.4 + (Math.random() - 0.5) * 50;
        this.bin.add(tray.color, x, r.y + r.h + 2, vx, 150 + Math.random() * 60);
        tray.left--;
        tray.pulse = 1;
        e.timer += EMIT_INTERVAL;
        this.emit('pour', {});
      }
      if (tray.left === 0) {
        tray.state = 'leaving';
        tray.t = 0;
        this.emitters.splice(k, 1);
      }
    }
  }

  updateBelt(dt) {
    const step = BELT_SPEED * dt;
    const oldOffset = this.offset;
    this.offset += step;
    const sp = this.spacing;

    // 1) коробки забирают подходящие шарики, проезжающие мимо точки забора
    for (const b of this.boxes) {
      if (!this.boxReady(b)) continue;
      const t = G.pickupS(G.BOX_CX(b.i));
      for (let i = 0; i < this.n && b.f < BOX_SIZE; i++) {
        const m = this.slots[i];
        if (!m || m.color !== b.color || m.drop > 0.3) continue;
        const sOld = G.mod(i * sp + oldOffset, G.L);
        const d = G.mod(t - sOld, G.L);
        if (d < step) {
          this.slots[i] = null;
          const p = G.pos(t);
          const dest = G.boxSlotPos(b.i, b.f);
          const k = b.f;
          b.f++;
          if (b.f >= BOX_SIZE) this.retire(b.i); // набрала всё — сразу уступает место следующей
          this.flyers.push({
            color: m.color, box: b, k,
            x0: p.x, y0: p.y, x1: dest.x, y1: dest.y, t: 0, dur: FLY_TO_BOX,
          });
        }
      }
    }

    // 2) из накопителя на свободную ячейку, проходящую точку входа
    if (this.bin.count > 0) {
      for (let i = 0; i < this.n; i++) {
        if (this.slots[i]) continue;
        const sOld = G.mod(i * sp + oldOffset, G.L);
        const d = G.mod(G.ENTRY_S - sOld, G.L);
        if (d < step) {
          const m = this.bin.take();
          if (!m) break;
          this.slots[i] = { id: this.nextId++, color: m.color, drop: 1, fx: m.x, fy: m.y };
          this.gateGlow = 1;
          this.emit('insert', {});
          if (this.bin.count === 0) break;
        }
      }
    }
    for (const m of this.slots) if (m && m.drop > 0) m.drop = Math.max(0, m.drop - dt / DROP_TIME);
  }

  // Заполненная коробка (все 3 шарика уже летят к ней) уезжает, а следующая из стопки встаёт на место МГНОВЕННО —
  // иначе шарики другого цвета, идущие вплотную за последним, проскакивали бы мимо во время анимации ухода.
  retire(i) {
    const b = this.boxes[i];
    if (!b) return;
    this.leaving.push(b);
    const next = this.stacks[i].shift();
    this.boxes[i] = next ? this.makeBox(next, i, 0, true) : null;
    if (next) this.stackAdv[i] = 1;
  }

  updateFlyers(dt) {
    for (let k = this.flyers.length - 1; k >= 0; k--) {
      const f = this.flyers[k];
      f.t += dt;
      if (f.t < f.dur) continue;
      this.flyers.splice(k, 1);
      const b = f.box;
      b.arrived++;
      b.bump[f.k] = 1;
      this.burst(f.x1, f.y1, f.color, 6, 70);
      this.emit('capture', {});
      if (b.arrived >= BOX_SIZE && !b.done) {
        b.done = true;
        b.popT = 0;
        b.flash = 1;
        this.burst(G.BOX_CX(b.i), G.BOX_TOP + 40, b.color, 26, 170);
        this.emit('boxDone', {});
      }
    }
  }

  updateBox(b, i, dt) {
    if (b.enterT < ENTER_DUR) {
      b.enterT += dt;
      if (b.enterT >= ENTER_DUR && !b.landed) {
        b.landed = true;
        b.flash = 0.6;
        this.burst(G.BOX_CX(i), G.BOX_TOP + G.BOX_H, b.color, 8, 60);
        this.emit('boxIn', {});
      }
    }
    if (b.flash > 0) b.flash = Math.max(0, b.flash - dt * 2.4);
    for (let k = 0; k < 3; k++) if (b.bump[k] > 0) b.bump[k] = Math.max(0, b.bump[k] - dt * 4.5);
    if (b.done) b.popT += dt;
  }

  updateBoxes(dt) {
    for (let i = 0; i < this.boxes.length; i++) {
      if (this.boxes[i]) this.updateBox(this.boxes[i], i, dt);
    }
    for (let k = this.leaving.length - 1; k >= 0; k--) {
      const b = this.leaving[k];
      this.updateBox(b, b.i, dt);
      if (b.done && b.popT >= POP_DUR) this.leaving.splice(k, 1);
    }
  }

  updateTrays(dt) {
    this.cols.forEach((col, ci) => {
      const tray = col[0];
      if (tray) {
        tray.t += dt;
        if (tray.pulse > 0) tray.pulse = Math.max(0, tray.pulse - dt * 7);
        else if (tray.pulse < 0) tray.pulse = Math.min(0, tray.pulse + dt * 4);
        if (tray.state === 'leaving' && tray.t >= TRAY_LEAVE) {
          col.shift();
          this.colAdv[ci] = 1;
        }
      }
      if (this.colAdv[ci] > 0) this.colAdv[ci] = Math.max(0, this.colAdv[ci] - dt / ADVANCE_DUR);
    });
  }

  updateFx(dt) {
    for (let k = this.fx.length - 1; k >= 0; k--) {
      const p = this.fx[k];
      p.life -= dt;
      if (p.life <= 0) { this.fx.splice(k, 1); continue; }
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.vy += 300 * dt;
    }
  }

  burst(x, y, color, n, speed) {
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2;
      const v = speed * (0.4 + Math.random() * 0.6);
      this.fx.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 50, life: 0.5 + Math.random() * 0.4, max: 0.9, color, star: Math.random() < 0.4 });
    }
  }

  canTake() {
    for (const m of this.slots) {
      if (!m) continue;
      for (const b of this.boxes) {
        if (this.boxReady(b) && b.f < BOX_SIZE && b.color === m.color) return true;
      }
    }
    return false;
  }

  hasTrays() {
    return this.cols.some((c) => c.length > 0);
  }

  checkEnd(dt) {
    const boxesLeft = this.boxes.some(Boolean) || this.stacks.some((st) => st.length > 0);
    if (!boxesLeft && this.flyers.length === 0 && this.leaving.length === 0) {
      this.state = 'won';
      this.endT = 0;
      this.emit('win', {});
      return;
    }

    const full = this.occupied >= this.n;
    const busy = this.flyers.length > 0 || this.leaving.length > 0 ||
      this.boxes.some((b) => b && !b.fromStack && b.enterT < ENTER_DUR);
    const take = this.canTake();
    let stuck = false;
    if (!busy && !take) {
      if (full) stuck = true; // конвейер забит, подходящих коробок нет
      else if (!this.hasTrays() && this.bin.count === 0 && this.emitters.length === 0 &&
        this.occupied > 0 && !this.slots.some((m) => m && m.drop > 0)) stuck = true; // тупик
    }
    this.stuckT = stuck ? this.stuckT + dt : 0;
    if (this.stuckT >= LOSE_DELAY) {
      this.state = 'lost';
      this.endT = 0;
      this.emit('lose', {});
    }
  }
}
