package dev.beadbelt.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil

/**
 * Всё тяжёлое (градиенты, ящики, стеклянная воронка, лента) рисуется ОДИН раз при смене размера
 * в битмапы с разрешением экрана. В кадре остаются только drawBitmap — это быстро и красиво.
 */
class Assets(scale: Float) {
    companion object {
        const val PAD = 8f          // запас вокруг ящика под тень
        const val EXTRA = 6f        // нижний борт ящика
        const val TRAY_W = 76f
        const val TRAY_H = 84f
        const val BOARD_Y0 = 60f    // верх запечённого поля (воронка + лента)
        const val BOARD_H = 436f
        /** Верх видимых стенок воронки (в координатах физики): ниже низа лотков, чтобы стекло их не касалось. */
        const val FUNNEL_TOP = 248f
    }

    val k = scale.coerceIn(1f, 2f)        // «лёгкое» разрешение запекания: меньше памяти и заливки
    private val white = Theme.WHITE

    private fun bitmap(wLogical: Float, hLogical: Float): Bitmap =
        Bitmap.createBitmap(ceil(wLogical * k).toInt().coerceAtLeast(1), ceil(hLogical * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------- шарики ----------

    val marbleSize = ceil(24f * k).toInt().coerceIn(48, 96)
    val marbles: Array<Bitmap> = Array(COLOR_COUNT) { bakeMarble(it) }

    /** Мягкая тень под шариком. */
    val shadow: Bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also {
        val q = paint()
        q.shader = RadialGradient(24f, 24f, 24f, intArrayOf(Theme.alpha(Theme.BLACK, 120), 0), floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP)
        Canvas(it).drawCircle(24f, 24f, 24f, q)
    }

    /** Белое мягкое пятно: тонируется цветовым фильтром — для боке и свечения. */
    val glow: Bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also {
        val q = paint()
        q.shader = RadialGradient(64f, 64f, 64f, intArrayOf(white, Theme.alpha(white, 90), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        Canvas(it).drawCircle(64f, 64f, 64f, q)
    }

    /** Кисти боке: фильтр SRC_IN красит белое пятно, альфа заложена в цвет (в тёмной теме слабее). */
    val bokeh: Array<Paint> = arrayOf(0x3A9FE0FF, 0x30B58CFF, 0x2EFFFFFF, 0x2860F0FF, 0x28FF9AD5).map { col ->
        val a = ((col ushr 24) * Theme.bokehScale).toInt()
        Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter((col and 0xFFFFFF) or (a shl 24), PorterDuff.Mode.SRC_IN) }
    }.toTypedArray()

    /** Золотое свечение (заставка и меню). */
    val goldGlow: Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(0x66FFC21F, PorterDuff.Mode.SRC_IN) }

    private fun bakeMarble(c: Int): Bitmap {
        val s = marbleSize
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val q = paint()
        val r = s / 2f
        val col = Theme.pal[c]
        // тело: объёмный шар, свет слева сверху
        q.shader = RadialGradient(r * 0.74f, r * 0.62f, r * 1.3f, intArrayOf(col[0], col[1], col[2]), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(r, r, r - 0.5f, q)
        // затемнение к краю
        q.shader = RadialGradient(r, r, r, intArrayOf(0, 0, Theme.alpha(col[3], 150)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(r, r, r - 0.5f, q)
        // отражённый свет снизу (внутри круга)
        val clip = Path().apply { addCircle(r, r, r - 0.5f, Path.Direction.CW) }
        cv.save(); cv.clipPath(clip)
        q.shader = RadialGradient(r, r * 1.85f, r * 0.95f, intArrayOf(Theme.alpha(white, 95), 0), null, Shader.TileMode.CLAMP)
        cv.drawCircle(r, r * 1.85f, r * 0.95f, q)
        cv.restore()
        q.shader = null
        // главный блик и точка
        val rr = RectF(r * 0.38f, r * 0.2f, r * 0.86f, r * 0.5f)
        q.color = Theme.alpha(white, 235); cv.drawOval(rr, q)
        q.color = Theme.alpha(white, 110); cv.drawCircle(r * 1.28f, r * 1.3f, r * 0.08f, q)
        // тонкий контур
        q.style = Paint.Style.STROKE; q.strokeWidth = s * 0.03f; q.color = Theme.alpha(col[3], 120)
        cv.drawCircle(r, r, r - s * 0.015f, q)
        return bmp
    }

    // ---------- ящики ----------

    val tray: Array<Bitmap> = Array(COLOR_COUNT) { bakeCrate(it, TRAY_W, TRAY_H, box = false, dimA = 0) }
    val box: Array<Bitmap> = Array(COLOR_COUNT) { bakeCrate(it, BOX_W, BOX_H, box = true, dimA = 0) }
    /** Запертая коробка: заметно затемнена. */
    val boxDim: Array<Bitmap> = Array(COLOR_COUNT) { bakeCrate(it, BOX_W, BOX_H, box = true, dimA = 130) }
    /** Коробка в очереди: цвет читается чётко, лишь чуть приглушён, чтобы было видно, какая из них активная. */
    val boxNext: Array<Bitmap> = Array(COLOR_COUNT) { bakeCrate(it, BOX_W, BOX_H, box = true, dimA = 30) }
    /** Лоток со скрытым цветом: нейтральный, без значка цвета (вопросительный знак рисует отрисовщик). */
    val trayHidden: Bitmap = bakeCrate(COLOR_COUNT, TRAY_W, TRAY_H, box = false, dimA = 0)
    /** Ждущая в очереди коробка со скрытым цветом: нейтральная, как скрытый лоток. */
    val boxHidden: Bitmap = bakeCrate(COLOR_COUNT, BOX_W, BOX_H, box = true, dimA = 30)

    /** Значок-фигурка цвета: на коробках и лотках цвет читается ещё и по форме (помогает, когда оттенки похожи). */
    private fun glyph(cv: Canvas, c: Int, x: Float, y: Float, r: Float, q: Paint) {
        val pth = Path()
        when (c) {
            0 -> {          // сердце
                pth.moveTo(x, y + r); pth.cubicTo(x - r * 1.5f, y - r * 0.1f, x - r * 0.8f, y - r * 1.0f, x, y - r * 0.35f)
                pth.cubicTo(x + r * 0.8f, y - r * 1.0f, x + r * 1.5f, y - r * 0.1f, x, y + r); pth.close()
            }
            1 -> pth.addCircle(x, y, r * 0.9f, Path.Direction.CW)           // круг
            2 -> { pth.moveTo(x, y - r); pth.lineTo(x + r * 1.0f, y + r * 0.8f); pth.lineTo(x - r * 1.0f, y + r * 0.8f); pth.close() }   // треугольник
            3 -> {          // звезда
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5; val rr = if (i % 2 == 0) r * 1.1f else r * 0.48f
                    val px = x + (Math.cos(a) * rr).toFloat(); val py = y + (Math.sin(a) * rr).toFloat()
                    if (i == 0) pth.moveTo(px, py) else pth.lineTo(px, py)
                }
                pth.close()
            }
            4 -> { pth.moveTo(x, y - r * 1.1f); pth.lineTo(x + r * 0.85f, y); pth.lineTo(x, y + r * 1.1f); pth.lineTo(x - r * 0.85f, y); pth.close() }   // ромб
            5 -> pth.addRoundRect(RectF(x - r * 0.85f, y - r * 0.85f, x + r * 0.85f, y + r * 0.85f), r * 0.25f, r * 0.25f, Path.Direction.CW)           // квадрат
            6 -> { for (i in 0 until 6) { val a = Math.PI / 6 + i * Math.PI / 3; val px = x + (Math.cos(a) * r).toFloat(); val py = y + (Math.sin(a) * r).toFloat(); if (i == 0) pth.moveTo(px, py) else pth.lineTo(px, py) }; pth.close() }   // шестиугольник
            else -> {       // крест
                val t = r * 0.38f
                pth.addRoundRect(RectF(x - r, y - t, x + r, y + t), t * 0.6f, t * 0.6f, Path.Direction.CW)
                pth.addRoundRect(RectF(x - t, y - r, x + t, y + r), t * 0.6f, t * 0.6f, Path.Direction.CW)
            }
        }
        q.style = Paint.Style.FILL; q.shader = null
        q.color = Theme.alpha(Theme.BLACK, 70); cv.save(); cv.translate(0f, 1.1f); cv.drawPath(pth, q); cv.restore()
        q.color = Theme.alpha(white, 235); cv.drawPath(pth, q)
        q.style = Paint.Style.FILL
    }

    /** c == COLOR_COUNT — нейтральный лоток со скрытым цветом. dimA — непрозрачность тёмной вуали (0 — без неё). */
    private fun bakeCrate(c: Int, w: Float, h: Float, box: Boolean, dimA: Int): Bitmap {
        val bmp = bitmap(w + 2 * PAD, h + 2 * PAD + EXTRA)
        val cv = Canvas(bmp)
        cv.scale(k, k); cv.translate(PAD, PAD)
        val col = if (c >= COLOR_COUNT) Theme.hiddenPal else Theme.pal[c]
        val q = paint()
        val rr = RectF()
        val rad = 15f

        // мягкая тень на полу
        q.color = Theme.alpha(Theme.BLACK, 26)
        for (g in 0..3) { rr.set(-g.toFloat(), 8f - g, w + g, h + 8f + g); cv.drawRoundRect(rr, rad + g, rad + g, q) }
        // нижний борт
        q.color = col[2]; rr.set(0f, 6f, w, h + 6f); cv.drawRoundRect(rr, rad, rad, q)
        // лицевая сторона
        rr.set(0f, 0f, w, h)
        q.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Theme.mix(col[1], white, 0.22f), col[1], Theme.mix(col[1], col[2], 0.35f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(rr, rad, rad, q)
        // глянцевый блик сверху
        rr.set(4f, 3f, w - 4f, 3f + h * 0.3f)
        q.shader = LinearGradient(0f, 3f, 0f, 3f + h * 0.3f, Theme.alpha(white, 110), Theme.alpha(white, 8), Shader.TileMode.CLAMP)
        cv.drawRoundRect(rr, 11f, 11f, q)
        q.shader = null
        // контур
        // чёткий тёмный контур: слои стопки лотков не сливаются друг с другом
        q.style = Paint.Style.STROKE; q.strokeWidth = 2.6f; q.color = Theme.alpha(col[3], 235)
        rr.set(1.3f, 1.3f, w - 1.3f, h - 1.3f); cv.drawRoundRect(rr, rad, rad, q)
        q.strokeWidth = 1.2f; q.color = Theme.alpha(white, 70)
        rr.set(2.9f, 2.9f, w - 2.9f, h - 2.9f); cv.drawRoundRect(rr, rad - 1.6f, rad - 1.6f, q)
        q.style = Paint.Style.FILL

        // полость (колодец)
        val cav = if (box) RectF(w / 2 - 36f, 17f, w / 2 + 36f, 51f) else RectF(w / 2 - 31f, 9f, w / 2 + 31f, 59f)
        val cr = if (box) 13f else 11f
        q.color = Theme.alpha(white, 55); rr.set(cav); rr.offset(0f, 1.6f); cv.drawRoundRect(rr, cr, cr, q)     // свет на нижней кромке
        q.shader = LinearGradient(0f, cav.top, 0f, cav.bottom, Theme.alpha(col[3], 245), Theme.alpha(Theme.mix(col[3], Theme.BLACK, 0.35f), 235), Shader.TileMode.CLAMP)
        cv.drawRoundRect(cav, cr, cr, q)
        q.shader = null
        cv.save()
        cv.clipPath(Path().apply { addRoundRect(cav, cr, cr, Path.Direction.CW) })
        q.shader = LinearGradient(0f, cav.top, 0f, cav.top + 10f, Theme.alpha(Theme.BLACK, 120), 0, Shader.TileMode.CLAMP)
        cv.drawRect(cav.left, cav.top, cav.right, cav.top + 10f, q)      // тень под верхней кромкой
        q.shader = null
        cv.restore()

        if (box) {
            for (i in 0..2) {   // три гнезда под шарики
                val hx = w / 2 + (i - 1) * 22f
                q.color = Theme.alpha(white, 40); cv.drawCircle(hx, 34.9f, 9.7f, q)
                q.color = Theme.alpha(Theme.BLACK, 150); cv.drawCircle(hx, 34f, 9.5f, q)
            }
            q.color = Theme.alpha(white, 26); rr.set(10f, 58f, w - 10f, h - 9f); cv.drawRoundRect(rr, 10f, 10f, q)
            if (c < COLOR_COUNT) glyph(cv, c, w / 2, 74f, 7.4f, q)
        } else {
            q.color = Theme.alpha(white, 30); rr.set(w / 2 - 17f, h - 26f, w / 2 + 17f, h - 10f); cv.drawRoundRect(rr, 8f, 8f, q)   // табличка со значком цвета
            if (c < COLOR_COUNT) glyph(cv, c, w / 2, h - 18f, 5.6f, q)
        }

        if (dimA > 0) {
            q.color = Theme.alpha(0xFF0A1535.toInt(), dimA)
            rr.set(0f, 0f, w, h + 6f); cv.drawRoundRect(rr, rad, rad, q)
        }
        return bmp
    }

    // ---------- стеклянная воронка и лента (статичная часть) ----------

    val board: Bitmap = bakeBoard()

    /** Гнездо ленты: тёмная лунка со светлым ободком — чётко видно, где место для шарика. */
    val pocket: Bitmap = bakePocket()

    private fun bakePocket(): Bitmap {
        val bmp = bitmap(30f, 30f)
        val cv = Canvas(bmp); cv.scale(k, k)
        val q = paint()
        q.color = Theme.alpha(white, 60); cv.drawCircle(15f, 16f, 14f, q)
        q.color = Theme.pocketHole; cv.drawCircle(15f, 15f, 13.6f, q)
        cv.save(); cv.clipPath(Path().apply { addCircle(15f, 15f, 13.6f, Path.Direction.CW) })
        q.shader = LinearGradient(0f, 1f, 0f, 16f, Theme.alpha(Theme.BLACK, 150), 0, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 1f, 30f, 16f, q); q.shader = null
        cv.restore()
        q.style = Paint.Style.STROKE; q.strokeWidth = 1.8f; q.color = 0xD28AA2E6.toInt()
        cv.drawCircle(15f, 15f, 13.6f, q)
        return bmp
    }

    private fun bakeBoard(): Bitmap {
        val bmp = bitmap(W, BOARD_H)
        val cv = Canvas(bmp)
        cv.scale(k, k); cv.translate(0f, -BOARD_Y0)
        val q = paint()
        val rr = RectF()
        val path = Path()

        // панель ленты
        rr.set(8f, 312f, 352f, 472f)
        q.color = Theme.alpha(Theme.BLACK, 70); rr.offset(0f, 6f); cv.drawRoundRect(rr, 28f, 28f, q); rr.offset(0f, -6f)
        q.shader = LinearGradient(0f, 312f, 0f, 472f, Theme.panelTop, Theme.panelBottom, Shader.TileMode.CLAMP)
        cv.drawRoundRect(rr, 28f, 28f, q); q.shader = null
        cv.save(); cv.clipPath(Path().apply { addRoundRect(rr, 28f, 28f, Path.Direction.CW) })
        q.shader = LinearGradient(0f, 312f, 0f, 352f, Theme.alpha(white, 34), 0, Shader.TileMode.CLAMP)
        cv.drawRect(8f, 312f, 352f, 352f, q); q.shader = null
        cv.restore()
        q.style = Paint.Style.STROKE; q.strokeWidth = 3f
        q.shader = LinearGradient(0f, 312f, 0f, 472f, Theme.panelEdgeTop, Theme.panelEdgeBottom, Shader.TileMode.CLAMP)
        cv.drawRoundRect(rr, 28f, 28f, q); q.shader = null

        // гнёзда, через которые шарики уходят в коробки
        q.style = Paint.Style.FILL
        for (i in 0 until SLOTS) {
            val x = 54f + 84f * i
            path.reset(); path.moveTo(x - 19f, 462f); path.lineTo(x + 19f, 462f); path.lineTo(x + 12f, 478f); path.lineTo(x - 12f, 478f); path.close()
            q.color = Theme.slotHole; cv.drawPath(path, q)
            q.style = Paint.Style.STROKE; q.strokeWidth = 1.5f; q.color = Theme.alpha(white, 50); cv.drawPath(path, q); q.style = Paint.Style.FILL
        }

        // лента: жёлоб, металлические борта, полотно
        rr.set(Geo.CX - Geo.A - Geo.R, Geo.CY - Geo.R, Geo.CX + Geo.A + Geo.R, Geo.CY + Geo.R)
        path.reset(); path.addRoundRect(rr, Geo.R, Geo.R, Path.Direction.CW)
        q.style = Paint.Style.STROKE; q.strokeJoin = Paint.Join.ROUND
        q.strokeWidth = 46f; q.color = Theme.trough; cv.drawPath(path, q)
        q.strokeWidth = 40f; q.color = Theme.troughRim; cv.drawPath(path, q)
        q.strokeWidth = 34f; q.color = Theme.troughInner; cv.drawPath(path, q)
        q.strokeWidth = 30f; q.color = Theme.beltFace; cv.drawPath(path, q)
        q.strokeWidth = 1.4f; q.color = Theme.alpha(white, 70)
        for (d in floatArrayOf(18.5f, -18.5f)) {
            rr.set(Geo.CX - Geo.A - Geo.R - d, Geo.CY - Geo.R - d, Geo.CX + Geo.A + Geo.R + d, Geo.CY + Geo.R + d)
            path.reset(); path.addRoundRect(rr, Geo.R + d, Geo.R + d, Path.Direction.CW); cv.drawPath(path, q)
        }

        // световой луч от горловины воронки до ленты: показывает, куда падают шарики
        q.style = Paint.Style.FILL; q.strokeCap = Paint.Cap.BUTT
        val beamTop = 332f - LIFT + 14f
        q.shader = LinearGradient(0f, beamTop, 0f, Geo.CY - Geo.R, Theme.alpha(0xFF9CF0FF.toInt(), 70), 0, Shader.TileMode.CLAMP)
        rr.set(Geo.CX - 9f, beamTop, Geo.CX + 9f, Geo.CY - Geo.R + 4f); cv.drawRoundRect(rr, 8f, 8f, q); q.shader = null

        // ---- воронка: поднята над лентой на LIFT; стенки видны только ниже лотков и не касаются их ----
        // Форма строго симметрична относительно x = 180: тело и горловина — один замкнутый контур,
        // а стенки, блики и ободки рисуются для левой половины и зеркально повторяются справа.
        cv.save(); cv.translate(0f, -LIFT)
        val top = FUNNEL_TOP

        // стеклянное тело — светлее у краёв и голубое свечение у горловины
        path.reset(); path.moveTo(12f, top); path.lineTo(12f, 250f); path.lineTo(171f, 328f); path.lineTo(189f, 328f); path.lineTo(348f, 250f); path.lineTo(348f, top); path.close()
        q.style = Paint.Style.FILL
        q.shader = LinearGradient(0f, top, 0f, 332f, intArrayOf(Theme.glassTop, Theme.glassMid, Theme.alpha(0xFF8FEAFF.toInt(), 70)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawPath(path, q); q.shader = null

        // металлическая горловина (один симметричный контур)
        path.reset(); path.moveTo(150f, 326f); path.lineTo(210f, 326f); path.lineTo(199f, 343f); path.lineTo(161f, 343f); path.close()
        q.shader = LinearGradient(0f, 326f, 0f, 343f, 0xFFD4E0FF.toInt(), 0xFF5A70AC.toInt(), Shader.TileMode.CLAMP)
        cv.drawPath(path, q); q.shader = null
        q.style = Paint.Style.STROKE; q.strokeWidth = 2f; q.strokeJoin = Paint.Join.ROUND; q.color = 0xFF2A3D7C.toInt(); cv.drawPath(path, q)

        // левая половина стенок, потом то же зеркально справа
        for (side in 0..1) {
            cv.save()
            if (side == 1) { cv.translate(W, 0f); cv.scale(-1f, 1f) }
            path.reset(); path.moveTo(12f, top); path.lineTo(12f, 250f); path.lineTo(171f, 328f); path.lineTo(180f, 328f)
            q.style = Paint.Style.STROKE; q.strokeCap = Paint.Cap.ROUND; q.strokeJoin = Paint.Join.ROUND
            q.strokeWidth = 11f; q.color = 0xFF22356F.toInt(); cv.drawPath(path, q)
            q.strokeWidth = 7f; q.color = 0xFF9DB2E8.toInt(); cv.drawPath(path, q)
            q.strokeWidth = 3.2f; q.color = 0xFFE4EDFF.toInt(); cv.save(); cv.translate(-0.8f, -1.2f); cv.drawPath(path, q); cv.restore()
            // световая полоса внутри стекла вдоль наклонной стенки
            q.strokeWidth = 2.4f; q.color = Theme.alpha(white, 80)
            path.reset(); path.moveTo(22f, 258f); path.lineTo(148f, 320f); cv.drawPath(path, q)
            // ободок сверху стенки
            q.style = Paint.Style.FILL; q.strokeCap = Paint.Cap.BUTT
            rr.set(1f, top - 7f, 23f, top + 8f)
            q.shader = LinearGradient(0f, top - 7f, 0f, top + 8f, 0xFFE4EDFF.toInt(), 0xFF6F86C8.toInt(), Shader.TileMode.CLAMP)
            cv.drawRoundRect(rr, 7.5f, 7.5f, q); q.shader = null
            q.style = Paint.Style.STROKE; q.strokeWidth = 1.6f; q.color = 0xFF22356F.toInt(); cv.drawRoundRect(rr, 7.5f, 7.5f, q)
            // заклёпка на горловине
            q.style = Paint.Style.FILL; q.color = Theme.alpha(white, 170); cv.drawCircle(158f, 332f, 1.8f, q)
            cv.restore()
        }

        // свечение у горловины
        q.style = Paint.Style.FILL; q.strokeCap = Paint.Cap.BUTT
        q.shader = RadialGradient(Geo.CX, 340f, 30f, Theme.alpha(0xFF9CF0FF.toInt(), 110), 0, Shader.TileMode.CLAMP)
        cv.drawCircle(Geo.CX, 340f, 30f, q); q.shader = null
        cv.restore()
        return bmp
    }
}
