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
            l.columns.flatten().forEach { t -> for (k in 0 until t.n) trayCount[t.ballColor(k)]++ }
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
            assertTrue("уровень ${l.id}: лента ${l.belt} вне 10..30", l.belt in 10..30)
        }
    }

    /** Лотки вмещают 3..6 шариков, коробок ровно столько, сколько нужно шарикам, параметры совпадают с Levels.spec. */
    @Test
    fun levelsMatchTheirSpec() {
        for (l in all) {
            val s = Levels.spec(l.id)
            assertEquals("уровень ${l.id}: коробок", s.colors * s.perColor, l.boxes.size)
            assertEquals("уровень ${l.id}: колонок", s.cols, l.columns.size)
            assertEquals("уровень ${l.id}: лифт", s.lift, l.elevator >= 0)
            assertTrue("уровень ${l.id}: размер лотка", l.columns.flatten().all { it.n in 3..6 })
            assertEquals("уровень ${l.id}: цветов", s.colors, l.boxes.toSet().size)
        }
    }

    /**
     * Связь — пара: у обоих лотков один pid, ровно один из них ведущий (со стрелкой), они стоят в соседних
     * колонках, и стрелка смотрит на ведомого. Стрелок «без пары» и «обратных» стрелок нет.
     */
    @Test
    fun everyLinkIsOneWayPairOfNeighbours() {
        for (l in all) {
            val byPid = HashMap<Int, MutableList<Pair<Int, TrayDef>>>()
            l.columns.forEachIndexed { ci, col -> col.forEach { t ->
                assertTrue("уровень ${l.id}: стрелка ${t.arrow}", t.arrow in 0..2)
                if (t.pid == 0) assertEquals("уровень ${l.id}: стрелка без пары", 0, t.arrow)
                else byPid.getOrPut(t.pid) { ArrayList() }.add(ci to t)
            } }
            assertEquals("уровень ${l.id}: число пар", Levels.spec(l.id).pairs, byPid.size)
            for ((pid, v) in byPid) {
                assertEquals("уровень ${l.id}, пара $pid", 2, v.size)
                val leads = v.filter { it.second.arrow != 0 }
                assertEquals("уровень ${l.id}, пара $pid: ведущих", 1, leads.size)
                val lead = leads[0]; val fol = v.first { it !== lead }
                assertEquals("уровень ${l.id}, пара $pid: соседние колонки", 1, Math.abs(lead.first - fol.first))
                assertEquals("уровень ${l.id}, пара $pid: стрелка на ведомого",
                    if (fol.first > lead.first) Links.RIGHT else Links.LEFT, lead.second.arrow)
                if (Levels.spec(l.id).sameOnly) assertEquals("уровень ${l.id}: учебная пара одного цвета", lead.second.c, fol.second.c)
            }
        }
    }

    @Test
    fun linksAppearFromTheirLessonAndTeachingLinksAreInFront() {
        val at = Lessons.all[LS_LINKS].level
        for (l in all) {
            val pairs = l.columns.flatten().count { it.arrow != 0 }
            if (l.id < at) assertEquals("уровень ${l.id}: связей быть не должно", 0, pairs)
            else assertTrue("уровень ${l.id}: нет связей", pairs >= 1)
        }
        for (l in all.filter { it.id in at until at + 6 }) {
            val g = Game(l) { _, _ -> }
            val active = g.cols.indices.count { i ->
                val h = g.cols[i][0]
                Links.follower(h.arrow, h.pid, i, g.cols.size) { c -> g.cols[c].firstOrNull()?.pid ?: 0 } >= 0
            }
            assertTrue("уровень ${l.id}: учебная связь должна стоять впереди", active >= 1)
        }
    }

    /** Скрытый цвет: первый лоток колонки всегда виден, до урока скрытых нет, после урока есть всегда. */
    @Test
    fun hiddenTraysFollowTheirLesson() {
        val at = Lessons.all[LS_HIDDEN].level
        for (l in all) {
            var hidden = 0
            l.columns.forEach { col -> col.forEachIndexed { i, t -> if (t.hidden) { hidden++; assertTrue("уровень ${l.id}: скрыт первый лоток", i > 0) } } }
            if (l.id < at) assertEquals("уровень ${l.id}: скрытые лотки раньше урока", 0, hidden)
            else assertTrue("уровень ${l.id}: нет скрытых лотков", hidden >= 2)
            if (l.id == at) {
                assertEquals(2, hidden)
                assertTrue("на учебном уровне скрыты вторые лотки", l.columns[0][1].hidden && l.columns[1][1].hidden)
            }
        }
    }

    private fun lv(vararg cols: List<TrayDef>) = Level(1, cols.toList(), listOf(0, 1, 2, 3), 20)

    /** Старая ошибка: связь срабатывала и «в обратную сторону». Теперь тап по ведомому запускает только его. */
    @Test
    fun tappingTheFollowerNeverLaunchesTheLeader() {
        val mk = { lv(listOf(TrayDef(0, 3, Links.RIGHT, 1)), listOf(TrayDef(0, 3, 0, 1))) }
        val g = Game(mk()) { _, _ -> }
        assertTrue(g.tapColumn(1))
        assertEquals("ведущий не должен уйти", 1, g.cols[0].size)
        assertEquals(0, g.cols[1].size)
        assertEquals(3, g.spawnQ.size)
        val h = Game(mk()) { _, _ -> }
        assertTrue(h.tapColumn(0))
        assertEquals("ведущий запускает и ведомого", 0, h.cols[0].size + h.cols[1].size)
        assertEquals(6, h.spawnQ.size)
    }

    @Test
    fun leaderLaunchesOnlyTheTrayItIsLinkedTo() {
        // сосед впереди — чужой лоток: ведущий уходит один, а ведомый остаётся за ним
        val g = Game(lv(listOf(TrayDef(0, 3, Links.RIGHT, 1)), listOf(TrayDef(1, 3), TrayDef(0, 3, 0, 1)))) { _, _ -> }
        assertTrue(g.tapColumn(0))
        assertEquals(2, g.cols[1].size)
        assertEquals(3, g.spawnQ.size)
        // и после этого связи больше нет: тап по чужому лотку ничего не тащит за собой
        assertTrue(g.tapColumn(1)); assertEquals(1, g.cols[1].size)
        // лотки в другой колонке, чем у стрелки, не трогаются
        val h = Game(lv(listOf(TrayDef(0, 3)), listOf(TrayDef(0, 3, Links.RIGHT, 1)), listOf(TrayDef(0, 3, 0, 1)), listOf(TrayDef(2, 3)))) { _, _ -> }
        assertTrue(h.tapColumn(1))
        assertEquals(listOf(1, 0, 0, 1), h.cols.map { it.size })
    }

    /** Игра и модель решателя запускают на каждый тап одни и те же лотки (на всех уровнях, случайные тапы). */
    @Test
    fun gameAndPlannerAgreeOnWhatATapLaunches() {
        val r = java.util.Random(7)
        for (l in all) {
            val g = Game(l) { _, _ -> }
            val board = Planner.board(l, l.belt)
            val heads = IntArray(l.columns.size)
            repeat(40) {
                val col = r.nextInt(l.columns.size)
                val before = g.spawnQ.size
                val batch = Planner.launch(board, heads, col, g.done)
                val ok = g.tapColumn(col)
                assertEquals("уровень ${l.id}: ход возможен", batch != null, ok)
                if (batch != null) assertEquals("уровень ${l.id}: шариков", batch.sumOf { it.n }, g.spawnQ.size - before)
                for (i in g.cols.indices) assertEquals("уровень ${l.id}: колонка $i", l.columns[i].size - heads[i], g.cols[i].size)
            }
        }
    }

    @Test
    fun generationIsDeterministic() {
        fun sig(l: Level) = l.columns.joinToString("|") { c -> c.joinToString(",") { "${it.c}.${it.n}.${it.arrow}.${it.pid}.${it.hidden}" } } + "#" + l.boxes.joinToString() + "#" + l.elevator
        for (id in 1..LEVEL_COUNT) assertEquals("уровень $id", sig(Levels.make(id)), sig(Levels.make(id)))
    }

    /**
     * Кривая сложности растёт до 200-го уровня: ничего не уменьшается, ступеньки по одному параметру за раз,
     * лифт, связи и новые усложнения только после своих уроков.
     */
    @Test
    fun difficultyCurveIsGentleAndMonotonic() {
        assertEquals(200, LEVEL_COUNT)
        var prev = Levels.spec(1)
        for (id in 2..LEVEL_COUNT) {
            val s = Levels.spec(id)
            assertTrue("уровень $id: цветов меньше", s.colors >= prev.colors)
            assertTrue("уровень $id: коробок на цвет меньше", s.perColor >= prev.perColor)
            assertTrue("уровень $id: пар меньше", s.pairs >= prev.pairs)
            assertTrue("уровень $id: скрытых меньше", s.hiddenShare >= prev.hiddenShare)
            assertTrue("уровень $id: обменов меньше", s.swaps >= prev.swaps)
            assertTrue("уровень $id: цепей меньше", s.chains >= prev.chains && s.maxLock >= prev.maxLock)
            assertTrue("уровень $id: скрытых коробок меньше", s.mysteryShare >= prev.mysteryShare)
            val jumps = (if (s.colors != prev.colors) 1 else 0) + (if (s.perColor != prev.perColor) 1 else 0) + (if (s.pairs != prev.pairs) 1 else 0)
            assertTrue("уровень $id: несколько ступенек сразу", jumps <= 1)
            prev = s
        }
        assertEquals(COLOR_COUNT, Levels.spec(LEVEL_COUNT).colors)
        assertEquals(5, Levels.spec(LEVEL_COUNT).perColor)
        assertEquals(4, Levels.spec(LEVEL_COUNT).pairs)
        assertTrue("к 200-му все усложнения в деле", Levels.spec(LEVEL_COUNT).let { it.swaps >= 4 && it.chains >= 3 && it.mysteryShare >= 0.5 })
        assertTrue("к 50-му уровню ещё далеко до максимума", Levels.spec(50).colors <= 5 && Levels.spec(50).perColor <= 3 && Levels.spec(50).pairs <= 1)
        assertTrue(all.filter { it.id < Lessons.all[LS_LIFT].level }.all { it.elevator < 0 })
        assertTrue(all.filter { it.id == Lessons.all[LS_LIFT].level }.all { it.elevator >= 0 })
        assertTrue("цвета на разных уровнях различаются", all.map { it.boxes.toSet() }.toSet().size > 5)
    }

    /** Первые 50 уровней остались такими же, как в прошлой версии (раскладка, цвета, лифты и ленты): прогресс и баланс сохранены. */
    @Test
    fun firstFiftyLevelsAreUnchanged() {
        val sb = StringBuilder()
        for (id in 1..50) {
            val l = Levels.make(id)
            sb.append(id).append(' ')
            sb.append(l.columns.joinToString("|") { c -> c.joinToString(",") { "${it.c}.${it.n}.${it.arrow}.${it.pid}.${it.hidden}" } })
            sb.append('#').append(l.boxes.joinToString()).append('#').append(l.elevator).append('#').append(l.belt).append('\n')
        }
        assertEquals(30132832, sb.toString().hashCode())
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
}
