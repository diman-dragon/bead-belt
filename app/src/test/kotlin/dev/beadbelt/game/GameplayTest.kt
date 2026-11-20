package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayTest {
    /**
     * Простой бот: тапает лоток, цвет которого нужен активной коробке, и только если его шарики
     * помещаются на ленту (с учётом тех, что ещё в воронке). Иначе ждёт — лента разгружается сама.
     */
    private fun play(id: Int, seconds: Int = 240): Game {
        val game = Game(LevelCache.get(id)) { _, _ -> }
        val dt = 1f / 60f
        var t = 0f
        var cooldown = 0f
        while (game.state == State.PLAY && t < seconds) {
            cooldown -= dt
            if (cooldown <= 0f && game.spawnQ.isEmpty()) {
                val free = game.n - game.occupied - game.hopper.size
                val want = game.boxes.filterNotNull().filter { !it.locked }.map { it.c }.toSet()
                val open = game.cols.indices.filter { game.cols[it].isNotEmpty() && game.cols[it][0].n <= free }
                val pick = open.firstOrNull { game.cols[it][0].c in want }
                    ?: if (t > 60f && game.occupied > game.n / 2) open.firstOrNull() else null
                if (pick != null) { game.tapColumn(pick); cooldown = 0.2f }
            }
            game.update(dt)
            t += dt
        }
        return game
    }

    @Test
    fun firstLevelsCanBePlayedToTheEnd() {
        for (id in 1..4) {
            val g = play(id)
            assertEquals("уровень $id", State.WON, g.state)
            assertEquals(g.total, g.done)
        }
    }

    @Test
    fun confettiAndShakeAreProducedOnWin() {
        val g = play(1)
        assertEquals(State.WON, g.state)
        assertTrue("конфетти", g.fx.n > 0)
    }

    @Test
    fun hopperHandlesManyMarblesFast() {
        val game = Game(LevelCache.get(100)) { _, _ -> }
        repeat(8) { game.tapColumn(it % game.cols.size) }
        val t0 = System.nanoTime()
        repeat(60 * 6) { game.update(1f / 60f) }
        val ms = (System.nanoTime() - t0) / 1e6
        for (b in game.hopper) assertTrue(b.x in 0f..360f && b.y < 345f)
        assertTrue("слишком медленно: $ms мс на 6 с игры", ms < 1500)
    }

    @Test
    fun particlesPoolNeverOverflowsAndDrains() {
        val ps = Particles(64)
        val r = java.util.Random(1)
        repeat(500) { ps.burst(10f, 10f, 1, 5, r) }
        assertTrue(ps.n <= 64)
        repeat(300) { ps.update(1f / 60f) }
        assertEquals(0, ps.n)
    }

    @Test
    fun beltPoseAnglesFollowTheTrack() {
        val o = FloatArray(3)
        Geo.pose(10f, o); assertEquals(0f, o[2], 0.01f)                       // верх: вправо
        Geo.pose(Geo.A + Geo.ARC / 2, o); assertEquals(90f, o[2], 0.5f)       // правая дуга: вниз
        Geo.pose(2 * Geo.A + Geo.ARC + 10f, o); assertEquals(180f, o[2], 0.01f)   // низ: влево
        Geo.pose(3 * Geo.A + 1.5f * Geo.ARC, o); assertEquals(270f, o[2], 0.5f)   // левая дуга: вверх
    }

    @Test
    fun levelCacheReturnsTheSameLevel() {
        assertTrue(LevelCache.get(7) === LevelCache.get(7))
    }

    @Test
    fun physicsIsStableOnAnyRefreshRate() {
        for (hz in intArrayOf(30, 60, 90, 120, 144)) {
            val game = Game(LevelCache.get(60)) { _, _ -> }
            repeat(10) { game.tapColumn(it % game.cols.size) }
            repeat(hz * 8) { game.update(1f / hz) }
            for (b in game.hopper) assertTrue("$hz Гц: шарик вне воронки ${b.x},${b.y}", b.x in 0f..360f && b.y < 345f && !b.vx.isNaN() && !b.vy.isNaN())
        }
    }

    @Test
    fun beltSpeedIsIndependentOfFrameRate() {
        fun phaseAfter(hz: Int): Float { val g = Game(LevelCache.get(5)) { _, _ -> }; repeat(hz) { g.update(1f / hz) }; return g.phase }
        val a = phaseAfter(60); val b = phaseAfter(120)
        assertEquals(a.toDouble(), b.toDouble(), 3.0)
    }

    @Test
    fun undoHintAndShuffleWorkCorrectly() {
        val game = Game(LevelCache.get(18)) { _, _ -> }
        val colsBefore = game.cols.sumOf { it.size }
        game.tapColumn(0)
        assertTrue(game.undoStack.isNotEmpty())
        assertTrue(game.undo())

        val hintCol = game.getHint()
        assertTrue(hintCol == null || hintCol in game.cols.indices)

        assertTrue(game.shuffleTrays())
        assertEquals(colsBefore, game.cols.sumOf { it.size })
    }
}
