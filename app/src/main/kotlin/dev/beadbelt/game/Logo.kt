package dev.beadbelt.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * Знак DataLaw DesignLab: чёрная «D» и золотой полумесяц. Контуры сняты с исходного логотипа и сведены
 * в гладкие кривые Безье в единицах 0..1000 (центр знака в нуле): рисуется любым цветом и в любом размере.
 */
object Logo {
    // начальная точка, затем тройки (контрольная 1, контрольная 2, конец) для cubicTo
    private val BODY = floatArrayOf(
        -500.0f, -440.6f,
        -446.2f, -482.7f, -154.5f, -456.5f, -80.0f, -441.0f,
        -35.9f, -431.8f, 8.7f, -412.1f, 48.1f, -390.7f,
        68.8f, -379.5f, 95.9f, -368.0f, 96.4f, -345.7f,
        94.6f, -344.7f, 94.8f, -340.7f, 92.5f, -340.7f,
        86.4f, -340.7f, 46.1f, -365.1f, 35.8f, -368.9f,
        -1.1f, -382.5f, -144.2f, -425.8f, -169.9f, -398.7f,
        -38.9f, -277.8f, -44.9f, -171.1f, -44.9f, -0.6f,
        -44.9f, 171.1f, -38.9f, 277.8f, -169.9f, 398.7f,
        -144.2f, 425.8f, -1.1f, 382.5f, 35.8f, 368.9f,
        46.1f, 365.1f, 86.4f, 340.7f, 92.5f, 340.7f,
        94.8f, 340.7f, 94.6f, 344.7f, 96.4f, 345.7f,
        95.9f, 368.0f, 68.8f, 379.5f, 48.1f, 390.7f,
        8.7f, 412.1f, -35.9f, 431.8f, -80.0f, 441.0f,
        -154.5f, 456.5f, -446.2f, 482.7f, -500.0f, 440.6f,
        -463.5f, 430.1f, -415.0f, 429.6f, -377.0f, 415.6f,
        -184.8f, 345.0f, -227.3f, 171.6f, -227.3f, 0.6f,
        -227.3f, -171.6f, -184.8f, -345.0f, -377.0f, -415.6f,
        -415.0f, -429.6f, -463.5f, -430.1f, -499.0f, -440.0f
    )
    private val CRESCENT = floatArrayOf(
        65.4f, -448.3f,
        499.7f, -312.3f, 500.0f, 312.5f, 65.4f, 448.3f,
        67.5f, 446.0f, 60.7f, 441.5f, 62.9f, 439.3f,
        94.5f, 407.5f, 159.9f, 386.3f, 210.8f, 329.0f,
        376.6f, 142.5f, 368.8f, -139.4f, 212.0f, -327.5f,
        160.9f, -388.9f, 96.7f, -405.3f, 62.9f, -439.3f,
        60.7f, -441.5f, 67.5f, -446.0f, 65.4f, -448.3f
    )

    val GOLD_LIGHT = 0xFFF2C14E.toInt()
    val GOLD_DARK = 0xFFB9851A.toInt()

    private fun build(d: FloatArray): Path {
        val p = Path()
        p.moveTo(d[0], d[1])
        var i = 2
        while (i + 5 < d.size) { p.cubicTo(d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4], d[i + 5]); i += 6 }
        p.close()
        return p
    }

    private val body = build(BODY)
    private val crescent = build(CRESCENT)
    private val tmp = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Рисует знак с центром в (cx, cy), size — высота в логических единицах.
     * `bodyColor` — цвет буквы, `appear` 0..1 — появление: буква «проявляется», полумесяц выезжает справа.
     */
    fun draw(cv: Canvas, cx: Float, cy: Float, size: Float, bodyColor: Int, appear: Float = 1f, alpha: Int = 255) {
        val k = size / 1000f
        val a = appear.coerceIn(0f, 1f)
        if (a <= 0f) return
        cv.save()
        cv.translate(cx + 54.5f * k, cy); cv.scale(k, k)
        paint.style = Paint.Style.FILL
        paint.shader = null
        val ea = a * a * (3f - 2f * a)
        paint.color = Theme.alpha(bodyColor, (alpha * minOf(1f, ea * 1.6f)).toInt())
        cv.save(); cv.translate(-60f * (1f - ea), 0f); cv.drawPath(body, paint); cv.restore()
        val b = ((a - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val eb = b * b * (3f - 2f * b)
        paint.shader = LinearGradient(0f, -500f, 0f, 500f, intArrayOf(GOLD_LIGHT, 0xFFE0A52A.toInt(), GOLD_DARK), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = (alpha * minOf(1f, eb * 1.6f)).toInt()
        cv.save(); cv.translate(90f * (1f - eb), 0f); cv.drawPath(crescent, paint); cv.restore()
        paint.shader = null
        cv.restore()
    }
}
