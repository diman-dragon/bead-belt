package dev.beadbelt.game

import kotlin.math.min

/**
 * Лоток: n шариков цвета c. arrow: 0 нет, 1 влево, 2 вправо (3 в обе стороны — только с уровня Levels.BOTH_FROM) —
 * тап по такому ведущему лотку запускает и передний лоток соседней колонки, на которую показывает стрелка.
 */
class TrayDef(val c: Int, val n: Int, val arrow: Int = 0)

/** elevator — индекс стопки коробок «на лифте»: первая коробка закрыта замком, пока не соберётся любая другая. */
class Level(val id: Int, val columns: List<List<TrayDef>>, val boxes: List<Int>, val belt: Int, val elevator: Int = -1)

const val LEVEL_COUNT = 500
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

    /** Сколько цветов в уровне: растёт с номером до всех восьми. */
    fun colorsOf(id: Int) = when { id <= 2 -> 3; id <= 6 -> 4; id <= 12 -> 5; id <= 22 -> 6; id <= 40 -> 7; else -> COLOR_COUNT }

    /** Коробок каждого цвета: чем дальше, тем длиннее уровень. */
    fun boxesPerColor(id: Int) = when { id <= 8 -> 2; id <= 25 -> 3; id <= 80 -> 4; id <= 250 -> 5; else -> 6 }

    /** Доля лотков со стрелкой. */
    fun arrowChance(id: Int) = when { id < 8 -> 0.0; id <= 30 -> 0.10; id <= 100 -> 0.16; id <= 200 -> 0.22; id <= 350 -> 0.28; else -> 0.34 }

    /** С этого уровня стрелка может смотреть сразу в обе стороны. До него она всегда в одну: ведущий лоток тянет ровно одного ведомого. */
    const val BOTH_FROM = 201

    /**
     * Раскладка уровня номер `id` для попытки `attempt` (лотки, коробки, лифт) без вместимости ленты.
     * Порядок обращений к генератору случайных чисел НЕ менять: от него зависят таблицы ATTEMPT и BELTS
     * (их считает tools/gen_levels.py тем же порядком).
     */
    fun layout(id: Int, attempt: Int, belt: Int = 30): Level {
        val r = Rng(id * 7919 + attempt * 104729 + 13)
        val k = colorsOf(id)
        val m = boxesPerColor(id)
        val nCols = if (id < 4) 3 else 4
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

        // Стрелки. Ведущий лоток тянет одного соседа и только в одну сторону (влево или вправо);
        // «в обе стороны» (3) бывает лишь с BOTH_FROM-го уровня и только у лотков, у которых есть оба соседа.
        val pArrow = arrowChance(id)
        val columns = raw.mapIndexed { ci, col ->
            col.map { t ->
                if (r.next() < pArrow) {
                    val canL = ci > 0
                    val canR = ci < nCols - 1
                    val a = if (id >= BOTH_FROM && canL && canR && r.next() < 0.35) 3
                    else if (canL && canR) 1 + r.nextInt(2)
                    else if (canL) 1
                    else 2
                    TrayDef(t.c, t.n, a)
                } else t
            }
        }
        val every = if (id > 250) 2 else 3
        val elevator = if (id >= 20 && id % every == 0) r.nextInt(SLOTS) else -1
        return Level(id, columns, boxes, belt, elevator)
    }

    /**
     * Какая из попыток раскладки (`layout(id, attempt)`) стала уровнем. Всё, чего здесь нет, — попытка 0.
     * Подбирал tools/gen_levels.py: берётся первая попытка, у которой минимальная лента модели не ниже целевой кривой сложности.
     */
    private val ATTEMPT = mapOf(18 to 1, 19 to 1, 21 to 2, 22 to 1, 35 to 1, 69 to 1, 71 to 1, 78 to 1, 99 to 2, 105 to 1, 111 to 1, 116 to 1, 127 to 3, 131 to 1, 132 to 1, 135 to 2, 136 to 1, 138 to 1, 142 to 1, 144 to 1, 145 to 2, 149 to 1, 150 to 2, 151 to 2, 154 to 2, 158 to 2, 159 to 9, 160 to 1, 161 to 6, 162 to 1, 166 to 3, 171 to 1, 173 to 6, 174 to 4, 181 to 1, 183 to 5, 185 to 5, 187 to 5, 191 to 2, 192 to 5, 193 to 2, 194 to 1, 195 to 3, 197 to 2, 198 to 3, 199 to 4, 200 to 2, 201 to 2, 202 to 1, 203 to 3, 204 to 3, 205 to 12, 207 to 3, 209 to 11, 212 to 1, 214 to 2, 217 to 7, 220 to 1, 222 to 2, 223 to 5, 224 to 1, 225 to 1, 227 to 1, 229 to 2, 230 to 2, 231 to 2, 235 to 6, 237 to 3, 238 to 1, 239 to 1, 241 to 3, 243 to 2, 244 to 3, 245 to 3, 247 to 1, 250 to 1, 251 to 1, 252 to 3, 253 to 5, 254 to 4, 255 to 1, 256 to 1, 257 to 2, 258 to 1, 260 to 1, 263 to 1, 265 to 1, 266 to 1, 270 to 2, 272 to 5, 273 to 3, 276 to 1, 277 to 1, 278 to 2, 279 to 2, 282 to 1, 283 to 1, 284 to 1, 285 to 2, 287 to 2, 288 to 1, 289 to 1, 290 to 6, 291 to 2, 292 to 1, 293 to 1, 294 to 1, 297 to 2, 300 to 1, 301 to 2, 304 to 2, 305 to 4, 306 to 3, 307 to 1, 308 to 1, 309 to 3, 310 to 4, 318 to 1, 319 to 1, 320 to 1, 321 to 1, 323 to 1, 325 to 2, 326 to 4, 327 to 2, 328 to 2, 330 to 1, 331 to 2, 332 to 1, 333 to 2, 334 to 1, 335 to 1, 337 to 3, 338 to 2, 339 to 3, 340 to 2, 341 to 2, 342 to 2, 345 to 2, 346 to 1, 347 to 1, 349 to 2, 353 to 1, 355 to 1, 358 to 3, 359 to 2, 361 to 4, 363 to 4, 365 to 1, 366 to 2, 367 to 1, 368 to 2, 374 to 1, 375 to 1, 376 to 5, 378 to 1, 381 to 1, 383 to 2, 385 to 1, 386 to 6, 387 to 1, 390 to 3, 392 to 2, 393 to 2, 395 to 1, 397 to 2, 398 to 3, 399 to 6, 400 to 2, 403 to 1, 407 to 1, 409 to 4, 410 to 1, 411 to 2, 413 to 2, 414 to 1, 415 to 1, 418 to 4, 420 to 2, 421 to 3, 422 to 1, 427 to 3, 429 to 3, 431 to 3, 433 to 1, 434 to 1, 435 to 1, 442 to 2, 443 to 1, 444 to 2, 445 to 1, 448 to 1, 450 to 2, 451 to 2, 452 to 1, 455 to 1, 456 to 4, 457 to 1, 458 to 2, 464 to 2, 467 to 1, 470 to 3, 471 to 3, 475 to 2, 476 to 2, 477 to 2, 479 to 1, 481 to 3, 484 to 1, 491 to 2, 493 to 3, 494 to 2, 495 to 2, 496 to 2, 498 to 2, 499 to 2, 500 to 1)

    /**
     * Вместимость ленты каждого уровня (индекс = номер − 1): минимум, при котором уровень проходим в модели (Planner),
     * плюс запас на непредсказуемость воронки (10 на уровнях 1–100, 9 на 101–300, 8 дальше; не больше 30).
     * Таблицу считает tools/gen_levels.py; настроить её по реальной физике можно через tools/README.md.
     */
    private val BELTS = intArrayOf(
        14, 14, 14, 14, 14, 14, 14, 14, 14, 19, 14, 17, 18, 18, 20, 16, 15, 17, 17, 19,
        15, 16, 17, 29, 18, 16, 17, 20, 22, 22, 20, 20, 19, 17, 21, 23, 20, 20, 17, 23,
        21, 20, 22, 20, 25, 26, 27, 23, 30, 27, 26, 24, 17, 17, 23, 23, 24, 23, 22, 22,
        20, 20, 21, 25, 26, 23, 23, 28, 19, 20, 20, 21, 21, 22, 19, 22, 23, 23, 28, 21,
        29, 27, 23, 28, 24, 30, 26, 24, 20, 25, 20, 29, 21, 24, 23, 20, 26, 23, 24, 24,
        20, 24, 22, 30, 25, 26, 23, 22, 25, 21, 28, 27, 22, 24, 21, 24, 26, 26, 22, 24,
        25, 25, 25, 25, 26, 25, 24, 24, 26, 28, 27, 25, 22, 22, 25, 23, 24, 25, 27, 28,
        23, 25, 26, 27, 29, 25, 29, 24, 23, 29, 28, 23, 28, 26, 24, 24, 24, 24, 25, 25,
        27, 27, 24, 24, 29, 28, 29, 26, 30, 27, 27, 25, 25, 29, 25, 28, 25, 28, 25, 28,
        27, 28, 26, 27, 26, 30, 26, 26, 28, 28, 28, 26, 30, 30, 28, 30, 26, 28, 28, 30,
        26, 26, 29, 26, 28, 27, 27, 30, 26, 26, 28, 28, 27, 30, 27, 26, 28, 28, 28, 27,
        28, 30, 28, 30, 30, 28, 30, 29, 27, 29, 30, 28, 28, 27, 27, 26, 26, 28, 27, 29,
        26, 26, 29, 28, 28, 26, 29, 29, 27, 30, 27, 28, 26, 28, 27, 27, 30, 27, 28, 30,
        26, 30, 29, 26, 30, 27, 26, 26, 27, 29, 29, 26, 26, 28, 30, 29, 28, 28, 27, 29,
        27, 30, 26, 28, 28, 27, 27, 28, 26, 28, 28, 26, 27, 27, 28, 26, 27, 28, 26, 28,
        28, 29, 27, 25, 30, 29, 30, 29, 27, 29, 25, 27, 29, 26, 25, 30, 26, 26, 27, 26,
        28, 29, 25, 25, 30, 25, 29, 25, 29, 28, 28, 27, 25, 27, 29, 29, 25, 26, 28, 28,
        26, 27, 30, 27, 29, 26, 27, 25, 26, 26, 27, 27, 30, 27, 29, 27, 27, 28, 30, 25,
        25, 26, 29, 30, 27, 28, 27, 28, 26, 28, 30, 27, 28, 29, 27, 26, 26, 28, 29, 26,
        26, 25, 28, 29, 28, 30, 28, 26, 25, 27, 25, 25, 30, 29, 28, 27, 27, 27, 26, 25,
        26, 27, 29, 25, 30, 28, 27, 27, 27, 28, 26, 28, 29, 30, 27, 30, 29, 26, 28, 28,
        27, 26, 27, 28, 26, 29, 30, 29, 27, 27, 25, 28, 25, 29, 27, 29, 25, 26, 27, 27,
        25, 28, 25, 25, 27, 28, 30, 29, 29, 25, 26, 25, 29, 26, 27, 25, 28, 25, 28, 25,
        25, 26, 27, 28, 26, 27, 30, 28, 26, 27, 27, 25, 30, 27, 26, 27, 25, 26, 28, 27,
        27, 27, 26, 28, 28, 27, 30, 25, 29, 30, 26, 30, 30, 26, 30, 25, 30, 26, 26, 25,
    )

    /** Запас вместимости ленты сверх найденного минимума. */
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
