package dev.beadbelt.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Логические координаты поля 360x640 (потом масштабируются под экран).
const val W = 360f
const val H = 640f
const val TRAY_Y = 186f
const val BOX_Y = 528f
const val BOX_W = 78f
const val BOX_H = 92f
const val BODY_R = 8.5f      // радиус шарика в воронке

const val EV_TAP = 0
const val EV_PICK = 1
const val EV_POP = 2
const val EV_WIN = 3
const val EV_LOSE = 4
const val EV_ROCKET = 5
const val EV_UNLOCK = 6

/** Овальный конвейер: сверху вправо, снизу влево (по часовой). */
object Geo {
    const val CX = 180f
    const val CY = 392f
    const val A = 100f      // полудлина прямой
    const val R = 50f       // радиус дуги
    val ARC = (PI * R).toFloat()
    val L = 4 * A + 2 * ARC

    fun point(p0: Float, out: FloatArray) {
        var p = p0 % L
        if (p < 0) p += L
        when {
            p < A -> { out[0] = CX + p; out[1] = CY - R }
            p < A + ARC -> {
                val a = -PI / 2 + (p - A) / R
                out[0] = (CX + A + R * cos(a)).toFloat(); out[1] = (CY + R * sin(a)).toFloat()
            }
            p < 3 * A + ARC -> { out[0] = CX + A - (p - A - ARC); out[1] = CY + R }
            p < 3 * A + 2 * ARC -> {
                val a = PI / 2 + (p - 3 * A - ARC) / R
                out[0] = (CX - A + R * cos(a)).toFloat(); out[1] = (CY + R * sin(a)).toFloat()
            }
            else -> { out[0] = CX - A + (p - 3 * A - 2 * ARC); out[1] = CY - R }
        }
    }
}

/** Стенки воронки (x1, y1, x2, y2): шарики из лотков падают сюда и скатываются к воротам над конвейером. */
val WALLS = arrayOf(
    floatArrayOf(12f, 120f, 12f, 250f), floatArrayOf(12f, 250f, 160f, 328f),
    floatArrayOf(160f, 328f, 200f, 328f),
    floatArrayOf(200f, 328f, 348f, 250f), floatArrayOf(348f, 250f, 348f, 120f),
)

class Tray(val c: Int, n0: Int, val arrow: Int = 0) { var n = n0; var d = 0f }   // d — отображаемая глубина
class Box(val c: Int) { var n = 0; var res = 0; var pop = -1f; var enter = 1f; var locked = false }
class Flyer(val c: Int, val x0: Float, val y0: Float, val box: Box, val boxIdx: Int, val hole: Int) { var t = 0f }
class Leaving(val tray: Tray, val x: Float) { var t = 0f }
class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val c: Int) { var life = 1f }
class Body(val c: Int, var x: Float, var y: Float) { var vx = 0f; var vy = 0f }
class Spawn(val c: Int, val x: Float)
class RocketFx(val boxIdx: Int) { var t = 0f }

enum class State { PLAY, WON, LOST }

class Game(val level: Level, private val onEvent: (Int, Int) -> Unit) {
    companion object {
        const val SPEED = 110f
        const val POP_T = 0.5f
        const val FLY_T = 0.2f
        const val ROCKET_T = 0.55f
        const val PH = 1f / 120f
    }

    val cols: List<MutableList<Tray>> =
        level.columns.map { col -> col.map { Tray(it.c, it.n, it.arrow) }.toMutableList() }
    val colX = FloatArray(cols.size) { 180f + (it - (cols.size - 1) / 2f) * 84f }
    val boxX = FloatArray(SLOTS) { 54f + 84f * it }
    val stacks = Levels.stacksOf(level.boxes)
    val boxes = arrayOfNulls<Box>(SLOTS).also { arr ->
        for (i in 0 until SLOTS) arr[i] = stacks[i].removeFirstOrNull()?.let { c -> Box(c).also { b -> b.locked = i == level.elevator } }
    }
    private var elevatorOpen = level.elevator < 0

    val n = level.belt
    val sp = Geo.L / n
    val slots = IntArray(n) { -1 }
    val born = FloatArray(n)
    var phase = 0f
    val spawnQ = ArrayDeque<Spawn>()
    val hopper = ArrayList<Body>()
    val flyers = ArrayList<Flyer>()
    val leaving = ArrayList<Leaving>()
    val sparks = ArrayList<Spark>()
    val rockets = ArrayList<RocketFx>()

    var time = 0f
    var state = State.PLAY
    var done = 0
    val total = level.boxes.size
    private var stuckT = 0f
    private var pickCombo = 0
    private var comboT = 0f
    private var spawnT = 0f
    private var acc = 0f
    private val rnd = java.util.Random(level.id.toLong())

    init { cols.forEach { col -> col.forEachIndexed { i, t -> t.d = i.toFloat() } } }

    val occupied get() = slots.count { it >= 0 }
    val progress get() = done.toFloat() / total

    fun ready(b: Box) = b.pop < 0f && b.enter < 0.35f && !b.locked

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

    private fun countColor(c: Int) = slots.count { it == c } + hopper.count { it.c == c } + spawnQ.count { it.c == c }

    /** Ракета: через мгновение мгновенно «собирает» самую выгодную коробку, убирая её шарики с поля. */
    fun useRocket(): Boolean {
        if (state != State.PLAY || rockets.isNotEmpty()) return false
        var bi = -1
        var bs = -1
        for (i in 0 until SLOTS) {
            val b = boxes[i] ?: continue
            if (!ready(b) || b.n + b.res >= BOX) continue
            val score = countColor(b.c) * 4 + b.n + b.res
            if (score > bs) { bs = score; bi = i }
        }
        if (bi < 0) return false
        rockets.add(RocketFx(bi))
        onEvent(EV_ROCKET, 0)
        return true
    }

    /** Убирает k шариков цвета c: с ленты, из воронки, из очереди, затем из лотков. */
    private fun removeMarbles(c: Int, k: Int) {
        var left = k
        val pt = FloatArray(2)
        for (j in slots.indices) if (left > 0 && slots[j] == c) {
            Geo.point(phase + j * sp, pt); burst(pt[0], pt[1], c, 4); slots[j] = -1; left--
        }
        val hi = hopper.iterator()
        while (left > 0 && hi.hasNext()) { val b = hi.next(); if (b.c == c) { burst(b.x, b.y, c, 4); hi.remove(); left-- } }
        val si = spawnQ.iterator()
        while (left > 0 && si.hasNext()) { if (si.next().c == c) { si.remove(); left-- } }
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

    /** «Вернуть шарики»: до 8 шариков с ленты возвращаются новыми лотками в самую короткую колонку. */
    fun returnMarbles(): Boolean {
        if (state != State.PLAY) return false
        val cnt = IntArray(COLOR_COUNT)
        var tot = 0
        val pt = FloatArray(2)
        for (j in slots.indices) if (slots[j] >= 0 && tot < 8) {
            Geo.point(phase + j * sp, pt); burst(pt[0], pt[1], slots[j], 3)
            cnt[slots[j]]++; tot++; slots[j] = -1
        }
        if (tot == 0) return false
        for (c in 0 until COLOR_COUNT) if (cnt[c] > 0) {
            val ci = cols.indices.minByOrNull { cols[it].size } ?: 0
            cols[ci].add(Tray(c, cnt[c]).also { it.d = cols[ci].size.toFloat() })
        }
        onEvent(EV_ROCKET, 0)
        return true
    }

    private fun burst(x: Float, y: Float, c: Int, count: Int) {
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            sparks.add(Spark(x, y, cos(a) * 90f, sin(a) * 90f - 60f, c))
        }
    }

    // ---------- симуляция ----------

    fun update(dt0: Float) {
        val dt = min(dt0, 1f / 30f)
        time += dt
        for (col in cols) for ((i, t) in col.withIndex()) t.d += (i - t.d) * min(1f, dt * 12f)
        for (l in leaving) l.t += dt * 3f
        leaving.removeAll { it.t >= 1f }
        for (s in sparks) { s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 500f * dt; s.life -= dt * 1.6f }
        sparks.removeAll { it.life <= 0f }
        comboT -= dt
        if (comboT <= 0f) pickCombo = 0

        if (state == State.PLAY) {
            // шарики по одному выпадают из лотков в воронку
            spawnT -= dt
            if (spawnT <= 0f && spawnQ.isNotEmpty()) {
                val s = spawnQ.removeFirst()
                hopper.add(Body(s.c, s.x + (rnd.nextFloat() - 0.5f) * 10f, TRAY_Y + 34f).also { it.vy = 120f })
                spawnT = 0.05f
            }
            acc += dt
            var steps = 0
            while (acc >= PH && steps < 6) { stepPhysics(PH); acc -= PH; steps++ }
            if (steps == 6) acc = 0f

            val old = phase
            phase += SPEED * dt
            for (j in 0 until n) {
                val prev = (old + j * sp) % Geo.L
                val cur = (phase + j * sp) % Geo.L
                if (cur < prev && slots[j] < 0) {
                    val c = takeGate()
                    if (c >= 0) { slots[j] = c; born[j] = time }
                }
            }
            phase %= Geo.L
            pickup()
        }

        val fi = flyers.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            f.t += dt / FLY_T
            if (f.t >= 1f) {
                f.box.res--; f.box.n++
                if (f.box.n == BOX && f.box.pop < 0f) startPop(f.box, f.boxIdx)
                fi.remove()
            }
        }

        val ri = rockets.iterator()
        while (ri.hasNext()) {
            val r = ri.next()
            r.t += dt / ROCKET_T
            if (r.t >= 1f) { ri.remove(); applyRocket(r.boxIdx) }
        }

        for (i in 0 until SLOTS) {
            val b = boxes[i] ?: continue
            if (b.enter > 0f) b.enter = max(0f, b.enter - dt * 3f)
            if (b.pop >= 0f) {
                b.pop += dt
                if (b.pop >= POP_T) {
                    done++
                    if (!elevatorOpen) {   // первая собранная коробка открывает лифт
                        elevatorOpen = true
                        boxes[level.elevator]?.locked = false
                        onEvent(EV_UNLOCK, 0)
                    }
                    boxes[i] = stacks[i].removeFirstOrNull()?.let { c -> Box(c).also { nb -> nb.locked = i == level.elevator && !elevatorOpen } }
                }
            }
        }

        if (state == State.PLAY) {
            if (done == total && flyers.isEmpty() && rockets.isEmpty()) { state = State.WON; onEvent(EV_WIN, 0) }
            else checkStuck(dt)
        }
    }

    private fun applyRocket(idx: Int) {
        val b = boxes[idx] ?: return
        if (!ready(b) || b.n + b.res >= BOX) return
        val need = BOX - b.n - b.res
        removeMarbles(b.c, need)
        b.n += need
        if (b.n == BOX && b.res == 0 && b.pop < 0f) startPop(b, idx)
    }

    /** Шарик, лежащий на воротах воронки, выпускается на свободную ячейку конвейера. */
    private fun takeGate(): Int {
        var best = -1
        var by = 0f
        for ((i, b) in hopper.withIndex()) if (abs(b.x - 180f) < 24f && b.y > 308f && b.y > by) { best = i; by = b.y }
        return if (best < 0) -1 else hopper.removeAt(best).c
    }

    /** Круги в воронке: гравитация, столкновения друг с другом и со стенками. */
    private fun stepPhysics(h: Float) {
        for (b in hopper) { b.vy += 1500f * h; b.x += b.vx * h; b.y += b.vy * h }
        val m = 2f * BODY_R
        repeat(3) {
            for (i in 0 until hopper.size) {
                val a = hopper[i]
                for (j in i + 1 until hopper.size) {
                    val b = hopper[j]
                    var dx = b.x - a.x
                    var dy = b.y - a.y
                    val d2 = dx * dx + dy * dy
                    if (d2 < m * m && d2 > 1e-6f) {
                        val d = sqrt(d2); dx /= d; dy /= d
                        val pen = (m - d) / 2f
                        a.x -= dx * pen; a.y -= dy * pen; b.x += dx * pen; b.y += dy * pen
                        val rv = (b.vx - a.vx) * dx + (b.vy - a.vy) * dy
                        if (rv < 0f) { val imp = -rv * 0.55f; a.vx -= dx * imp; a.vy -= dy * imp; b.vx += dx * imp; b.vy += dy * imp }
                    }
                }
                for (w in WALLS) {
                    val ex = w[2] - w[0]; val ey = w[3] - w[1]
                    val t = (((a.x - w[0]) * ex + (a.y - w[1]) * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
                    val px = w[0] + ex * t; val py = w[1] + ey * t
                    var dx = a.x - px; var dy = a.y - py
                    val d2 = dx * dx + dy * dy
                    if (d2 < BODY_R * BODY_R) {
                        val d = sqrt(d2)
                        if (d > 1e-4f) { dx /= d; dy /= d } else { dx = 0f; dy = -1f }
                        a.x = px + dx * BODY_R; a.y = py + dy * BODY_R
                        val vn = a.vx * dx + a.vy * dy
                        if (vn < 0f) { a.vx -= 1.3f * vn * dx; a.vy -= 1.3f * vn * dy; a.vx *= 0.985f }
                    }
                }
            }
        }
        for (b in hopper) { b.x = b.x.coerceIn(14f, 346f); if (b.y > 336f) b.y = 336f }
    }

    private fun pickup() {
        val pt = FloatArray(2)
        for (j in 0 until n) {
            val c = slots[j]
            if (c < 0) continue
            Geo.point(phase + j * sp, pt)
            if (pt[1] < Geo.CY + 8f) continue
            for (i in 0 until SLOTS) {
                val b = boxes[i] ?: continue
                if (b.c == c && ready(b) && b.n + b.res < BOX && abs(pt[0] - boxX[i]) < 7f) {
                    slots[j] = -1
                    flyers.add(Flyer(c, pt[0], pt[1], b, i, b.n + b.res))
                    b.res++
                    onEvent(EV_PICK, min(pickCombo, 7)); pickCombo++; comboT = 0.6f
                    break
                }
            }
        }
    }

    private fun startPop(b: Box, i: Int) {
        b.pop = 0f
        burst(boxX[i], BOX_Y, b.c, 14)
        onEvent(EV_POP, 0)
    }

    /** Проигрыш: конвейер забит (или ходов нет) и ни один шарик не подходит ни к одной коробке. */
    private fun checkStuck(dt: Float) {
        val anyMatch = slots.any { c -> c >= 0 && boxes.any { b -> b != null && b.c == c && !b.locked && b.n + b.res < BOX } }
        val busy = flyers.isNotEmpty() || rockets.isNotEmpty() || boxes.any { b -> b != null && (b.pop >= 0f || b.enter > 0.35f) }
        val full = slots.none { it < 0 }
        val noMoves = cols.all { it.isEmpty() } && spawnQ.isEmpty() && hopper.isEmpty()
        if (!busy && !anyMatch && (full || noMoves)) stuckT += dt else stuckT = 0f
        if (stuckT > 0.9f) { state = State.LOST; onEvent(EV_LOSE, 0) }
    }
}
