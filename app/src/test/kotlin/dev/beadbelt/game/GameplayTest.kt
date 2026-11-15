package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayTest {
    /** Простой бот: тапает лоток, цвет которого нужен одной из активных коробок, и ждёт, пока лента разгрузится. */
    private fun play(id: Int, seconds: Int = 240): Game {
        val game = Game(LevelCache.get(id)) { _, _ -> }
        val dt = 1f / 60f
        var t = 0f
        var cooldown = 0f
        while (game.state == State.PLAY && t < seconds) {
            cooldown -= dt
            if (cooldown <= 0f && game.spawnQ.isEmpty() && game.hopper.size < 6 && game.occupied < game.n - 8) {
                val want = game.boxes.filterNotNull().filter { !it.locked }.map { it.c }.toSet()
                val open = game.cols.indices.filter { game.cols[it].isNotEmpty() }
                val pick = open.firstOrNull { game.cols[it][0].c in want } ?: open.firstOrNull()
                if (pick != null) game.tapColumn(pick)
                cooldown = 0.2f
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
}
