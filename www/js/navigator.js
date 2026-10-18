// Навигация по экранам: текущий режим и стек «куда вернуться».
export class Navigator {
  // screens: { имя: HTMLElement }; onChange(mode) вызывается после каждого переключения.
  constructor(screens, onChange) {
    this.screens = screens;
    this.onChange = onChange;
    this.mode = 'menu';  // menu | levels | settings | about | play | pause | end
    this.stack = [];
  }

  // name = null — экран игры (все оверлеи скрыты).
  show(name) {
    for (const [k, el] of Object.entries(this.screens)) el.hidden = k !== name;
    this.mode = name || 'play';
    this.onChange(this.mode);
  }

  // Открыть вложенный экран, запомнив текущий.
  open(name) {
    this.stack.push(this.mode);
    this.show(name);
  }

  // Куда вернуться: предыдущий экран или меню.
  back() {
    const prev = this.stack.pop();
    return !prev || prev === 'menu' ? 'menu' : prev;
  }

  clear() {
    this.stack.length = 0;
  }
}
