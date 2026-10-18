// Модель отображения: превращает состояние игры в «позы» и раскладки для рисования.
// Без DOM и без canvas — поэтому проверяется в Node (tools/viewmodel-test.mjs), а render/* только рисует готовое.
// Функции *Pose пишут результат в переданный объект (makePose) — в горячем цикле не создаются новые объекты.
import * as G from './geometry.js';
import { ENTER_DUR, POP_DUR, TRAY_LEAVE } from './game.js';
import { clamp01, easeOut, easeOutBack } from './anim.js';

export const STACK_DY = 17;   // на сколько каждая следующая коробка стопки выглядывает вниз
export const STACK_SC = 0.94; // во сколько раз уменьшается каждая следующая коробка стопки
export const MAX_TRAY_DEPTH = 3; // сколько лотков колонки вообще рисуется

// Поза: точка привязки (x, y), поворот, масштаб по осям и прозрачность.
export function makePose() {
  return { x: 0, y: 0, rot: 0, sx: 1, sy: 1, alpha: 1, glow: 0 };
}

function resetPose(p) {
  p.x = 0; p.y = 0; p.rot = 0; p.sx = 1; p.sy = 1; p.alpha = 1; p.glow = 0;
  return p;
}

// ---------------------------------------------------------------- лотки

// Что видно на лотке глубины d: ближний и лотки в пределах «видимости» показывают цвет, остальные — «туман».
export function trayShownColor(game, d, tray) {
  return d === 0 || d <= game.fog.top ? tray.color : 'X';
}

export function trayIsSimple(d, depth) {
  return d !== 0 && depth > 1.2;
}

// Насколько затемнять дальний лоток, чтобы передний выделялся.
export function trayDim(d) {
  return d === 1 ? 0.08 : 0.2;
}

function frontTrayMotion(p, tray, ci, t) {
  if (tray.state === 'pouring') {
    const k = Math.max(0, tray.pulse);
    p.rot = Math.sin(t * 38 + ci) * 0.035 * (0.4 + k);
    p.sx = 1 + 0.045 * k;
    p.sy = 1 - 0.06 * k;
    p.y += 2 * k;
  } else if (tray.state === 'leaving') {
    const k = clamp01(tray.t / TRAY_LEAVE);
    p.rot = (ci - 1) * 0.35 * k;
    p.y += k * k * 46;
    p.x += (ci - 1) * 10 * k;
    p.sx = p.sy = 1 - 0.3 * k;
    p.alpha = 1 - k * k;
  } else {
    p.y += Math.sin(t * 2.4 + ci * 1.7) * 1.3;
    p.glow = 0.5 + 0.5 * Math.sin(t * 4 + ci);
    if (tray.pulse < 0) p.x += Math.sin(t * 60) * 5 * -tray.pulse; // отказ: лоток дёргается
  }
}

// Поза лотка глубины d колонки ci. Точка привязки — центр лотка по высоте; рисовать с pivot TRAY_H / 2.
export function trayPose(out, tray, ci, d, adv, t) {
  const p = resetPose(out);
  const r = G.trayRect(ci, d + adv);
  p.x = r.cx;
  p.y = r.y + r.h / 2;
  p.alpha = d === 0 || d === 1 ? 1 : 0.94;
  if (d === 0) frontTrayMotion(p, tray, ci, t);
  p.sx *= r.scale;
  p.sy *= r.scale;
  return p;
}

// Чаша лотка: верх и высота. Одна и та же геометрия нужна и корпусу, и шарикам внутри.
export function trayCup(n) {
  const big = n > 6; // три ряда (лоток на 9)
  return { y: 10, h: G.TRAY_H * (big ? 0.68 : 0.58), big };
}

// Место i-го оставшегося шарика в лотке на n шариков (координаты от верхнего центра лотка).
export function trayMarbleSlot(out, n, i) {
  const cup = trayCup(n);
  const rows = Math.ceil(n / 3);
  const rowGap = cup.big ? 17 : 19;
  const y0 = cup.y + cup.h / 2 - ((rows - 1) * rowGap) / 2 + 1;
  const row = Math.floor(i / 3);
  const inRow = Math.min(3, n - row * 3);
  out.x = ((i % 3) - (inRow - 1) / 2) * 22;
  out.y = y0 + row * rowGap;
  out.r = cup.big ? G.TRAY_R * 0.93 : G.TRAY_R;
  return out;
}

// ---------------------------------------------------------------- коробки

export function boxShownColor(game, depth, real) {
  return depth <= game.fog.bottom ? real : 'X';
}

// Прогресс появления коробки 0..1 (коробка из стопки уже на месте).
export function boxEnter(b) {
  return b.fromStack ? 1 : clamp01(b.enterT / ENTER_DUR);
}

function boxArrival(p, b) {
  const e = easeOutBack(boxEnter(b));
  p.y += (1 - e) * 70;
  p.sx = p.sy = 0.78 + 0.22 * Math.min(1, e);
  p.sy *= 1 + (e > 1 ? (e - 1) * 0.5 : 0);
  p.alpha = clamp01(boxEnter(b) * 3.2);
}

function boxPop(p, b) {
  const q = clamp01(b.popT / POP_DUR);
  if (q < 0.3) {
    p.sx = p.sy = 1 + 0.15 * Math.sin((q / 0.3) * Math.PI * 0.5);
    return;
  }
  const r = (q - 0.3) / 0.7;
  p.sx = p.sy = 1.15 - 0.4 * r;
  p.y = G.BOX_TOP + G.BOX_H / 2 + r * r * 80;
  p.alpha = 1 - r * r;
}

// Поза активной (или уезжающей) коробки. adv — «выдвижение» стопки 1 → 0. Привязка — центр коробки, pivot BOX_H / 2.
export function boxPose(out, b, adv) {
  const p = resetPose(out);
  p.x = G.BOX_CX(b.i);
  p.y = G.BOX_TOP + G.BOX_H / 2;
  if (b.fromStack && !b.done) {
    p.y += STACK_DY * adv; // выдвигается вперёд из стопки
    p.sx = p.sy = Math.pow(STACK_SC, adv);
  } else if (!b.fromStack && boxEnter(b) < 1) {
    boxArrival(p, b);
  }
  if (b.done) boxPop(p, b);
  return p;
}

// Поза коробки, ждущей в стопке на глубине depth (1 — следующая). enter — видимость стопки 0..1.
export function stackedBoxPose(out, i, depth, enter) {
  const p = resetPose(out);
  p.x = G.BOX_CX(i);
  p.y = G.BOX_TOP + G.BOX_H / 2 + STACK_DY * depth;
  p.sx = p.sy = Math.pow(STACK_SC, depth);
  p.alpha = (depth > 2 ? clamp01(3 - depth) : 1) * enter;
  return p;
}

export function stackedBoxShade(depth) {
  return 0.03 + 0.06 * depth;
}

// Гнездо s в коробке (координаты от верхнего центра коробки) и поправка, на которую шарик «сидит» ниже гнезда.
export const BOX_MARBLE_DY = 0.5;
export function boxSocket(s) {
  return { x: (s - 1) * 22, y: 36 };
}

// Сжатие шарика в гнезде после попадания: 1 → 1.2 → 1.
export function boxMarbleSquash(bump) {
  return 1 + 0.2 * Math.sin(bump * Math.PI);
}

// ---------------------------------------------------------------- конвейер и накопитель

export const DANGER_MARGIN = 3; // тревога, когда свободных ячеек осталось не больше этого

export function beltDanger(game) {
  return game.occupied >= game.n - DANGER_MARGIN;
}

const ENTRY = G.pos(G.ENTRY_S);

// Положение шарика в ячейке i конвейера. Возвращает false, если ячейка пуста.
export function beltMarblePose(out, game, i) {
  const m = game.slots[i];
  if (!m) return false;
  G.posTo(out, game.slotS(i));
  if (m.drop > 0) {
    out.x += (m.fx - ENTRY.x) * m.drop;
    out.y += (m.fy - ENTRY.y) * m.drop * m.drop;
  }
  out.color = m.color;
  out.onBelt = m.drop <= 0.05; // пока падает — без тени
  return true;
}

// Шкала заполнения накопителя.
export function binGauge(game) {
  const fill = clamp01(game.bin.count / G.BIN_CAP);
  return { fill, hot: fill > 0.8, label: `${game.bin.count}/${G.BIN_CAP}` };
}

// Подпись в «острове» конвейера.
export function beltLabel(game) {
  return `${game.occupied} / ${game.n}`;
}

// ---------------------------------------------------------------- шарики в полёте

// Положение летящего к коробке шарика при прогрессе p (0..1).
export function flyerAt(out, f, p) {
  const e = easeOut(p);
  out.x = f.x0 + (f.x1 - f.x0) * e;
  out.y = f.y0 + (f.y1 - f.y0) * e - Math.sin(p * Math.PI) * 12;
  out.r = G.MARBLE_R + (G.BOX_R - G.MARBLE_R) * p;
  return out;
}

export function flyerProgress(f) {
  return Math.min(1, f.t / f.dur);
}

// Видна ли подсказка и с какой «качкой» стрелки.
export function hintVisible(game) {
  return !!game.hintText && game.taps === 0 && game.state === 'play';
}
