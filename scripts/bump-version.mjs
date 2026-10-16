// Поднимает версию на единицу в последнем разряде с переносом: 0.0.9 -> 0.1.0, 0.9.9 -> 1.0.0.
// Каждый разряд 0..9, поэтому versionCode = major*100 + minor*10 + patch (0.0.1 -> 1, 0.1.0 -> 10).
// Запуск: npm run bump           (ещё: npm run bump -- --check — только проверить согласованность)
import { readFileSync, writeFileSync } from 'node:fs';

export function parse(v) {
  const m = /^(\d)\.(\d)\.(\d)$/.exec(v);
  if (!m) throw new Error(`Версия «${v}» должна быть вида X.Y.Z, где каждая цифра 0–9`);
  return m.slice(1).map(Number);
}
export const next = (v) => {
  let [a, b, c] = parse(v);
  if (++c > 9) { c = 0; if (++b > 9) { b = 0; a++; } }
  if (a > 9) throw new Error('Версия переполнена (больше 9.9.9)');
  return `${a}.${b}.${c}`;
};
export const code = (v) => { const [a, b, c] = parse(v); return a * 100 + b * 10 + c; };

export function writeVersionFiles(v) {
  const pkg = readFileSync('package.json', 'utf8').replace(/("version":\s*")[^"]+(")/, `$1${v}$2`);
  writeFileSync('package.json', pkg);
  writeFileSync('www/js/version.js', `// Генерируется scripts/bump-version.mjs — не править руками\nexport const VERSION = '${v}';\n`);
}

export function currentVersion() {
  return JSON.parse(readFileSync('package.json', 'utf8')).version;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const cur = currentVersion();
  if (process.argv.includes('--check')) {
    const js = readFileSync('www/js/version.js', 'utf8');
    if (!js.includes(`'${cur}'`)) { console.error(`✗ www/js/version.js не совпадает с package.json (${cur}). Запустите npm run bump:sync`); process.exit(1); }
    parse(cur);
    console.log(`✓ версия ${cur} (versionCode ${code(cur)})`);
  } else if (process.argv.includes('--sync')) {
    writeVersionFiles(cur);
    console.log(`✓ синхронизировано: ${cur}`);
  } else {
    const n = next(cur);
    writeVersionFiles(n);
    console.log(`✓ ${cur} → ${n} (versionCode ${code(n)})`);
  }
}
