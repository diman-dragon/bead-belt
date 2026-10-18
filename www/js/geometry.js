// Геометрия игрового поля (логические координаты 360x600) и траектория конвейера.
//
// Сверху вниз: лотки -> накопитель (физика) -> конвейер -> коробки -> очередь коробок.

export const W = 360;
export const H = 600;

// ---------- конвейер: «стадион» (две прямые + две полуокружности), по часовой стрелке ----------
export const CX = 180;
export const CY = 338;
export const R = 34;            // радиус дуги: чем меньше, тем «уже» конвейер по высоте
export const HALF = 96;         // половина длины прямого участка
export const STRAIGHT = HALF * 2;
export const ARC = Math.PI * R;
export const L = 2 * STRAIGHT + 2 * ARC;

export const MARBLE_R = 10.5;   // шарик на конвейере
export const BIN_R = 9.5;       // шарик в накопителе
export const TRAY_R = 8.5;      // шарик в лотке
export const BOX_R = 9;         // шарик в коробке

export const ENTRY_S = HALF;    // точка входа: середина верхней прямой (x = CX)

// ---------- накопитель ----------
export const BIN = {
  left: 40, right: 320,
  top: 152,
  wallBottom: 208,              // ниже — скошенные стенки воронки
  floorY: 266,                  // уровень затвора
  gateL: 160, gateR: 200,
};
export const BIN_CAP = 30;      // сколько шариков помещается

// Стенки накопителя для физики (отрезки).
export function binSegments() {
  const b = BIN;
  return [
    [b.left, b.top - 20, b.left, b.wallBottom],
    [b.left, b.wallBottom, b.gateL, b.floorY],
    [b.gateL, b.floorY, b.gateR, b.floorY],
    [b.gateR, b.floorY, b.right, b.wallBottom],
    [b.right, b.wallBottom, b.right, b.top - 20],
  ];
}

// ---------- лотки сверху ----------
export const COL_X = [62, 180, 298];
export const TRAY_W = 100;
export const TRAY_H = 86;
export const TRAY_TOP = 54;     // верх переднего лотка
export const TRAY_STEP = 22;    // насколько каждый следующий лоток выглядывает вверх (видно два следующих)

// depth может быть дробным — так лотки плавно выдвигаются вперёд.
export function trayRect(ci, depth = 0) {
  const scale = Math.pow(0.94, depth);
  const w = TRAY_W * scale;
  const h = TRAY_H * scale;
  const top = TRAY_TOP - depth * TRAY_STEP;
  return { x: COL_X[ci] - w / 2, y: top + (TRAY_H - h), w, h, cx: COL_X[ci], scale };
}

// ---------- коробки снизу ----------
export const BOX_W = 80;
export const BOX_H = 94;
export const BOX_TOP = 416;
export const BOX_CX = (i) => 54 + 84 * i;

export function boxSlotPos(i, k) {
  return { x: BOX_CX(i) + (k - 1) * 22, y: BOX_TOP + 36 };
}

// ---------- путь конвейера ----------
export function mod(a, m) {
  return ((a % m) + m) % m;
}

// Точка на пути по параметру s. posTo пишет результат в готовый объект — без выделений памяти в горячем цикле.
export function posTo(out, sRaw) {
  const s = mod(sRaw, L);
  if (s < STRAIGHT) {
    out.x = CX - HALF + s; out.y = CY - R;
  } else if (s < STRAIGHT + ARC) {
    const phi = (s - STRAIGHT) / R;
    out.x = CX + HALF + R * Math.sin(phi); out.y = CY - R * Math.cos(phi);
  } else if (s < 2 * STRAIGHT + ARC) {
    out.x = CX + HALF - (s - STRAIGHT - ARC); out.y = CY + R;
  } else {
    const phi = (s - 2 * STRAIGHT - ARC) / R;
    out.x = CX - HALF - R * Math.sin(phi); out.y = CY + R * Math.cos(phi);
  }
  return out;
}

export function pos(s) {
  return posTo({ x: 0, y: 0 }, s);
}

// Нормаль (наружу) в точке пути — нужна для рёбер ленты.
export function normal(sRaw) {
  const s = mod(sRaw, L);
  if (s < STRAIGHT) return { x: 0, y: -1 };
  if (s < STRAIGHT + ARC) {
    const phi = (s - STRAIGHT) / R;
    return { x: Math.sin(phi), y: -Math.cos(phi) };
  }
  if (s < 2 * STRAIGHT + ARC) return { x: 0, y: 1 };
  const phi = (s - 2 * STRAIGHT - ARC) / R;
  return { x: -Math.sin(phi), y: Math.cos(phi) };
}

// Параметр s точки нижней половины конвейера с заданным x (место, где коробка забирает шарики).
export function pickupS(x) {
  if (x >= CX - HALF && x <= CX + HALF) {
    return STRAIGHT + ARC + (CX + HALF - x);
  }
  if (x < CX - HALF) {
    const phi = Math.asin(Math.min(1, (CX - HALF - x) / R));
    return 2 * STRAIGHT + ARC + R * phi;
  }
  const phi = Math.PI - Math.asin(Math.min(1, (x - (CX + HALF)) / R));
  return STRAIGHT + R * phi;
}
