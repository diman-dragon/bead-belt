// Геометрия игрового поля (логические координаты 360x600) и траектория конвейера.

export const W = 360;
export const H = 600;

// Конвейер — «стадион»: две прямые и две полуокружности. Движение по часовой стрелке.
export const CX = 180;
export const CY = 322;
export const R = 78;          // радиус дуги
export const HALF = 80;       // половина длины прямого участка
export const STRAIGHT = HALF * 2;
export const ARC = Math.PI * R;
export const L = 2 * STRAIGHT + 2 * ARC; // полная длина пути

export const MARBLE_R = 11;   // радиус шарика на конвейере
export const SMALL_R = 9;     // радиус шарика в лотке/коробке

// Точка входа шариков на конвейер (середина верхней прямой) и воронка над ней.
export const ENTRY_S = HALF;
export const CHUTE = { x: CX, y: CY - R - 34 };

// Лотки сверху
export const COL_X = [60, 180, 300];
export const TRAY_W = 96;
export const TRAY_H = 86;
export const TRAY_TOP = 98;       // верх переднего лотка
export const TRAY_STEP = 19;      // смещение вверх для каждого следующего лотка

// Коробки снизу
export const BOX_W = 80;
export const BOX_H = 86;
export const BOX_TOP = 478;
export const BOX_CX = (i) => 45 + 90 * i;

export function mod(a, m) {
  return ((a % m) + m) % m;
}

// Координаты точки на пути конвейера по параметру s.
export function pos(sRaw) {
  const s = mod(sRaw, L);
  if (s < STRAIGHT) {
    return { x: CX - HALF + s, y: CY - R };
  }
  if (s < STRAIGHT + ARC) {
    const phi = (s - STRAIGHT) / R;
    return { x: CX + HALF + R * Math.sin(phi), y: CY - R * Math.cos(phi) };
  }
  if (s < 2 * STRAIGHT + ARC) {
    return { x: CX + HALF - (s - STRAIGHT - ARC), y: CY + R };
  }
  const phi = (s - 2 * STRAIGHT - ARC) / R;
  return { x: CX - HALF - R * Math.sin(phi), y: CY + R * Math.cos(phi) };
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

// Центры трёх ячеек коробки i.
export function boxSlotPos(i, k) {
  return { x: BOX_CX(i) + (k - 1) * 24, y: BOX_TOP + 54 };
}

export function trayRect(ci, depth = 0) {
  const scale = Math.pow(0.93, depth);
  const w = TRAY_W * scale;
  const h = TRAY_H * scale;
  const top = TRAY_TOP - depth * TRAY_STEP;
  return { x: COL_X[ci] - w / 2, y: top + (TRAY_H - h), w, h, cx: COL_X[ci], scale };
}
