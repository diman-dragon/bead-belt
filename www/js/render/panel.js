// Панель-«станок», на которой стоят накопитель, конвейер и коробки (статичная, рисуется в слой один раз).
import * as G from '../geometry.js';
import { TH } from './theme.js';
import { roundRect, vgrad } from './draw-util.js';

const PANEL = { x: 14, w: 332, h: 264, r: 24 };

function screw(ctx, x, y) {
  const g = ctx.createRadialGradient(x - 1, y - 1, 0.5, x, y, 4);
  g.addColorStop(0, TH.screw[0]);
  g.addColorStop(1, TH.screw[1]);
  ctx.fillStyle = g;
  ctx.beginPath(); ctx.arc(x, y, 3.6, 0, Math.PI * 2); ctx.fill();
  ctx.strokeStyle = 'rgba(10,16,30,0.7)';
  ctx.lineWidth = 1.2;
  ctx.beginPath(); ctx.moveTo(x - 2, y + 1); ctx.lineTo(x + 2, y - 1); ctx.stroke();
}

function paintPanelBody(ctx, x, y) {
  const { w, h, r } = PANEL;
  ctx.fillStyle = 'rgba(0,0,0,0.35)';
  roundRect(ctx, x + 2, y + 7, w, h, r); ctx.fill();
  ctx.fillStyle = vgrad(ctx, y, y + h, [[0, TH.panel[0]], [0.5, TH.panel[1]], [1, TH.panel[2]]]);
  roundRect(ctx, x, y, w, h, r); ctx.fill();
  ctx.strokeStyle = TH.panelEdge;
  ctx.lineWidth = 2;
  roundRect(ctx, x + 1, y + 1, w - 2, h - 2, r - 1); ctx.stroke();
  ctx.strokeStyle = TH.panelInner;
  ctx.lineWidth = 1.5;
  roundRect(ctx, x + 6, y + 6, w - 12, h - 12, r - 5); ctx.stroke();
}

// мягкое свечение в центре
function paintPanelGlow(ctx, x, y) {
  const { w, h, r } = PANEL;
  const g = ctx.createRadialGradient(G.CX, G.CY - 10, 10, G.CX, G.CY - 10, 190);
  g.addColorStop(0, TH.panelGlow);
  g.addColorStop(1, 'rgba(80,130,230,0)');
  ctx.fillStyle = g;
  roundRect(ctx, x, y, w, h, r); ctx.fill();
}

function paintScrews(ctx, x, y) {
  const { w, h } = PANEL;
  screw(ctx, x + 14, y + 14); screw(ctx, x + w - 14, y + 14);
  screw(ctx, x + 14, y + h - 14); screw(ctx, x + w - 14, y + h - 14);
}

export function paintPanel(ctx) {
  const x = PANEL.x, y = G.BIN.top - 8;
  ctx.save();
  paintPanelBody(ctx, x, y);
  paintPanelGlow(ctx, x, y);
  paintScrews(ctx, x, y);
  ctx.restore();
}
