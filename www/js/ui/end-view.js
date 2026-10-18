// Экран конца уровня: победа или поражение.
import { $ } from './dom.js';

const ICON_WIN = '<svg viewBox="0 0 24 24" width="42" height="42"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_LOSE = '<svg viewBox="0 0 24 24" width="38" height="38"><path d="M6 6l12 12M18 6L6 18" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round"/></svg>';

export function renderEnd(T, { won, last, taps }) {
  const icon = $('endIcon');
  icon.className = 'end-icon ' + (won ? 'win' : 'lose');
  icon.innerHTML = won ? ICON_WIN : ICON_LOSE;
  $('endTitle').textContent = won ? T.win : T.lose;
  $('endText').textContent = won ? (last ? T.winLast(taps) : T.winText(taps)) : T.loseText;
  $('btnNext').hidden = !won;
  $('btnNext').textContent = last ? T.again : T.next;
  $('btnRetry').hidden = won;
  $('btnRetry').textContent = T.retry;
}
