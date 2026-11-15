package dev.beadbelt.game

import java.util.Random

/**
 * Пул частиц без аллокаций: параллельные массивы фиксированного размера.
 * Виды: искра (с гравитацией), конфетти, расширяющееся кольцо, мерцающий блик.
 */
class Particles(private val cap: Int = 480) {
    companion object {
        const val SPARK = 0
        const val CONFETTI = 1
        const val RING = 2
        const val GLINT = 3
    }

    var n = 0; private set
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val life = FloatArray(cap); val rate = FloatArray(cap)
    val size = FloatArray(cap); val rot = FloatArray(cap); val vr = FloatArray(cap)
    val c = IntArray(cap); val kind = ByteArray(cap)

    private fun add(k: Int, color: Int, px: Float, py: Float, pvx: Float, pvy: Float, r: Float, sz: Float, spin: Float = 0f, a0: Float = 0f) {
        if (n >= cap) return
        val i = n++
        kind[i] = k.toByte(); c[i] = color; x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = 1f; rate[i] = r; size[i] = sz; rot[i] = a0; vr[i] = spin
    }

    fun burst(px: Float, py: Float, color: Int, count: Int, rnd: Random, speed: Float = 90f) {
        repeat(count) {
            val a = rnd.nextFloat() * 6.2831855f
            val v = speed * (0.5f + rnd.nextFloat() * 0.7f)
            add(SPARK, color, px, py, kotlin.math.cos(a) * v, kotlin.math.sin(a) * v - 60f, 1.4f + rnd.nextFloat() * 0.6f, 2.4f + rnd.nextFloat() * 2.4f)
        }
    }

    fun ring(px: Float, py: Float, color: Int) = add(RING, color, px, py, 0f, 0f, 2.3f, 8f)

    fun glint(px: Float, py: Float, color: Int, rnd: Random) =
        add(GLINT, color, px, py, 0f, 0f, 2.2f + rnd.nextFloat(), 4f + rnd.nextFloat() * 4f, 0f, rnd.nextFloat() * 1.5f)

    fun confetti(rnd: Random, colors: Int) {
        add(CONFETTI, rnd.nextInt(colors), rnd.nextFloat() * W, -10f - rnd.nextFloat() * 40f,
            (rnd.nextFloat() - 0.5f) * 90f, 40f + rnd.nextFloat() * 120f, 0.33f + rnd.nextFloat() * 0.12f,
            4f + rnd.nextFloat() * 3f, (rnd.nextFloat() - 0.5f) * 12f, rnd.nextFloat() * 6.28f)
    }

    fun clear() { n = 0 }

    fun update(dt: Float) {
        var i = 0
        while (i < n) {
            life[i] -= rate[i] * dt
            when (kind[i].toInt()) {
                SPARK -> { x[i] += vx[i] * dt; y[i] += vy[i] * dt; vy[i] += 500f * dt }
                CONFETTI -> {
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt; vy[i] += 160f * dt
                    vx[i] *= 1f - 0.9f * dt; rot[i] += vr[i] * dt
                }
                RING -> size[i] += 110f * dt
                GLINT -> rot[i] += dt * 3f
            }
            if (life[i] <= 0f || y[i] > H + 40f) {
                val l = --n
                if (i != l) {
                    x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; life[i] = life[l]; rate[i] = rate[l]
                    size[i] = size[l]; rot[i] = rot[l]; vr[i] = vr[l]; c[i] = c[l]; kind[i] = kind[l]
                }
            } else i++
        }
    }
}
