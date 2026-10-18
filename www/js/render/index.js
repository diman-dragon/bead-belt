// Отрисовка кадра: порядок слоёв и публичный интерфейс рендера (тема, масштаб, draw).
import * as G from '../geometry.js';
import { selectTheme } from './theme.js';
import { makeCache, cached, offscreen, blit, clearCaches, setRenderScale } from './cache.js';
import { paintPanel } from './panel.js';
import { paintBinBack, paintBinFront, drawBinMarbles, drawBinHud } from './bin.js';
import { paintBelt, drawBeltDynamic, drawBeltMarbles } from './belt.js';
import { drawTrays } from './trays.js';
import { paintBoxPlatform, drawBoxes, drawBeams } from './boxes.js';
import { drawFlyers, drawConfetti, drawSparks } from './effects.js';
import { drawHint } from './hint.js';

export { COLORS } from './theme.js';
export { setRenderScale };

// Статичные слои. Размеры подобраны по содержимому: чем меньше слой, тем дешевле его копировать каждый кадр.
const BACK = { x: -4, y: 136, w: G.W + 8, h: 446 };    // панель, накопитель сзади, конвейер, платформа
const FRONT = { x: 24, y: 140, w: 312, h: 150 };       // стекло и рамка накопителя
const layerCache = makeCache();

function buildLayers() {
  return {
    back: offscreen(BACK.x, BACK.y, BACK.w, BACK.h, (c) => {
      paintPanel(c); paintBinBack(c); paintBelt(c); paintBoxPlatform(c);
    }),
    front: offscreen(FRONT.x, FRONT.y, FRONT.w, FRONT.h, (c) => paintBinFront(c)),
  };
}

export function setTheme(name) {
  selectTheme(name);
  clearCaches();
}

export function draw(ctx, game) {
  ctx.clearRect(-200, -200, G.W + 400, G.H + 400);
  const t = game.time;
  const layers = cached(layerCache, 'all', buildLayers);
  drawTrays(ctx, game, t);
  blit(ctx, layers.back);
  drawBeltDynamic(ctx, game, t);
  drawBinMarbles(ctx, game);
  blit(ctx, layers.front);
  drawBinHud(ctx, game);
  drawBeams(ctx, game);
  drawBeltMarbles(ctx, game);
  drawBoxes(ctx, game);
  drawFlyers(ctx, game);
  drawSparks(ctx, game);
  drawConfetti(ctx, game);
  drawHint(ctx, game, t);
}
