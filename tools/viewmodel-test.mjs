// Проверка модели отображения, пары «политика кадров / адаптивное качество» — всё без браузера: npm run test:view
import { LEVELS } from '../www/js/levels.js';
import { Game } from '../www/js/game.js';
import { solve } from '../www/js/solver.js';
import * as G from '../www/js/geometry.js';
import * as VM from '../www/js/viewmodel.js';
import { FramePacer, IDLE_STEP, MAX_PLAY_DT, clampFrameDt } from '../www/js/frame-pacer.js';
import { AdaptiveQuality } from '../www/js/quality.js';

let failed = 0;
const check = (ok, msg) => { if (!ok) { failed++; console.error('✗ ' + msg); } };
const finite = (p) => Object.values(p).every((v) => typeof v !== 'number' || Number.isFinite(v));

// 1) Позы лотков, коробок, шариков при прохождении уровней: конечные числа, прозрачность 0..1, шарики внутри лотка.
const pose = VM.makePose();
const slot = { x: 0, y: 0, r: 0 };
const mp = { x: 0, y: 0, color: '', onBelt: true };
const at = { x: 0, y: 0, r: 0 };
const DT = 1 / 30;
for (const level of LEVELS.filter((_, i) => i % 15 === 0)) {
  const { solution } = solve(level);
  const queue = solution.slice();
  const g = new Game(level, () => {});
  for (let step = 0; step < 30 * 600 && g.state === 'play'; step++) {
    if (queue.length && g.emitters.length === 0 && g.bin.count === 0 && !g.flyers.length &&
        !g.canTake() && !g.slots.some((m) => m && m.drop > 0) && !g.leaving.length &&
        !g.boxes.some((b) => b && !b.fromStack && b.enterT < 0.4)) { // как в simulate.mjs: ждём, пока всё успокоится
      const ci = queue[0];
      const r = G.trayRect(ci, 0);
      if (g.tap(r.cx, r.y + r.h / 2)) queue.shift();
    }
    g.update(DT);
    const t = g.time;
    g.cols.forEach((col, ci) => col.slice(0, VM.MAX_TRAY_DEPTH + 1).forEach((tray, d) => {
      VM.trayPose(pose, tray, ci, d, g.colAdv[ci], t);
      check(finite(pose) && pose.alpha >= 0 && pose.alpha <= 1, `L${level.id}: поза лотка некорректна`);
      for (let i = 0; i < tray.left; i++) {
        VM.trayMarbleSlot(slot, tray.n, i);
        const cup = VM.trayCup(tray.n);
        check(Math.abs(slot.x) < G.TRAY_W / 2 - 10 && slot.y > cup.y && slot.y < cup.y + cup.h, `L${level.id}: шарик ${i} вне чаши лотка (n=${tray.n})`);
      }
    }));
    for (const b of [...g.boxes, ...g.leaving]) {
      if (!b) continue;
      VM.boxPose(pose, b, g.stackAdv[b.i] || 0);
      check(finite(pose) && pose.alpha >= 0 && pose.alpha <= 1 && pose.sx > 0 && pose.sy > 0, `L${level.id}: поза коробки некорректна`);
    }
    for (let i = 0; i < g.n; i++) if (VM.beltMarblePose(mp, g, i)) check(finite(mp), `L${level.id}: шарик конвейера`);
    for (const f of g.flyers) { VM.flyerAt(at, f, VM.flyerProgress(f)); check(finite(at) && at.r > 0, `L${level.id}: летящий шарик`); }
    const gauge = VM.binGauge(g);
    check(gauge.fill >= 0 && gauge.fill <= 1 && gauge.label === `${g.bin.count}/${G.BIN_CAP}`, `L${level.id}: шкала накопителя`);
    check(VM.beltLabel(g) === `${g.occupied} / ${g.n}`, `L${level.id}: подпись конвейера`);
  }
  check(g.state === 'won', `L${level.id}: бот не выиграл`);
}

// 2) Туман: цвет за пределами видимости скрыт, в пределах — виден.
{
  const lv = LEVELS.find((l) => l.fog && l.fog.top === 0 && l.fog.bottom === 0) || LEVELS[40];
  const g = new Game(lv, () => {});
  const tray = g.cols[0][1];
  check(VM.trayShownColor(g, 0, g.cols[0][0]) === g.cols[0][0].color, 'ближний лоток всегда виден');
  check(VM.trayShownColor(g, 1, tray) === 'X', 'следующий лоток скрыт туманом');
  check(VM.boxShownColor(g, 1, 'R') === 'X', 'следующая коробка скрыта туманом');
  g.fog.top = 2; g.fog.bottom = 2;
  check(VM.trayShownColor(g, 2, tray) === tray.color && VM.boxShownColor(g, 2, 'R') === 'R', 'в пределах видимости цвет показан');
}

// 3) Политика кадров.
{
  const p = new FramePacer();
  let o = p.plan('play', 1 / 60);
  check(o.render && Math.abs(o.update - 1 / 60) < 1e-9, 'play: каждый кадр обновляется и рисуется');
  o = p.plan('play', 0.5);
  check(o.update === MAX_PLAY_DT, 'play: шаг симуляции ограничен');
  o = p.plan('menu', 1 / 60);
  check(!o.render && o.update === 0, 'menu: кадр пропускается, пока не набралось 1/20 с');
  o = p.plan('menu', IDLE_STEP);
  check(o.render && o.update > 0, 'menu: ~20 к/с');
  p.plan('pause', 1 / 60);
  o = p.plan('pause', 1 / 60);
  check(!o.render && o.update === 0, 'pause: без изменений ничего не рисуется');
  p.invalidate();
  check(p.plan('pause', 1 / 60).render, 'pause: после invalidate рисуется один кадр');
  check(clampFrameDt(3) === 1 / 60 && clampFrameDt(0.02) === 0.02, 'скачок времени после фона сглаживается');
}

// 4) Адаптивное качество: быстрые кадры не трогают, медленные снижают до минимума и не дальше.
{
  const fast = new AdaptiveQuality();
  let dropped = false;
  for (let i = 0; i < 600; i++) dropped = fast.sample(1 / 60) || dropped;
  check(!dropped && fast.value === 1, 'качество: 60 к/с не снижается');
  const slow = new AdaptiveQuality();
  for (let i = 0; i < 3000; i++) slow.sample(1 / 20);
  check(slow.value < 1 && slow.value >= slow.min - 1e-9, 'качество: 20 к/с снижается, но не ниже минимума');
}

if (failed) { console.error(`\nПровалено проверок: ${failed}`); process.exit(1); }
console.log('✓ модель отображения, политика кадров, качество — всё в порядке');
