// Проверка уровней: npm run validate
// Код возврата 1, если какой-либо уровень непроходим или данные неконсистентны.
import { LEVELS } from '../www/js/levels.js';
import { checkLevelData, solve, firstMoveStats, minBelt } from '../www/js/solver.js';

let failed = false;
for (const lv of LEVELS) {
  const errs = checkLevelData(lv);
  if (errs.length) {
    failed = true;
    console.error(`✗ Уровень ${lv.id} «${lv.name}»: данные некорректны`);
    errs.forEach((e) => console.error('   - ' + e));
    continue;
  }
  const { solution, visited } = solve(lv);
  if (!solution) {
    failed = true;
    const need = minBelt(lv);
    console.error(`✗ Уровень ${lv.id} «${lv.name}»: НЕПРОХОДИМ при belt=${lv.belt}` +
      (need ? ` (нужно минимум ${need})` : ' (непроходим при любой вместимости)'));
    continue;
  }
  const stats = firstMoveStats(lv);
  const need = minBelt(lv);
  console.log(`✓ Уровень ${lv.id} «${lv.name}»: проходим`);
  console.log(`   belt=${lv.belt}, минимум для прохождения=${need}, запас=${lv.belt - need}`);
  console.log(`   первых ходов, ведущих к победе: ${stats.winning} из ${stats.total}`);
  console.log(`   решение (колонки по порядку): ${solution.map((c) => c + 1).join(' → ')}`);
  console.log(`   проверено состояний: ${visited}`);
}
process.exit(failed ? 1 : 0);
