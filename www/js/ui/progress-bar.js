// Полоска прогресса уровня: двигается через transform (без пересчёта вёрстки) и только при смене процента.
import { $ } from './dom.js';

let shown = -1;

export function setProgress(pct) {
  if (pct === shown) return;
  shown = pct;
  $('progBar').style.transform = `scaleX(${pct / 100})`;
}

export function resetProgress() {
  shown = -1;
  setProgress(0);
}
