// Доводит сгенерированный Capacitor-проект android/ до требований Google Play.
// Скрипт идемпотентный: можно запускать сколько угодно раз.
//
//  - compileSdk/targetSdk >= 36 (требование Google Play для новых приложений и обновлений)
//  - портретная ориентация, разрешение VIBRATE для haptics
//  - versionName/versionCode из package.json (X.Y.Z -> X*100+Y*10+Z)
//  - debug и release подписываются одним публичным ключом signing/dev.keystore (только для разработки!)
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { currentVersion, code } from './bump-version.mjs';

const REQUIRED_SDK = 36;
const SIGN_MARK = '// bead-belt:signing';

function read(p) {
  if (!existsSync(p)) {
    console.error(`✗ Не найден ${p}. Сначала выполните: npm run android:init`);
    process.exit(1);
  }
  return readFileSync(p, 'utf8');
}

// ---------- variables.gradle ----------
{
  const p = 'android/variables.gradle';
  let s = read(p);
  const before = s;
  for (const key of ['compileSdkVersion', 'targetSdkVersion']) {
    const re = new RegExp(`(${key}\\s*=\\s*)(\\d+)`);
    const m = s.match(re);
    if (!m) { console.error(`✗ В ${p} не найден ${key}`); process.exit(1); }
    if (Number(m[2]) < REQUIRED_SDK) s = s.replace(re, `$1${REQUIRED_SDK}`);
  }
  if (s !== before) writeFileSync(p, s);
  const t = s.match(/targetSdkVersion\s*=\s*(\d+)/)[1];
  const c = s.match(/compileSdkVersion\s*=\s*(\d+)/)[1];
  console.log(`✓ variables.gradle: compileSdk=${c}, targetSdk=${t}`);
}

// ---------- AndroidManifest.xml ----------
{
  const p = 'android/app/src/main/AndroidManifest.xml';
  let s = read(p);
  let changed = false;
  if (!/android:screenOrientation=/.test(s)) {
    s = s.replace(/<activity\b/, '<activity\n            android:screenOrientation="portrait"');
    changed = true;
  }
  if (!/android\.permission\.VIBRATE/.test(s)) {
    s = s.replace(/<\/manifest>/, '    <uses-permission android:name="android.permission.VIBRATE" />\n</manifest>');
    changed = true;
  }
  if (changed) writeFileSync(p, s);
  console.log('✓ AndroidManifest.xml: portrait, VIBRATE');
}

// ---------- app/build.gradle ----------
{
  const p = 'android/app/build.gradle';
  if (existsSync('android/app/build.gradle.kts') && !existsSync(p)) {
    console.error('✗ Найден build.gradle.kts — этот скрипт рассчитан на Groovy build.gradle. Подпись и версию настройте вручную.');
    process.exit(1);
  }
  let s = read(p);

  // версия: каждый запуск подставляет актуальные значения из package.json
  const version = currentVersion();
  s = s.replace(/versionCode\s+[^\n]+/, `versionCode ${code(version)}`);
  s = s.replace(/versionName\s+[^\n]+/, `versionName "${version}"`);

  // общий публичный ключ для debug и release: все сборки ставятся друг поверх друга
  if (!s.includes(SIGN_MARK)) {
    const signing = `    ${SIGN_MARK}
    signingConfigs {
        dev {
            storeFile rootProject.file("../signing/dev.keystore")
            storePassword "beadbelt"
            keyAlias "beadbelt"
            keyPassword "beadbelt"
        }
    }
`;
    if (!/buildTypes\s*\{/.test(s)) { console.error('✗ В app/build.gradle не найден блок buildTypes'); process.exit(1); }
    s = s.replace(/(\n\s*)buildTypes\s*\{/, `\n${signing}$1buildTypes {\n        debug { signingConfig signingConfigs.dev }`);
    s = s.replace(/(buildTypes\s*\{[\s\S]*?release\s*\{)/, `$1\n            signingConfig signingConfigs.dev`);
    if (!/release\s*\{\s*signingConfig signingConfigs\.dev/.test(s)) {
      console.error('✗ Не удалось подключить подпись к release — проверьте android/app/build.gradle');
      process.exit(1);
    }
  }
  writeFileSync(p, s);
  console.log(`✓ app/build.gradle: версия ${version} (code ${code(version)}), общий ключ signing/dev.keystore`);
}
