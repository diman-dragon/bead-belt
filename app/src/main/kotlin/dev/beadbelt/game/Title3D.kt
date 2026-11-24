package dev.beadbelt.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Region
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Объёмный заголовок «BEAD / BELT». Каждое слово запекается один раз: мягкая тень, выдавка вниз-вправо (слоями),
 * контур, градиентная грань, скошенные кромки (светлая слева-сверху, тёмная справа-снизу) и глянец.
 * В кадре остаётся дешёвое: покачивание слов, лёгкая пульсация и блик, пробегающий по буквам раз в несколько секунд.
 */
class Title3D(private val k: Float, private val font: Typeface) {
    private class Word(val face: Bitmap, val mask: Bitmap)

    private val bead = bake("BEAD",
        intArrayOf(0xFFFFF096.toInt(), 0xFFFFC828.toInt(), 0xFFE28000.toInt()), 0xFF783400.toInt(), 0xFF964600.toInt(), 0xFF461C00.toInt())
    private val belt = bake("BELT",
        intArrayOf(0xFFC8F8FF.toInt(), 0xFF3CC8F5.toInt(), 0xFF146EDC.toInt()), 0xFF0A2878.toInt(), 0xFF1450AA.toInt(), 0xFF081850.toInt())

    private val dst = RectF(0f, 0f, W, H)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dstIn = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val band = Path().apply { moveTo(0f, 0f); lineTo(34f, 0f); lineTo(8f, H); lineTo(-26f, H); close() }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-26f, 0f, 34f, 0f, intArrayOf(0, 0xD0FFFFFF.toInt(), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    private fun bake(text: String, face: IntArray, edge: Int, sideNear: Int, sideFar: Int): Word {
        val bmp = Bitmap.createBitmap(ceil(W * k).toInt(), ceil(H * k).toInt(), Bitmap.Config.ARGB_8888)
        val msk = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(k, k)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = font; p.textAlign = Paint.Align.LEFT; p.strokeJoin = Paint.Join.ROUND
        p.textSize = 100f
        val size = 100f * 232f / p.measureText(text)          // слово занимает 232 из 300 по ширине
        p.textSize = size
        val x = (W - p.measureText(text)) / 2f - 4f            // чуть левее: выдавка уходит вправо
        val y = BASE
        val path = Path(); p.getTextPath(text, 0, text.length, x, y, path)
        val capTop = y - size * 0.70f

        // мягкая тень под буквами: несколько расширяющихся полупрозрачных проходов
        p.style = Paint.Style.FILL_AND_STROKE
        for (j in 1..6) {
            p.strokeWidth = 4f + j * 3.2f; p.color = Theme.alpha(Theme.BLACK, 20)
            c.save(); c.translate(5f, DEPTH + 8f); c.drawPath(path, p); c.restore()
        }
        // выдавка: от дальнего тёмного слоя к ближнему
        p.strokeWidth = 5f
        for (i in DEPTH downTo 1) {
            p.color = Theme.mix(sideNear, sideFar, i / DEPTH.toFloat())
            c.save(); c.translate(i * 0.5f, i.toFloat()); c.drawPath(path, p); c.restore()
        }
        // контур и градиентная грань
        p.strokeWidth = 6f; p.color = edge; c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, capTop, 0f, y, face, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p); p.shader = null

        // скошенные кромки и глянец рисуются только внутри буквы (обрезка по контуру)
        val shifted = Path()
        c.save(); c.clipPath(path)
        shifted.set(path); shifted.offset(2.4f, 3.2f); c.clipPath(shifted, Region.Op.DIFFERENCE)
        p.color = Theme.alpha(Theme.WHITE, 190); c.drawRect(0f, 0f, W, H, p)
        c.restore()
        c.save(); c.clipPath(path)
        shifted.set(path); shifted.offset(-2.4f, -3.2f); c.clipPath(shifted, Region.Op.DIFFERENCE)
        p.color = Theme.alpha(Theme.BLACK, 110); c.drawRect(0f, 0f, W, H, p)
        c.restore()
        c.save(); c.clipPath(path)
        p.shader = LinearGradient(0f, capTop, 0f, capTop + size * 0.34f, Theme.alpha(Theme.WHITE, 120), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p); p.shader = null
        c.restore()

        // силуэт буквы (с контуром) — маска для блика
        val mc = Canvas(msk); mc.scale(k, k)
        p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = 6f; p.color = Theme.WHITE
        mc.drawPath(path, p)
        return Word(bmp, msk)
    }

    /** Рисует оба слова; (0, top) — верхний левый угол первого слова в логических координатах (ширина слова W). */
    fun draw(cv: Canvas, t: Float, left: Float, top: Float) {
        word(cv, bead, left, top, t, 0f)
        word(cv, belt, left, top + LINE, t, 1.2f)
    }

    private fun word(cv: Canvas, w: Word, left: Float, top: Float, t: Float, ph: Float) {
        val bob = sin(t * 1.6f + ph) * 3f
        val s = 1f + 0.012f * sin(t * 2.2f + ph)
        cv.save()
        cv.translate(left + W / 2f, top + H / 2f + bob); cv.scale(s, s); cv.translate(-W / 2f, -H / 2f)
        cv.drawBitmap(w.face, null, dst, bmpPaint)
        val cyc = (t + ph * 0.18f) % PERIOD
        if (cyc < SWEEP) {                                       // блик бежит слева направо по силуэту букв
            val bx = -50f + cyc / SWEEP * (W + 100f)
            val sc = cv.saveLayer(0f, 0f, W, H, null)
            cv.save(); cv.translate(bx, 0f); cv.drawPath(band, bandPaint); cv.restore()
            cv.drawBitmap(w.mask, null, dst, dstIn)
            cv.restoreToCount(sc)
        }
        cv.restore()
    }

    companion object {
        const val W = 300f
        const val H = 130f
        const val BASE = 78f          // базовая линия слова внутри картинки
        const val DEPTH = 14          // толщина выдавки
        const val LINE = 78f          // расстояние между строками
        const val PERIOD = 4.6f       // раз в сколько секунд пробегает блик
        const val SWEEP = 1.1f        // сколько он бежит
    }
}
