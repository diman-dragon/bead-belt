// Генератор уровней: node tools/gen-levels.mjs [--write]
//   151–200: лотки только по 6 шариков, колонки растут от 5 до 8 лотков
//   201–250: лотки только по 9 шариков, колонки от 6 до 10 лотков, конвейер уже и тесный
//   251–400: по 9 шариков, колонки от 10 до 13 лотков, конвейер до 25 ячеек
// Параметры: --from 251 --to 400 --write (по умолчанию 151..400); --ids 229,246 — перегенерировать только их;
//            --runs 30 — строгость проверки симуляцией; --salt 1 — другие случайные уровни
// Каждый кандидат проверяется solver.js: должен проходиться, belt = минимум + небольшой запас,
// и не все первые ходы должны вести к победе (иначе уровень слишком лёгкий).
import fs from 'node:fs';
import { checkLevelData, solve, firstMoveStats } from '../www/js/solver.js';
import { Game } from '../www/js/game.js';
import * as G from '../www/js/geometry.js';

const COLORS = ['R', 'B', 'G', 'Y', 'P', 'O'];
const FILE = new URL('../www/js/levels.js', import.meta.url);

function rng(seed) {
  let s = seed >>> 0 || 1;
  return () => {
    s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0;
    return s / 4294967296;
  };
}

function shuffle(a, rnd) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Один кандидат: depth лотков в каждой из 3 колонок, все лотки по n шариков.
function candidate(depth, n, rnd) {
  const trays = [];
  const total = depth * 3;
  // цвета распределены неравномерно: 1–2 «тяжёлых» цвета создают пробки на конвейере
  const weights = COLORS.map(() => 1 + rnd() * 0.6);
  const sum = weights.reduce((a, b) => a + b, 0);
  for (let i = 0; i < total; i++) {
    let r = rnd() * sum, k = 0;
    while (k < 5 && r > weights[k]) { r -= weights[k]; k++; }
    trays.push(COLORS[k]);
  }
  const columns = [[], [], []];
  trays.forEach((c, i) => columns[i % 3].push({ c, n }));
  columns.forEach((col) => shuffle(col, rnd));

  // коробки: по (n/3) на каждый лоток своего цвета, порядок перемешан
  const boxes = [];
  for (const t of trays) for (let k = 0; k < n / 3; k++) boxes.push(t);
  shuffle(boxes, rnd);
  return { columns, boxes };
}


// Реальная симуляция: бот по решению из solver.js. Модель решателя консервативна, но не идеальна
// (например, одноцветные коробки рядом делят шарики иначе), а физика накопителя случайна —
// поэтому принимаем уровень, только если бот выигрывает ВСЕ прогоны подряд.
const DT = 1 / 30;
const argv = (f, d) => { const i = process.argv.indexOf(f); return i > 0 ? process.argv[i + 1] : d; };
const RUNS = +argv('--runs', 30);          // сколько раз подряд бот обязан выиграть
const SALT = +argv('--salt', 0);           // меняет семя, чтобы получить другие уровни
const IDS = argv('--ids', '');             // --ids 229,246: перегенерировать только эти уровни (с --write заменяет их)
function settled(g) {
  return g.emitters.length === 0 && g.bin.count === 0 && !g.flyers.length && !g.canTake() &&
    !g.slots.some((m) => m && m.drop > 0) && !g.leaving.length &&
    !g.boxes.some((b) => b && !b.fromStack && b.enterT < 0.4);
}
function botWins(level, solution) {
  const g = new Game(level, () => {});
  const q = solution.slice();
  let t = 0;
  while (g.state === 'play' && t < 600) {
    if (settled(g) && q.length) { const r = G.trayRect(q.shift(), 0); g.tap(r.cx, r.y + r.h / 2); }
    g.update(DT); t += DT;
  }
  for (let i = 0; i < 60; i++) g.update(DT);
  return g.state === 'won';
}
function robust(level, runs = RUNS) {
  const { solution } = solve(level);
  if (!solution) return false;
  for (let i = 0; i < runs; i++) if (!botWins(level, solution)) return false;
  return true;
}

// Минимальная вместимость конвейера в пределах [n, maxBelt]; null — если даже при maxBelt непроходим.
function tightBelt(lv, n, maxBelt) {
  if (!solve(lv, maxBelt).solution) return null;
  for (let b = n; b <= maxBelt; b++) if (solve(lv, b).solution) return b;
  return null;
}

// Чем теснее конвейер и чем меньше выигрышных первых ходов — тем сложнее уровень.
function makeLevel(id, name, depth, n, tries, slack, maxBelt, target, rnd) {
  const pool = [];
  for (let t = 0; t < tries; t++) {
    const { columns, boxes } = candidate(depth, n, rnd);
    const probe = { id, name, belt: maxBelt, fog: { top: 0, bottom: 0 }, columns, boxes };
    if (checkLevelData(probe).length) continue;
    const need = tightBelt(probe, n, maxBelt);
    if (!need) continue;
    const lv = { ...probe, belt: need + slack };
    const st = firstMoveStats(lv);
    const score = -Math.abs(need - target) * 10 - (st.winning / Math.max(1, st.total)) * 15;
    pool.push({ lv, score, need, st });
  }
  pool.sort((x, y) => y.score - x.score);
  for (const c of pool) if (robust(c.lv)) return c;   // лучший из тех, что реально проходятся
  return null;
}

function fmt(lv) {
  const col = (c) => '      [' + c.map((t) => `{ c: '${t.c}', n: ${t.n} }`).join(', ') + '],';
  return [
    '  {',
    `    id: ${lv.id},`,
    `    name: '${lv.name}',`,
    `    belt: ${lv.belt},`,
    '    fog: { top: 0, bottom: 0 },',
    '    columns: [',
    ...lv.columns.map(col),
    '    ],',
    `    boxes: [${lv.boxes.map((c) => `'${c}'`).join(', ')}],`,
    '  },',
  ].join('\n');
}

const out = [];
const seedBase = 20261024;

// Глубина колонки по номеру уровня внутри блока: плавно растёт, к концу блока — максимум.
function plan(id) {
  if (id > 250) {
    const k = (id - 251) / 149;
    return { k, n: 9, depth: Math.round(10 + k * 3), name: 'Марафон', slack: id % 3 === 0 ? 1 : 0, tries: 150, maxBelt: 25, target: 21 + Math.round(k * 3) };
  }
  if (id <= 200) {
    const k = (id - 151) / 49;               // 0..1
    return { k, n: 6, depth: Math.round(5 + k * 3), name: 'Шестёрка', slack: id % 4 === 0 ? 1 : 0, tries: 150, maxBelt: 22, target: 10 + Math.round(k * 6) };
  }
  const k = (id - 201) / 49;
  return { k, n: 9, depth: Math.round(6 + k * 4), name: 'Девятка', slack: id % 3 === 0 ? 1 : 0, tries: 150, maxBelt: 24, target: 15 + Math.round(k * 7) };
}

const arg = (f, d) => { const i = process.argv.indexOf(f); return i > 0 ? +process.argv[i + 1] : d; };
const FROM = arg('--from', 151), TO = arg('--to', 400);
const LIST = IDS ? IDS.split(',').map(Number) : null;
for (let id = FROM; id <= TO; id++) {
  if (LIST && !LIST.includes(id)) continue;
  const p = plan(id);
  const rnd = rng(seedBase + id * 7919 + SALT * 104729);
  let res = makeLevel(id, `${p.name} ${id}`, p.depth, p.n, p.tries, p.slack, p.maxBelt, p.target, rnd);
  // если не нашлось ни одного проходимого — берём меньше колонку
  let d = p.depth;
  while (!res && d > 3) { d--; res = makeLevel(id, `${p.name} ${id}`, d, p.n, p.tries, p.slack, p.maxBelt, p.target, rnd); }
  if (!res) { console.error(`✗ ${id}: не удалось сгенерировать`); process.exit(1); }
  console.log(`✓ ${id} цель=${p.target} n=${p.n} лотков в колонке ${d} belt=${res.lv.belt} (мин ${res.need}) первых ходов к победе ${res.st.winning}/${res.st.total}`);
  out.push(fmt(res.lv));
}

if (process.argv.includes('--write')) {
  let src = fs.readFileSync(FILE, 'utf8');
  if (LIST) {
    // замена существующих уровней по id
    const ids = LIST.filter((id) => id >= FROM && id <= TO);
    ids.forEach((id, i) => {
      const re = new RegExp(`  \\{\\n    id: ${id},[\\s\\S]*?\\n  \\},\\n`);
      if (!re.test(src)) { console.error(`Уровень ${id} не найден в levels.js`); process.exit(1); }
      src = src.replace(re, () => out[i] + '\n');
    });
    fs.writeFileSync(FILE, src);
    console.log(`Заменено ${ids.length} уровней в www/js/levels.js`);
  } else {
    if (new RegExp(`^\\s+id: ${FROM},`, 'm').test(src)) { console.error(`Уровень ${FROM} уже есть в levels.js — сначала удалите его.`); process.exit(1); }
    const idx = src.lastIndexOf('];');
    src = src.slice(0, idx) + out.join('\n') + '\n' + src.slice(idx);
    fs.writeFileSync(FILE, src);
    console.log(`Записано ${out.length} уровней в www/js/levels.js`);
  }
}
