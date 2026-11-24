package dev.beadbelt.game

import kotlin.math.abs
import kotlin.math.min

/**
 * Лоток: n шариков цвета c.
 *
 * Связь двух лотков — это ПАРА, а не «стрелка на лотке»: у обоих лотков один и тот же `pid` (> 0).
 * Ведущий лоток несёт стрелку (`arrow` = [Links.LEFT] или [Links.RIGHT] — туда, где стоит ведомый),
 * у ведомого `arrow` = 0. Связь работает только в одну сторону: тап по ведущему высыпает и ведомого
 * (если тот сейчас впереди своей колонки), тап по ведомому высыпает только его самого.
 */
class TrayDef(val c: Int, val n: Int, val arrow: Int = 0, val pid: Int = 0)

/** elevator — индекс стопки коробок «на лифте»: первая коробка закрыта замком, пока не соберётся любая другая. */
class Level(val id: Int, val columns: List<List<TrayDef>>, val boxes: List<Int>, val belt: Int, val elevator: Int = -1)

const val LEVEL_COUNT = 500
const val BOX = 3          // шариков в коробке
const val SLOTS = 4        // активных коробок
const val COLOR_COUNT = 8

/**
 * Единственное место, где записано правило связи. Им пользуются игра, модель-решатель и отрисовка,
 * поэтому они не могут разойтись.
 */
object Links {
    const val LEFT = 1
    const val RIGHT = 2

    /**
     * Колонка ведомого лотка, который запустится вместе с ведущим лотком (стрелка `arrow`, пара `pid`),
     * стоящим в колонке `col`; -1 — запускается только сам лоток.
     * `headPid(колонка)` — pid переднего лотка колонки или 0, если колонка пуста или лоток ни с кем не связан.
     * Ведомый должен стоять впереди своей колонки именно сейчас; у ведомого стрелки нет, обратного хода нет.
     */
    inline fun follower(arrow: Int, pid: Int, col: Int, cols: Int, headPid: (Int) -> Int): Int {
        if (pid == 0) return -1
        val to = when (arrow) { LEFT -> col - 1; RIGHT -> col + 1; else -> return -1 }
        if (to < 0 || to >= cols) return -1
        return if (headPid(to) == pid) to else -1
    }
}

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

/** Параметры уровня: всё, что отличает один уровень от другого, кроме случайной раскладки. */
class Spec(
    val colors: Int, val perColor: Int, val cols: Int,
    /** Сколько пар связанных лотков. */
    val pairs: Int,
    /** Пары только из лотков одного цвета (учебные связи). */
    val sameOnly: Boolean,
    /** Первая пара стоит впереди обеих колонок с самого начала. */
    val headFirst: Boolean,
    val lift: Boolean,
    /** Наибольший «обычный» размер лотка (3..6). */
    val sizeHi: Int,
)

object Levels {
    /**
     * Прогрессия, заново с нуля:
     *  1–40    вводные: 3→6 цветов, по 2–3 коробки на цвет, без связей и лифта;
     *  41–120  связи (одна пара, потом две) и лифт, 6–7 цветов, по 3–4 коробки;
     *  121–200 8 цветов, по 4 коробки, две пары, лифт на каждом втором;
     *  201–330 по 5 коробок на цвет, 2–3 пары;
     *  331–500 по 6 коробок на цвет, 3–4 пары.
     * Связи везде односторонние: ведущий → ведомый.
     */
    fun spec(id: Int): Spec {
        val colors = when { id <= 40 -> 3 + (id - 1) / 10; id <= 80 -> 6; id <= 120 -> 7; else -> COLOR_COUNT }
        val perColor = when { id <= 10 -> 2; id <= 80 -> 3; id <= 200 -> 4; id <= 330 -> 5; else -> 6 }
        val pairs = when {
            id <= 40 -> 0
            id <= 80 -> 1
            id <= 120 -> 1 + (id - 81) / 20
            id <= 200 -> 2
            id <= 330 -> 2 + (id - 201) / 65
            else -> 3 + (id - 331) / 85
        }
        val lift = when { id < 60 -> false; id <= 120 -> id % 3 == 0; else -> id % 2 == 0 }
        val sizeHi = when { id <= 20 -> 4; id <= 80 -> 5; else -> 6 }
        return Spec(colors, perColor, if (id <= 5) 3 else 4, pairs, id <= 60, id in 41..60, lift, sizeHi)
    }

    fun stacksOf(boxes: List<Int>): List<ArrayDeque<Int>> {
        val st = List(SLOTS) { ArrayDeque<Int>() }
        boxes.forEachIndexed { i, c -> st[i % SLOTS].addLast(c) }
        return st
    }

    /** Делит `total` шариков одного цвета на лотки по 3..6 (ни одного лотка меньше трёх). */
    private fun split(total: Int, hi: Int, r: Rng): List<Int> {
        val out = ArrayList<Int>()
        var rem = total
        while (rem > 0) {
            var n = minOf(rem, 3 + r.nextInt(hi - 2))
            if (rem - n in 1..2) n = if (rem - 3 >= 3) rem - 3 else rem
            out.add(n); rem -= n
        }
        return out
    }

    private fun <T> shuffle(a: MutableList<T>, r: Rng) {
        for (i in a.size - 1 downTo 1) { val j = r.nextInt(i + 1); val t = a[i]; a[i] = a[j]; a[j] = t }
    }

    /**
     * Расставляет `spec.pairs` связанных пар в соседних колонках. Ведущим становится случайный лоток пары;
     * стрелка ведущего смотрит на ведомого. Каждый лоток входит не более чем в одну пару.
     */
    private fun placePairs(cols: List<MutableList<TrayDef>>, spec: Spec, r: Rng) {
        val used = HashSet<Int>()
        fun key(ci: Int, ti: Int) = ci * 1000 + ti
        fun link(ci: Int, ti: Int, cj: Int, tj: Int, pid: Int) {
            val a = cols[ci][ti]; val b = cols[cj][tj]
            val leadIsA = r.nextInt(2) == 0
            val dirA = if (cj > ci) Links.RIGHT else Links.LEFT
            val dirB = if (ci > cj) Links.RIGHT else Links.LEFT
            cols[ci][ti] = TrayDef(a.c, a.n, if (leadIsA) dirA else 0, pid)
            cols[cj][tj] = TrayDef(b.c, b.n, if (leadIsA) 0 else dirB, pid)
            used.add(key(ci, ti)); used.add(key(cj, tj))
        }
        val depth = if (spec.sameOnly) 4 else 6
        for (pid in 1..spec.pairs) {
            if (pid == 1 && spec.headFirst) {
                // учебная пара: два лотка одного цвета впереди соседних колонок (недостающий подтягиваем вперёд)
                val order = (0 until cols.size - 1).toMutableList(); shuffle(order, r)
                var done = false
                for (ci in order) {
                    val want = cols[ci][0].c
                    val tj = cols[ci + 1].indexOfFirst { it.c == want }
                    if (tj < 0) continue
                    val t = cols[ci + 1][0]; cols[ci + 1][0] = cols[ci + 1][tj]; cols[ci + 1][tj] = t
                    link(ci, 0, ci + 1, 0, pid); done = true; break
                }
                if (done) continue
            }
            val wantSame = spec.sameOnly || r.next() < 0.4
            fun candidates(sameRequired: Boolean?): ArrayList<IntArray> {
                val out = ArrayList<IntArray>()
                for (ci in 0 until cols.size - 1) for (ti in 0 until min(depth, cols[ci].size)) for (tj in 0 until min(depth, cols[ci + 1].size)) {
                    if (abs(ti - tj) > 2 || key(ci, ti) in used || key(ci + 1, tj) in used) continue
                    if (sameRequired != null && (cols[ci][ti].c == cols[ci + 1][tj].c) != sameRequired) continue
                    out.add(intArrayOf(ci, ti, tj))
                }
                return out
            }
            var cand = candidates(wantSame)
            if (cand.isEmpty() && !spec.sameOnly) cand = candidates(null)
            if (cand.isEmpty() && spec.sameOnly) {
                // подходящего соседа нет: подтягиваем лоток нужного цвета из другой колонки на место рядом
                val order = (0 until cols.size - 1).toMutableList(); shuffle(order, r)
                search@ for (ci in order) for (ti in 0 until min(depth, cols[ci].size)) {
                    if (key(ci, ti) in used) continue
                    val want = cols[ci][ti].c
                    for (tj in maxOf(0, ti - 2)..min(ti + 2, min(depth, cols[ci + 1].size) - 1)) {
                        if (key(ci + 1, tj) in used) continue
                        for (ck in cols.indices) for (tk in cols[ck].indices) {
                            if (ck == ci || key(ck, tk) in used || cols[ck][tk].c != want || (ck == ci + 1 && tk == tj)) continue
                            val t = cols[ci + 1][tj]; cols[ci + 1][tj] = cols[ck][tk]; cols[ck][tk] = t
                            cand = arrayListOf(intArrayOf(ci, ti, tj)); break@search
                        }
                    }
                }
            }
            if (cand.isEmpty()) break
            val p = cand[r.nextInt(cand.size)]
            link(p[0], p[1], p[0] + 1, p[2], pid)
        }
    }

    /**
     * Раскладка уровня `id`. `attempt` — номер варианта (генерируются разные, подходящий выбран проверкой),
     * `belt` — вместимость ленты.
     */
    fun layout(id: Int, attempt: Int, belt: Int = 30): Level {
        val r = Rng(id * 15485863 + attempt * 32452843 + 2027)
        val s = spec(id)
        val trays = ArrayList<TrayDef>()
        val boxes = ArrayList<Int>()
        for (c in 0 until s.colors) {
            repeat(s.perColor) { boxes.add(c) }
            for (n in split(s.perColor * BOX, s.sizeHi, r)) trays.add(TrayDef(c, n))
        }
        shuffle(trays, r); shuffle(boxes, r)
        // первые четыре коробки — по возможности разных цветов
        for (i in 1 until min(SLOTS, s.colors)) {
            if (boxes.subList(0, i).contains(boxes[i])) {
                val j = (i + 1 until boxes.size).firstOrNull { !boxes.subList(0, i).contains(boxes[it]) } ?: continue
                val t = boxes[i]; boxes[i] = boxes[j]; boxes[j] = t
            }
        }
        val cols = List(s.cols) { ArrayList<TrayDef>() }
        trays.forEachIndexed { i, t -> cols[i % s.cols].add(t) }
        placePairs(cols, s, r)

        // свой набор цветов палитры на каждый уровень: цвета перемешаны, структура задачи не меняется
        val palette = (0 until COLOR_COUNT).toMutableList(); shuffle(palette, r)
        fun tint(t: TrayDef) = TrayDef(palette[t.c], t.n, t.arrow, t.pid)
        val elevator = if (s.lift) r.nextInt(SLOTS) else -1
        return Level(id, cols.map { col -> col.map(::tint) }, boxes.map { palette[it] }, belt, elevator)
    }

    /** Номер варианта раскладки, выбранный проверкой (по умолчанию 0). */
    private val ATTEMPT = mapOf<Int, Int>(
        // ATTEMPT-BEGIN
        45 to 1, 53 to 2, 57 to 1,
        // ATTEMPT-END
    )

    /**
     * Вместимость ленты каждого уровня (индекс = номер − 1). Подобрана статистикой: игрок, который после каждого
     * хода слушается подсказки, выигрывает не реже 28 раз из 30 на разных раскладках физики воронки.
     * Игра непредсказуема по замыслу (шарики перемешиваются в воронке), поэтому доказать проходимость нельзя —
     * её проверяют на многих раскладках. Пересчёт: tools/README.md.
     */
    private val BELTS = intArrayOf(
        // BELTS-BEGIN
        14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
        14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
        15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 16, 15, 15, 15, 15, 15, 15, 15, 15, 15,
        15, 15, 15, 15, 15, 15, 16, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
        15, 15, 15, 16, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 16,
        15, 15, 16, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 16, 15, 15,
        14, 14, 14, 17, 17, 19, 14, 14, 14, 14, 23, 14, 14, 17, 14, 14, 24, 14, 14, 14,
        14, 16, 14, 17, 17, 16, 17, 18, 14, 14, 14, 16, 23, 16, 14, 19, 14, 14, 14, 14,
        14, 14, 20, 14, 14, 14, 14, 14, 19, 16, 17, 14, 14, 14, 18, 24, 14, 14, 17, 17,
        17, 14, 14, 15, 14, 14, 14, 14, 14, 22, 18, 16, 14, 17, 14, 23, 14, 24, 17, 19,
        16, 25, 16, 23, 18, 18, 17, 15, 22, 14, 23, 21, 14, 17, 22, 16, 22, 16, 16, 18,
        18, 26, 17, 26, 20, 15, 24, 15, 14, 19, 19, 17, 18, 21, 14, 21, 21, 19, 17, 22,
        16, 15, 18, 15, 16, 21, 15, 20, 22, 22, 14, 15, 22, 17, 14, 15, 18, 15, 22, 14,
        17, 14, 14, 20, 20, 28, 18, 14, 21, 16, 17, 18, 14, 18, 14, 16, 15, 24, 19, 14,
        16, 19, 17, 20, 19, 17, 20, 23, 19, 18, 19, 15, 14, 19, 19, 21, 20, 19, 18, 17,
        24, 17, 23, 18, 19, 24, 17, 14, 14, 15, 19, 17, 16, 16, 15, 16, 14, 14, 17, 20,
        23, 18, 23, 20, 19, 18, 23, 19, 14, 16, 19, 16, 17, 18, 17, 20, 24, 18, 30, 20,
        30, 20, 14, 19, 14, 20, 16, 21, 17, 14, 18, 20, 21, 14, 16, 27, 17, 30, 14, 22,
        20, 17, 17, 16, 17, 17, 20, 21, 24, 20, 18, 14, 16, 20, 18, 14, 20, 22, 15, 21,
        22, 22, 21, 17, 22, 14, 21, 18, 20, 16, 26, 25, 26, 23, 26, 20, 19, 14, 20, 14,
        22, 24, 18, 21, 26, 17, 20, 21, 15, 15, 16, 17, 14, 16, 16, 19, 17, 17, 19, 14,
        19, 19, 14, 19, 17, 14, 19, 20, 16, 22, 14, 17, 21, 20, 24, 18, 17, 18, 21, 20,
        19, 26, 21, 27, 16, 21, 15, 26, 15, 20, 26, 20, 17, 25, 17, 21, 22, 21, 19, 18,
        28, 14, 16, 19, 21, 20, 18, 19, 21, 26, 17, 17, 14, 20, 15, 21, 21, 18, 17, 16,
        26, 21, 16, 14, 17, 17, 18, 14, 20, 19, 18, 17, 15, 15, 19, 19, 30, 23, 15, 26,
        // BELTS-END
    )

    fun make(id: Int): Level {
        val i = id.coerceIn(1, LEVEL_COUNT)
        return layout(i, ATTEMPT[i] ?: 0, min(30, BELTS[i - 1] + if (Lessons.atLevel(i) != null) Lessons.BELT_BONUS else 0))
    }

    fun attemptOf(id: Int) = ATTEMPT[id] ?: 0
    /** Вместимость ленты по таблице (без бонуса учебного уровня). */
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
