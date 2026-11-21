package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRulesTest {
    @Test
    fun starsDependOnHowMuchHelpWasUsed() {
        assertEquals(3, Progress.starsFor(0))
        assertEquals(2, Progress.starsFor(1))
        assertEquals(2, Progress.starsFor(2))
        assertEquals(1, Progress.starsFor(3))
    }

    @Test
    fun giftGrowsWithTheStreakAndCapsAtSeven() {
        assertEquals(20, Progress.giftCoins(1))
        assertEquals(80, Progress.giftCoins(7))
        assertEquals(80, Progress.giftCoins(12))
        assertTrue((1..7).map { Progress.giftCoins(it) }.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun dayNumberFollowsTheLocalMidnight() {
        val msk = java.util.TimeZone.getTimeZone("GMT+3")
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val t = 21L * 3_600_000L                       // 21:00 UTC первого дня = 00:00 следующего в GMT+3
        assertEquals(0L, Progress.dayNumber(t, utc))
        assertEquals(1L, Progress.dayNumber(t, msk))
    }
}
