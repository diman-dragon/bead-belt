package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Новые усложнения: двухцветные лотки, цепи и скрытые коробки. */
class MechanicsTest {
    private val dt = 1f / 60f
    private fun run(g: Game, sec: Float) { var t = 0f; while (t < sec) { g.update(dt); t += dt } }

    @Test
    fun chainedTrayRefusesUntilEnoughBoxesAreCollected() {
        val cols = listOf(listOf(TrayDef(0, 3, lock = 1)), listOf(TrayDef(1, 3)), listOf(TrayDef(2, 3)))
        val g = Game(Level(1, cols, listOf(0, 1, 2, 3), 20)) { _, _ -> }
        assertTrue("цепь держит", !g.tapColumn(0))
        assertEquals("лоток остался", 1, g.cols[0].size)
        assertTrue("дрожит", g.rattle[0] > 0f)
        assertTrue(g.tapColumn(1))
        run(g, 12f)
        assertTrue("коробка собрана", g.done >= 1)
        assertTrue("цепь спала", g.tapColumn(0))
        assertEquals(0, g.cols[0].size)
    }

    @Test
    fun plannerRespectsChains() {
        val cols = listOf(listOf(TrayDef(0, 3, lock = 2)), listOf(TrayDef(1, 3)))
        val b = Board(cols, Levels.stacksOf(listOf(0, 1, 2, 3)).map { it.toIntArray() }, -1, 20)
        assertEquals(null, Planner.launch(b, IntArray(2), 0, 1))
        assertTrue(Planner.launch(b, IntArray(2), 0, 2) != null)
        assertTrue(Planner.launch(b, IntArray(2), 1, 0) != null)
    }

    @Test
    fun mixedTrayPoursBothColorsInOrder() {
        val cols = listOf(listOf(TrayDef(0, 5, c2 = 1, n2 = 2)), listOf(TrayDef(2, 3)))
        val g = Game(Level(1, cols, listOf(0, 1, 2, 3), 20)) { _, _ -> }
        assertTrue(g.tapColumn(0))
        assertEquals(listOf(0, 0, 0, 1, 1), g.spawnQ.map { it.c })
    }

    @Test
    fun mysteryBoxesAreHiddenOnlyWhileTheyWait() {
        val g = Game(Level(1, listOf(listOf(TrayDef(0, 3))), List(8) { it % 4 }, 20, -1, setOf(4, 5))) { _, _ -> }
        assertTrue(g.queues[0][0].hidden && g.queues[1][0].hidden)
        assertTrue(!g.queues[2][0].hidden && !g.queues[3][0].hidden)
        assertTrue("активные коробки видны", g.boxes.all { it != null && !it.hidden })
    }

    @Test
    fun lockedOutPositionLosesInsteadOfFreezing() {
        val cols = listOf(listOf(TrayDef(0, 3, lock = 2)), listOf(TrayDef(1, 3, lock = 3)))
        val g = Game(Level(1, cols, listOf(0, 1, 2, 3), 20)) { _, _ -> }
        run(g, 6f)
        assertEquals(State.LOST, g.state)
        assertTrue("шанс не поможет", !g.canRevive())
    }

    @Test
    fun hintsNeverAdviseLockedTrays() {
        for (id in 76..200 step 7) {
            val g = Game(Levels.make(id), 3L) { _, _ -> }
            val a = Hints.advise(g)
            if (a.kind == Advice.TAP) assertTrue("уровень $id: совет на закованный лоток", g.cols[a.col][0].lock <= g.done)
        }
    }
}
