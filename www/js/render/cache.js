// Кэши готовых картинок. Всё статическое рисуется один раз и дальше только копируется drawImage —
// на кадр остаются движущиеся части. Это главная оптимизация для слабых телефонов.

// RS — сколько физических пикселей в одной логической единице (живая привязка модуля).
export let RS = 2;

const registry = new Set();

// Создаёт Map-кэш, который очищается вместе со всеми остальными при смене масштаба или темы.
export function makeCache() {
  const m = new Map();
  registry.add(m);
  return m;
}

export function clearCaches() {
  for (const m of registry) m.clear();
}

export function cached(map, key, build) {
  let v = map.get(key);
  if (v === undefined) {
    v = build();
    map.set(key, v);
  }
  return v;
}

export function setRenderScale(scale) {
  const s = Math.max(0.5, Math.round(scale * 16) / 16);
  if (s === RS) return;
  RS = s;
  clearCaches();
}

// Рисует fn(ctx) в отдельный холст, покрывающий логический прямоугольник (x0, y0, w, h).
export function offscreen(x0, y0, w, h, fn) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.ceil(w * RS));
  c.height = Math.max(1, Math.ceil(h * RS));
  const x = c.getContext('2d');
  x.setTransform(RS, 0, 0, RS, -x0 * RS, -y0 * RS);
  fn(x);
  return { c, x0, y0, w: c.width / RS, h: c.height / RS };
}

export function blit(ctx, o) {
  ctx.drawImage(o.c, o.x0, o.y0, o.w, o.h);
}
