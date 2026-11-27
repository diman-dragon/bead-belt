package dev.beadbelt.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Лоток. `hidden` — цвет показывается, только когда лоток выйдет вперёд колонки. d — отображаемая глубина.
 * `lock` — цепь: лоток не высыпается, пока собрано меньше `lock` коробок. `n2` последних шариков имеют цвет `c2` (двухцветный лоток).
 */
class Tray(val c: Int, n0: Int, val arrow: Int = 0, val pid: Int = 0, val hidden: Boolean = false,
           val lock: Int = 0, val c2: Int = -1, val n2: Int = 0) {
    var n = n0; var d = 0f
    fun ballColor(k: Int) = if (k >= n - n2) c2 else c
    val mixed: Boolean get() = n2 > 0
}
/**
 * Коробка. n — шарики внутри, res — летят прямо сейчас, slot — место (0..3).
 * Собранная коробка тут же уступает место следующей и дозавершается в списке [Game.popping].
 */
class Box(val c: Int, val slot: Int) { var n = 0; var res = 0; var pop = -1f; var enter = 1f; var locked = false
    /** Цвет скрыт («?»), пока коробка ждёт в очереди; на месте она показывается как обычная. */
    var hidden = false
    /** Толчок от упавшего шарика: 1 → 0, коробка слегка проседает и пружинит. */
    var bump = 0f
    var smokeT = 0f
    val committed: Int get() = n + res }
class Flyer(val c: Int, val x0: Float, val y0: Float, val box: Box, val boxIdx: Int, val hole: Int) { var t = 0f }
class Leaving(val tray: Tray, val x: Float) { var t = 0f }
class Spawn(val c: Int, val x: Float)
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
        /** «Перемешать»: сколько случайных раскладок пробуем и сколько состояний модели разбираем на каждую. */
        const val SHUFFLE_TRIES = 10
        const val SHUFFLE_NODES = 15_000
    }

    val cols: List<MutableList<Tray>> =
        level.columns.map { col -> col.map { Tray(it.c, it.n, it.arrow, it.pid, it.hidden, it.lock, it.c2, it.n2) }.toMutableList() }
    val colX = FloatArray(cols.size) { 180f + (it - (cols.size - 1) / 2f) * 84f }
    val boxX = FloatArray(SLOTS) { 54f + 84f * it }
    /** Очереди коробок по местам: вторая и далее (первая стоит в [boxes]). */
    val queues: List<ArrayDeque<Box>> = List(SLOTS) { ArrayDeque<Box>() }.also { q ->
        level.boxes.forEachIndexed { i, c -> q[i % SLOTS].addLast(Box(c, i % SLOTS).also { b -> b.hidden = i in level.mystery }) }
    }
    val boxes = arrayOfNulls<Box>(SLOTS).also { arr ->
        for (i in 0 until SLOTS) arr[i] = queues[i].removeFirstOrNull()?.also { b -> b.locked = i == level.elevator }
    }
    /** Лифт открыт: первая коробка уже собрана (или лифта нет на уровне). */
    private var elevatorOpen = level.elevator < 0
    val elevatorIsOpen: Boolean get() = elevatorOpen

    val n = level.belt
    val sp = Geo.L / n
    val slots = IntArray(n) { -1 }
    /** Сколько раз игрок пользовался помощниками (кроме подсказки) и «ещё шансом» на этом уровне. */
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
    val popups = ArrayList<Popup>()
    val fx = Particles()

    var time = 0f
    var state = State.PLAY
    var done = 0
    val total = level.boxes.size
    /** Тряска экрана: 0..1, сама затухает. */
    var shake = 0f
    /** Дрожь лотка в цепи, по которому нажали слишком рано: 1 → 0 по колонкам. */
    val rattle = FloatArray(cols.size)

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

    /** Тап по колонке: лоток высыпается; если он ведущий в паре и ведомый впереди своей колонки — высыпается и ведомый. */
    fun tapColumn(i: Int): Boolean {
        if (state != State.PLAY || i !in cols.indices || cols[i].isEmpty()) return false
        if (cols[i][0].lock > done) { rattle[i] = 1f; onEvent(EV_CLINK, 2); return false }      // цепь ещё держит лоток
        val snap = takeSnap()
        val first = cols[i].removeAt(0)
        release(first, colX[i])
        // связь строго от ведущего к ведомому; тап по ведомому запускает только его самого
        val to = Links.follower(first.arrow, first.pid, i, cols.size) { c -> cols[c].firstOrNull()?.pid ?: 0 }
        if (to >= 0) release(cols[to].removeAt(0), colX[to])
        lastMove = snap
        onEvent(EV_TAP, 0)
        return true
    }

    private fun release(t: Tray, x: Float) {
        for (k in 0 until t.n) spawnQ.addLast(Spawn(t.ballColor(k), x))
        leaving.add(Leaving(t, x))
    }

    /** Нажатие в логических координатах. */
    fun tap(x: Float, y: Float): Boolean {
        for (i in cols.indices) {
            if (abs(x - colX[i]) <= 42f && abs(y - TRAY_Y) <= 50f && tapColumn(i)) return true
        }
        return false
    }

    // ---------- помощники ----------

    private class Snap(
        val cols: List<List<Tray>>, val boxes: Array<Box?>, val queues: List<List<Box>>, val popping: List<Box>,
        val slots: IntArray, val born: FloatArray, val bornX: FloatArray, val bornY: FloatArray,
        val phase: Float, val time: Float, val done: Int, val elevatorOpen: Boolean,
        val spawnQ: List<Spawn>, val hopper: List<Body>, val flyers: List<Flyer>, val leaving: List<Leaving>,
    )

    /** Положение до последнего хода (тап, «очистить», «перемешать»): к нему возвращает «отменить». */
    private var lastMove: Snap? = null

    private fun copyTray(t: Tray) = Tray(t.c, t.n, t.arrow, t.pid, t.hidden, t.lock, t.c2, t.n2).also { it.d = t.d }
    private fun copyBox(b: Box) = Box(b.c, b.slot).also {
        it.n = b.n; it.res = b.res; it.pop = b.pop; it.enter = b.enter; it.locked = b.locked; it.bump = b.bump; it.smokeT = b.smokeT; it.hidden = b.hidden
    }

    /** Полный снимок партии: лотки, коробки, шарики на ленте, в воронке и в полёте. Копии независимы от живых объектов. */
    private fun takeSnap(): Snap {
        val map = java.util.IdentityHashMap<Box, Box>()
        fun cb(b: Box): Box = map.getOrPut(b) { copyBox(b) }
        return Snap(
            cols.map { col -> col.map { copyTray(it) } },
            Array(SLOTS) { i -> boxes[i]?.let { cb(it) } },
            queues.map { q -> q.map { cb(it) } },
            popping.map { cb(it) },
            slots.clone(), born.clone(), bornX.clone(), bornY.clone(), phase, time, done, elevatorOpen,
            spawnQ.toList(),
            hopper.map { b -> Body(b.c, b.x, b.y).also { it.vx = b.vx; it.vy = b.vy; it.rx = b.rx; it.ry = b.ry } },
            flyers.map { f -> Flyer(f.c, f.x0, f.y0, cb(f.box), f.boxIdx, f.hole).also { it.t = f.t } },
            leaving.map { l -> Leaving(copyTray(l.tray), l.x).also { it.t = l.t } },
        )
    }

    private fun restore(sn: Snap) {
        for (i in cols.indices) { cols[i].clear(); cols[i].addAll(sn.cols[i]) }
        for (i in 0 until SLOTS) { boxes[i] = sn.boxes[i]; queues[i].clear(); queues[i].addAll(sn.queues[i]) }
        popping.clear(); popping.addAll(sn.popping)
        System.arraycopy(sn.slots, 0, slots, 0, n); System.arraycopy(sn.born, 0, born, 0, n)
        System.arraycopy(sn.bornX, 0, bornX, 0, n); System.arraycopy(sn.bornY, 0, bornY, 0, n)
        phase = sn.phase; time = sn.time; done = sn.done; elevatorOpen = sn.elevatorOpen
        spawnQ.clear(); spawnQ.addAll(sn.spawnQ)
        hopper.clear(); hopper.addAll(sn.hopper)
        flyers.clear(); flyers.addAll(sn.flyers)
        leaving.clear(); leaving.addAll(sn.leaving)
        popups.clear()
        state = State.PLAY; stuckT = 0f; shake = 0f; pickCombo = 0; comboT = 0f; spawnT = 0f
    }

    /** Есть что отменять: был ход, и партия не выиграна. После отмены повторно отменить нельзя. */
    val canUndo: Boolean get() = lastMove != null && state != State.WON

    /** «Отменить последний ход»: всё возвращается в то положение, в котором было перед последним тапом (или помощником). */
    fun undo(): Boolean {
        val sn = lastMove ?: return false
        if (state == State.WON) return false
        restore(sn); lastMove = null
        assists++
        onEvent(EV_AID, 0)
        return true
    }

    /** На ленте есть шарики, которые можно вернуть. */
    val canClear: Boolean get() { for (c in slots) if (c >= 0) return true; return false }

    /** «Очистить ленту»: все шарики с ленты возвращаются новыми лотками в самые короткие колонки. */
    fun clearBelt(): Boolean {
        if (state != State.PLAY || !canClear) return false
        val snap = takeSnap()
        if (takeBack(Int.MAX_VALUE, 3) == 0) return false
        lastMove = snap
        assists++
        onEvent(EV_AID, 0)
        return true
    }

    /** Есть что перемешивать: в лотках осталось хотя бы два лотка. */
    val canShuffle: Boolean get() { var k = 0; for (c in cols) k += c.size; return k >= 2 }

    /**
     * «Перемешать»: оставшиеся лотки раздаются по колонкам заново. Связи при этом разрываются, скрытые цвета остаются скрытыми.
     * Пробуется несколько случайных раскладок, и берётся первая, из которой модель находит путь к победе.
     */
    fun shuffle(): Boolean {
        if (state != State.PLAY || !canShuffle) return false
        val snap = takeSnap()
        val all = ArrayList<Tray>(); for (c in cols) all.addAll(c)
        for (attempt in 0 until SHUFFLE_TRIES) {
            java.util.Collections.shuffle(all, rnd)
            for (c in cols) c.clear()
            all.forEachIndexed { i, t -> cols[i % cols.size].add(Tray(t.c, t.n, 0, 0, t.hidden, t.lock, t.c2, t.n2)) }
            for (c in cols) c.forEachIndexed { i, t -> t.d = i.toFloat() }
            val (b, st) = Hints.snapshot(this)
            if (st.done == b.totalBoxes || Planner.solve(b, st, SHUFFLE_NODES).win) break
        }
        lastMove = snap
        assists++
        onEvent(EV_AID, 0)
        return true
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

    /** Можно ли продолжить после поражения: на ленте есть шарики, которые вернутся в лотки. */
    fun canRevive(): Boolean {
        if (state != State.LOST) return false
        // вернувшиеся шарики встают в конец колонок: если все головы в цепях, шанс ничего не изменит
        if (!cols.any { it.isEmpty() || it[0].lock <= done }) return false
        for (c in slots) if (c >= 0) return true
        return false
    }

    /** «Ещё шанс» после поражения: все ничьи шарики с ленты возвращаются лотками, игра продолжается. */
    fun revive(): Boolean {
        if (state != State.LOST) return false
        if (takeBack(COLOR_COUNT * 40, 2) == 0) return false
        state = State.PLAY; stuckT = 0f; shake = 0f; assists++; lastMove = null
        onEvent(EV_AID, 0)
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
        for (i in rattle.indices) if (rattle[i] > 0f) rattle[i] = max(0f, rattle[i] - dt * 3.5f)
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
            if (done == total && flyers.isEmpty()) {
                state = State.WON; onEvent(EV_WIN, 0)
                repeat(40) { fx.confetti(rnd, COLOR_COUNT) }
            } else checkStuck(dt)
        }
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
        val busy = anyMatch || flyers.isNotEmpty()
        val full = slots.none { it < 0 }
        // ходов нет, если колонки пусты или все головы в цепях, а собранных коробок для их снятия не хватает
        val noMoves = cols.all { it.isEmpty() || it[0].lock > done } && spawnQ.isEmpty() && hopper.isEmpty()
        if (!busy && (full || noMoves)) stuckT += dt else stuckT = 0f
        if (stuckT > 0.9f) { state = State.LOST; shake = 0.6f; onEvent(EV_LOSE, 0) }
    }

 
}
