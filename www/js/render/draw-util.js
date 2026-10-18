// Мелкие рисовальные примитивы без знания об игре.
const TAU = Math.PI * 2;

export function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

// Путь-круг (без заливки).
export function circle(ctx, x, y, r) {
  ctx.beginPath();
  ctx.arc(x, y, r, 0, TAU);
}

export function fillCircle(ctx, x, y, r) {
  circle(ctx, x, y, r);
  ctx.fill();
}

export function line(ctx, x0, y0, x1, y1) {
  ctx.beginPath();
  ctx.moveTo(x0, y0);
  ctx.lineTo(x1, y1);
  ctx.stroke();
}

export function vgrad(ctx, y0, y1, stops) {
  const g = ctx.createLinearGradient(0, y0, 0, y1);
  for (const [o, c] of stops) g.addColorStop(o, c);
  return g;
}

export function hgrad(ctx, x0, x1, stops) {
  const g = ctx.createLinearGradient(x0, 0, x1, 0);
  for (const [o, c] of stops) g.addColorStop(o, c);
  return g;
}

export function sparkle(ctx, x, y, r) {
  ctx.beginPath();
  ctx.moveTo(x, y - r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.quadraticCurveTo(x, y, x, y + r);
  ctx.quadraticCurveTo(x, y, x - r, y);
  ctx.quadraticCurveTo(x, y, x, y - r);
  ctx.fill();
}

// Ставит систему координат в позу (см. viewmodel.makePose): привязка, поворот, масштаб и смещение к точке pivotY.
export function applyPose(ctx, pose, pivotY) {
  ctx.translate(pose.x, pose.y);
  if (pose.rot) ctx.rotate(pose.rot);
  ctx.scale(pose.sx, pose.sy);
  ctx.translate(0, -pivotY);
}

// Выполняет fn с режимом наложения «lighter» (свечение) и возвращает всё как было.
export function glowing(ctx, alpha, fn) {
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  ctx.globalAlpha = alpha;
  fn();
  ctx.restore();
}

// Знак «?» на скрытых лотках и коробках («туман»).
export function questionMark(ctx, y, px) {
  ctx.fillStyle = 'rgba(255,255,255,0.55)';
  ctx.font = `800 ${px}px system-ui, sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('?', 0, y);
}
