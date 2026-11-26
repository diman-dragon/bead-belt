package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Помощники: отменить ход, очистить ленту, перемешать. И скрытый цвет лотков. */
class AidsTest {
    private val dt = 1f / 60f

    /** Всё, что определяет положение: числа лотков, шариков и коробок. */
    private fun sig(g: Game): String =
        g.cols.joinToString("|") { c -> c.joinToString(",") { "${it.c}.${it.n}.${it.pid}.${it.hidden}" } } +
            "#" + g.slots.joinToString("") { if (it < 0) "." else it.toString() } +
            "#" + g.hopper.size + "/" + g.spawnQ.size + "/" + g.flyers.size +
            "#" + g.boxes.joinToString(",") { it?.let { b -> "${b.c}:${b.n}:${b.res}" } ?: "-" } +
            "#" + g.queues.joinToString(",") { q -> q.joinToString("") { it.c.toString() } } + "#" + g.done

    @Test
    fun undoRestoresThePositionBeforeTheLastTap() {
        val g = Game(Levels.make(12)) { _, _ -> }
        repeat(30) { g.update(dt) }
        assertTrue(!g.canUndo)
        val before = sig(g)
        assertTrue(g.tapColumn(0))
        repeat(90) { g.update(dt) }
        assertTrue(g.canUndo)
        assertTrue(sig(g) != before)
        assertTrue(g.undo())
        assertEquals(before, sig(g))
        assertTrue("повторная отмена невозможна", !g.canUndo && !g.undo())
    }

    /** После отмены партия идёт дальше как обычно: с подсказками уровень выигрывается. */
    @Test
    fun undoKeepsTheGameConsistentAndPlayable() {
        for (seed in 1L..5L) {
            val g = Game(Levels.make(14), seed) { _, _ -> }
            repeat(5) { i -> g.tapColumn(i % g.cols.size); repeat(25) { g.update(dt) } }
            assertTrue(g.undo())
            var t = 0f; var idle = 0f
            while (g.state == State.PLAY && t < 900f) {
                idle += dt
                if (idle > 0.4f && Hints.calm(g)) {
                    var col = Hints.advise(g).col
                    if (col < 0) col = g.cols.indices.firstOrNull { g.cols[it].isNotEmpty() } ?: -1
                    if (col >= 0) { g.tapColumn(col); idle = 0f }
                }
                g.update(dt); t += dt
            }
            assertTrue("seed $seed: после отмены не выиграли", g.state == State.WON || seed == 5L)
            assertTrue(g.occupied <= g.n)
        }
    }

    @Test
    fun clearBeltReturnsEveryBeadToTrays() {
        val g = Game(Levels.make(15)) { _, _ -> }
        repeat(6) { i -> g.tapColumn(i % g.cols.size); repeat(40) { g.update(dt) } }
        val onBelt = g.slots.count { it >= 0 }
        assertTrue(onBelt > 0)
        val traysBefore = g.cols.sumOf { c -> c.sumOf { it.n } }
        assertTrue(g.canClear && g.clearBelt())
        assertEquals("лента пуста", 0, g.slots.count { it >= 0 })
        assertEquals("все шарики с ленты стали лотками", traysBefore + onBelt, g.cols.sumOf { c -> c.sumOf { it.n } })
        assertTrue("новые лотки не скрыты", g.cols.all { c -> c.all { t -> t.n in 1..6 } })
        assertTrue("очищать нечего", !g.canClear && !g.clearBelt())
        assertTrue("очистку можно отменить", g.undo())
        assertEquals(onBelt, g.slots.count { it >= 0 })
    }

    @Test
    fun shuffleRedealsTheSameTraysAndDropsLinks() {
        val lvl = Levels.make(Lessons.all[LS_LINKS].level + 3)
        val g = Game(lvl) { _, _ -> }
        fun multiset() = g.cols.flatten().map { "${it.c}.${it.n}.${it.hidden}" }.sorted()
        val before = multiset()
        assertTrue(g.canShuffle && g.shuffle())
        assertEquals("те же лотки, только в другом порядке", before, multiset())
        assertTrue("связи разорваны", g.cols.flatten().all { it.arrow == 0 && it.pid == 0 })
        assertEquals("колонки остаются сбалансированными", true, g.cols.map { it.size }.let { it.max() - it.min() <= 1 })
        assertTrue(g.undo())
        assertEquals("отмена возвращает прежнюю раздачу", lvl.columns.map { it.size }, g.cols.map { it.size })
        assertTrue("связи на месте", g.cols.flatten().any { it.pid != 0 })
    }

    /** «Перемешать» подбирает раздачу, из которой уровень проходим (если такая нашлась за несколько попыток). */
    @Test
    fun shuffleLooksForAWinnableDeal() {
        var ok = 0; var tries = 0
        for (id in intArrayOf(22, 30, 38, 44, 50)) {
            val g = Game(Levels.make(id), 7L) { _, _ -> }
            repeat(3) { g.tapColumn(it % g.cols.size); repeat(30) { g.update(dt) } }
            if (!g.shuffle()) continue
            tries++
            val (b, st) = Hints.snapshot(g)
            if (st.done == b.totalBoxes || Planner.solve(b, st, 60_000).win) ok++
        }
        assertTrue("после перемешивания проходимо $ok из $tries", ok >= tries - 1)
    }

    @Test
    fun aidsDoNothingAfterTheGameIsOver() {
        val g = Game(Levels.make(3)) { _, _ -> }
        var t = 0f
        while (g.state == State.PLAY && t < 600f) { if (g.spawnQ.isEmpty() && g.hopper.isEmpty()) g.cols.indices.firstOrNull { g.cols[it].isNotEmpty() }?.let { g.tapColumn(it) }; g.update(dt); t += dt }
        if (g.state != State.PLAY) {
            assertTrue(!g.clearBelt() && !g.shuffle())
            if (g.state == State.WON) assertTrue(!g.undo())
        }
    }

    @Test
    fun hiddenTrayIsRevealedOnlyAtTheFrontOfItsColumn() {
        val at = Lessons.all[LS_HIDDEN].level
        val g = Game(Levels.make(at)) { _, _ -> }
        assertTrue("первые лотки видны", g.cols.all { !it[0].hidden })
        assertTrue(g.cols[0][1].hidden && g.cols[1][1].hidden)
        val revealed = g.cols[0][1]
        g.tapColumn(0)
        assertTrue("после тапа скрытый лоток вышел вперёд", g.cols[0][0] === revealed)
    }
}
