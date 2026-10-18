// Накопитель: шарики сыплются из лотков, катятся и перемешиваются (простая физика кругов).
// Без DOM — работает и в браузере, и в Node (для симуляции).
import * as G from './geometry.js';

const GRAVITY = 1250;
const REST = 0.22;       // упругость
const WALL_FRICTION = 0.04;
const FIXED_H = 1 / 120;  // шаг физики
const ITER = 4;

// Раздвигает два пересекающихся шарика и гасит их скорость сближения. dx = b.x - a.x уже посчитан.
function collidePair(a, b, dx) {
  const dy = b.y - a.y;
  const min = a.r + b.r;
  const d2 = dx * dx + dy * dy;
  if (d2 >= min * min || d2 < 1e-8) return;
  const d = Math.sqrt(d2);
  const nx = dx / d;
  const ny = dy / d;
  const push = (min - d) * 0.5;
  a.x -= nx * push; a.y -= ny * push;
  b.x += nx * push; b.y += ny * push;
  const vn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
  if (vn >= 0) return;
  const e = vn < -60 ? REST : 0;
  const imp = -(1 + e) * vn * 0.5;
  a.vx -= imp * nx; a.vy -= imp * ny;
  b.vx += imp * nx; b.vy += imp * ny;
}

// Выталкивает шарик из стенки-отрезка и отражает скорость с небольшим трением вдоль стенки.
function collideSegment(m, sg) {
  const ax = sg[0], ay = sg[1];
  const ex = sg[2] - ax, ey = sg[3] - ay;
  const len2 = ex * ex + ey * ey;
  let t = ((m.x - ax) * ex + (m.y - ay) * ey) / len2;
  t = t < 0 ? 0 : t > 1 ? 1 : t;
  const dx = m.x - (ax + ex * t), dy = m.y - (ay + ey * t);
  const d2 = dx * dx + dy * dy;
  if (d2 >= m.r * m.r) return;
  let d = Math.sqrt(d2);
  let nx, ny;
  if (d < 1e-6) { // центр на стенке: нормаль берём от отрезка (внутрь накопителя)
    const l = Math.sqrt(len2);
    nx = ey / l; ny = -ex / l;
    if ((m.x - G.CX) * nx < 0 && Math.abs(nx) > 0.5) { nx = -nx; ny = -ny; }
    d = 0;
  } else { nx = dx / d; ny = dy / d; }
  m.x += nx * (m.r - d);
  m.y += ny * (m.r - d);
  const vn = m.vx * nx + m.vy * ny;
  if (vn >= 0) return;
  const e = vn < -70 ? REST : 0;
  m.vx -= (1 + e) * vn * nx;
  m.vy -= (1 + e) * vn * ny;
  const tx = -ny, ty = nx;
  const vt = m.vx * tx + m.vy * ty;
  m.vx -= vt * WALL_FRICTION * tx;
  m.vy -= vt * WALL_FRICTION * ty;
}

export class Bin {
  constructor() {
    this.items = [];
    this.segs = G.binSegments();
    this.nextId = 1;
    this.acc = 0;
  }

  get count() {
    return this.items.length;
  }

  add(color, x, y, vx = 0, vy = 0) {
    this.items.push({ id: this.nextId++, color, x, y, vx, vy, r: G.BIN_R, squash: 0 });
  }

  // Подбросить всё содержимое (встряска от нового потока шариков).
  jostle(power = 90) {
    for (const m of this.items) {
      m.vy -= power * (0.4 + Math.random() * 0.6);
      m.vx += (Math.random() - 0.5) * power * 0.6;
    }
  }

  // Забрать шарик, лежащий у затвора (его отправят на конвейер). Возвращает тело или null.
  take() {
    const gx = G.CX;
    const gy = G.BIN.floorY - G.BIN_R;
    let best = -1;
    let bestD = 48;
    for (let i = 0; i < this.items.length; i++) {
      const m = this.items[i];
      const d = Math.hypot(m.x - gx, m.y - gy);
      if (d < bestD) { bestD = d; best = i; }
    }
    if (best < 0) return null;
    return this.items.splice(best, 1)[0];
  }

  // Физика идёт с фиксированным шагом (1/120 с), поэтому не зависит от частоты кадров и не «дёргается» при просадках.
  step(dtRaw) {
    this.acc += Math.min(dtRaw, 0.05);
    if (!this.items.length) { this.acc = 0; return; }
    let steps = 0;
    while (this.acc >= FIXED_H && steps < 5) {
      this.sub(FIXED_H);
      this.acc -= FIXED_H;
      steps++;
    }
    if (steps >= 5) this.acc = 0; // не успеваем — не накапливаем долг
  }

  // Один шаг физики длиной h: сдвиг, затем несколько итераций разрешения столкновений, затем успокоение.
  sub(h) {
    this.integrate(h);
    for (let it = 0; it < ITER; it++) {
      this.collideBalls();
      this.collideWalls();
    }
    this.settle(h);
  }

  integrate(h) {
    for (const m of this.items) {
      m.vy += GRAVITY * h;
      m.x += m.vx * h;
      m.y += m.vy * h;
    }
  }

  // шарик–шарик
  collideBalls() {
    const items = this.items;
    const n = items.length;
    for (let i = 0; i < n; i++) {
      const a = items[i];
      for (let j = i + 1; j < n; j++) {
        const b = items[j];
        const dx = b.x - a.x;
        if (dx > 20 || dx < -20) continue;
        collidePair(a, b, dx);
      }
    }
  }

  // шарик–стенки
  collideWalls() {
    for (const m of this.items) {
      for (const sg of this.segs) collideSegment(m, sg);
    }
  }

  // Потери скорости, затухание «сжатия» и страховка от выхода за границы.
  settle(h) {
    for (const m of this.items) {
      m.vx *= 0.9985; m.vy *= 0.9985;
      if (m.squash > 0) m.squash = Math.max(0, m.squash - h * 5);
      this.contain(m);
    }
  }

  // Страховка от «пролёта» сквозь стенку на углах и при давке: шарик всегда возвращается внутрь.
  contain(m) {
    const b = G.BIN;
    if (!Number.isFinite(m.x) || !Number.isFinite(m.y) || !Number.isFinite(m.vx) || !Number.isFinite(m.vy)) {
      m.x = G.CX; m.y = b.floorY - 3 * m.r; m.vx = 0; m.vy = 0;
      return;
    }
    const maxV = 800;
    if (m.vx > maxV) m.vx = maxV; else if (m.vx < -maxV) m.vx = -maxV;
    if (m.vy > maxV) m.vy = maxV; else if (m.vy < -maxV) m.vy = -maxV;
    if (m.y < b.top - 20) return; // ещё летит сверху, из лотка
    const lo = b.left + m.r * 0.9;
    const hi = b.right - m.r * 0.9;
    if (m.x < lo) { m.x = lo; if (m.vx < 0) m.vx = 0; }
    if (m.x > hi) { m.x = hi; if (m.vx > 0) m.vx = 0; }
    let floor;
    if (m.x <= b.gateL) floor = b.wallBottom + ((m.x - b.left) / (b.gateL - b.left)) * (b.floorY - b.wallBottom);
    else if (m.x >= b.gateR) floor = b.wallBottom + ((b.right - m.x) / (b.right - b.gateR)) * (b.floorY - b.wallBottom);
    else floor = b.floorY;
    if (m.x > b.left + 1 && m.x < b.right - 1 && m.y > floor - m.r * 0.5) {
      m.y = floor - m.r * 1.1;
      if (m.vy > 0) m.vy = 0;
    }
  }
}
