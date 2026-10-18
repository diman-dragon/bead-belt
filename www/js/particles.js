// Искры и конфетти: короткоживущие частицы, не влияющие на правила игры. Без DOM.
import * as G from './geometry.js';

const CONFETTI_KEYS = ['R', 'Y', 'G', 'O', 'B', 'P'];
const CONFETTI_COUNT = 110;
const SPARK_GRAVITY = 300;

export class Particles {
  constructor() {
    this.sparks = [];
    this.confetti = [];
  }

  // Россыпь искр из точки (x, y).
  burst(x, y, color, n, speed) {
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2;
      const v = speed * (0.4 + Math.random() * 0.6);
      this.sparks.push({
        x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 50,
        life: 0.5 + Math.random() * 0.4, max: 0.9, color, star: Math.random() < 0.4,
      });
    }
  }

  // Конфетти при победе: сыплется сверху по всей ширине поля.
  spawnConfetti() {
    for (let i = 0; i < CONFETTI_COUNT; i++) {
      this.confetti.push({
        x: Math.random() * G.W, y: -10 - Math.random() * 120,
        vx: (Math.random() - 0.5) * 70, vy: 110 + Math.random() * 190,
        rot: Math.random() * 6.28, vr: (Math.random() - 0.5) * 12,
        w: 5 + Math.random() * 5, h: 3 + Math.random() * 4,
        phase: Math.random() * 6.28, color: CONFETTI_KEYS[i % CONFETTI_KEYS.length],
      });
    }
  }

  update(dt) {
    this.updateSparks(dt);
    this.updateConfetti(dt);
  }

  updateSparks(dt) {
    const list = this.sparks;
    for (let k = list.length - 1; k >= 0; k--) {
      const p = list[k];
      p.life -= dt;
      if (p.life <= 0) { list.splice(k, 1); continue; }
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.vy += SPARK_GRAVITY * dt;
    }
  }

  updateConfetti(dt) {
    const list = this.confetti;
    for (let k = list.length - 1; k >= 0; k--) {
      const c = list[k];
      c.phase += dt * 5;
      c.x += (c.vx + Math.sin(c.phase) * 40) * dt;
      c.y += c.vy * dt;
      c.rot += c.vr * dt;
      if (c.y > G.H + 20) list.splice(k, 1);
    }
  }
}
