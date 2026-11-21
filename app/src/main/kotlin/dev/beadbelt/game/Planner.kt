package dev.beadbelt.game

import kotlin.math.min

/**
 * Дискретная модель партии — для генератора уровней и подсказок.
 *
 * Один ход — тап по колонке: лоток (и соседи по стрелкам) высыпается, шарики пачки оказываются на ленте,
 * коробки забирают своё, а освободившееся место тут же занимает следующая коробка.
 *
 * Модель сознательно ПРИБЛИЖЁННАЯ. В настоящей игре шарики падают в коробки по мере проезда над ними, а порядок
 * шариков задаёт воронка, где они перемешиваются, и это непредсказуемо — так и задумано. Поэтому модель не
 * «предсказывает» исход хода, а оценивает его: подсказка перепланирует после каждого хода от реального положения,
 * а проходимость уровней проверяется статистически на многих раскладках физики (tools/README.md).
 *
 * Порядок коробок: лента на нижней прямой идёт справа налево, так что шарик сначала проезжает коробку 3, затем 2, 1, 0.
 */
class Board(val cols: List<List<TrayDef>>, val stacks: List<IntArray>, val elevator: Int, val cap: Int) {
    val totalBoxes = stacks.sumOf { it.size }

    init {
        require(cols.size <= 4 && cols.all { it.size < 63 } && stacks.size == SLOTS && stacks.all { it.size < 16 })
    }
}

/** Состояние партии в модели. Копируется дёшево. */
class PState(val heads: IntArray, val pos: IntArray, val actN: IntArray, val belt: IntArray, var open: Boolean, var total: Int, var done: Int) {
    fun copy() = PState(heads.clone(), pos.clone(), actN.clone(), belt.clone(), open, total, done)

    /** Ключ для мемоизации: головы колонок, позиции стопок, наполнение коробок, лента, лифт. */
    fun key(): Key {
        var a = 0L
        for (h in heads) a = a * 64 + h
        for (p in pos) a = a * 16 + p
        for (n in actN) a = a * 4 + n
        a = a * 2 + (if (open) 1 else 0)
        var b = 0L
        for (c in belt) b = b * 32 + c
        return Key(a, b)
    }

    data class Key(val a: Long, val b: Long)
}

class Outcome(val win: Boolean, val path: List<Int>, val exhausted: Boolean, val nodes: Int)

object Planner {
    fun board(level: Level, belt: Int) = Board(
        level.columns,
        Levels.stacksOf(level.boxes).map { it.toIntArray() },
        level.elevator,
        belt,
    )

    /** Начальное состояние: коробки открыты, всё, что можно, уже собрано (ничего). */
    fun initial(b: Board): PState = PState(
        IntArray(b.cols.size), IntArray(SLOTS), IntArray(SLOTS), IntArray(COLOR_COUNT), b.elevator < 0, 0, 0,
    ).also { settle(b, it) }

    /** Коробки забирают шарики с ленты. Порядок проверки — порядок, в котором шарик проезжает коробки. */
    fun settle(b: Board, s: PState) {
        var changed = true
        while (changed) {
            changed = false
            for (i in SLOTS - 1 downTo 0) {
                val st = b.stacks[i]
                if (s.pos[i] >= st.size) continue
                if (i == b.elevator && !s.open) continue          // лифт закрыт, пока ничего не собрано
                val c = st[s.pos[i]]
                val take = min(BOX - s.actN[i], s.belt[c])
                if (take > 0) { s.belt[c] -= take; s.total -= take; s.actN[i] += take; changed = true }
                if (s.actN[i] == BOX) { s.done++; s.open = true; s.actN[i] = 0; s.pos[i]++; changed = true }
            }
        }
    }

    /** Тап по колонке `col`: лотки, которые он запускает (сам лоток + соседи по стрелкам), и новые головы колонок. */
    private fun batchOf(b: Board, s: PState, col: Int, heads: IntArray): List<TrayDef>? {
        if (heads[col] >= b.cols[col].size) return null
        val first = b.cols[col][heads[col]]; heads[col]++
        val batch = arrayListOf(first)
        if (first.arrow and 1 != 0 && col > 0 && heads[col - 1] < b.cols[col - 1].size) { batch.add(b.cols[col - 1][heads[col - 1]]); heads[col - 1]++ }
        if (first.arrow and 2 != 0 && col < b.cols.size - 1 && heads[col + 1] < b.cols[col + 1].size) { batch.add(b.cols[col + 1][heads[col + 1]]); heads[col + 1]++ }
        return batch
    }

    /** Результат тапа по `col` или null, если ход невозможен или шарикам не хватает места на ленте. */
    fun tap(b: Board, s: PState, col: Int): PState? {
        val heads = s.heads.clone()
        val batch = batchOf(b, s, col, heads) ?: return null
        val main = s.copy()
        System.arraycopy(heads, 0, main.heads, 0, heads.size)
        // шарики приходят по одному, в порядке лотков; подходящая коробка забирает их сразу
        for (t in batch) repeat(t.n) {
            if (main.total >= b.cap) { settle(b, main); if (main.total >= b.cap) return null }
            main.belt[t.c]++; main.total++
            settle(b, main)
        }
        return main
    }

    /** Поиск выигрышной последовательности тапов. `limit` — предел числа разобранных состояний. */
    fun solve(b: Board, start: PState, limit: Int = 400_000): Outcome {
        val seen = HashSet<PState.Key>()
        val path = ArrayList<Int>()
        var nodes = 0
        var hit = false

        fun dfs(s: PState): Boolean {
            if (s.done == b.totalBoxes) return true
            if (++nodes > limit) { hit = true; return false }
            if (!seen.add(s.key())) return false
            // дети: сначала ходы, после которых собрано больше коробок и на ленте меньше шариков
            val kids = ArrayList<Pair<Int, PState>>(b.cols.size)
            for (col in b.cols.indices) tap(b, s, col)?.let { kids.add(col to it) }
            kids.sortWith(compareBy({ -it.second.done }, { it.second.total }))
            for ((col, ns) in kids) {
                path.add(col)
                if (dfs(ns)) return true
                path.removeAt(path.size - 1)
                if (hit) return false
            }
            return false
        }

        val ok = dfs(start)
        return Outcome(ok, if (ok) ArrayList(path) else emptyList(), !hit, nodes)
    }

    fun solvable(level: Level, cap: Int, limit: Int = 400_000): Boolean {
        val b = board(level, cap)
        return solve(b, initial(b), limit).win
    }

    /** Наименьшая вместимость ленты, при которой уровень проходим (бинарный поиск: вместимость монотонна). */
    fun minCap(level: Level, hi: Int = 30, limit: Int = 400_000): Int? {
        if (!solvable(level, hi, limit)) return null
        var lo = 4; var h = hi
        while (lo < h) {
            val mid = (lo + h) / 2
            if (solvable(level, mid, limit)) h = mid else lo = mid + 1
        }
        return h
    }
}
