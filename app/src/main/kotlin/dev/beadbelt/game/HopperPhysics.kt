package dev.beadbelt.game

import kotlin.math.sqrt

class Body(val c: Int, var x: Float, var y: Float) { var vx = 0f; var vy = 0f }

/**
 * Круги в воронке: гравитация, столкновения друг с другом и со стенками.
 * Пары ищутся через равномерную сетку (ячейка = диаметр шарика), поэтому цена шага
 * линейна по числу шариков, а не квадратична. Аллокаций в шаге нет.
 */
class HopperPhysics(private val bodies: ArrayList<Body>) {
    private companion object {
        const val CELL = 2f * BODY_R
        const val GY0 = 90f
        const val NX = 23
        const val NY = 17
        const val GRAVITY = 1500f
    }

    /** Самый сильный удар за шаг (скорость сближения) и его место — игра превращает это в звук и блик. */
    var hitSpeed = 0f; private set
    var hitX = 0f; private set
    var hitY = 0f; private set
    fun resetHit() { hitSpeed = 0f }

    private fun noteHit(speed: Float, x: Float, y: Float) { if (speed > hitSpeed) { hitSpeed = speed; hitX = x; hitY = y } }

    private val head = IntArray(NX * NY)
    private var next = IntArray(128)

    // стенки с заранее посчитанной длиной
    private val wx = FloatArray(WALLS.size) { WALLS[it][0] }
    private val wy = FloatArray(WALLS.size) { WALLS[it][1] }
    private val ex = FloatArray(WALLS.size) { WALLS[it][2] - WALLS[it][0] }
    private val ey = FloatArray(WALLS.size) { WALLS[it][3] - WALLS[it][1] }
    private val inv = FloatArray(WALLS.size) { 1f / (ex[it] * ex[it] + ey[it] * ey[it]) }

    private fun cellOf(b: Body): Int {
        var cx = (b.x / CELL).toInt(); var cy = ((b.y - GY0) / CELL).toInt()
        if (cx < 0) cx = 0 else if (cx >= NX) cx = NX - 1
        if (cy < 0) cy = 0 else if (cy >= NY) cy = NY - 1
        return cy * NX + cx
    }

    fun step(h: Float) {
        val n = bodies.size
        if (n == 0) return
        for (i in 0 until n) { val b = bodies[i]; b.vy += GRAVITY * h; b.x += b.vx * h; b.y += b.vy * h }
        if (next.size < n) next = IntArray(n * 2)
        val m = 2f * BODY_R
        val m2 = m * m
        repeat(3) {
            java.util.Arrays.fill(head, -1)
            for (i in 0 until n) { val c = cellOf(bodies[i]); next[i] = head[c]; head[c] = i }
            for (i in 0 until n) {
                val a = bodies[i]
                val c = cellOf(a)
                val cx = c % NX; val cy = c / NX
                for (gy in maxOf(cy - 1, 0)..minOf(cy + 1, NY - 1)) for (gx in maxOf(cx - 1, 0)..minOf(cx + 1, NX - 1)) {
                    var j = head[gy * NX + gx]
                    while (j >= 0) {
                        if (j > i) collide(a, bodies[j], m, m2)
                        j = next[j]
                    }
                }
                walls(a)
            }
        }
        for (i in 0 until n) { val b = bodies[i]; b.x = b.x.coerceIn(14f, 346f); if (b.y > 336f) b.y = 336f }
    }

    private fun collide(a: Body, b: Body, m: Float, m2: Float) {
        var dx = b.x - a.x
        var dy = b.y - a.y
        val d2 = dx * dx + dy * dy
        if (d2 >= m2 || d2 <= 1e-6f) return
        val d = sqrt(d2); dx /= d; dy /= d
        val pen = (m - d) / 2f
        a.x -= dx * pen; a.y -= dy * pen; b.x += dx * pen; b.y += dy * pen
        val rv = (b.vx - a.vx) * dx + (b.vy - a.vy) * dy
        if (rv < 0f) {
            // сильный удар отскакивает по-настоящему, слабый гасится — куча шариков спокойно ложится, не дрожит
            val e = if (-rv > 60f) 0.7f else 0.08f
            val imp = -rv * (1f + e) * 0.5f
            a.vx -= dx * imp; a.vy -= dy * imp; b.vx += dx * imp; b.vy += dy * imp
            noteHit(-rv, (a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
        }
    }

    private fun walls(a: Body) {
        for (w in wx.indices) {
            val t = (((a.x - wx[w]) * ex[w] + (a.y - wy[w]) * ey[w]) * inv[w]).coerceIn(0f, 1f)
            val px = wx[w] + ex[w] * t; val py = wy[w] + ey[w] * t
            var dx = a.x - px; var dy = a.y - py
            val d2 = dx * dx + dy * dy
            if (d2 < BODY_R * BODY_R) {
                val d = sqrt(d2)
                if (d > 1e-4f) { dx /= d; dy /= d } else { dx = 0f; dy = -1f }
                a.x = px + dx * BODY_R; a.y = py + dy * BODY_R
                val vn = a.vx * dx + a.vy * dy
                if (vn < 0f) {
                    val e = if (-vn > 90f) 0.55f else 0.12f
                    a.vx -= (1f + e) * vn * dx; a.vy -= (1f + e) * vn * dy; a.vx *= 0.99f
                    noteHit(-vn * 0.8f, a.x, a.y)
                }
            }
        }
    }

    /** Шарик, лежащий на воротах воронки, выпускается на свободную ячейку конвейера. Возвращает цвет или -1. */
    fun takeGate(): Int {
        var best = -1
        var by = 0f
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (kotlin.math.abs(b.x - 180f) < 24f && b.y > 308f && b.y > by) { best = i; by = b.y }
        }
        return if (best < 0) -1 else bodies.removeAt(best).c
    }
}
