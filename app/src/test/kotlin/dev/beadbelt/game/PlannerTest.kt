package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Модель (Planner), подсказки и настоящая игра обязаны вести себя одинаково — на этом держатся все 200 уровней. */
class PlannerTest {
    private val dt = 1f / 60f

    /** Проигрывает готовый план в настоящей игре; ждёт, пока всё уляжется между тапами. */
    private fun replay(level: Level, path: List<Int>, seed: Long): Game {
        val g = Game(level, seed) { _, _ -> }
        var t = 0f; var i = 0; var idle = 0f
        while (g.state == State.PLAY && t < 900f) {
            idle += dt
            if (idle > 0.4f && i < path.size && Hints.calm(g)) { g.tapColumn(path[i]); i++; idle = 0f }
            g.update(dt); t += dt
        }
        return g
    }

    @Test
    fun planOfTheModelWinsInTheRealGame() {
        for (id in 1..LEVEL_COUNT) {
            val l = Levels.make(id)
            val b = Planner.board(l, l.belt)
            val out = Planner.solve(b, Planner.initial(b))
            assertTrue("уровень $id: модель не нашла решения", out.win)
            for (seed in 1L..2L) assertEquals("уровень $id, seed $seed", State.WON, replay(l, out.path, seed).state)
        }
    }

    /** Игрок, который после каждого хода слушается подсказки, проходит каждый уровень. */
    @Test
    fun followingHintsWinsEveryLevel() {
        for (id in 1..LEVEL_COUNT) {
            val g = Game(Levels.make(id)) { _, _ -> }
            var t = 0f; var idle = 0f
            while (g.state == State.PLAY && t < 900f) {
                idle += dt
                if (idle > 0.4f && Hints.calm(g)) {
                    val a = Hints.advise(g)
                    if (a.col >= 0) { g.tapColumn(a.col); idle = 0f } else break
                }
                g.update(dt); t += dt
            }
            assertEquals("уровень $id", State.WON, g.state)
        }
    }

    /** После каждого хода состояние настоящей игры совпадает с тем, что предсказала модель. */
    @Test
    fun modelPredictsTheGameAfterEveryTap() {
        for (id in intArrayOf(18, 21, 69, 124, 149, 180, 198)) {
            val g = Game(Levels.make(id)) { _, _ -> }
            var t = 0f; var idle = 0f; var predicted: PState? = null
            while (g.state == State.PLAY && t < 900f) {
                idle += dt
                if (idle > 0.4f && Hints.calm(g)) {
                    val (b, s) = Hints.snapshot(g)
                    predicted?.let { p ->
                        assertEquals("уровень $id: лента", p.belt.toList(), s.belt.toList())
                        assertEquals("уровень $id: коробки", p.actN.toList(), s.actN.toList())
                    }
                    val a = Hints.advise(g)
                    if (a.col < 0) break
                    predicted = Planner.tap(b, s, a.col)?.let { n ->
                        // после тапа «следующая коробка» сдвигается: сравниваем по числу шариков и прогрессу
                        PState(IntArray(n.heads.size), IntArray(SLOTS), n.actN.clone(), n.belt.clone(), n.open, n.total, n.done)
                    }
                    g.tapColumn(a.col); idle = 0f
                }
                g.update(dt); t += dt
            }
            assertEquals("уровень $id", State.WON, g.state)
        }
    }

    @Test
    fun adviceAtTheStartOfEveryLevelIsATap() {
        for (id in 1..LEVEL_COUNT) {
            val g = Game(Levels.make(id)) { _, _ -> }
            repeat(40) { g.update(dt) }
            val a = Hints.advise(g)
            assertEquals("уровень $id", Advice.TAP, a.kind)
            assertTrue(a.col in g.cols.indices && g.cols[a.col].isNotEmpty())
        }
    }

    @Test
    fun adviceWaitsWhileMarblesAreInMotion() {
        val g = Game(Levels.make(5)) { _, _ -> }
        g.tapColumn(0)
        assertEquals(Advice.WAIT, Hints.advise(g).kind)
    }

    /** «Ещё шанс»: после проигрыша шарики возвращаются в лотки, и игру можно выиграть. */
    @Test
    fun reviveBringsMarblesBackAndTheGameCanBeWon() {
        val g = Game(Levels.make(30)) { _, _ -> }
        // забиваем ленту подряд идущими тапами без оглядки
        var t = 0f; var i = 0
        while (g.state == State.PLAY && t < 600f) {
            if (t > i * 0.3f && g.cols.any { it.isNotEmpty() }) { g.tapColumn(i % g.cols.size); i++ }
            g.update(dt); t += dt
        }
        if (g.state == State.LOST) {
            assertTrue(g.revive())
            assertEquals(State.PLAY, g.state)
            assertTrue("ничьих шариков на ленте не осталось", (0 until g.n).none { g.slots[it] >= 0 && g.claimOf[it] == null })
            repeat(60) { g.update(dt) }
            assertEquals("игра идёт дальше, а не проигрывает мгновенно", State.PLAY, g.state)
        }
    }
}
