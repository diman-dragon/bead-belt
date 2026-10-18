// Общие мелочи работы с DOM.
export const $ = (id) => document.getElementById(id);

// У SVG-элементов нет свойства hidden (оно есть только у HTML), поэтому переключаем атрибут.
export function setHidden(el, hide) {
  if (hide) el.setAttribute('hidden', '');
  else el.removeAttribute('hidden');
}

let toastTimer = 0;
export function showToast(text, ms = 1200) {
  const el = $('toast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), ms);
}

// Перезапускает CSS-анимацию класса cls на элементе.
export function replayAnimation(el, cls) {
  el.classList.remove(cls);
  void el.offsetWidth;
  el.classList.add(cls);
}
