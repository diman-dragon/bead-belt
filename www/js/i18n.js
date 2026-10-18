// Строки интерфейса (ru/en) и выбор языка. Без DOM-зависимостей, кроме чтения языка браузера.
const STR = {
  ru: {
    sub: 'Высыпай шарики на конвейер — собирай коробки одного цвета.',
    play: 'Играть', cont: 'Продолжить', level: 'Уровень', levels: 'Уровни', settings: 'Настройки', about: 'О нас',
    pause: 'Пауза', resume: 'Продолжить', restart: 'Начать заново', menu: 'В меню', next: 'Дальше', again: 'Играть ещё раз', retry: 'Ещё раз',
    win: 'Уровень пройден!', lose: 'Конвейер забит!',
    winText: (n) => `Нажатий: ${n}.`, winLast: (n) => `Нажатий: ${n}. Это был последний уровень — дальше всё сначала!`,
    loseText: 'Нет места для шариков, а подходящих коробок нет. Попробуй другой порядок лотков.',
    soundOn: 'Звук включён', soundOff: 'Звук выключен',
    sound: 'Звук', vibration: 'Вибрация', theme: 'Тема', themeDark: 'Тёмная', themeLight: 'Светлая', themeAuto: 'Авто',
    language: 'Язык', langAuto: 'Авто', reset: 'Сбросить прогресс', resetSure: 'Нажми ещё раз — сбросить всё', resetDone: 'Прогресс сброшен',
    legDone: 'пройден', legFog: 'с туманом',
    version: 'Версия', levelsCount: (a, b) => `${a} / ${b}`,
    aboutText: 'Bead Belt — головоломка про шарики и конвейер. Высыпай лотки в накопитель, лови нужные цвета в коробки и не дай конвейеру забиться.',
    aboutPrivacy: 'Игра не собирает и не передаёт никаких данных: прогресс хранится только на этом устройстве.',
    aboutMade: (n) => `${n} уровней · сделано на Capacitor`,
    hint: ['Нажимай на лотки сверху:', 'шарики попадут в коробки своего цвета'],
  },
  en: {
    sub: 'Drop beads on the belt and fill the boxes with matching colors.',
    play: 'Play', cont: 'Continue', level: 'Level', levels: 'Levels', settings: 'Settings', about: 'About',
    pause: 'Paused', resume: 'Resume', restart: 'Restart', menu: 'Menu', next: 'Next', again: 'Play again', retry: 'Try again',
    win: 'Level complete!', lose: 'Belt jammed!',
    winText: (n) => `Taps: ${n}.`, winLast: (n) => `Taps: ${n}. That was the last level — starting over!`,
    loseText: 'The belt is full and no box matches. Try a different tray order.',
    soundOn: 'Sound on', soundOff: 'Sound off',
    sound: 'Sound', vibration: 'Vibration', theme: 'Theme', themeDark: 'Dark', themeLight: 'Light', themeAuto: 'Auto',
    language: 'Language', langAuto: 'Auto', reset: 'Reset progress', resetSure: 'Tap again to erase everything', resetDone: 'Progress reset',
    legDone: 'completed', legFog: 'fog',
    version: 'Version', levelsCount: (a, b) => `${a} / ${b}`,
    aboutText: 'Bead Belt is a puzzle about beads and a conveyor belt. Pour the trays into the hopper, catch the right colors with the boxes and keep the belt from jamming.',
    aboutPrivacy: 'The game collects and sends no data: progress is stored only on this device.',
    aboutMade: (n) => `${n} levels · made with Capacitor`,
    hint: ['Tap the trays on top:', 'beads go into boxes of their color'],
  },
};

// Язык системы: русский для ru/uk/be, иначе английский.
export function systemLang() {
  return /^ru|^uk|^be/i.test((typeof navigator !== 'undefined' && navigator.language) || '') ? 'ru' : 'en';
}

// pref — настройка пользователя: 'auto' | 'ru' | 'en'.
export function resolveLang(pref) {
  return pref === 'auto' || !STR[pref] ? systemLang() : pref;
}

export function strings(lang) {
  return STR[lang] || STR.en;
}
