// Дискретная модель уровня для проверки проходимости. Без DOM — работает и в браузере, и в Node.
//
// Модель консервативная: нажатие на лоток разрешено, только если все его шарики
// помещаются на конвейер (занято + n <= belt). Шарики на конвейере считаются «мешком»:
// за один оборот каждый проезжает мимо каждой активной коробки, поэтому подходящие
// шарики сразу уходят в коробки (settle). Если модель говорит «проходим», игрок
// гарантированно может пройти уровень. Реальная игра чуть мягче модели.

import { BOX_SIZE, ACTIVE_BOXES } from './levels.js';

export function checkLevelData(level) {
  const errors = [];
  const marbles = {};
  for (const col of level.columns) {
    for (const t of col) marbles[t.c] = (marbles[t.c] || 0) + t.n;
  }
  const boxes = {};
  for (const c of level.boxes) boxes[c] = (boxes[c] || 0) + 1;
  const colors = new Set([...Object.keys(marbles), ...Object.keys(boxes)]);
  for (const c of colors) {
    const m = marbles[c] || 0;
    const b = boxes[c] || 0;
    if (m !== b * BOX_SIZE) {
      errors.push(`цвет ${c}: шариков ${m}, а коробок ${b} (нужно ${b * BOX_SIZE} шариков)`);
    }
  }
  if (level.belt < 3) errors.push('belt слишком мал');
  return errors;
}

function initialState(level) {
  return {
    belt: {},           // цвет -> количество шариков на конвейере
    beltCount: 0,
    active: level.boxes.slice(0, ACTIVE_BOXES).map((c) => ({ c, f: 0 })),
    q: ACTIVE_BOXES,    // индекс следующей коробки в очереди
    heads: level.columns.map(() => 0), // сколько лотков уже взято из каждой колонки
  };
}

function clone(s) {
  return {
    belt: { ...s.belt },
    beltCount: s.beltCount,
    active: s.active.map((b) => ({ ...b })),
    q: s.q,
    heads: s.heads.slice(),
  };
}

function settle(s, level) {
  let changed = true;
  while (changed) {
    changed = false;
    for (let i = 0; i < s.active.length; i++) {
      const b = s.active[i];
      const have = s.belt[b.c] || 0;
      if (have > 0 && b.f < BOX_SIZE) {
        const take = Math.min(BOX_SIZE - b.f, have);
        b.f += take;
        s.belt[b.c] = have - take;
        s.beltCount -= take;
        changed = true;
      }
      if (b.f >= BOX_SIZE) {
        if (s.q < level.boxes.length) s.active[i] = { c: level.boxes[s.q++], f: 0 };
        else { s.active.splice(i, 1); i--; }
        changed = true;
      }
    }
  }
}

function key(s) {
  const belt = Object.keys(s.belt).sort().map((c) => c + s.belt[c]).join('');
  const act = s.active.map((b) => b.c + b.f).join(',');
  return `${belt}|${act}|${s.q}|${s.heads.join(',')}`;
}

function isWin(s, level) {
  return s.active.length === 0 && s.q >= level.boxes.length &&
    s.heads.every((h, i) => h >= level.columns[i].length);
}

function moves(s, level) {
  const out = [];
  for (let i = 0; i < level.columns.length; i++) {
    const t = level.columns[i][s.heads[i]];
    if (!t) continue;
    if (s.beltCount + t.n > level.belt) continue;
    out.push(i);
  }
  return out;
}

function apply(s, level, col) {
  const n = clone(s);
  const t = level.columns[col][n.heads[col]];
  n.heads[col]++;
  n.belt[t.c] = (n.belt[t.c] || 0) + t.n;
  n.beltCount += t.n;
  settle(n, level);
  return n;
}

// Полный перебор с запоминанием состояний. Возвращает решение (порядок колонок) или null.
export function solve(level, beltOverride) {
  const lv = beltOverride ? { ...level, belt: beltOverride } : level;
  const seen = new Map();
  let visited = 0;
  function dfs(s) {
    if (isWin(s, lv)) return [];
    const k = key(s);
    if (seen.has(k)) return seen.get(k);
    visited++;
    seen.set(k, null);
    for (const m of moves(s, lv)) {
      const r = dfs(apply(s, lv, m));
      if (r) { const sol = [m, ...r]; seen.set(k, sol); return sol; }
    }
    return null;
  }
  const s0 = initialState(lv);
  settle(s0, lv);
  const solution = dfs(s0);
  return { solution, visited };
}

// Доля первых ходов, ведущих к победе, — грубая мера «лёгкости» уровня.
export function firstMoveStats(level) {
  const s0 = initialState(level);
  settle(s0, level);
  const first = moves(s0, level);
  let winning = 0;
  for (const m of first) {
    const s1 = apply(s0, level, m);
    const lv = { ...level, columns: level.columns };
    // продолжаем поиск из s1
    const seen = new Map();
    const dfs = (s) => {
      if (isWin(s, lv)) return true;
      const k = key(s);
      if (seen.has(k)) return seen.get(k);
      seen.set(k, false);
      for (const mm of moves(s, lv)) if (dfs(apply(s, lv, mm))) { seen.set(k, true); return true; }
      return false;
    };
    if (dfs(s1)) winning++;
  }
  return { total: first.length, winning };
}

// Минимальная вместимость конвейера, при которой уровень проходим в этой модели.
export function minBelt(level, max = 60) {
  for (let b = 1; b <= max; b++) {
    if (solve(level, b).solution) return b;
  }
  return null;
}
