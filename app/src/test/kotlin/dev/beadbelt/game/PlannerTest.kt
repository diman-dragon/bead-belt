package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Исход хода в игре непредсказуем: шарики перемешиваются в воронке и падают в коробки по мере проезда над ними.
 * Поэтому здесь проверяется не точное совпадение с моделью, а то, что игрок, который после каждого хода
 * слушается подсказки, выигрывает почти всегда.
 */
class PlannerTest {
    private val dt = 1f / 60f

    /** Игрок с подсказкой. Если подсказка говорит «тупик» — всё равно ходит, как делал бы человек. */
    private fun playWithHints(level: Level, seed: Long): State {
        val g = Game(level, seed) { _, _ -> }
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
        return g.state
    }

    /**
     * Игрок с подсказкой выигрывает почти всегда (раскладка физики каждый раз другая). Проверяется каждый седьмой уровень:
     * на каждом из них не реже 3 раз из 5, а в среднем не реже 85%. Точная настройка лент — tools/README.md.
     */
    @Test
    fun followingHintsWinsAlmostAlways() {
        var played = 0; var won = 0
        for (id in 1..LEVEL_COUNT step 7) {
            val wins = (1L..5L).count { playWithHints(Levels.make(id), it) == State.WON }
            played += 5; won += wins
            assertTrue("уровень $id: подсказке удалось выиграть лишь $wins из 5", wins >= 3)
        }
        assertTrue("подсказка выигрывает слишком редко: $won из $played", won * 100 >= played * 85)
    }

    /** Разный seed физики воронки даёт разную партию: непредсказуемость сохранена. */
    @Test
    fun outcomesDifferBetweenSeeds() {
        val sigs = HashSet<String>()
        for (seed in 1L..8L) {
            val g = Game(Levels.make(40), seed) { _, _ -> }
            repeat(30) { g.update(dt) }
            g.tapColumn(0)
            repeat(180) { g.update(dt) }
            sigs.add(g.slots.joinToString(""))
        }
        assertTrue("порядок шариков на ленте должен зависеть от физики", sigs.size > 1)
    }

    @Test
    fun adviceAtTheStartOfEveryLevelIsATap() {
        for (id in 1..LEVEL_COUNT) {
            val g = Game(Levels.make(id)) { _, _ -> }
            repeat(40) { g.update(dt) }
            val a = Hints.advise(g)
            assertTrue("уровень $id: подсказка в начале — ход", a.kind == Advice.TAP || a.kind == Advice.UNSURE)
            assertTrue(a.col in g.cols.indices && g.cols[a.col].isNotEmpty())
        }
    }

    @Test
    fun adviceWaitsWhileMarblesAreInMotion() {
        val g = Game(Levels.make(5)) { _, _ -> }
        g.tapColumn(0)
        assertEquals(Advice.WAIT, Hints.advise(g).kind)
    }

    /** Собранная коробка сразу уступает место: в слоте уже стоит следующая (или пусто), а старая только дозавершается. */
    @Test
    fun nextBoxAppearsTheMomentOneIsFull() {
        val g = Game(Levels.make(1)) { _, _ -> }
        var t = 0f; var checked = false
        while (g.state == State.PLAY && t < 180f && !checked) {
            if (Hints.calm(g)) { val a = Hints.advise(g); if (a.col >= 0) g.tapColumn(a.col) }
            g.update(dt); t += dt
            for (b in g.popping) {
                assertTrue("в слоте ${b.slot} всё ещё стоит собранная коробка", g.boxes[b.slot] !== b)
                checked = true
            }
        }
        assertTrue("за 3 минуты ни одна коробка не собралась", checked)
    }

    /** «Ещё шанс»: после проигрыша шарики возвращаются в лотки, и игру можно продолжить. */
    @Test
    fun reviveBringsMarblesBackAndTheGameContinues() {
        val g = Game(Levels.make(30)) { _, _ -> }
        var t = 0f; var i = 0
        while (g.state == State.PLAY && t < 600f) {
            if (t > i * 0.3f && g.cols.any { it.isNotEmpty() }) { g.tapColumn(i % g.cols.size); i++ }
            g.update(dt); t += dt
        }
        if (g.state == State.LOST) {
            assertTrue(g.revive())
            assertEquals(State.PLAY, g.state)
            assertTrue("шариков на ленте не осталось", g.slots.all { it < 0 })
            repeat(60) { g.update(dt) }
            assertEquals("игра идёт дальше, а не проигрывает мгновенно", State.PLAY, g.state)
        }
    }
}
