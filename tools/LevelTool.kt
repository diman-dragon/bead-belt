import dev.beadbelt.game.*

/**
 * Печатает точный минимум ленты для каждого из 200 уровней и рекомендуемую вместимость (минимум + запас).
 * Помечает уровни, у которых таблица в Levels.kt ниже рекомендуемой. Запуск: см. tools/README.md.
 */
fun main() {
    var bad = 0
    val rec = IntArray(LEVEL_COUNT + 1)
    for (id in 1..LEVEL_COUNT) {
        val min = Planner.minCap(Levels.layout(id, Levels.attemptOf(id))) ?: error("уровень $id не проходится — смени попытку раскладки")
        rec[id] = minOf(30, min + Levels.BELT_SLACK)
        val cur = Levels.beltOf(id)
        val flag = if (cur < rec[id] && cur < 30 || cur < min) { bad++; "  <<< ТАБЛИЦА НИЖЕ НОРМЫ" } else ""
        println("уровень $id: минимум $min, в таблице $cur, рекомендуется ${rec[id]}$flag")
        if (min > 26) println("  ^ минимум больше 26: подумай о другой попытке раскладки (ATTEMPT в Levels.kt)")
    }
    println("\nBELTS = intArrayOf(" + (1..LEVEL_COUNT).joinToString(", ") { "${rec[it]}" } + ")")
    println("уровней ниже нормы: $bad")
}
