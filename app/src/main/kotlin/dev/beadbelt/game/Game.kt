package dev.beadbelt.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class Tray(val c: Int, n0: Int, val arrow: Int = 0) { var n = n0; var d = 0f }   // d — отображаемая глубина
/**
 * Коробка. n — шарики внутри, res — летят прямо сейчас, slot — место (0..3).
 * Собранная коробка тут же уступает место следующей и дозавершается в списке [Game.popping].
 */
class Box(val c: Int, val slot: Int) { var n = 0; var res = 0; var pop = -1f; var enter = 1f; var locked = false
    /** Толчок от упавшего шарика: 1 → 0, коробка слегка проседает и пружинит. */
    var bump = 0f
    var smokeT = 0f
    val committed: Int get() = n + res }
class Flyer(val c: Int, val x0: Float, val y0: Float, val box: Box, val boxIdx: Int, val hole: Int) { var t = 0f }
class Leaving(val tray: Tray, val x: Float) { var t = 0f }
class Spawn(val c: Int, val x: Float)
class RocketFx(val boxIdx: Int) { var t = 0f }
class Popup(val text: String, val x: Float, val y: Float, val c: Int) { var t = 0f }

enum class State { PLAY, WON, LOST }

/** Правила и симуляция без зависимостей от Android. */
class Game(val level: Level, seed: Long = level.id.toLong(), private val onEvent: (Int, Int) -> Unit) {
    companion object {
        const val SPEED = 290f      // скорость ленты, логических единиц/с (исходно было 110)
        const val POP_T = 1.15f          // собранная коробка поднимается дымком и тает
        const val FLY_T = 0.34f
        /** Подъём дымком: сколько пикселей прошла коробка к моменту f (0..1) — медленно, потом легко уходит вверх. */
        fun rise(f: Float): Float { val e = f.coerceIn(0f, 1f); return 170f * (e * e * (3f - 2f * e) * 0.55f + e * 0.45f) }
        const val ROCKET_T = 0.55f
    }

    val cols: List<MutableList<Tray>> =
        level.columns.map { col -> col.map { Tray(it.c, it.n, it.arrow) }.toMutableList() }
    val colX = FloatArray(cols.size) { 180f + (it - (cols.size - 1) / 2f) * 84f }
    val boxX = FloatArray(SLOTS) { 54f + 84f * it }
    /** Очереди коробок по местам: вторая и далее (первая стоит в [boxes]). */
    val queues: List<ArrayDeque<Box>> = Levels.stacksOf(level.boxes).mapIndexed { i, st -> ArrayDeque(st.map { Box(it, i) }) }
    val boxes = arrayOfNulls<Box>(SLOTS).also { arr ->
        for (i in 0 until SLOTS) arr[i] = queues[i].removeFirstOrNull()?.also { b -> b.locked = i == level.elevator }
    }
    /** Лифт открыт: первая коробка уже собрана (или лифта нет на уровне). */
    private var elevatorOpen = level.elevator < 0
    val elevatorIsOpen: Boolean get() = elevatorOpen

    val n = level.belt
    val sp = Geo.L / n
    val slots = IntArray(n) { -1 }
    /** Сколько раз игрок пользовался бустерами и «ещё шансом» на этом уровне. */
    var assists = 0
    val born = FloatArray(n)
    /** Откуда шарик начал падать с горловины воронки на ленту (для отрисовки). */
    val bornX = FloatArray(n)
    val bornY = FloatArray(n)
    var phase = 0f
    val spawnQ = ArrayDeque<Spawn>()
    val hopper = ArrayList<Body>()
    val flyers = ArrayList<Flyer>()
    val leaving = ArrayList<Leaving>()
    /** Собранные коробки: уже уступили место следующим, но ещё принимают летящие шарики и лопаются. */
    val popping = ArrayList<Box>()
    val rockets = ArrayList<RocketFx>()
    val popups = ArrayList<Popup>()
    val fx = Particles()

    var time = 0f
    var state = State.PLAY
    var done = 0
    val total = level.boxes.size
    /** Тряска экрана: 0..1, сама затухает. */
    var shake = 0f

    private val physics = HopperPhysics(hopper)
    private val pt = FloatArray(2)
    private var stuckT = 0f
    private var pickCombo = 0
    private var comboT = 0f
    private var spawnT = 0f
    private var clinkT = 0f
    private val rnd = java.util.Random(seed)

    init { cols.forEach { col -> col.forEachIndexed { i, t -> t.d = i.toFloat() } } }

    val occupied: Int get() { var k = 0; for (c in slots) if (c >= 0) k++; return k }
    val progress get() = done.toFloat() / total

    /** Коробка принимает шарики: стоит на месте, не лопается и не заперта. */
    fun ready(b: Box) = !b.locked

    /** Сколько шариков ещё должна получить коробка (с учётом летящих и забронированных). */
    private fun need(b: Box) = BOX - b.committed

    // ---------- ввод ----------

    /** Тап по колонке: лоток высыпается; если на нём стрелки — запускаются и соседние лотки. */
    fun tapColumn(i: Int): Boolean {
        if (state != State.PLAY || i !in cols.indices || cols[i].isEmpty()) return false
        val first = cols[i].removeAt(0)
        release(first, colX[i])
        if (first.arrow and 1 != 0 && i > 0 && cols[i - 1].isNotEmpty()) release(cols[i - 1].removeAt(0), colX[i - 1])
        if (first.arrow and 2 != 0 && i < cols.size - 1 && cols[i + 1].isNotEmpty()) release(cols[i + 1].removeAt(0), colX[i + 1])
        onEvent(EV_TAP, 0)
        return true
    }

    private fun release(t: Tray, x: Float) {
        repeat(t.n) { spawnQ.addLast(Spawn(t.c, x)) }
        leaving.add(Leaving(t, x))
    }

    /** Нажатие в логических координатах. */
    fun tap(x: Float, y: Float): Boolean {
        for (i in cols.indices) {
            if (abs(x - colX[i]) <= 42f && abs(y - TRAY_Y) <= 50f && tapColumn(i)) return true
        }
        return false
    }

    // ---------- бустеры ----------

    /** Шарики цвета c, которые ещё не закреплены за коробкой: на ленте, в воронке и в очереди. */
    private fun countColor(c: Int): Int {
        var k = 0
        for (j in slots.indices) if (slots[j] == c) k++
        for (i in 0 until hopper.size) if (hopper[i].c == c) k++
        for (s in spawnQ) if (s.c == c) k++
        return k
    }

    /** Ракета: через мгновение мгновенно «собирает» самую выгодную коробку, убирая её шарики с поля. */
    fun useRocket(): Boolean {
        if (state != State.PLAY || rockets.isNotEmpty()) return false
        var bi = -1
        var bs = -1
        for (i in 0 until SLOTS) {
            val b = boxes[i] ?: continue
            if (!ready(b) || need(b) <= 0) continue
            val score = countColor(b.c) * 4 + b.committed
            if (score > bs) { bs = score; bi = i }
        }
        if (bi < 0) return false
        rockets.add(RocketFx(bi))
        assists++
        onEvent(EV_ROCKET, 0)
        return true
    }

    /** Убирает k шариков цвета c: с ленты (только ничьих), из воронки, из очереди, затем из лотков. */
    private fun removeMarbles(c: Int, k: Int) {
        var left = k
        for (j in slots.indices) if (left > 0 && slots[j] == c) {
            Geo.point(phase + j * sp, pt); burst(pt[0], pt[1], c, 4); slots[j] = -1; left--
        }
        var hi = hopper.size - 1
        while (left > 0 && hi >= 0) {
            val b = hopper[hi]
            if (b.c == c) { burst(b.x, b.y - LIFT, c, 4); hopper.removeAt(hi); left-- }
            hi--
        }
        val si = spawnQ.iterator()
        while (left > 0 && si.hasNext()) { val sp0 = si.next(); if (sp0.c == c) { si.remove(); left-- } }
        for (col in cols) {
            val ti = col.iterator()
            while (left > 0 && ti.hasNext()) {
                val t = ti.next()
                if (t.c != c) continue
                val take = min(left, t.n); t.n -= take; left -= take
                if (t.n == 0) ti.remove()
            }
        }
    }

    /** Новые лотки из шариков cnt[цвет] — в самые короткие колонки (не больше 6 шариков в лотке). */
    private fun addTrays(cnt: IntArray) {
        for (c in 0 until COLOR_COUNT) {
            var left = cnt[c]
            while (left > 0) {
                val n = min(left, 6); left -= n
                val ci = cols.indices.minByOrNull { cols[it].size } ?: 0
                cols[ci].add(Tray(c, n).also { it.d = cols[ci].size.toFloat() })
            }
        }
    }

    /** Снимает с ленты до `max` ничьих шариков и возвращает их лотками. Возвращает, сколько снято. */
    private fun takeBack(max: Int, spark: Int): Int {
        val cnt = IntArray(COLOR_COUNT)
        var tot = 0
        for (j in slots.indices) if (slots[j] >= 0 && tot < max) {
            Geo.point(phase + j * sp, pt); burst(pt[0], pt[1], slots[j], spark)
            cnt[slots[j]]++; tot++; slots[j] = -1
        }
        if (tot > 0) addTrays(cnt)
        return tot
    }

    /** «Вернуть шарики»: до 8 шариков с ленты возвращаются новыми лотками в самую короткую колонку. */
    fun returnMarbles(): Boolean {
        if (state != State.PLAY) return false
        if (takeBack(8, 3) == 0) return false
        assists++
        onEvent(EV_ROCKET, 0)
        return true
    }

    /** «Ещё шанс» после поражения: все ничьи шарики с ленты возвращаются лотками, игра продолжается. */
    fun revive(): Boolean {
        if (state != State.LOST) return false
        if (takeBack(COLOR_COUNT * 40, 2) == 0) return false
        state = State.PLAY; stuckT = 0f; shake = 0f; assists++
        onEvent(EV_ROCKET, 0)
        return true
    }

    private fun burst(x: Float, y: Float, c: Int, count: Int) = fx.burst(x, y, c, count, rnd)

    // ---------- симуляция ----------

    fun update(dt0: Float) {
        val dt = min(dt0, 1f / 30f)
        time += dt
        for (col in cols) for (i in col.indices) { val t = col[i]; t.d += (i - t.d) * min(1f, dt * 12f) }
        var li = leaving.size - 1
        while (li >= 0) { val l = leaving[li]; l.t += dt * 3f; if (l.t >= 1f) leaving.removeAt(li); li-- }
        var pi = popups.size - 1
        while (pi >= 0) { val p = popups[pi]; p.t += dt; if (p.t >= 1f) popups.removeAt(pi); pi-- }
        fx.update(dt)
        shake = max(0f, shake - dt * 2.2f)
        comboT -= dt
        if (comboT <= 0f) pickCombo = 0

        if (state == State.PLAY) {
            // шарики по одному выпадают из лотков в воронку
            spawnT -= dt
            if (spawnT <= 0f && spawnQ.isNotEmpty()) {
                val s = spawnQ.removeFirst()
                // шарики вываливаются из лотка с разбросом — рассыпаются веером
                hopper.add(Body(s.c, s.x + (rnd.nextFloat() - 0.5f) * 14f, TRAY_Y + 34f).also { it.vy = 110f + rnd.nextFloat() * 70f; it.vx = (rnd.nextFloat() - 0.5f) * 110f })
                spawnT = 0.04f
            }
            // Равные подшаги на кадр (а не «накопитель» 120 Гц): число шагов не прыгает 1-2-1-2, движение ровное
            val sub = kotlin.math.ceil(dt / 0.0105f).toInt().coerceIn(1, 4)
            val h = dt / sub
            physics.resetHit()
            repeat(sub) { physics.step(h) }
            val sm = min(1f, dt * 40f)
            for (i in 0 until hopper.size) { val b = hopper[i]; b.rx += (b.x - b.rx) * sm; b.ry += (b.y - b.ry) * sm }
            clinkT -= dt
            if (physics.hitSpeed > 130f && clinkT <= 0f) {     // сильный удар: блик и звонкий «цок»
                fx.glint(physics.hitX, physics.hitY - LIFT, rnd.nextInt(COLOR_COUNT), rnd)
                onEvent(EV_CLINK, min(7, (physics.hitSpeed / 90f).toInt()))
                clinkT = 0.16f
            }

            val old = phase
            phase += SPEED * dt
            for (j in 0 until n) {
                val prev = (old + j * sp) % Geo.L
                val cur = (phase + j * sp) % Geo.L
                if (cur < prev && slots[j] < 0) {
                    val body = physics.takeGate()
                    if (body != null) { slots[j] = body.c; born[j] = time; bornX[j] = body.rx; bornY[j] = body.ry - LIFT }
                }
            }
            phase %= Geo.L
            pickup(dt)
        }

        var fi = flyers.size - 1
        while (fi >= 0) {
            val f = flyers[fi]
            f.t += dt / FLY_T
            if (f.t >= 1f) {
                f.box.res--; f.box.n++
                f.box.bump = 1f
                val lx = boxX[f.boxIdx] + (f.hole - 1) * 22f
                fx.glint(lx, BOX_Y - 12f, f.c, rnd)
                fx.burst(lx, BOX_Y - 14f, f.c, 4, rnd, 46f)
                if (f.box.n == BOX && f.box.pop < 0f) startPop(f.box, f.boxIdx)
                flyers.removeAt(fi)
            }
            fi--
        }

        var ri = rockets.size - 1
        while (ri >= 0) {
            val r = rockets[ri]
            r.t += dt / ROCKET_T
            val e = r.t.coerceIn(0f, 1f)
            fx.burst(ROCKET_X + (boxX[r.boxIdx] - ROCKET_X) * e, BOOST_Y + (BOX_Y - BOOST_Y) * e, 6, 1, rnd, 25f)
            if (r.t >= 1f) { rockets.removeAt(ri); applyRocket(r.boxIdx) }
            ri--
        }

        for (i in 0 until SLOTS) { val b = boxes[i] ?: continue; if (b.enter > 0f) b.enter = max(0f, b.enter - dt * 3f); if (b.bump > 0f) b.bump = max(0f, b.bump - dt * 4.5f) }
        var bi = popping.size - 1
        while (bi >= 0) {
            val b = popping[bi]
            if (b.bump > 0f) b.bump = max(0f, b.bump - dt * 4.5f)
            if (b.pop >= 0f) {
                b.pop += dt
                b.smokeT -= dt                                   // дымок тянется за поднимающейся коробкой
                if (b.smokeT <= 0f && b.pop < POP_T * 0.8f) {
                    b.smokeT = 0.055f
                    fx.puff(boxX[b.slot], BOX_Y - rise(b.pop / POP_T) + 18f, b.c, rnd)
                }
                if (b.pop >= POP_T) popping.removeAt(bi)
            }
            bi--
        }

        if (state == State.PLAY) {
            if (done == total && flyers.isEmpty() && rockets.isEmpty()) {
                state = State.WON; onEvent(EV_WIN, 0)
                repeat(40) { fx.confetti(rnd, COLOR_COUNT) }
            } else checkStuck(dt)
        }
    }

    private fun applyRocket(idx: Int) {
        val b = boxes[idx] ?: return
        if (!ready(b) || need(b) <= 0) return
        val need = need(b)
        removeMarbles(b.c, need)
        b.n += need
        if (b.n == BOX && b.res == 0 && b.pop < 0f) startPop(b, idx)
        if (b.committed >= BOX) finish(idx)
    }

    /**
     * Шарик падает в коробку, просто проезжая над ней: так распорядилась воронка и порядок на ленте, больше ничего.
     * Собранная коробка тут же уступает место следующей, поэтому следующий шарик сразу падает в неё.
     */
    private fun pickup(dt: Float) {
        // окно захвата растёт со скоростью кадра, чтобы быстрая лента не «перепрыгивала» коробку
        val tol = max(7f, SPEED * dt * 0.6f)
        for (j in 0 until n) {
            val c = slots[j]
            if (c < 0) continue
            Geo.point(phase + j * sp, pt)
            if (pt[1] < Geo.CY + 8f) continue
            for (i in 0 until SLOTS) {
                val b = boxes[i] ?: continue
                if (b.c != c || b.locked || b.committed >= BOX || abs(pt[0] - boxX[i]) >= tol) continue
                slots[j] = -1
                flyers.add(Flyer(c, pt[0], pt[1], b, i, b.committed))
                b.res++
                onEvent(EV_PICK, min(pickCombo, 7)); pickCombo++; comboT = 0.6f
                if (pickCombo >= 3 && pickCombo % 2 == 1) popups.add(Popup("×$pickCombo", boxX[i], 484f, c))
                if (b.committed >= BOX) finish(i)
                break
            }
        }
    }

    /** Коробка собрана (все шарики внутри или уже летят): место сразу занимает следующая, лифт открывается. */
    private fun finish(i: Int) {
        val b = boxes[i] ?: return
        done++
        popping.add(b)
        boxes[i] = queues[i].removeFirstOrNull()
        if (!elevatorOpen) {                      // первая собранная коробка открывает лифт
            elevatorOpen = true
            boxes[level.elevator]?.locked = false
            onEvent(EV_UNLOCK, 0)
        }
    }

    private fun startPop(b: Box, i: Int) {
        b.pop = 0f
        burst(boxX[i], BOX_Y, b.c, 8)
        fx.ring(boxX[i], BOX_Y, b.c)
        repeat(2) { fx.glint(boxX[i] + (rnd.nextFloat() - 0.5f) * 56f, BOX_Y + (rnd.nextFloat() - 0.5f) * 56f, b.c, rnd) }
        repeat(5) { fx.puff(boxX[i], BOX_Y + 10f, b.c, rnd) }
        onEvent(EV_POP, 0)
    }

    /**
     * Проигрыш: конвейер забит (или ходов нет), и ни один шарик ни за кем не закреплён.
     * Закреплённые шарики ещё поедут к своим коробкам и освободят места, так что это не тупик.
     */
    private fun checkStuck(dt: Float) {
        // шарик, который проедет мимо подходящей коробки, — ещё не тупик
        val anyMatch = slots.any { c -> c >= 0 && boxes.any { b -> b != null && b.c == c && !b.locked && b.committed < BOX } }
        val busy = anyMatch || flyers.isNotEmpty() || rockets.isNotEmpty()
        val full = slots.none { it < 0 }
        val noMoves = cols.all { it.isEmpty() } && spawnQ.isEmpty() && hopper.isEmpty()
        if (!busy && (full || noMoves)) stuckT += dt else stuckT = 0f
        if (stuckT > 0.9f) { state = State.LOST; shake = 0.6f; onEvent(EV_LOSE, 0) }
    }

    /** Угол полёта ракеты (градусы) — для отрисовки. */
    fun rocketAngle(r: RocketFx): Float =
        Math.toDegrees(atan2((BOX_Y - BOOST_Y).toDouble(), (boxX[r.boxIdx] - ROCKET_X).toDouble())).toFloat() + 90f
}
