// Палитра шариков и темы «станка». Только данные и выбор текущей темы.

// hl — блик, lt — свет, base — основной тон, dk — тень, deep — самая тёмная кромка
// Неоновая палитра «под AMOLED»: чистые насыщенные оттенки, разнесённые по кругу (~55–75° друг от друга).
// Ключ O — бирюзовый (раньше был оранжевым: слишком похож на красный и жёлтый).
export const COLORS = {
  R: { hl: '#ff9aa6', lt: '#ff4560', base: '#ff0a2e', dk: '#b5001c', deep: '#5a0010' },
  Y: { hl: '#fff98a', lt: '#ffee2e', base: '#ffe100', dk: '#b8a800', deep: '#585000' },
  G: { hl: '#8affb0', lt: '#2dff7c', base: '#00e65a', dk: '#009a3a', deep: '#004d1c' },
  O: { hl: '#8af4ff', lt: '#2ee9ff', base: '#00d4f5', dk: '#0090b8', deep: '#00465c' },
  B: { hl: '#9db4ff', lt: '#4d73ff', base: '#1f45ff', dk: '#0d28c4', deep: '#060f66' },
  P: { hl: '#f59cff', lt: '#e84bff', base: '#d010ff', dk: '#8e00b8', deep: '#46005e' },
  // «туман»: скрытый лоток/коробка (цвет неизвестен)
  X: { hl: '#8d9bc4', lt: '#667399', base: '#46517a', dk: '#323b5e', deep: '#1a2040' },
};

export const col = (k) => COLORS[k] || COLORS.R;

// Темы «станка» (холст). Тёмная — под AMOLED, светлая — серебристая.
const THEMES = {
  dark: {
    panel: ['#26396b', '#16254f', '#0b1431'], panelEdge: 'rgba(120,150,210,0.4)', panelInner: 'rgba(0,0,0,0.55)', panelGlow: 'rgba(80,130,230,0.18)',
    binWall: ['#0c1636', '#1c3162'], binGlow: ['rgba(110,170,255,0.2)', 'rgba(110,170,255,0)'], grid: 'rgba(140,180,255,0.06)',
    tube: ['#2a3552', '#8fa0c4', '#46557a', '#1c2640'], tubeIn: 'rgba(5,10,24,0.82)',
    frameDark: '#060a18', frameLight: '#8a9bc0', frameMid: '#475a86', frameHi: 'rgba(255,255,255,0.5)',
    flange: ['#b5c3e0', '#52638c'], gate: ['#c3cfe8', '#6f81aa', '#2b3860'],
    gaugeBg: 'rgba(4,8,20,0.7)', gaugeEdge: 'rgba(160,185,235,0.4)', gaugeText: 'rgba(190,205,240,0.85)',
    island: ['#060b1a', '#0f1833'], rail: ['#0a1020', '#aab8d6', '#5f7099', '#27345a', '#0d1426'],
    ribDark: 'rgba(0,0,0,0.55)', ribLight: 'rgba(150,180,255,0.14)', counter: 'rgba(200,215,245,0.9)',
    plat: ['#222f55', '#0f1834'], platEdge: 'rgba(160,185,235,0.32)', slot: ['rgba(2,5,16,0.88)', 'rgba(2,5,16,0.5)'], slotEdge: ['rgba(150,180,240,0.4)', 'rgba(150,180,240,0.15)'],
    screw: ['#9fb0cf', '#3a4766'], shade: '#040a1c', shadow: 0.35,
  },
  light: {
    panel: ['#f3f6ff', '#d6dff4', '#bccae8'], panelEdge: 'rgba(255,255,255,0.95)', panelInner: 'rgba(70,90,140,0.28)', panelGlow: 'rgba(255,255,255,0.55)',
    binWall: ['#c8d6f3', '#adc1ec'], binGlow: ['rgba(255,255,255,0.6)', 'rgba(255,255,255,0)'], grid: 'rgba(60,90,160,0.11)',
    tube: ['#94a3c6', '#f2f5ff', '#b4c0dd', '#7280a6'], tubeIn: 'rgba(40,55,95,0.78)',
    frameDark: '#56668f', frameLight: '#f6f8ff', frameMid: '#a7b6d8', frameHi: 'rgba(255,255,255,0.9)',
    flange: ['#ffffff', '#a7b6d8'], gate: ['#ffffff', '#b0bedb', '#7a8ab1'],
    gaugeBg: 'rgba(255,255,255,0.75)', gaugeEdge: 'rgba(60,80,130,0.5)', gaugeText: 'rgba(40,55,95,0.95)',
    island: ['#a9b7d8', '#c3cfea'], rail: ['#5f6f98', '#f5f7ff', '#c2cde8', '#9cadd3', '#7e8eb6'],
    ribDark: 'rgba(40,55,95,0.4)', ribLight: 'rgba(255,255,255,0.4)', counter: 'rgba(30,45,85,0.92)',
    plat: ['#dde5f7', '#bdcbe9'], platEdge: 'rgba(60,80,130,0.35)', slot: ['rgba(70,90,140,0.4)', 'rgba(70,90,140,0.2)'], slotEdge: ['rgba(60,80,130,0.5)', 'rgba(60,80,130,0.22)'],
    screw: ['#ffffff', '#8c9bc0'], shade: '#6f7ba0', shadow: 0.22,
  },
};

// Текущая тема. Это «живая» привязка модуля: после selectTheme все импортёры видят новое значение.
export let TH = THEMES.dark;

export function selectTheme(name) {
  TH = THEMES[name] || THEMES.dark;
}
