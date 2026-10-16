// Проверки сгенерированного Android-проекта (запускаются в CI): npm run check:android
import { readFileSync, existsSync } from 'node:fs';

const errors = [];
const cfg = JSON.parse(readFileSync('capacitor.config.json', 'utf8'));
if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(cfg.appId)) errors.push(`appId «${cfg.appId}» имеет недопустимый формат.`);

const vars = 'android/variables.gradle';
if (!existsSync(vars)) errors.push('Нет android/variables.gradle — выполните npm run android:init.');
else {
  const t = Number(readFileSync(vars, 'utf8').match(/targetSdkVersion\s*=\s*(\d+)/)?.[1] ?? 0);
  if (t < 36) errors.push(`targetSdkVersion=${t}; для будущей публикации в Google Play нужно 36 или выше.`);
}
if (!existsSync('signing/dev.keystore')) errors.push('Нет signing/dev.keystore.');

if (errors.length) { console.error('✗ Проверки Android:'); errors.forEach((e) => console.error('  - ' + e)); process.exit(1); }
console.log(`✓ Android: appId ${cfg.appId}, targetSdk >= 36, общий ключ на месте`);
