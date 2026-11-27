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
    fun lessonTableIsOrderedAndFitsIntoTheGame() {
        assertEquals(11, Lessons.all.size)
        Lessons.all.forEachIndexed { i, l -> assertEquals("номер урока $i", i, l.id) }
        assertTrue("уровни уроков растут", Lessons.all.zipWithNext().all { (a, b) -> a.level < b.level })
        assertTrue("все уроки в пределах игры", Lessons.all.all { it.level in 1..LEVEL_COUNT })
        assertEquals(1, Lessons.all.first().level)
        for (l in Lessons.all) {
            assertTrue("урок ${l.id}: приз пуст", l.prize.total > 0)
            assertEquals(l, Lessons.atLevel(l.level))
        }
        assertEquals(null, Lessons.atLevel(2))
    }

    @Test
    fun everyHelperIsOpenedByItsOwnLessonAndRewardsItself() {
        assertEquals(AID_COUNT, Lessons.all.count { it.aid >= 0 })
        for (aid in 0 until AID_COUNT) {
            val l = Lessons.forAid(aid)
            assertEquals(aid, l.aid)
            assertTrue("приз за урок помощника $aid должен включать его самого", l.prize.aids[aid] > 0)
        }
    }

    /** Каждая механика появляется ровно на уровне своего урока и не раньше. */
    @Test
    fun mechanicsAppearExactlyAtTheirLessons() {
        val links = Lessons.all[LS_LINKS].level; val lift = Lessons.all[LS_LIFT].level; val hid = Lessons.all[LS_HIDDEN].level
        for (id in 1..LEVEL_COUNT) {
            val l = Levels.make(id)
            val hasLink = l.columns.flatten().any { it.pid != 0 }
            val hasHidden = l.columns.flatten().any { it.hidden }
            if (id < links) assertTrue("уровень $id: связь раньше урока", !hasLink)
            if (id == links) assertTrue("на уровне урока должна быть связь", hasLink)
            if (id < lift) assertTrue("уровень $id: лифт раньше урока", l.elevator < 0)
            if (id == lift) assertTrue("на уровне урока должен быть лифт", l.elevator >= 0)
            if (id < hid) assertTrue("уровень $id: скрытый цвет раньше урока", !hasHidden)
            if (id == hid) assertTrue("на уровне урока должен быть скрытый цвет", hasHidden)
            val flat = l.columns.flatten()
            if (id < Lessons.all[LS_MIXED].level) assertTrue("уровень $id: двухцветный лоток раньше урока", flat.none { it.mixed })
            if (id == Lessons.all[LS_MIXED].level) assertTrue("на уроке есть двухцветный лоток впереди", l.columns.any { it[0].mixed })
            if (id < Lessons.all[LS_CHAIN].level) assertTrue("уровень $id: цепь раньше урока", flat.none { it.lock > 0 })
            if (id == Lessons.all[LS_CHAIN].level) assertTrue("на уроке есть цепь впереди", l.columns.any { it[0].lock > 0 })
            if (id < Lessons.all[LS_MYSTERY].level) assertTrue("уровень $id: скрытая коробка раньше урока", l.mystery.isEmpty())
            if (id == Lessons.all[LS_MYSTERY].level) assertTrue("на уроке есть скрытые коробки", l.mystery.size >= 2)
        }
        // сначала помощники, потом новые правила поля
        val aidsEnd = Lessons.all.filter { it.aid >= 0 }.maxOf { it.level }
        assertTrue(aidsEnd < hid && hid < links && links < lift)
        assertTrue(lift < Lessons.all[LS_MIXED].level && Lessons.all[LS_MIXED].level < Lessons.all[LS_CHAIN].level && Lessons.all[LS_CHAIN].level < Lessons.all[LS_MYSTERY].level)
    }

    @Test
    fun trainingLevelsHaveRoomierBelt() {
        for (l in Lessons.all) {
            assertEquals("уровень ${l.level}", minOf(30, Levels.beltOf(l.level) + Lessons.BELT_BONUS), Levels.make(l.level).belt)
        }
        assertEquals(Levels.beltOf(2), Levels.make(2).belt)
    }

    /** Учебные уровни проходимы при подсказках: не реже 28 из 30. */
    @Test
    fun trainingLevelsAreWinnable() {
        for (l in Lessons.all) {
            val wins = (1L..30L).count { playWithHints(Levels.make(l.level), it) }
            assertTrue("учебный уровень ${l.level}: побед $wins из 30", wins >= 28)
        }
    }

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
