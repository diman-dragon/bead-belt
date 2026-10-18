// Политика кадров: что делать в этом кадре — обновлять игру, перерисовывать или ничего.
// Игра: полный темп. За меню: ~20 к/с (экономит батарею). Пауза/конец уровня: один кадр после изменения.
export const IDLE_STEP = 1 / 20;
export const MAX_PLAY_DT = 1 / 30;
const LIVE_BACKGROUND = new Set(['menu', 'levels', 'settings', 'about']);

export function isBackgroundMode(mode) {
  return LIVE_BACKGROUND.has(mode);
}

// Скачок времени после возврата из фона не должен «прыгать».
export function clampFrameDt(dt) {
  return dt > 0.25 ? 1 / 60 : dt;
}

export class FramePacer {
  constructor() {
    this.idleAcc = 0;
    this.dirty = true;
    this.out = { update: 0, render: false };
  }

  invalidate() {
    this.dirty = true;
  }

  // Результат — общий объект: update (секунды симуляции, 0 — не обновлять) и render.
  plan(mode, dt) {
    const o = this.out;
    o.update = 0;
    o.render = false;
    if (mode === 'play') {
      this.idleAcc = 0;
      this.dirty = false;
      o.update = Math.min(dt, MAX_PLAY_DT);
      o.render = true;
    } else if (LIVE_BACKGROUND.has(mode)) {
      this.idleAcc += dt;
      if (this.idleAcc >= IDLE_STEP) {
        o.update = Math.min(this.idleAcc, IDLE_STEP);
        o.render = true;
        this.idleAcc = 0;
      }
    } else if (this.dirty) {
      o.render = true;
      this.dirty = false;
    }
    return o;
  }
}
