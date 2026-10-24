// Безголовая симуляция реального времени (без браузера): npm run simulate
// 1) бот проходит уровень по решению из solver.js — игра обязана закончиться победой;
// 2) случайные боты — игра обязана иногда заканчиваться и поражением, и победой.
import { LEVELS } from '../www/js/levels.js';
import { solve } from '../www/js/solver.js';
import { Game } from '../www/js/game.js';
import * as G from '../www/js/geometry.js';

const DT = 1 / 30;
const MAX_SECONDS = 600;

// «Успокоилось»: всё высыпано, накопитель пуст, ничего не летит и ни одна коробка не может ничего взять.
function settled(g) {
  return g.emitters.length === 0 && g.bin.count === 0 && !g.flyers.length && !g.canTake() &&
    !g.slots.some((m) => m && m.drop > 0) &&
    !g.leaving.length && !g.boxes.some((b) => b && !b.fromStack && b.enterT < 0.4);
}

function tapColumn(g, ci) {
  const r = G.trayRect(ci, 0);
  return g.tap(r.cx, r.y + r.h / 2);
}

function run(level, chooser) {
  const events = [];
  const g = new Game(level, (e) => events.push(e));
  let t = 0;
  while (g.state === 'play' && t < MAX_SECONDS) {
    if (settled(g)) {
      const ci = chooser(g);
      if (ci != null) tapColumn(g, ci);
    }
    g.update(DT);
    t += DT;
  }
  // дать анимации конца доиграть
  for (let i = 0; i < 60; i++) g.update(DT);
  return { state: g.state, seconds: t, taps: g.taps, events };
}

let failed = false;
for (const level of LEVELS) {
  const { solution } = solve(level);
  if (!solution) { console.error(`✗ Уровень ${level.id}: нет решения в модели`); failed = true; continue; }

  const queue = solution.slice();
  const res = run(level, () => (queue.length ? queue.shift() : null));
  const ok = res.state === 'won';
  console.log(`${ok ? '✓' : '✗'} Уровень ${level.id}: бот по решению → ${res.state} за ${res.seconds.toFixed(1)} с, нажатий ${res.taps}`);
  if (!ok) failed = true;

  let wins = 0, losses = 0, stalls = 0;
  const N = 60;
  let seed = 12345;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  for (let k = 0; k < N; k++) {
    const r = run(level, (g) => {
      const avail = g.cols.map((c, i) => (c[0] && c[0].state === 'idle' && g.colAdv[i] < 0.5 ? i : -1)).filter((i) => i >= 0);
      return avail.length ? avail[Math.floor(rnd() * avail.length)] : null;
    });
    if (r.state === 'won') wins++; else if (r.state === 'lost') losses++; else stalls++;
  }
  console.log(`   случайные боты (${N}): побед ${wins}, поражений ${losses}, зависаний ${stalls}`);
  if (stalls > 0) { console.error('✗ есть зависания: игра не завершилась'); failed = true; }
  if (wins === 0) console.warn('! случайные боты ни разу не выиграли — очень жёсткий уровень (по решению он проходится)');
  if (losses === 0) console.warn('! случайные боты ни разу не проиграли — уровень, возможно, слишком лёгкий');
}
process.exit(failed ? 1 : 0);
