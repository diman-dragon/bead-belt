// Создаёт/обновляет Android-проект: npm run android:init
// Порядок: cap add android (если нет) -> иконки -> патч под Google Play -> cap sync
import { existsSync } from 'node:fs';
import { execSync } from 'node:child_process';

const run = (cmd) => { console.log(`\n$ ${cmd}`); execSync(cmd, { stdio: 'inherit' }); };

if (!existsSync('node_modules')) {
  console.error('✗ Нет node_modules. Сначала выполните: npm install');
  process.exit(1);
}
if (!existsSync('android')) run('npx cap add android');
if (existsSync('assets/icon-only.png')) run('npx capacitor-assets generate --android');
run('node scripts/patch-android.mjs');
run('npx cap sync android');
console.log('\n✓ Android-проект готов: откройте его командой npm run android:open или соберите ./gradlew в папке android');
