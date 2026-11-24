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

    /** Уровень 18 когда-то был непроходим: старой ленты 18 не хватало. */
    @Test
    fun levelEighteenIsPlayable() {
        val l = Levels.make(18)
        assertTrue("лента уровня 18 слишком мала: ${l.belt}", l.belt >= 21)
        val b = Planner.board(l, l.belt)
        assertTrue(Planner.solve(b, Planner.initial(b)).win)
    }

    /** До 200-го уровня стрелка всегда односторонняя; двусторонняя механика — только с 201-го. */
    @Test
    fun arrowDirectionFollowsChapterRules() {
        for (l in all) l.columns.forEach { col ->
            for (t in col) {
                if (l.id <= 200) assertTrue("двусторонняя стрелка на уровне ${l.id}", t.arrow != 3)
                assertTrue("неизвестное значение стрелки ${t.arrow}", t.arrow in 0..3)
            }
        }
    }

    @Test
    fun earlyLinksConnectSameColorTraysInOneDirection() {
        for (l in all.filter { it.id in 8..200 }) {
            l.columns.forEachIndexed { ci, col ->
                val first = col.firstOrNull() ?: return@forEachIndexed
                if (first.arrow != 0) {
                    assertEquals("уровень ${l.id}: ведущий и ведомый лотки должны быть одного цвета", first.c,
                        l.columns[if (first.arrow == 1) ci - 1 else ci + 1].first().c)
                    assertTrue("стрелка разрешена только у ведущего лотка", col.drop(1).all { it.arrow == 0 })
                }
            }
        }
    }

    @Test
    fun generatorV2RebuildsEveryLevelDeterministically() {
        for (id in 1..LEVEL_COUNT) {
            val a = Levels.make(id)
            val b = Levels.make(id)
            assertEquals("уровень $id должен быть детерминированным", a.columns.joinToString { c -> c.joinToString { "${it.c}.${it.n}.${it.arrow}" } },
                b.columns.joinToString { c -> c.joinToString { "${it.c}.${it.n}.${it.arrow}" } })
        }
    }

    @Test
    fun secondHundredIsHarder() {
        assertEquals(500, LEVEL_COUNT)
        assertTrue(all.filter { it.id in 101..150 }.all { it.boxes.size == 32 })
        assertTrue(all.filter { it.id in 151..350 }.all { it.boxes.size == 40 })
        assertTrue(all.filter { it.id > 350 }.all { it.boxes.size == 48 })
        assertTrue(all.filter { it.id > 100 && it.id % 2 == 0 }.all { it.elevator >= 0 })
        assertTrue("после 200-го уровня должны появиться двусторонние связи", all.filter { it.id in 201..500 }.any { l ->
            l.columns.flatten().any { it.arrow == 3 }
        })
        assertTrue("в первых двух главах стрелки только односторонние", all.filter { it.id <= 200 }.all { l ->
            l.columns.flatten().all { it.arrow != 3 }
        })
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
        if (before > 0 && game.state == State.PLAY) assertTrue(game.returnMarbles())
        repeat(120) { game.update(1f / 60f) }
        game.useRocket()
        repeat(180) { game.update(1f / 60f) }
        assertTrue(game.done >= 0 && game.occupied <= game.n)
    }
}
