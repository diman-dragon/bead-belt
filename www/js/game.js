// Игровая симуляция без DOM: лотки -> накопитель -> конвейер -> коробки.
import { BOX_SIZE, ACTIVE_BOXES, boxStacks } from './levels.js';
import { Bin } from './bin.js';
import { Particles } from './particles.js';
import * as G from './geometry.js';

export const BELT_SPEED = 190;     // пикселей в секунду
const EMIT_INTERVAL = 0.075;       // пауза между шариками из лотка
const FLY_TO_BOX = 0.24;
const DROP_TIME = 0.17;            // падение шарика из накопителя на конвейер
export const ENTER_DUR = 0.55;     // появление коробки
export const POP_DUR = 0.62;       // исчезновение заполненной коробки
export const TRAY_LEAVE = 0.38;
const ADVANCE_DUR = 0.32;           // выдвижение следующего лотка
const STACK_ADVANCE_DUR = 0.34;     // выдвижение следующей коробки
const LOSE_DELAY = 0.8;

export class Game {
  constructor(level, onEvent) {
    this.level = level;
    this.emit = onEvent || (() => {});
    this.fog = { top: 2, bottom: 2, ...(level.fog || {}) }; // сколько следующих лотков/коробок видно заранее
    this.initBelt(level);
    this.initTrays(level);
    this.initBoxes(level);
    this.bin = new Bin();
    this.effects = new Particles();
    this.flyers = [];       // шарики, летящие с конвейера в коробки
    this.emitters = [];     // лотки, которые сейчас высыпаются
    this.time = 0;
    this.state = 'play';    // play | won | lost
    this.taps = 0;
    this.stuckT = 0;
    this.endT = 0;
    this.gateGlow = 0;
    this.nextId = 1;
  }

  initBelt(level) {
    this.n = level.belt;
    this.spacing = G.L / this.n;
    this.offset = 0;
    this.slots = new Array(this.n).fill(null);
  }

  initTrays(level) {
    this.cols = level.columns.map((col) =>
      col.map((t) => ({ color: t.c, n: t.n, left: t.n, state: 'idle', t: 0, pulse: 0 })));
    this.colAdv = this.cols.map(() => 0);   // 1 -> 0: лотки выдвигаются вперёд
  }

  initBoxes(level) {
    this.stacks = boxStacks(level);          // за активной коробкой в каждой стопке лежат следующие
    this.stackAdv = this.stacks.map(() => 0); // 1 -> 0: стопка выдвигается вперёд
    this.totalBoxes = level.boxes.length;    // для полоски прогресса
    this.doneBoxes = 0;
    this.leaving = [];                       // заполненные коробки, которые уезжают (на их месте уже стоит следующая)
    this.boxes = [];
    for (let i = 0; i < ACTIVE_BOXES; i++) {
      const c = this.stacks[i].shift();
      this.boxes.push(c ? this.makeBox(c, i, 0.25 + i * 0.16) : null);
    }
  }

  // fromStack: коробка выдвигается из стопки (а не выезжает снизу, как в начале уровня)
  makeBox(color, i, delay = 0.05, fromStack = false) {
    return { color, i, f: 0, arrived: 0, done: false, popT: 0, enterT: fromStack ? 0 : -delay, landed: fromStack, flash: 0, bump: [0, 0, 0], fromStack };
  }

  boxReady(b) {
    // коробка из стопки готова брать шарики сразу: пока старая уезжает, новая уже работает
    return b && !b.done && (b.fromStack || b.enterT >= ENTER_DUR * 0.7);
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
    this.effects.update(dt);
    this.decayTimers(dt);
    if (this.state === 'play') this.checkEnd(dt);
    else this.endT += dt;
  }

  // Плавное затухание свечения затвора и «выдвижения» стопок коробок.
  decayTimers(dt) {
    if (this.gateGlow > 0) this.gateGlow = Math.max(0, this.gateGlow - dt * 4);
    for (let i = 0; i < this.stackAdv.length; i++) {
      if (this.stackAdv[i] > 0) this.stackAdv[i] = Math.max(0, this.stackAdv[i] - dt / STACK_ADVANCE_DUR);
    }
  }

  updateEmitters(dt) {
    for (let k = this.emitters.length - 1; k >= 0; k--) {
      const e = this.emitters[k];
      const tray = e.tray;
      e.timer -= dt;
      while (e.timer <= 0 && tray.left > 0) {
        this.pourOne(e);
        e.timer += EMIT_INTERVAL;
      }
      if (tray.left === 0) {
        tray.state = 'leaving';
        tray.t = 0;
        this.emitters.splice(k, 1);
      }
    }
  }

  // Один шарик из лотка падает в накопитель.
  pourOne(e) {
    const tray = e.tray;
    const r = G.trayRect(e.ci, 0);
    const x = Math.min(G.BIN.right - 26, Math.max(G.BIN.left + 26, r.cx + (Math.random() - 0.5) * 30));
    const vx = (G.CX - x) * 0.4 + (Math.random() - 0.5) * 50;
    this.bin.add(tray.color, x, r.y + r.h + 2, vx, 150 + Math.random() * 60);
    tray.left--;
    tray.pulse = 1;
    this.emit('pour', {});
  }

  updateBelt(dt) {
    const step = BELT_SPEED * dt;
    const oldOffset = this.offset;
    this.offset += step;
    this.collectIntoBoxes(step, oldOffset);
    this.feedFromBin(step, oldOffset);
    this.settleDrops(dt);
  }

  // Где ячейка i была до сдвига ленты.
  slotSBefore(i, oldOffset) {
    return G.mod(i * this.spacing + oldOffset, G.L);
  }

  // Коробки забирают подходящие шарики, проезжающие мимо точки забора.
  collectIntoBoxes(step, oldOffset) {
    for (const b of this.boxes) {
      if (!this.boxReady(b)) continue;
      const t = G.pickupS(G.BOX_CX(b.i));
      for (let i = 0; i < this.n && b.f < BOX_SIZE; i++) {
        const m = this.slots[i];
        if (!m || m.color !== b.color || m.drop > 0.3) continue;
        if (G.mod(t - this.slotSBefore(i, oldOffset), G.L) < step) this.grab(b, i, t);
      }
    }
  }

  // Коробка b забирает шарик из ячейки i в точке пути t: он летит в свободное гнездо.
  grab(b, i, t) {
    const m = this.slots[i];
    this.slots[i] = null;
    const p = G.pos(t);
    const dest = G.boxSlotPos(b.i, b.f);
    const k = b.f;
    b.f++;
    if (b.f >= BOX_SIZE) this.retire(b.i); // набрала всё — сразу уступает место следующей
    this.flyers.push({ color: m.color, box: b, k, x0: p.x, y0: p.y, x1: dest.x, y1: dest.y, t: 0, dur: FLY_TO_BOX });
  }

  // Из накопителя шарик падает на свободную ячейку, проходящую точку входа.
  feedFromBin(step, oldOffset) {
    for (let i = 0; i < this.n && this.bin.count > 0; i++) {
      if (this.slots[i]) continue;
      if (G.mod(G.ENTRY_S - this.slotSBefore(i, oldOffset), G.L) >= step) continue;
      const m = this.bin.take();
      if (!m) break;
      this.slots[i] = { id: this.nextId++, color: m.color, drop: 1, fx: m.x, fy: m.y };
      this.gateGlow = 1;
      this.emit('insert', {});
    }
  }

  // Шарики, упавшие на ленту, «досаживаются» в ячейку.
  settleDrops(dt) {
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
      this.land(f);
    }
  }

  // Шарик долетел: занимает гнездо, а заполненная коробка помечается готовой.
  land(f) {
    const b = f.box;
    b.arrived++;
    b.bump[f.k] = 1;
    this.effects.burst(f.x1, f.y1, f.color, 6, 70);
    this.emit('capture', {});
    if (b.arrived >= BOX_SIZE && !b.done) this.complete(b);
  }

  complete(b) {
    b.done = true;
    this.doneBoxes++;
    b.popT = 0;
    b.flash = 1;
    this.effects.burst(G.BOX_CX(b.i), G.BOX_TOP + 40, b.color, 26, 170);
    this.emit('boxDone', {});
  }

  updateBox(b, dt) {
    if (b.enterT < ENTER_DUR) {
      b.enterT += dt;
      if (b.enterT >= ENTER_DUR && !b.landed) this.touchDown(b);
    }
    if (b.flash > 0) b.flash = Math.max(0, b.flash - dt * 2.4);
    for (let k = 0; k < BOX_SIZE; k++) if (b.bump[k] > 0) b.bump[k] = Math.max(0, b.bump[k] - dt * 4.5);
    if (b.done) b.popT += dt;
  }

  // Коробка, выехавшая снизу, встала на место.
  touchDown(b) {
    b.landed = true;
    b.flash = 0.6;
    this.effects.burst(G.BOX_CX(b.i), G.BOX_TOP + G.BOX_H, b.color, 8, 60);
    this.emit('boxIn', {});
  }

  updateBoxes(dt) {
    for (const b of this.boxes) if (b) this.updateBox(b, dt);
    for (let k = this.leaving.length - 1; k >= 0; k--) {
      const b = this.leaving[k];
      this.updateBox(b, dt);
      if (b.done && b.popT >= POP_DUR) this.leaving.splice(k, 1);
    }
  }

  updateTrays(dt) {
    for (let ci = 0; ci < this.cols.length; ci++) this.updateColumn(ci, dt);
  }

  updateColumn(ci, dt) {
    const col = this.cols[ci];
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

  // Все коробки собраны и ничего не летит.
  isCleared() {
    const boxesLeft = this.boxes.some(Boolean) || this.stacks.some((st) => st.length > 0);
    return !boxesLeft && this.flyers.length === 0 && this.leaving.length === 0;
  }

  // Что-то ещё происходит: шарики летят, коробки уезжают или выезжают.
  isBusy() {
    return this.flyers.length > 0 || this.leaving.length > 0 ||
      this.boxes.some((b) => b && !b.fromStack && b.enterT < ENTER_DUR);
  }

  // Конвейер забит без подходящих коробок или всё высыпано и дальше хода нет.
  isDeadEnd() {
    if (this.isBusy() || this.canTake()) return false;
    if (this.occupied >= this.n) return true;
    return !this.hasTrays() && this.bin.count === 0 && this.emitters.length === 0 &&
      this.occupied > 0 && !this.slots.some((m) => m && m.drop > 0);
  }

  win() {
    this.state = 'won';
    this.endT = 0;
    this.effects.spawnConfetti();
    this.emit('win', {});
  }

  lose() {
    this.state = 'lost';
    this.endT = 0;
    this.emit('lose', {});
  }

  checkEnd(dt) {
    if (this.isCleared()) { this.win(); return; }
    this.stuckT = this.isDeadEnd() ? this.stuckT + dt : 0;
    if (this.stuckT >= LOSE_DELAY) this.lose();
  }
}
