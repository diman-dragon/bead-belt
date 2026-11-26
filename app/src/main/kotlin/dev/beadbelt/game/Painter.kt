package dev.beadbelt.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Кэш строки для числа: перерисовка каждый кадр не создаёт мусор, пока число не изменилось. */
class NumText {
    private var v = Int.MIN_VALUE
    private var w = Int.MIN_VALUE
    private var s = ""
    fun of(n: Int): String { if (n != v) { v = n; s = n.toString() }; return s }
    fun of(n: Int, m: Int): String { if (n != v || m != w) { v = n; w = m; s = "$n/$m" }; return s }
}

/** Рисовалка примитивов (текст, кнопки, значки, фон). Все кисти, шейдеры и пути переиспользуются. */
class Painter(val a: Assets, val sw: Float, val sh: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    val rect = RectF()
    val path = Path()

    // ---------- фон ----------

    // Весь статичный фон (градиент, свет, боке, виньетка) запечён в ОДИН битмап вдвое меньшего размера:
    // раньше это были ~6 полноэкранных слоёв с шейдерами в каждом кадре.
    private val bx = floatArrayOf(0.12f, 0.85f, 0.5f, 0.2f, 0.78f, 0.35f, 0.92f, 0.05f, 0.62f, 0.3f, 0.7f, 0.15f, 0.45f, 0.95f)
    private val by = floatArrayOf(0.08f, 0.15f, 0.3f, 0.45f, 0.5f, 0.68f, 0.75f, 0.82f, 0.92f, 0.22f, 0.38f, 0.6f, 0.05f, 0.4f)
    private val bs = floatArrayOf(0.5f, 0.38f, 0.62f, 0.32f, 0.55f, 0.45f, 0.35f, 0.5f, 0.4f, 0.3f, 0.28f, 0.42f, 0.34f, 0.26f)

    private val bgBitmap: Bitmap = bakeBackground()      // после массивов боке — порядок инициализации важен
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun bakeBackground(): Bitmap {
        val s = 0.5f
        val bmp = Bitmap.createBitmap(ceil(sw * s).toInt(), ceil(sh * s).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.scale(s, s)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = LinearGradient(0f, 0f, 0f, sh, intArrayOf(Theme.bgTop, Theme.bgMid, Theme.bgBottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, sw, sh, q)
        q.shader = RadialGradient(sw / 2, sh * 0.1f, sw * 1.0f, Theme.bgGlow, 0, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, sw, sh, q)
        for (i in bx.indices) {
            val sz = (0.22f + bs[i] * 0.6f) * sw
            rect.set(bx[i] * sw - sz / 2, by[i] * sh - sz / 2, bx[i] * sw + sz / 2, by[i] * sh + sz / 2)
            cv.drawBitmap(a.glow, null, rect, a.bokeh[i % a.bokeh.size])
        }
        q.shader = RadialGradient(sw / 2, sh / 2, maxOf(sw, sh) * 0.75f, intArrayOf(0, 0, Theme.vignette), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, sw, sh, q)
        return bmp
    }

    /** Фон — один статичный битмап (никакой анимации: максимум плавности). */
    fun background(cv: Canvas, t: Float) {
        rect.set(0f, 0f, sw, sh)
        cv.drawBitmap(bgBitmap, null, rect, bgPaint)
    }

    // ---------- текст ----------

    fun text(
        cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = Theme.text,
        font: Typeface = Theme.fontBlack, align: Paint.Align = Paint.Align.CENTER, shadow: Boolean = true, outline: Int = 0,
        spacing: Float = 0f,
    ) {
        p.shader = null
        p.textSize = size; p.typeface = font; p.textAlign = align; p.letterSpacing = spacing
        if (shadow && Theme.isLight(color)) { p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.BLACK, 80); cv.drawText(s, x, y + size * 0.07f, p) }
        if (outline != 0) {
            p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.2f; p.strokeJoin = Paint.Join.ROUND; p.color = outline
            cv.drawText(s, x, y, p)
        }
        p.style = Paint.Style.FILL; p.color = color
        cv.drawText(s, x, y, p)
        p.letterSpacing = 0f
    }

    /** Разбивает текст на строки не шире maxW. */
    fun wrap(s: String, size: Float, font: Typeface, maxW: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (w in s.split(' ')) {
            val t = if (line.isEmpty()) w else "$line $w"
            if (line.isEmpty() || textWidth(t, size, font) <= maxW) line = t else { out.add(line); line = w }
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    /** Заголовок с готовым градиентом (шейдер создаёт вызывающий один раз). */
    fun gradientText(cv: Canvas, s: String, x: Float, y: Float, size: Float, fill: Shader, outline: Int) {
        text(cv, s, x, y, size, 0, outline = outline)
        p.style = Paint.Style.FILL
        p.shader = fill
        cv.drawText(s, x, y, p)
        p.shader = null
    }

    fun textWidth(s: String, size: Float, font: Typeface = Theme.fontBlack): Float {
        p.textSize = size; p.typeface = font; return p.measureText(s)
    }

    // ---------- спрайты ----------

    fun marble(cv: Canvas, x: Float, y: Float, r: Float, c: Int, alpha: Int = 255) {
        rect.set(x - r, y - r, x + r, y + r)
        bp.alpha = alpha
        cv.drawBitmap(a.marbles[c], null, rect, bp)
    }

    fun shadowBlob(cv: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        rect.set(x - r, y - r * 0.8f, x + r, y + r * 0.8f)
        bp.alpha = alpha
        cv.drawBitmap(a.shadow, null, rect, bp)
    }

    /** Ящик из запечённого битмапа; (cx, cy) — центр лица, sc — масштаб от центра. */
    fun crate(cv: Canvas, bmp: Bitmap, cx: Float, cy: Float, w: Float, h: Float, sc: Float, alpha: Int) {
        val l = cx - sc * (w / 2 + Assets.PAD)
        val t = cy - sc * (h / 2 + Assets.PAD)
        rect.set(l, t, cx + sc * (w / 2 + Assets.PAD), t + sc * (h + 2 * Assets.PAD + Assets.EXTRA))
        bp.alpha = alpha
        cv.drawBitmap(bmp, null, rect, bp)
    }

    // ---------- кнопки ----------

    /** kind: 0 — главная (зелёная), 1 — вторичная (синяя). press: 0..1 — «вдавленность». */
    fun button(cv: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, kind: Int, press: Float) {
        val face = if (kind == 0) 0xFF34D96A.toInt() else Theme.btn2Face
        val lip = if (kind == 0) 0xFF15853A.toInt() else Theme.btn2Lip
        val dy = 4f * press
        val rad = (b - t) * 0.32f
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 60); rect.set(l, t + 9f, r, b + 9f); cv.drawRoundRect(rect, rad, rad, p)
        p.color = lip; rect.set(l, t + 5f, r, b + 5f); cv.drawRoundRect(rect, rad, rad, p)
        p.color = face; rect.set(l, t + dy, r, b + dy); cv.drawRoundRect(rect, rad, rad, p)
        p.color = Theme.alpha(Theme.WHITE, 46); rect.set(l + 4f, t + dy + 3f, r - 4f, (t + b) / 2f + dy - 1f); cv.drawRoundRect(rect, rad * 0.8f, rad * 0.8f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Theme.alpha(Theme.WHITE, 70)
        rect.set(l + 0.75f, t + dy + 0.75f, r - 0.75f, b + dy - 0.75f); cv.drawRoundRect(rect, rad, rad, p)
        var size = (b - t) * 0.4f
        val tw = textWidth(label, size)
        if (tw > r - l - 28f) size *= (r - l - 28f) / tw          // длинная подпись ужимается, а не вылезает
        text(cv, label, (l + r) / 2f, (t + b) / 2f + size * 0.4f + dy, size, Theme.WHITE)
    }

    /** Круглая «стеклянная» кнопка HUD. */
    fun roundButton(cv: Canvas, x: Float, y: Float, r0: Float, press: Float, enabled: Boolean = true) {
        val r = r0 * (1f - 0.1f * press)
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 50); cv.drawCircle(x, y + 3f, r, p)
        p.color = if (enabled) Theme.roundFace else Theme.roundFaceOff; cv.drawCircle(x, y, r, p)
        p.color = Theme.alpha(Theme.WHITE, 36); rect.set(x - r * 0.72f, y - r * 0.92f, x + r * 0.72f, y - r * 0.05f); cv.drawOval(rect, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.alpha(Theme.WHITE, 110); cv.drawCircle(x, y, r - 1f, p)
    }

    /** Переключатель-плашка в настройках: выбранная — зелёная, остальные — приглушённые. */
    fun chip(cv: Canvas, r: FloatArray, label: String, selected: Boolean, press: Float) {
        val dy = 3f * press
        val face = if (selected) 0xFF34D96A.toInt() else Theme.chipOff
        val lip = if (selected) 0xFF15853A.toInt() else Theme.chipOffLip
        p.style = Paint.Style.FILL; p.shader = null
        p.color = lip; rect.set(r[0], r[1] + 4f, r[2], r[3] + 4f); cv.drawRoundRect(rect, 14f, 14f, p)
        p.color = face; rect.set(r[0], r[1] + dy, r[2], r[3] + dy); cv.drawRoundRect(rect, 14f, 14f, p)
        p.color = Theme.alpha(Theme.WHITE, if (selected) 46 else 18); rect.set(r[0] + 4f, r[1] + dy + 3f, r[2] - 4f, (r[1] + r[3]) / 2f + dy - 1f); cv.drawRoundRect(rect, 11f, 11f, p)
        var size = 16f
        val tw = textWidth(label, size)
        if (tw > r[2] - r[0] - 10f) size *= (r[2] - r[0] - 10f) / tw
        text(cv, label, (r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f + size * 0.36f + dy, size, if (selected) Theme.WHITE else Theme.alpha(Theme.WHITE, 190), shadow = selected)
    }

    // ---------- значки ----------

    private fun vecGear(cv: Canvas) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = Theme.WHITE
        for (i in 0 until 8) {
            cv.save(); cv.rotate(i * 45f)
            rect.set(-2.7f, -14f, 2.7f, -8.5f); cv.drawRoundRect(rect, 1.3f, 1.3f, p)
            cv.restore()
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f
        cv.drawCircle(0f, 0f, 8f, p)
    }

    private fun vecInfo(cv: Canvas) {
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.INK; cv.drawCircle(0f, 0f, 10.5f, p)
        p.color = Theme.GOLD; cv.drawCircle(0f, 0f, 9f, p)
        p.color = Theme.INK
        cv.drawCircle(0f, -4.2f, 1.7f, p)
        rect.set(-1.5f, -1f, 1.5f, 6f); cv.drawRoundRect(rect, 1.3f, 1.3f, p)
    }

    private fun vecCoin(cv: Canvas, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL; p.shader = null
        p.color = 0xFFE59A00.toInt(); cv.drawCircle(x, y + r * 0.12f, r, p)
        p.color = 0xFFFFC21F.toInt(); cv.drawCircle(x, y, r, p)
        p.color = 0xFFFFE27A.toInt(); cv.drawCircle(x - r * 0.1f, y - r * 0.12f, r * 0.72f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.2f; p.color = 0xFFD98E00.toInt(); cv.drawCircle(x, y, r * 0.62f, p)
        p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.WHITE, 170)
        rect.set(x - r * 0.62f, y - r * 0.7f, x - r * 0.12f, y - r * 0.4f); cv.drawOval(rect, p)
    }

    private val coinNum = NumText()

    /** Плашка с монетами по центру (x, y). */
    fun coinPill(cv: Canvas, x: Float, y: Float, amount: Int) {
        val s = coinNum.of(amount)
        val tw = textWidth(s, 19f)
        val w = 52f + tw
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.pillBg; rect.set(x - w / 2, y - 17f, x + w / 2, y + 17f); cv.drawRoundRect(rect, 17f, 17f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Theme.alpha(Theme.WHITE, 70); cv.drawRoundRect(rect, 17f, 17f, p)
        coin(cv, x - w / 2 + 19f, y, 11f)
        text(cv, s, x - w / 2 + 36f, y + 7f, 19f, Theme.WHITE, align = Paint.Align.LEFT)
    }

    private fun vecLock(cv: Canvas, x: Float, y: Float, sc: Float = 1f) {
        cv.save(); cv.translate(x, y); cv.scale(sc, sc)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFFE6ECFF.toInt(); p.shader = null
        rect.set(-8f, -20f, 8f, -2f); cv.drawArc(rect, 180f, 180f, false, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFC21F.toInt()
        rect.set(-13f, -11f, 13f, 13f); cv.drawRoundRect(rect, 5f, 5f, p)
        p.color = 0xFF7A4F00.toInt(); cv.drawCircle(0f, 1f, 3.2f, p)
        cv.restore()
    }

    private fun vecCheck(cv: Canvas, x: Float, y: Float, sc: Float, color: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.4f * sc; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = color; p.shader = null
        path.reset(); path.moveTo(x - 6f * sc, y); path.lineTo(x - 1.5f * sc, y + 5f * sc); path.lineTo(x + 7f * sc, y - 5f * sc)
        cv.drawPath(path, p); p.strokeCap = Paint.Cap.BUTT
    }

    fun star(cv: Canvas, x: Float, y: Float, r: Float, color: Int) {
        path.reset()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) r else r * 0.45f
            val px = x + (cos(a) * rr).toFloat(); val py = y + (sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.style = Paint.Style.FILL; p.shader = null; p.color = color; cv.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.strokeJoin = Paint.Join.ROUND; p.color = Theme.alpha(Theme.BLACK, 60); cv.drawPath(path, p)
    }

    /** Контур значка: мягкая тень снизу и цветная линия поверх, чтобы читалось на любой кнопке. */
    private fun strokeGlyph(cv: Canvas, color: Int, w: Float) {
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = w; p.color = Theme.alpha(Theme.BLACK, 90)
        cv.save(); cv.translate(0f, 1.3f); cv.drawPath(path, p); cv.restore()
        p.color = color; cv.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun fillGlyph(cv: Canvas, color: Int) {
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 90)
        cv.save(); cv.translate(0f, 1.3f); cv.drawPath(path, p); cv.restore()
        p.color = color; cv.drawPath(path, p)
    }

    /** Треугольник-наконечник: кончик (tx, ty), основание на расстоянии `len` назад вдоль (dx, dy) шириной ±half. */
    private fun head(tx: Float, ty: Float, dx: Float, dy: Float, len: Float, half: Float) {
        val bx = tx - dx * len; val by = ty - dy * len
        path.reset(); path.moveTo(tx, ty); path.lineTo(bx - dy * half, by + dx * half); path.lineTo(bx + dy * half, by - dx * half); path.close()
    }

    /** «Отменить»: круговая стрелка против часовой, центр в (0,0). */
    private fun vecUndo(cv: Canvas) {
        val col = 0xFF7FD1FF.toInt()
        rect.set(-9f, -8f, 9f, 10f)                                   // окружность радиуса 9 с центром (0, 1)
        path.reset(); path.arcTo(rect, 200f, 250f); strokeGlyph(cv, col, 3.6f)
        val a = Math.toRadians(200.0)
        val px = (9.0 * cos(a)).toFloat(); val py = (1.0 + 9.0 * sin(a)).toFloat()
        val dx = sin(a).toFloat(); val dy = (-cos(a)).toFloat()       // направление против часовой стрелки
        head(px + dx * 5.5f, py + dy * 5.5f, dx, dy, 6.5f, 5f); fillGlyph(cv, col)
    }

    /** «Перемешать»: две пересекающиеся стрелки, центр в (0,0). */
    private fun vecShuffle(cv: Canvas) {
        val col = 0xFFD4B2FF.toInt()
        path.reset(); path.moveTo(-11f, 5f); path.lineTo(-4f, 5f); path.lineTo(4f, -5f); path.lineTo(7f, -5f); strokeGlyph(cv, col, 3.4f)
        path.reset(); path.moveTo(-11f, -5f); path.lineTo(-4f, -5f); path.lineTo(4f, 5f); path.lineTo(7f, 5f); strokeGlyph(cv, col, 3.4f)
        head(13f, -5f, 1f, 0f, 6f, 4.6f); fillGlyph(cv, col)
        head(13f, 5f, 1f, 0f, 6f, 4.6f); fillGlyph(cv, col)
    }

    /** «Очистить ленту»: метла со звёздочкой, центр в (0,0). */
    private fun vecClear(cv: Canvas) {
        val col = 0xFF6EE7B7.toInt()
        path.reset(); path.moveTo(10f, -12f); path.lineTo(1.5f, 0.5f); strokeGlyph(cv, col, 3.6f)                    // ручка
        path.reset(); path.moveTo(-1f, -0.5f); path.lineTo(5.5f, 4f); path.lineTo(-3f, 13f); path.lineTo(-11f, 9f); path.close(); fillGlyph(cv, col)   // щётка
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.strokeCap = Paint.Cap.ROUND; p.color = Theme.alpha(Theme.BLACK, 110)
        cv.drawLine(-4f, 5f, -7f, 10.5f, p); cv.drawLine(0.5f, 6.5f, -3f, 11.5f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL; p.color = Theme.WHITE                                                            // блеск чистоты
        path.reset(); path.moveTo(10f, 3f); path.quadTo(10f, 6f, 13f, 6f); path.quadTo(10f, 6f, 10f, 9f); path.quadTo(10f, 6f, 7f, 6f); path.quadTo(10f, 6f, 10f, 3f); cv.drawPath(path, p)
    }

    /** Лампочка (подсказка), центр в (0,0). */
    private fun vecBulb(cv: Canvas) {
        p.style = Paint.Style.FILL; p.shader = null
        p.color = 0xFFFFD84A.toInt(); cv.drawCircle(0f, -4f, 9f, p)
        path.reset(); path.moveTo(-5.5f, 3f); path.lineTo(5.5f, 3f); path.lineTo(4f, 9f); path.lineTo(-4f, 9f); path.close(); cv.drawPath(path, p)
        p.color = 0xFFB8BFD6.toInt(); rect.set(-4.5f, 9f, 4.5f, 11.8f); cv.drawRoundRect(rect, 1.2f, 1.2f, p)
        rect.set(-3f, 11.8f, 3f, 14.5f); cv.drawRoundRect(rect, 1.2f, 1.2f, p)
        p.color = Theme.alpha(Theme.WHITE, 170); cv.drawCircle(-3.4f, -7.2f, 2.6f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFFC48A00.toInt()
        path.reset(); path.moveTo(-3f, 3f); path.lineTo(-1.5f, -2.5f); path.lineTo(0f, 0.5f); path.lineTo(1.5f, -2.5f); path.lineTo(3f, 3f); cv.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** Подарочная коробка, центр в (0,0). */
    private fun vecGift(cv: Canvas) {
        p.style = Paint.Style.FILL; p.shader = null
        p.color = 0xFFFF4D6D.toInt(); rect.set(-11f, -1f, 11f, 13f); cv.drawRoundRect(rect, 2.5f, 2.5f, p)
        p.color = 0xFFFF7A90.toInt(); rect.set(-13f, -7f, 13f, 0f); cv.drawRoundRect(rect, 2.5f, 2.5f, p)
        p.color = 0xFFFFD84A.toInt(); rect.set(-2.6f, -7f, 2.6f, 13f); cv.drawRect(rect, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.6f; p.strokeCap = Paint.Cap.ROUND
        rect.set(-9f, -14f, -1f, -6f); cv.drawOval(rect, p)
        rect.set(1f, -14f, 9f, -6f); cv.drawOval(rect, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun vecBack(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.8f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = Theme.WHITE; p.shader = null
        path.reset(); path.moveTo(x + 4f, y - 9f); path.lineTo(x - 5f, y); path.lineTo(x + 4f, y + 9f); cv.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun vecSpeaker(cv: Canvas, x: Float, y: Float, on: Boolean) {
        p.style = Paint.Style.FILL; p.color = Theme.WHITE; p.shader = null
        path.reset(); path.moveTo(x - 9f, y - 4f); path.lineTo(x - 4f, y - 4f); path.lineTo(x + 3f, y - 10f)
        path.lineTo(x + 3f, y + 10f); path.lineTo(x - 4f, y + 4f); path.lineTo(x - 9f, y + 4f); path.close()
        cv.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.6f; p.strokeCap = Paint.Cap.ROUND
        if (on) {
            rect.set(x - 2f, y - 7f, x + 10f, y + 7f); cv.drawArc(rect, -50f, 100f, false, p)
            rect.set(x - 4f, y - 11f, x + 14f, y + 11f); cv.drawArc(rect, -45f, 90f, false, p)
        } else { cv.drawLine(x + 7f, y - 5f, x + 14f, y + 5f, p); cv.drawLine(x + 14f, y - 5f, x + 7f, y + 5f, p) }
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun vecRestart(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.2f; p.color = Theme.WHITE; p.strokeCap = Paint.Cap.ROUND; p.shader = null
        rect.set(x - 9f, y - 9f, x + 9f, y + 9f); cv.drawArc(rect, 40f, 280f, false, p)
        p.style = Paint.Style.FILL
        path.reset(); path.moveTo(x + 9f, y - 12f); path.lineTo(x + 14f, y - 1f); path.lineTo(x + 3f, y - 2f); path.close()
        cv.drawPath(path, p); p.strokeCap = Paint.Cap.BUTT
    }

    // ---------- полосы ----------

    fun progressBar(cv: Canvas, l: Float, t: Float, r: Float, b: Float, frac: Float) {
        val rad = (b - t) / 2f
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.track; rect.set(l, t, r, b); cv.drawRoundRect(rect, rad, rad, p)
        if (frac > 0f) {
            val fr = maxOf(b - t, (r - l) * frac)
            p.color = 0xFF2FD060.toInt(); rect.set(l, t, l + fr, b); cv.drawRoundRect(rect, rad, rad, p)
            p.color = Theme.alpha(Theme.WHITE, 80); rect.set(l + 3f, t + 2f, l + fr - 3f, t + (b - t) * 0.5f); cv.drawRoundRect(rect, rad * 0.6f, rad * 0.6f, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Theme.alpha(Theme.WHITE, 80)
        rect.set(l, t, r, b); cv.drawRoundRect(rect, rad, rad, p)
    }

    // ---------- запечённые значки ----------
    // Значки раньше рисовались Path-ами, которые пересоздавались каждый кадр, — GPU не может их кэшировать.
    // Теперь они один раз запекаются в маленькие битмапы и рисуются через drawBitmap.

    private class Icon(val bmp: Bitmap, val hw: Float, val hh: Float)

    private fun bake(hw: Float, hh: Float, draw: (Canvas) -> Unit): Icon {
        val bmp = Bitmap.createBitmap(ceil(2 * hw * a.k).toInt(), ceil(2 * hh * a.k).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(a.k, a.k); c.translate(hw, hh)
        draw(c)
        return Icon(bmp, hw, hh)
    }

    private fun put(cv: Canvas, ic: Icon, x: Float, y: Float, sc: Float = 1f, alpha: Int = 255) {
        rect.set(x - ic.hw * sc, y - ic.hh * sc, x + ic.hw * sc, y + ic.hh * sc)
        bp.alpha = alpha
        cv.drawBitmap(ic.bmp, null, rect, bp)
    }

    private val icGear = bake(15f, 15f) { vecGear(it) }
    private val icInfo = bake(11.5f, 11.5f) { vecInfo(it) }
    private val icSpeakerOn = bake(18f, 14f) { vecSpeaker(it, 0f, 0f, true) }
    private val icSpeakerOff = bake(18f, 14f) { vecSpeaker(it, 0f, 0f, false) }
    private val icRestart = bake(17f, 17f) { vecRestart(it, 0f, 0f) }
    private val icBack = bake(10f, 12f) { vecBack(it, 0f, 0f) }
    private val icUndo = bake(15f, 15f) { vecUndo(it) }
    private val icShuffle = bake(16f, 14f) { vecShuffle(it) }
    private val icClear = bake(15f, 16f) { vecClear(it) }
    private val icBulb = bake(14f, 18f) { vecBulb(it) }
    private val icGift = bake(16f, 17f) { vecGift(it) }
    private val icLock = bake(16f, 24f) { vecLock(it, 0f, 0f, 1f) }
    private val icCheck = bake(12f, 10f) { vecCheck(it, 0f, 0f, 1f, Theme.WHITE) }
    private val icCoin = bake(18f, 18f) { vecCoin(it, 0f, 0f, 16f) }
    private val icLinkR = bake(18f, 16f) { vecLink(it, true) }
    private val icLinkL = bake(18f, 16f) { vecLink(it, false) }
    private val icGlint = bake(10f, 10f) { c ->
        p.style = Paint.Style.FILL; p.shader = null; p.color = Theme.WHITE
        path.reset(); path.moveTo(0f, -10f); path.quadTo(0f, 0f, 10f, 0f); path.quadTo(0f, 0f, 0f, 10f); path.quadTo(0f, 0f, -10f, 0f); path.quadTo(0f, 0f, 0f, -10f)
        c.drawPath(path, p)
    }

    /**
     * Единственный значок связи между лотками: золотая «таблетка» с жирной стрелкой от ведущего лотка к ведомому.
     * Рисуется одним и тем же компонентом для каждой пары, зеркально для левой и правой стороны.
     */
    private fun vecLink(c: Canvas, right: Boolean) {
        val d = if (right) 1f else -1f
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 70); rect.set(-15f, -9f, 15f, 15f); c.drawRoundRect(rect, 12f, 12f, p)               // тень
        p.color = 0xFF5A3A00.toInt(); rect.set(-15f, -12f, 15f, 12f); c.drawRoundRect(rect, 12f, 12f, p)                        // тёмный борт
        p.shader = LinearGradient(0f, -10f, 0f, 10f, 0xFFFFE27A.toInt(), 0xFFF2A100.toInt(), Shader.TileMode.CLAMP)
        rect.set(-13f, -10f, 13f, 10f); c.drawRoundRect(rect, 10f, 10f, p); p.shader = null                                      // золотая лицевая сторона
        p.color = Theme.alpha(Theme.WHITE, 120); rect.set(-9.5f, -8.2f, 9.5f, -3f); c.drawRoundRect(rect, 3f, 3f, p)           // блик
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.6f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFF5A3A00.toInt()
        c.drawLine(-7f * d, 0f, 5.5f * d, 0f, p)
        path.reset(); path.moveTo(0.5f * d, -5f); path.lineTo(7f * d, 0f); path.lineTo(0.5f * d, 5f); c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    fun gear(cv: Canvas, x: Float, y: Float) = put(cv, icGear, x, y)
    fun info(cv: Canvas, x: Float, y: Float, sc: Float = 1f, alpha: Int = 255) = put(cv, icInfo, x, y, sc, alpha)
    fun speaker(cv: Canvas, x: Float, y: Float, on: Boolean) = put(cv, if (on) icSpeakerOn else icSpeakerOff, x, y)
    fun restart(cv: Canvas, x: Float, y: Float) = put(cv, icRestart, x, y)
    fun back(cv: Canvas, x: Float, y: Float) = put(cv, icBack, x, y)
    fun undo(cv: Canvas, x: Float, y: Float, sc: Float = 1f) = put(cv, icUndo, x, y, sc)
    fun shuffle(cv: Canvas, x: Float, y: Float, sc: Float = 1f) = put(cv, icShuffle, x, y, sc)
    fun clear(cv: Canvas, x: Float, y: Float, sc: Float = 1f) = put(cv, icClear, x, y, sc)
    fun bulb(cv: Canvas, x: Float, y: Float, sc: Float = 1f, alpha: Int = 255) = put(cv, icBulb, x, y, sc, alpha)
    fun gift(cv: Canvas, x: Float, y: Float, sc: Float = 1f) = put(cv, icGift, x, y, sc)
    fun lock(cv: Canvas, x: Float, y: Float, sc: Float = 1f) = put(cv, icLock, x, y, sc)
    fun check(cv: Canvas, x: Float, y: Float, sc: Float, color: Int) = put(cv, icCheck, x, y, sc, color ushr 24)
    fun coin(cv: Canvas, x: Float, y: Float, r: Float) = put(cv, icCoin, x, y, r / 16f)
    /** Гнездо ленты радиусом r. */
    fun pocket(cv: Canvas, x: Float, y: Float, r: Float) {
        val h = r * 15f / 14f
        rect.set(x - h, y - h, x + h, y + h)
        bp.alpha = 255
        cv.drawBitmap(a.pocket, null, rect, bp)
    }
    /** Значок связи между двумя лотками (стрелка от ведущего к ведомому); (x, y) — его центр. */
    fun linkBadge(cv: Canvas, x: Float, y: Float, right: Boolean, sc: Float, alpha: Int = 255) = put(cv, if (right) icLinkR else icLinkL, x, y, sc, alpha)
    fun glint(cv: Canvas, x: Float, y: Float, arm: Float, alpha: Int) = put(cv, icGlint, x, y, arm / 10f, alpha)
}
