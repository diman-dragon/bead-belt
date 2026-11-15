package dev.beadbelt.game

import kotlin.math.min

/**
 * Лоток: n шариков цвета c. arrow: 0 нет, 1 влево, 2 вправо, 3 в обе стороны —
 * тап по такому лотку запускает и передние лотки соседних колонок (по стрелкам).
 */
class TrayDef(val c: Int, val n: Int, val arrow: Int = 0)

/** elevator — индекс стопки коробок «на лифте»: первая коробка закрыта замком, пока не соберётся любая другая. */
class Level(val id: Int, val columns: List<List<TrayDef>>, val boxes: List<Int>, val belt: Int, val elevator: Int = -1)

const val LEVEL_COUNT = 100
const val BOX = 3          // шариков в коробке
const val SLOTS = 4        // активных коробок
const val COLOR_COUNT = 8

/** mulberry32: одинаковая последовательность на любом устройстве. */
class Rng(seed: Int) {
    private var a = seed
    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
    fun nextInt(n: Int) = (next() * n).toInt()
}

object Levels {
    fun stacksOf(boxes: List<Int>): List<ArrayDeque<Int>> {
        val st = List(SLOTS) { ArrayDeque<Int>() }
        boxes.forEachIndexed { i, c -> st[i % SLOTS].addLast(c) }
        return st
    }

    /** Лотки, которые запускает тап по колонке `col` (сам лоток + соседи по стрелкам). Лотки снимаются из колонок. */
    fun takeBatch(cols: List<MutableList<TrayDef>>, col: Int): List<TrayDef> {
        val first = cols[col].removeAt(0)
        val batch = arrayListOf(first)
        if (first.arrow and 1 != 0 && col > 0 && cols[col - 1].isNotEmpty()) batch.add(cols[col - 1].removeAt(0))
        if (first.arrow and 2 != 0 && col < cols.size - 1 && cols[col + 1].isNotEmpty()) batch.add(cols[col + 1].removeAt(0))
        return batch
    }

    /** Дискретная модель: тап выкладывает шарики на ленту, коробки сразу забирают своё. */
    private fun playOnce(level: Level, cap: Int, r: Rng): Boolean {
        val cols = level.columns.map { it.toMutableList() }
        val stacks = stacksOf(level.boxes)
        val actC = IntArray(SLOTS) { stacks[it].removeFirstOrNull() ?: -1 }
        val actN = IntArray(SLOTS)
        val belt = IntArray(COLOR_COUNT)
        var total = 0
        var done = 0

        fun settle() {
            var changed = true
            while (changed) {
                changed = false
                for (i in 0 until SLOTS) {
                    val c = actC[i]
                    if (c < 0 || (i == level.elevator && done == 0)) continue   // лифт закрыт, пока ничего не собрано
                    val take = minOf(BOX - actN[i], belt[c])
                    if (take > 0) { belt[c] -= take; total -= take; actN[i] += take; changed = true }
                    if (actN[i] == BOX) {
                        done++; actN[i] = 0; actC[i] = stacks[i].removeFirstOrNull() ?: -1; changed = true
                    }
                }
            }
        }

        while (done < level.boxes.size) {
            val open = cols.indices.filter { cols[it].isNotEmpty() }
            if (open.isEmpty()) return false
            val good = open.filter { i -> actC.any { it == cols[i][0].c } }
            val pick = if (good.isNotEmpty() && r.next() < 0.7) good[r.nextInt(good.size)] else open[r.nextInt(open.size)]
            for (tray in takeBatch(cols, pick)) {
                repeat(tray.n) {
                    if (total >= cap) { settle(); if (total >= cap) return false }
                    belt[tray.c]++; total++
                    settle()
                }
            }
        }
        return true
    }

    fun solvable(level: Level, cap: Int, tries: Int = 150): Boolean {
        val r = Rng(level.id * 31 + 7)
        repeat(tries) { if (playOnce(level, cap, r)) return true }
        return false
    }

    fun make(id: Int): Level {
        for (attempt in 0 until 20) {
            val r = Rng(id * 7919 + attempt * 104729 + 13)
            val k = min(3 + (id - 1) / 2, COLOR_COUNT)
            val m = min(2 + id / 6, 4)
            val nCols = if (id < 3) 3 else 4
            val trays = ArrayList<TrayDef>()
            val boxes = ArrayList<Int>()
            for (c in 0 until k) {
                repeat(m) { boxes.add(c) }
                var rem = m * BOX
                while (rem > 0) {
                    var n = minOf(rem, 3 + r.nextInt(4))
                    if (rem - n in 1..2) n = rem
                    trays.add(TrayDef(c, n)); rem -= n
                }
            }
            fun <T> shuffle(a: MutableList<T>) {
                for (i in a.size - 1 downTo 1) { val j = r.nextInt(i + 1); val t = a[i]; a[i] = a[j]; a[j] = t }
            }
            shuffle(trays); shuffle(boxes)
            val raw = List(nCols) { ArrayList<TrayDef>() }
            trays.forEachIndexed { i, t -> raw[i % nCols].add(t) }

            // стрелки с 8-го уровня, лифт с 15-го (каждый третий)
            val pArrow = if (id < 8) 0.0 else min(0.15 + id / 400.0, 0.35)
            val columns = raw.mapIndexed { ci, col ->
                col.map { t ->
                    if (r.next() < pArrow) {
                        val opts = ArrayList<Int>()
                        if (ci > 0) opts.add(1)
                        if (ci < nCols - 1) opts.add(2)
                        if (ci > 0 && ci < nCols - 1) opts.add(3)
                        TrayDef(t.c, t.n, opts[r.nextInt(opts.size)])
                    } else t
                }
            }
            val elevator = if (id >= 15 && id % 3 == 0) r.nextInt(SLOTS) else -1

            val probe = Level(id, columns, boxes, 30, elevator)
            val minCap = (8..30).firstOrNull { solvable(probe, it) } ?: continue
            var belt = min(30, maxOf(10, minCap + maxOf(0, 5 - id / 12)))
            while (belt < 30 && !solvable(probe, belt)) belt++
            if (!solvable(probe, belt)) continue
            return Level(id, columns, boxes, belt, elevator)
        }
        error("Не удалось создать уровень $id")
    }
}
