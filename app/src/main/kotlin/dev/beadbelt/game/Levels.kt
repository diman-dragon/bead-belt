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
class TrayDef(val c: Int, val n: Int, val arrow: Int = 0, val pid: Int = 0,
              /** Цвет скрыт («?»), пока лоток не выйдет вперёд своей колонки. Бывает только у лотков не в самом начале колонки. */
              val hidden: Boolean = false,
              /** Цепь: лоток нельзя высыпать, пока не собрано `lock` коробок (0 — цепи нет). Лоток в связанной паре цепей не носит. */
              val lock: Int = 0,
              /** Двухцветный лоток: последние `n2` шариков из `n` имеют цвет `c2`, остальные цвета `c` (`n2` = 0 — лоток обычный). */
              val c2: Int = -1, val n2: Int = 0) {
    /** Цвет шарика с номером `k` (они высыпаются по порядку 0..n-1). */
    fun ballColor(k: Int) = if (k >= n - n2) c2 else c
    val mixed: Boolean get() = n2 > 0
}

/**
 * elevator — индекс стопки коробок «на лифте»: первая коробка закрыта замком, пока не соберётся любая другая.
 * mystery — номера в списке `boxes` (кроме первых четырёх), цвет которых скрыт («?»), пока коробка не выйдет на место.
 */
class Level(val id: Int, val columns: List<List<TrayDef>>, val boxes: List<Int>, val belt: Int, val elevator: Int = -1,
            val mystery: Set<Int> = emptySet())

/**
 * В игре 200 уровней. Первые 50 остались такими, какими были (те же раскладки и ленты), дальше кривая сложности
 * (`Levels.spec`) растёт до максимума к 200-му: больше цветов и коробок, больше связей и новые усложнения
 * (двухцветные лотки, цепи, скрытые коробки). Ленты подбирает `tools/Tune.kt` (tools/README.md).
 */
const val LEVEL_COUNT = 200
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
    /** Доля лотков со скрытым цветом (среди тех, что не в начале колонки). */
    val hiddenShare: Double,
    /** Учебный уровень про скрытый цвет: два скрытых лотка стоят сразу за первыми. */
    val hiddenLesson: Boolean,
    /** Сколько раз два лотка разных цветов меняются шариками: каждый обмен даёт два двухцветных лотка. */
    val swaps: Int = 0,
    /** Сколько лотков закованы в цепь и какое наибольшее число коробок надо собрать, чтобы цепь спала. */
    val chains: Int = 0,
    val maxLock: Int = 0,
    /** Доля скрытых («?») коробок среди тех, что ждут в очереди. */
    val mysteryShare: Double = 0.0,
)

object Levels {
    /**
     * Кривая сложности на 200 уровней. Новые механики вводятся уроками (`Lessons`), дальше растёт только сложность,
     * по одной ступеньке за раз (`LevelsTest.difficultyCurveIsGentleAndMonotonic`):
     *  цветов 3 (до 9-го), 4 (до 24-го), 5 (до 69-го), 6, 7 (с 120-го), 8 (с 165-го);
     *  коробок на цвет 2 (до 12-го), 3 (до 94-го), 4, 5 (с 150-го);
     *  связанных пар 0 (до урока связей), 1, 2 (с 85-го), 3 (с 140-го), 4 (с 185-го);
     *  скрытых лотков от 12% до 45%; лифт каждый третий уровень, с 120-го через один;
     *  двухцветные лотки (с урока «Два цвета»), цепи (с урока «Цепь»), скрытые коробки (с урока «Тайна»): число растёт к 200-му.
     * Связи односторонние: ведущий → ведомый. Первые 50 уровней совпадают с прежней версией до последнего шарика.
     */
    /**
     * Первые десять уровней — спрессованные прежние 50: берутся уровни сложности 1, 4, 8, 12, 16, 20, 26, 34, 42 и 50,
     * так что на каждом из первых восьми идёт свой урок (основы, подсказка, отмена, очистка, перемешивание, скрытые лотки,
     * связи, лифт), а два последних — свободная игра. Дальше номер уровня = сложность − 40 (11-й = прежний 51-й).
     */
    val EARLY = intArrayOf(1, 4, 8, 12, 16, 20, 26, 34, 42, 50)

    /** Индекс сложности уровня `id`: по нему считаются параметры, раскладка, уроки и ленты. */
    fun diff(id: Int): Int = if (id <= EARLY.size) EARLY[(id - 1).coerceAtLeast(0)] else id + (50 - EARLY.size)

    /** Номер уровня, у которого индекс сложности `d` (-1, если такого нет среди спрессованных первых). */
    fun idOf(d: Int): Int = if (d > 50) d - (50 - EARLY.size) else EARLY.indexOf(d).let { if (it < 0) -1 else it + 1 }

    fun spec(id: Int): Spec = specD(diff(id))

    /** Кривая по индексу сложности `id` (в этой функции он зовётся id, как раньше). */
    private fun specD(id: Int): Spec {
        val colors = when { id < 10 -> 3; id < 25 -> 4; id < 70 -> 5; id < 120 -> 6; id < 165 -> 7; else -> COLOR_COUNT }
        val perColor = when { id < 13 -> 2; id < 95 -> 3; id < 150 -> 4; else -> 5 }
        val linksAt = Lessons.all[LS_LINKS].level
        val pairs = when { id < linksAt -> 0; id < 85 -> 1; id < 140 -> 2; id < 185 -> 3; else -> 4 }
        val liftAt = Lessons.all[LS_LIFT].level
        val lift = id >= liftAt && (if (id < 120) (id - liftAt) % 3 == 0 else id % 2 == 0)
        val sizeHi = when { id <= 15 -> 4; id <= 100 -> 5; else -> 6 }
        val hiddenAt = Lessons.all[LS_HIDDEN].level
        val share = when {
            id < hiddenAt -> 0.0
            id <= 50 -> 0.12 + 0.38 * (id - hiddenAt) / (500.0 - hiddenAt)
            else -> { val at50 = 0.12 + 0.38 * (50 - hiddenAt) / (500.0 - hiddenAt); at50 + (0.45 - at50) * minOf(id - 50, 150) / 150.0 }
        }
        val mixedAt = Lessons.all[LS_MIXED].level
        val chainAt = Lessons.all[LS_CHAIN].level
        val mysteryAt = Lessons.all[LS_MYSTERY].level
        val swaps = if (id < mixedAt) 0 else minOf(5, 1 + (id - mixedAt) / 30)
        val chains = if (id < chainAt) 0 else minOf(4, 1 + (id - chainAt) / 40)
        val maxLock = if (id < chainAt) 0 else minOf(6, 2 + (id - chainAt) / 30)
        val mystery = if (id < mysteryAt) 0.0 else minOf(0.6, 0.25 + 0.35 * (id - mysteryAt) / 100.0)
        return Spec(colors, perColor, if (id <= 5) 3 else 4, pairs, id < linksAt + 20, id in linksAt until linksAt + 6, lift, sizeHi, share, id == hiddenAt,
            swaps, chains, maxLock, mystery)
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
     * Прячет цвет части лотков. Прячутся только лотки не в начале колонки (первый всегда виден), а на учебном
     * уровне — вторые лотки двух колонок, чтобы «?» сразу бросился в глаза.
     */
    private fun hideSome(cols: List<MutableList<TrayDef>>, spec: Spec, r: Rng) {
        if (spec.hiddenShare <= 0.0) return
        fun hide(ci: Int, ti: Int) { val t = cols[ci][ti]; cols[ci][ti] = TrayDef(t.c, t.n, t.arrow, t.pid, true) }
        if (spec.hiddenLesson) { for (ci in 0 until minOf(2, cols.size)) if (cols[ci].size > 1) hide(ci, 1); return }
        val eligible = ArrayList<IntArray>()
        for (ci in cols.indices) for (ti in 1 until cols[ci].size) eligible.add(intArrayOf(ci, ti))
        var count = 0
        for (e in eligible) if (r.next() < spec.hiddenShare) { hide(e[0], e[1]); count++ }
        // на каждом уровне после урока хотя бы два скрытых лотка, иначе механика бы пропадала
        while (count < minOf(2, eligible.size)) {
            val e = eligible[r.nextInt(eligible.size)]
            if (!cols[e[0]][e[1]].hidden) { hide(e[0], e[1]); count++ }
        }
    }

    /**
     * Двухцветные лотки. Два лотка разных цветов обмениваются 1–2 шариками: оба становятся двухцветными, размеры лотков
     * и число шариков каждого цвета не меняются, так что баланс шариков и коробок сохраняется.
     * На учебном уровне первый обмен ставит два двухцветных лотка в начало колонок, чтобы они сразу бросались в глаза.
     */
    private fun mixTrays(cols: List<MutableList<TrayDef>>, spec: Spec, lesson: Boolean, r: Rng) {
        val used = HashSet<Int>()
        fun key(ci: Int, ti: Int) = ci * 1000 + ti
        fun swap(ci: Int, ti: Int, cj: Int, tj: Int, m: Int) {
            val a = cols[ci][ti]; val b = cols[cj][tj]
            cols[ci][ti] = TrayDef(a.c, a.n, a.arrow, a.pid, a.hidden, a.lock, b.c, m)
            cols[cj][tj] = TrayDef(b.c, b.n, b.arrow, b.pid, b.hidden, b.lock, a.c, m)
            used.add(key(ci, ti)); used.add(key(cj, tj))
        }
        for (k in 0 until spec.swaps) {
            if (k == 0 && lesson) {
                // учебный обмен: головы двух колонок с разными цветами
                var done = false
                for (ci in cols.indices) for (cj in ci + 1 until cols.size) {
                    if (done) break
                    val a = cols[ci][0]; val b = cols[cj][0]
                    if (a.c != b.c && a.pid == 0 && b.pid == 0) { swap(ci, 0, cj, 0, 1); done = true }
                }
                if (done) continue
            }
            // случайная пара: разные цвета, оба лотка ещё не двухцветные
            for (attempt in 0 until 60) {
                val ci = r.nextInt(cols.size); val cj = r.nextInt(cols.size)
                if (cols[ci].isEmpty() || cols[cj].isEmpty()) continue
                val ti = r.nextInt(min(6, cols[ci].size)); val tj = r.nextInt(min(6, cols[cj].size))
                if (ci == cj && ti == tj) continue
                if (key(ci, ti) in used || key(cj, tj) in used) continue
                val a = cols[ci][ti]; val b = cols[cj][tj]
                if (a.c == b.c || a.n < 3 || b.n < 3) continue
                val m = if (a.n >= 4 && b.n >= 4 && r.next() < 0.45) 2 else 1
                swap(ci, ti, cj, tj, m)
                break
            }
        }
    }

    /**
     * Цепи: у нескольких лотков (не из связанных пар) висит замок «открывается, когда собрано N коробок».
     * Цепи ложатся в разные колонки и в начало (три первых места), потому что именно там они мешают.
     * Лоток первой цепи на учебном уровне всегда стоит впереди и снимается после первой коробки.
     */
    private fun chainSome(cols: List<MutableList<TrayDef>>, spec: Spec, lesson: Boolean, r: Rng) {
        val order = cols.indices.toMutableList(); shuffle(order, r)
        // на учебном уровне первой цепью занимается колонка, у которой голова не из связанной пары
        if (lesson) { val j = order.indexOfFirst { cols[it].isNotEmpty() && cols[it][0].pid == 0 }; if (j > 0) { val t = order[0]; order[0] = order[j]; order[j] = t } }
        for (k in 0 until spec.chains) {
            val ci = order[k % order.size]
            val col = cols[ci]
            if (col.isEmpty()) continue
            val ti = if (k == 0 && lesson) 0 else r.nextInt(min(3, col.size))
            val t = col[ti]
            if (t.pid != 0 || t.lock != 0) {            // место занято парой или цепью: ищем другое случайное (первые попытки раскладки прежние)
                for (tries in 0 until 40) {
                    val cj = r.nextInt(cols.size); val cl = cols[cj]
                    if (cl.isEmpty()) continue
                    val tj = r.nextInt(min(3, cl.size)); val u = cl[tj]
                    if (u.pid != 0 || u.lock != 0) continue
                    cl[tj] = TrayDef(u.c, u.n, u.arrow, u.pid, u.hidden, 1 + r.nextInt(spec.maxLock), u.c2, u.n2)
                    break
                }
                continue
            }
            val lock = if (k == 0 && lesson) 1 else 1 + r.nextInt(spec.maxLock)
            col[ti] = TrayDef(t.c, t.n, t.arrow, t.pid, t.hidden, lock, t.c2, t.n2)
        }
    }

    /** Скрытые коробки: цвет части ждущих в очереди коробок показывается «?», пока они не выйдут на место. */
    private fun mysteryBoxes(count: Int, spec: Spec, lesson: Boolean, r: Rng): Set<Int> {
        if (spec.mysteryShare <= 0.0 || count <= SLOTS) return emptySet()
        val out = HashSet<Int>()
        for (i in SLOTS until count) if (r.next() < spec.mysteryShare) out.add(i)
        // на каждом уровне хотя бы две скрытые коробки (а на учебном — обе в первых двух стопках, сразу за активными)
        if (lesson) { out.add(SLOTS); out.add(SLOTS + 1) }
        while (out.size < 2) out.add(SLOTS + r.nextInt(count - SLOTS))
        return out
    }

    /**
     * Раскладка уровня `id`. `attempt` — номер варианта (генерируются разные, подходящий выбран проверкой),
     * `belt` — вместимость ленты.
     */
    fun layout(id: Int, attempt: Int, belt: Int = 30): Level {
        val d = diff(id)                            // всё в раскладке (параметры, зерно, уроки) считается по индексу сложности
        val r = Rng(d * 15485863 + attempt * 32452843 + 2027)
        val s = specD(d)
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
        hideSome(cols, s, r)

        // Новые усложнения берут числа из отдельного генератора: поток `r` выше не трогаем, поэтому
        // уровни до их появления остаются точно такими же, как были.
        val fx = Rng(d * 7368787 + attempt * 2038074 + 1013)
        if (s.swaps > 0) mixTrays(cols, s, d == Lessons.all[LS_MIXED].level, fx)
        if (s.chains > 0) chainSome(cols, s, d == Lessons.all[LS_CHAIN].level, fx)
        val mystery = mysteryBoxes(boxes.size, s, d == Lessons.all[LS_MYSTERY].level, fx)

        // свой набор цветов палитры на каждый уровень: цвета перемешаны, структура задачи не меняется
        val palette = (0 until COLOR_COUNT).toMutableList(); shuffle(palette, r)
        fun tint(t: TrayDef) = TrayDef(palette[t.c], t.n, t.arrow, t.pid, t.hidden, t.lock, if (t.c2 >= 0) palette[t.c2] else -1, t.n2)
        val elevator = if (s.lift) r.nextInt(SLOTS) else -1
        return Level(id, cols.map { col -> col.map(::tint) }, boxes.map { palette[it] }, belt, elevator, mystery)
    }

    /** Номер варианта раскладки, выбранный проверкой (по умолчанию 0). */
    private val ATTEMPT = mapOf<Int, Int>(
        // ATTEMPT-BEGIN
        27 to 1,
        88 to 1,
        103 to 1,
        109 to 1,
        110 to 1,
        114 to 1,
        115 to 1,
        121 to 1,
        123 to 2,
        125 to 2,
        126 to 4,
        132 to 1,
        137 to 1,
        140 to 2,
        141 to 1,
        142 to 7,
        143 to 2,
        145 to 1,
        149 to 1,
        152 to 1,
        157 to 2,
        158 to 1,
        159 to 3,
        160 to 2,
        161 to 2,
        164 to 3,
        165 to 1,
        167 to 2,
        170 to 2,
        172 to 1,
        173 to 4,
        175 to 1,
        177 to 1,
        181 to 1,
        185 to 2,
        186 to 2,
        187 to 1,
        188 to 1,
        189 to 7,
        190 to 1,
        191 to 3,
        192 to 7,
        193 to 4,
        194 to 4,
        196 to 3,
        197 to 6,
        198 to 19,
        200 to 7,
        201 to 17,
        202 to 5,
        204 to 21,
        205 to 27,
        206 to 3,
        207 to 2,
        208 to 11,
        209 to 10,
        210 to 2,
        212 to 1,
        214 to 5,
        215 to 3,
        216 to 14,
        217 to 2,
        218 to 6,
        219 to 6,
        220 to 8,
        221 to 5,
        222 to 1,
        223 to 12,
        224 to 5,
        225 to 10,
        226 to 10,
        228 to 3,
        229 to 6,
        230 to 20,
        231 to 2,
        232 to 20,
        233 to 15,
        234 to 4,
        237 to 2,
        238 to 18,
        239 to 8,
        240 to 8,
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
        18, 18, 18, 18, 18, 18, 18, 18, 18, 18,
        17, 17, 17, 17, 17, 17, 16, 16, 16, 16,
        16, 16, 16, 16, 16, 16, 16, 16, 16, 16,
        15, 15, 15, 14, 14, 14, 14, 14, 14, 14,
        14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
        14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
        14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
        13, 13, 13, 13, 13, 13, 13, 13, 13, 13,
        13, 13, 13, 13, 13, 13, 13, 13, 13, 13,
        14, 14, 14, 14, 14, 15, 14, 14, 14, 14,
        15, 14, 14, 14, 14, 14, 16, 14, 14, 14,
        14, 17, 14, 14, 14, 15, 14, 14, 14, 14,
        14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
        14, 15, 14, 22, 14, 18, 14, 14, 14, 14,
        14, 14, 18, 14, 14, 18, 16, 14, 14, 14,
        18, 24, 14, 14, 14, 15, 22, 21, 14, 14,
        16, 19, 14, 14, 19, 15, 15, 18, 14, 21,
        21, 29, 23, 22, 23, 18, 22, 17, 15, 14,
        17, 14, 17, 15, 27, 17, 15, 14, 16, 20,
        16, 14, 17, 19, 17, 15, 25, 14, 17, 17,
        14, 30, 15, 21, 15, 19, 14, 17, 24, 17,
        28, 15, 19, 20, 15, 24, 20, 18, 14, 30,
        21, 24, 25, 22, 21, 17, 28, 16, 20, 26,
        17, 26, 16, 24, 16, 30, 28, 26, 20, 15,
        // BELTS-END
    )

    fun make(id: Int): Level {
        val i = id.coerceIn(1, LEVEL_COUNT)
        val d = diff(i)                                      // таблицы ATTEMPT и BELTS ведутся по индексу сложности
        return layout(i, ATTEMPT[d] ?: 0, min(30, BELTS[d - 1] + if (Lessons.atLevel(i) != null) Lessons.BELT_BONUS else 0))
    }

    fun attemptOf(id: Int) = ATTEMPT[diff(id)] ?: 0
    /** Вместимость ленты по таблице (без бонуса учебного уровня). */
    fun beltOf(id: Int) = BELTS[diff(id) - 1]
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
