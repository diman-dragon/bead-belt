// Объёмный стеклянный шарик: один большой спрайт на цвет, дальше он уменьшается до реального размера на экране.
import { col } from './theme.js';
import { RS, makeCache, cached } from './cache.js';
import { drawShadow } from './shadow.js';

const SP = 128;   // размер большого спрайта
const PAD = 10;   // поле под свечение
const TAU = Math.PI * 2;

const hiRes = new Map();        // большие спрайты не зависят ни от темы, ни от масштаба — живут вечно
const scaledSprites = makeCache();

// Спрайт рисуется шагами; все шаги получают одно и то же описание шарика.
function describe(key, x) {
  const m = SP / 2;
  return { x, k: col(key), key, m, r: m - PAD };
}

function discPath(s) {
  s.x.beginPath();
  s.x.arc(s.m, s.m, s.r, 0, TAU);
}

// Едва заметный значок формы на шарике — для людей, не различающих цвета.
const GLYPHS = {
  R: (x, cx, cy, r) => { x.moveTo(cx, cy - r * 0.2); x.lineTo(cx + r * 0.22, cy + r * 0.18); x.lineTo(cx - r * 0.22, cy + r * 0.18); x.closePath(); x.fill(); },
  B: (x, cx, cy, r) => { x.rect(cx - r * 0.16, cy - r * 0.16, r * 0.32, r * 0.32); x.fill(); },
  G: (x, cx, cy, r) => { x.arc(cx, cy, r * 0.18, 0, TAU); x.stroke(); },
  Y: (x, cx, cy, r) => { x.moveTo(cx, cy - r * 0.24); x.lineTo(cx + r * 0.2, cy); x.lineTo(cx, cy + r * 0.24); x.lineTo(cx - r * 0.2, cy); x.closePath(); x.fill(); },
  P: (x, cx, cy, r) => { x.moveTo(cx - r * 0.2, cy); x.lineTo(cx + r * 0.2, cy); x.moveTo(cx, cy - r * 0.2); x.lineTo(cx, cy + r * 0.2); x.stroke(); },
};
const dotGlyph = (x, cx, cy, r) => { x.arc(cx, cy, r * 0.1, 0, TAU); x.fill(); };

function paintGlyph(s) {
  const { x, r } = s;
  x.save();
  x.fillStyle = 'rgba(255,255,255,0.34)';
  x.strokeStyle = 'rgba(255,255,255,0.34)';
  x.lineWidth = r * 0.12;
  x.beginPath();
  (GLYPHS[s.key] || dotGlyph)(x, s.m + r * 0.03, s.m + r * 0.18, r);
  x.restore();
}

// Радиальный градиент между кругами (x0, y0, r0) и (x1, y1, r1).
function radial(x, x0, y0, r0, x1, y1, r1, stops) {
  const g = x.createRadialGradient(x0, y0, r0, x1, y1, r1);
  for (const [o, c] of stops) g.addColorStop(o, c);
  return g;
}

// мягкое неоновое свечение вокруг шарика
function paintAura(s) {
  const { x, k, m, r } = s;
  x.fillStyle = radial(x, m, m, r * 0.85, m, m, r + PAD * 0.95, [[0, k.base + '55'], [1, k.base + '00']]);
  x.fillRect(0, 0, SP, SP);
}

// основное тело сферы
function paintBody(s) {
  const { x, k, m, r } = s;
  x.fillStyle = radial(x, m - r * 0.36, m - r * 0.42, r * 0.04, m + r * 0.04, m + r * 0.06, r * 1.06,
    [[0, k.hl], [0.12, k.lt], [0.5, k.base], [0.86, k.dk], [1, k.deep]]);
  discPath(s);
  x.fill();
}

// свет, прошедший сквозь «стекло» — тёплое пятно в нижней части
function paintInnerLight(s) {
  const { x, k, m, r } = s;
  x.globalCompositeOperation = 'screen';
  x.globalAlpha = 0.6;
  x.fillStyle = radial(x, m + r * 0.22, m + r * 0.42, r * 0.02, m + r * 0.22, m + r * 0.42, r * 0.95, [[0, k.lt], [0.55, k.base + '66'], [1, k.base + '00']]);
  x.fillRect(0, 0, SP, SP);
  x.globalAlpha = 1;
  x.globalCompositeOperation = 'source-over';
}

// затемнённая кромка (эффект Френеля)
function paintFresnel(s) {
  const { x, m, r } = s;
  x.fillStyle = radial(x, m, m, r * 0.6, m, m, r, [[0, 'rgba(0,0,0,0)'], [1, 'rgba(0,0,0,0.34)']]);
  x.fillRect(0, 0, SP, SP);
}

// всё, что должно лежать внутри круга: внутренний свет, кромка, значок
function paintInterior(s) {
  s.x.save();
  discPath(s);
  s.x.clip();
  paintInnerLight(s);
  paintFresnel(s);
  paintGlyph(s);
  s.x.restore();
}

// отражённый свет по нижнему правому краю
function paintBounce(s) {
  const { x, k, m, r } = s;
  x.save();
  discPath(s);
  x.clip();
  x.strokeStyle = k.hl;
  x.globalAlpha = 0.5;
  x.lineWidth = r * 0.12;
  x.lineCap = 'round';
  x.beginPath(); x.arc(m, m, r * 0.9, Math.PI * 0.08, Math.PI * 0.62); x.stroke();
  x.restore();
}

// основной блик (окно) + мягкое свечение вокруг него
function paintWindowHighlight(s) {
  const { x, m, r } = s;
  x.save();
  x.translate(m - r * 0.37, m - r * 0.5);
  x.rotate(-0.62);
  x.fillStyle = radial(x, 0, 0, 0, 0, 0, r * 0.5, [[0, 'rgba(255,255,255,0.45)'], [1, 'rgba(255,255,255,0)']]);
  x.beginPath(); x.ellipse(0, 0, r * 0.5, r * 0.34, 0, 0, TAU); x.fill();
  const g = x.createLinearGradient(0, -r * 0.2, 0, r * 0.2);
  g.addColorStop(0, 'rgba(255,255,255,1)');
  g.addColorStop(1, 'rgba(255,255,255,0.3)');
  x.fillStyle = g;
  x.beginPath(); x.ellipse(0, 0, r * 0.3, r * 0.15, 0, 0, TAU); x.fill();
  x.restore();
}

// острая точка-искра и вторичный блик
function paintSparks(s) {
  const { x, m, r } = s;
  x.fillStyle = 'rgba(255,255,255,0.95)';
  x.beginPath(); x.arc(m - r * 0.12, m - r * 0.7, r * 0.055, 0, TAU); x.fill();
  x.fillStyle = 'rgba(255,255,255,0.5)';
  x.beginPath(); x.ellipse(m + r * 0.46, m + r * 0.52, r * 0.1, r * 0.052, -0.8, 0, TAU); x.fill();
}

// тонкий контур
function paintOutline(s) {
  const { x, k, m, r } = s;
  x.strokeStyle = k.deep + 'aa';
  x.lineWidth = 1.3;
  x.beginPath(); x.arc(m, m, r - 0.5, 0, TAU); x.stroke();
}

function buildSprite(key) {
  const c = document.createElement('canvas');
  c.width = c.height = SP;
  const s = describe(key, c.getContext('2d'));
  paintAura(s);
  paintBody(s);
  paintInterior(s);
  paintBounce(s);
  paintWindowHighlight(s);
  paintSparks(s);
  paintOutline(s);
  return c;
}

// Спрайт нужного размера: большой один раз уменьшается до реальных пикселей (рисовать 128px в 18px на каждом кадре дорого).
function scaledSprite(key, d) {
  const hi = cached(hiRes, key, () => buildSprite(key));
  const px = Math.max(4, Math.round(d * RS));
  if (px >= SP * 0.9) return hi;
  return cached(scaledSprites, key + px, () => {
    const c = document.createElement('canvas');
    c.width = c.height = px;
    const x = c.getContext('2d');
    x.imageSmoothingEnabled = true;
    x.imageSmoothingQuality = 'high';
    x.drawImage(hi, 0, 0, px, px);
    return c;
  });
}

const SPRITE_SCALE = SP / (SP - 2 * PAD); // спрайт больше самого шарика на поле свечения

// Шарик радиусом r в (x, y). sx/sy — сжатие при попадании в гнездо.
export function drawMarble(ctx, key, x, y, r, alpha = 1, shadow = false, sx = 1, sy = 1) {
  const d = r * 2 * SPRITE_SCALE;
  const s = scaledSprite(key, d);
  if (shadow) drawShadow(ctx, x + 1.5, y + r * 0.9 + 1.5, r * 2.6, r * 1.4, alpha * 0.9);
  if (alpha !== 1) ctx.globalAlpha = alpha;
  if (sx === 1 && sy === 1) ctx.drawImage(s, x - d / 2, y - d / 2, d, d);
  else ctx.drawImage(s, x - (d * sx) / 2, y - (d * sy) / 2, d * sx, d * sy);
  if (alpha !== 1) ctx.globalAlpha = 1;
}
