package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {
    private val dt = 1f / 60f

    private fun playWithHints(level: Level, seed: Long): Boolean {
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
        return g.state == State.WON
    }

    @Test
    fun lessonTableIsOrderedAndComplete() {
        assertEquals(6, Lessons.all.size)
        Lessons.all.forEachIndexed { i, l -> assertEquals("номер урока $i", i, l.id) }
        assertTrue("уровни уроков растут", Lessons.all.zipWithNext().all { (a, b) -> a.level < b.level })
        assertTrue(Lessons.all.all { it.level in 1..LEVEL_COUNT })
        assertEquals(1, Lessons.all.first().level)
        for (l in Lessons.all) {
            val p = l.prize
            assertTrue("урок ${l.id}: приз пуст", p.coins + p.rockets + p.returns + p.hints > 0)
            assertEquals(l, Lessons.atLevel(l.level))
        }
        assertEquals(null, Lessons.atLevel(2))
    }

    @Test
    fun everyHelperIsOpenedByItsOwnLessonAndRewardsItself() {
        assertTrue(Lessons.forAid(AID_ROCKET).prize.rockets > 0)
        assertTrue(Lessons.forAid(AID_RETURN).prize.returns > 0)
        assertTrue(Lessons.forAid(AID_HINT).prize.hints > 0)
        for (aid in intArrayOf(AID_ROCKET, AID_RETURN, AID_HINT)) assertEquals(aid, Lessons.forAid(aid).aid)
    }

    /** Связь и лифт появляются ровно на уровне своего урока и не раньше. */
    @Test
    fun linksAndLiftAppearExactlyAtTheirLessons() {
        val links = Lessons.all[LS_LINKS].level; val lift = Lessons.all[LS_LIFT].level
        for (id in 1..LEVEL_COUNT) {
            val l = Levels.make(id)
            val hasLink = l.columns.flatten().any { it.pid != 0 }
            if (id < links) assertTrue("уровень $id: связь раньше урока", !hasLink)
            if (id == links) assertTrue("на уровне урока должна быть связь", hasLink)
            if (id < lift) assertTrue("уровень $id: лифт раньше урока", l.elevator < 0)
            if (id == lift) assertTrue("на уровне урока должен быть лифт", l.elevator >= 0)
        }
        // помощники объясняются раньше связей
        assertTrue(Lessons.all[LS_BULB].level < Lessons.all[LS_ROCKET].level && Lessons.all[LS_ROCKET].level < Lessons.all[LS_RETURN].level)
        assertTrue(Lessons.all[LS_RETURN].level < links && links < lift)
    }

    @Test
    fun trainingLevelsHaveRoomierBelt() {
        for (l in Lessons.all) {
            assertEquals("уровень ${l.level}", minOf(30, Levels.beltOf(l.level) + Lessons.BELT_BONUS), Levels.make(l.level).belt)
        }
        assertEquals(Levels.beltOf(2), Levels.make(2).belt)
    }

    /** Учебные уровни проходимы (с лентой побольше) при подсказках: не реже 28 из 30. */
    @Test
    fun trainingLevelsAreWinnable() {
        for (l in Lessons.all) {
            val wins = (1L..30L).count { playWithHints(Levels.make(l.level), it) }
            assertTrue("учебный уровень ${l.level}: побед $wins из 30", wins >= 28)
        }
    }

    /** На учебном уровне про связь первая пара стоит впереди, и ведущий действительно один. */
    @Test
    fun linkLessonLevelShowsAnActiveLinkFromTheStart() {
        val l = Levels.make(Lessons.all[LS_LINKS].level)
        val g = Game(l) { _, _ -> }
        val leaders = g.cols.indices.filter { i ->
            val h = g.cols[i][0]
            Links.follower(h.arrow, h.pid, i, g.cols.size) { c -> g.cols[c].firstOrNull()?.pid ?: 0 } >= 0
        }
        assertEquals(1, leaders.size)
        val before = g.cols.sumOf { it.size }
        g.tapColumn(leaders[0])
        assertEquals("ведущий уносит и ведомого", before - 2, g.cols.sumOf { it.size })
    }
}
