# Пересчёт вместимости ленты

Вместимость ленты каждого уровня лежит в таблице `BELTS` (`Levels.kt`) и считается заранее, поэтому на устройстве
уровень создаётся мгновенно, без поиска. Таблица = `max(прежнее значение, точный минимум + 2)`.

Точный минимум считает `Planner.minCap`. Модель в `Planner.kt` проверена на настоящей физике воронки
(`PlannerTest`), поэтому минимум — не оценка, а гарантия: при такой ленте уровень можно пройти.

## Когда пересчитывать
Если менял `Levels.layout` (генерацию раскладки), правила коробок/лент в `Game.kt` или `Planner.kt`,
тест `LevelsTest.beltTableMatchesThePlanner` упадёт. Тогда:

```sh
# нужен kotlinc (https://github.com/JetBrains/kotlin/releases) и android.jar любой версии для компиляции Game.kt
G=app/src/main/kotlin/dev/beadbelt/game
kotlinc -cp android.jar $G/Levels.kt $G/Planner.kt tools/LevelTool.kt -d /tmp/lt
java -Xss128m -cp /tmp/lt:<kotlin-stdlib.jar> LevelToolKt
```

Скопируй напечатанную строку `BELTS = ...` в `Levels.kt`. Если у уровня минимум больше 26 (почти нет запаса),
выбери для него другую попытку раскладки в `ATTEMPT`.

Раскладки уровней 1–100 менять нельзя: их защищает `firstHundredLayoutsStayExactlyTheSame`.
