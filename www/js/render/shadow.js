// Мягкая овальная тень под предметами — один маленький спрайт на всё.
let sprite = null;

function buildShadow() {
  const c = document.createElement('canvas');
  c.width = 64; c.height = 32;
  const x = c.getContext('2d');
  x.translate(32, 16);
  x.scale(1, 0.5);
  const g = x.createRadialGradient(0, 0, 0, 0, 0, 32);
  g.addColorStop(0, 'rgba(0,0,0,0.55)');
  g.addColorStop(0.6, 'rgba(0,0,0,0.2)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  x.fillStyle = g;
  x.beginPath(); x.arc(0, 0, 32, 0, Math.PI * 2); x.fill();
  return c;
}

export function drawShadow(ctx, x, y, w, h, alpha = 1) {
  if (!sprite) sprite = buildShadow();
  if (alpha !== 1) ctx.globalAlpha = alpha;
  ctx.drawImage(sprite, x - w / 2, y - h / 2, w, h);
  if (alpha !== 1) ctx.globalAlpha = 1;
}
