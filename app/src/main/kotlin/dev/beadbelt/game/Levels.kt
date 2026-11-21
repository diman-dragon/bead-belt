package dev.beadbelt.game

import kotlin.math.min

/**
 * Лоток: n шариков цвета c. arrow: 0 нет, 1 влево, 2 вправо, 3 в обе стороны —
 * тап по такому лотку запускает и передние лотки соседних колонок (по стрелкам).
 */
class TrayDef(val c: Int, val n: Int, val arrow: Int = 0)

/** elevator — индекс стопки коробок «на лифте»: первая коробка закрыта замком, пока не соберётся любая другая. */
class Level(val id: Int, val columns: List<List<TrayDef>>, val boxes: List<Int>, val belt: Int, val elevator: Int = -1)

const val LEVEL_COUNT = 200
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

    /**
     * Раскладка уровня номер `id` для попытки `attempt` (лотки, коробки, лифт) без вместимости ленты.
     * Порядок обращений к генератору случайных чисел НЕ менять: от него зависит облик каждого уровня.
     */
    fun layout(id: Int, attempt: Int, belt: Int = 30): Level {
        val r = Rng(id * 7919 + attempt * 104729 + 13)
        // 1–100: прежняя прогрессия (уровни не меняются). 101–200: все 8 цветов, коробок на цвет 4 → 5,
        // больше стрелок, лифт на каждом втором уровне.
        val hard = id > 100
        val k = if (hard) COLOR_COUNT else min(3 + (id - 1) / 2, COLOR_COUNT)
        val m = if (hard) 4 + (id - 101) / 50 else min(2 + id / 6, 4)
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
        val pArrow = if (id < 8) 0.0 else if (hard) min(0.25 + (id - 100) / 400.0, 0.5) else min(0.15 + id / 400.0, 0.35)
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
        val elevator = if (id >= 15 && id % (if (hard) 2 else 3) == 0) r.nextInt(SLOTS) else -1
        return Level(id, columns, boxes, belt, elevator)
    }

    /**
     * Какая из попыток раскладки (`layout(id, attempt)`) стала уровнем. Всё, чего здесь нет, — попытка 0.
     * 69, 88, 165 — как и раньше. 149, 151, 181, 192 переложены: прежняя раскладка требовала ленту 28–30 без единого запаса.
     */
    private val ATTEMPT = mapOf(69 to 1, 88 to 1, 149 to 1, 151 to 1, 165 to 1, 192 to 1)

    /**
     * Вместимость ленты каждого уровня (индекс = номер − 1): max(прежняя, минимум + 2).
     * Минимум считает [Planner.minCap] — точная модель игры, проверенная на настоящей физике воронки.
     * Запас +2 — право на пару лишних шариков на ленте. Таблицу проверяет `LevelsTest.beltTableMatchesThePlanner`;
     * пересчитать после правки генератора: `tools/README.md`.
     */
    private val BELTS = intArrayOf(
        13, 13, 13, 13, 13, 13, 13, 13, 13, 15, 21, 25, 21, 18, 26, 17, 15, 23, 19, 21,
        17, 24, 19, 23, 17, 17, 14, 22, 20, 15, 14, 17, 18, 26, 20, 23, 22, 19, 15, 17,
        15, 17, 22, 13, 17, 21, 24, 21, 27, 27, 21, 18, 12, 18, 18, 16, 24, 20, 17, 19,
        19, 16, 16, 19, 22, 21, 21, 25, 15, 19, 15, 18, 22, 17, 15, 15, 15, 14, 23, 16,
        21, 14, 14, 21, 21, 14, 19, 18, 24, 19, 27, 22, 12, 24, 23, 21, 22, 27, 18, 19,
        20, 22, 16, 15, 18, 18, 27, 11, 14, 20, 15, 13, 19, 24, 22, 25, 23, 25, 26, 14,
        17, 16, 16, 16, 27, 18, 12, 21, 12, 23, 28, 15, 16, 18, 17, 15, 15, 18, 16, 21,
        18, 19, 15, 17, 16, 15, 14, 22, 16, 12, 19, 29, 22, 19, 18, 26, 22, 19, 19, 16,
        19, 14, 24, 23, 23, 17, 28, 25, 23, 24, 25, 23, 20, 18, 25, 24, 19, 22, 29, 25,
        21, 25, 17, 25, 18, 30, 22, 27, 29, 25, 22, 21, 24, 16, 21, 27, 22, 30, 17, 20,
    )

    /** Запас вместимости ленты сверх точного минимума. */
    const val BELT_SLACK = 2

    fun make(id: Int): Level {
        val i = id.coerceIn(1, LEVEL_COUNT)
        return layout(i, ATTEMPT[i] ?: 0, BELTS[i - 1])
    }

    fun attemptOf(id: Int) = ATTEMPT[id] ?: 0
    fun beltOf(id: Int) = BELTS[id - 1]
}

/** Кэш сгенерированных уровней: следующий уровень готовится в фоне, пока играешь текущий. */
object LevelCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Level>()
    private val pool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "level-gen").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY } }

    fun get(id: Int): Level = cache.getOrPut(id.coerceIn(1, LEVEL_COUNT)) { Levels.make(id.coerceIn(1, LEVEL_COUNT)) }

    /** Уже готовый уровень или null — чтобы не считать на UI-потоке. */
    fun peek(id: Int): Level? = cache[id.coerceIn(1, LEVEL_COUNT)]

    /** Генерирует уровень в фоне и вызывает done в том же фоновом потоке. */
    fun load(id: Int, done: () -> Unit) { pool.execute { try { get(id) } finally { done() } } }

    fun prefetch(id: Int) {
        val i = id.coerceIn(1, LEVEL_COUNT)
        if (!cache.containsKey(i)) pool.execute { try { get(i) } catch (e: Exception) { /* не критично: уровень создастся при входе */ } }
    }
}
