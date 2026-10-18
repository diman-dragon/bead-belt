// Сетка выбора уровней.
import { $ } from './dom.js';

function levelButton(level, index, { completed, current, onPick }) {
  const b = document.createElement('button');
  b.className = 'lv';
  b.textContent = String(level.id);
  if (completed[level.id]) b.classList.add('done');
  if (index === current) b.classList.add('cur');
  if (level.fog && (level.fog.top < 2 || level.fog.bottom < 2)) b.classList.add('fog');
  b.addEventListener('click', () => onPick(index));
  return b;
}

export function renderLevelGrid(levels, state, T) {
  const grid = $('lvGrid');
  grid.textContent = '';
  let done = 0;
  levels.forEach((lv, i) => {
    if (state.completed[lv.id]) done++;
    grid.appendChild(levelButton(lv, i, state));
  });
  $('lvCount').textContent = T.levelsCount(done, levels.length);
}
