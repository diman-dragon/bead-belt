package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
    private val all by lazy { (1..LEVEL_COUNT).map { Levels.make(it) } }

    @Test
    fun allLevelsAreConsistent() {
        for (l in all) {
            val trayCount = IntArray(COLOR_COUNT)
            l.columns.flatten().forEach { trayCount[it.c] += it.n }
            val boxCount = IntArray(COLOR_COUNT)
            l.boxes.forEach { boxCount[it]++ }
            for (c in 0 until COLOR_COUNT) assertEquals("уровень ${l.id} цвет $c", boxCount[c] * BOX, trayCount[c])
        }
    }

    /** Лента каждого уровня не меньше минимума модели, и в таблицу попало нужное число значений. */
    @Test
    fun beltTableIsSane() {
        for (l in all) {
            val min = Planner.minCap(Levels.layout(l.id, Levels.attemptOf(l.id)))
            assertTrue("уровень ${l.id} не проходится ни при какой ленте", min != null)
            assertTrue("уровень ${l.id}: лента ${l.belt} меньше минимума $min", l.belt >= min!!)
            assertTrue("уровень ${l.id}: лента ${l.belt} вне 8..30", l.belt in 8..30)
        }
    }

    /** Подпись раскладки: колонки лотков (цвет.число.стрелка), коробки, лифт. */
    private fun signature(l: Level) =
        l.columns.joinToString("|") { c -> c.joinToString(",") { "${it.c}.${it.n}.${it.arrow}" } } + "@" + l.boxes.joinToString(",") + "@" + l.elevator

    /** Kotlin-раскладка совпадает с Python-генератором (tools/gen_levels.py), по которому посчитаны ATTEMPT и BELTS. */
    @Test
    fun layoutsMatchTheGenerator() {
        val expected = mapOf(
            1 to "1.6.0|0.6.0|2.6.0@0,0,2,1,1,2@-1",
            37 to "6.6.0,2.3.0,4.3.2,5.6.0,0.4.0|1.8.2,4.6.0,3.3.0,4.3.0,5.6.0|2.4.1,6.3.0,3.6.0,2.5.0,0.4.0|6.3.1,1.4.0,0.4.0,3.3.0@2,1,5,2,4,3,5,3,6,0,4,2,6,1,3,0,5,3,1,4,0,1,2,0,5,6,4,6@-1",
            250 to "5.6.2,0.4.0,7.3.0,2.5.0,1.3.0,6.5.2,0.4.0|0.4.0,0.3.1,6.4.0,2.6.0,1.4.0,6.3.0,6.3.0|5.5.3,7.4.0,4.6.0,1.5.0,3.6.0,4.3.0,2.4.0|3.3.1,7.4.0,1.3.0,4.6.0,3.6.0,5.4.0,7.4.0@3,2,3,6,6,2,0,6,0,4,2,1,5,7,0,2,5,7,0,4,5,4,0,4,5,1,3,1,1,7,6,1,7,3,5,2,7,6,4,3@-1",
            499 to "1.6.0,4.4.0,7.3.0,4.4.0,6.5.0,3.6.0,3.6.0,5.4.0|6.3.0,2.4.3,2.3.0,2.6.0,3.6.0,0.3.0,1.3.0,0.6.1|4.4.0,7.3.0,2.5.0,4.6.0,7.7.0,5.5.1,1.4.0,6.3.2|0.3.0,5.4.0,1.5.1,5.5.1,0.6.0,7.5.0,6.7.0@4,2,1,0,2,5,1,0,0,0,1,4,3,7,3,7,5,2,6,0,2,1,3,1,6,3,4,6,6,5,2,5,4,3,7,4,6,3,4,7,6,7,7,2,5,0,1,5@-1",
        )
        for ((id, sig) in expected) assertEquals("уровень $id", sig, signature(Levels.make(id)))
    }

    @Test
    fun levelsGrowAndStayWithinTheBeltLimit() {
        assertEquals(500, LEVEL_COUNT)
        assertTrue(all.all { it.belt in 8..30 })
        assertEquals(6, all[0].boxes.size)
        assertTrue(all.filter { it.id > 350 }.all { it.boxes.size == 48 })
        val avgBelt = { r: IntRange -> all.filter { it.id in r }.map { it.belt }.average() }
        assertTrue("лента должна расти вместе со сложностью", avgBelt(101..200) > avgBelt(1..30))
        assertTrue(all.filter { it.id >= 20 && it.id <= 250 && it.id % 3 == 0 }.all { it.elevator >= 0 })
        assertTrue(all.filter { it.id > 250 && it.id % 2 == 0 }.all { it.elevator >= 0 })
    }

    /** Одна стрелка — один ведомый и одно направление. «В обе стороны» только с уровня BOTH_FROM. */
    @Test
    fun arrowsPointOneWayBeforeBothFrom() {
        for (l in all) for (t in l.columns.flatten()) {
            if (l.id < Levels.BOTH_FROM) assertTrue("уровень ${l.id}: стрелка в обе стороны", t.arrow in 0..2)
            assertTrue(t.arrow in 0..3)
        }
        assertTrue("с ${Levels.BOTH_FROM}-го уровня должны встречаться двусторонние стрелки",
            all.filter { it.id >= Levels.BOTH_FROM }.any { l -> l.columns.flatten().any { it.arrow == 3 } })
        assertTrue("стрелок нет на первых уровнях", all.filter { it.id < 8 }.all { l -> l.columns.flatten().all { it.arrow == 0 } })
        val avgArrows = { r: IntRange -> all.filter { it.id in r }.map { l -> l.columns.flatten().count { it.arrow != 0 } }.average() }
        assertTrue("стрелок должно стать больше", avgArrows(201..500) > avgArrows(11..100))
    }

    @Test
    fun arrowsNeverPointOutsideTheField() {
        for (l in all) l.columns.forEachIndexed { ci, col ->
            for (t in col) {
                if (t.arrow and 1 != 0) assertTrue(ci > 0)
                if (t.arrow and 2 != 0) assertTrue(ci < l.columns.size - 1)
            }
        }
    }

    @Test
    fun hopperPhysicsKeepsMarblesInside() {
        val game = Game(all[9]) { _, _ -> }
        repeat(60 * 25) { i ->
            if (i % 20 == 0) game.tapColumn(i / 20 % game.cols.size)
            game.update(1f / 60f)
            for (b in game.hopper) assertTrue("шарик вне воронки: ${b.x}, ${b.y}", b.x in 0f..360f && b.y < 345f)
        }
    }

    @Test
    fun rocketAndReturnKeepMarbleCountsConsistent() {
        val game = Game(all[11]) { _, _ -> }
        repeat(30) { i -> if (i % 3 == 0) game.tapColumn(i / 3 % game.cols.size); repeat(20) { game.update(1f / 60f) } }
        val before = game.occupied
        if (before > 0) assertTrue(game.returnMarbles())
        repeat(120) { game.update(1f / 60f) }
        game.useRocket()
        repeat(180) { game.update(1f / 60f) }
        assertTrue(game.done >= 0 && game.occupied <= game.n)
    }
}
