// Размер холста, плотность пикселей и пересчёт координат касания в логические.
import * as G from './geometry.js';
import { setRenderScale } from './render/index.js';

const MAX_DPR = 2; // выше 2x на телефоне глазом не видно, а стоит дорого

export function createViewport(canvas, quality, onResize) {
  let view = { k: 1, ox: 0, oy: 0, dpr: 1 };

  function resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR) * quality.value;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    canvas.width = Math.max(1, Math.round(w * dpr));
    canvas.height = Math.max(1, Math.round(h * dpr));
    const k = Math.min(w / G.W, h / G.H);
    const eff = w ? canvas.width / w : dpr;
    view = { k, ox: (w - G.W * k) / 2, oy: (h - G.H * k) / 2, dpr: eff };
    setRenderScale(eff * k);
    onResize();
  }

  new ResizeObserver(resize).observe(canvas);
  window.addEventListener('resize', resize);

  return {
    resize,
    // Ставит преобразование «логические координаты → пиксели холста».
    apply(ctx) {
      const s = view.dpr * view.k;
      ctx.setTransform(s, 0, 0, s, view.dpr * view.ox, view.dpr * view.oy);
    },
    toLogical(e) {
      const r = canvas.getBoundingClientRect();
      return { x: (e.clientX - r.left - view.ox) / view.k, y: (e.clientY - r.top - view.oy) / view.k };
    },
  };
}
