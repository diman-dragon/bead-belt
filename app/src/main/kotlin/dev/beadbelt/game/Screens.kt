package dev.beadbelt.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val SPLASH_BX = floatArrayOf(152f, 216f, 152f)
private val SPLASH_BY = floatArrayOf(-222f, 0f, 222f)

/** Заставка, меню, настройки, список уровней, итоговая карточка и пауза. */
class Screens(private val g: Painter, private val progress: Progress, private val version: String) {
    // ---------- заголовок «BEAD BELT» ----------
    // Каждая буква запечена в свой битмап в НАСТОЯЩЕМ разрешении экрана (а не в урезанном до 2x, как остальная графика):
    // раньше заголовок растягивался из меньшей картинки и расплывался. Буквы отдельно, чтобы их можно было оживить.

    /** Пикселей экрана на логическую единицу и сдвиг поля: нужны, чтобы ставить буквы ровно на пиксель. */
    private val pxs = min(g.sw / W, g.sh / H)
    private val pox = (g.sw - W * pxs) / 2f
    private val poy = (g.sh - H * pxs) / 2f
    private fun snapX(x: Float) = (Math.round(x * pxs + pox) - pox) / pxs
    private fun snapY(y: Float) = (Math.round(y * pxs + poy) - poy) / pxs

    private class Letter(val bmp: Bitmap, val cx: Float, val base: Float, val hw: Float, val row: Int)
    private val letterAbove = 66f
    private val letterBelow = 24f
    private val letters: Array<Letter> = bakeLetters()

    private fun bakeLetters(): Array<Letter> {
        val size = 68f; val gap = 3f; val pad = 9f
        val px = pxs.coerceIn(1f, 4f)
        val out = ArrayList<Letter>(8)
        for (row in 0..1) {
            val word = if (row == 0) "BEAD" else "BELT"
            val base = if (row == 0) 176f else 244f
            val ws = FloatArray(4) { g.textWidth(word[it].toString(), size, Theme.fontBlack) }
            var x = 180f - (ws.sum() + gap * 3) / 2f
            val fill = if (row == 0) LinearGradient(0f, letterAbove - 54f, 0f, letterAbove + 4f, Theme.WHITE, 0xFFBDE4FF.toInt(), Shader.TileMode.CLAMP)
                       else LinearGradient(0f, letterAbove - 54f, 0f, letterAbove + 4f, 0xFFFFF27A.toInt(), 0xFFFF9A1F.toInt(), Shader.TileMode.CLAMP)
            val edge = if (row == 0) 0xFF1B2F9E.toInt() else 0xFF8A2A00.toInt()
            for (k in 0 until 4) {
                val w = ws[k]
                val bw = w + 2 * pad
                val bmp = Bitmap.createBitmap(ceil(bw * px).toInt(), ceil((letterAbove + letterBelow) * px).toInt(), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); c.scale(px, px)
                val ch = word[k].toString()
                // Настоящий объём: многослойная экструзия, тёмная боковая грань, контур и металлический блик.
                val depth = if (row == 0) 11 else 13
                for (d in depth downTo 1) {
                    val shade = if (row == 0) 0xFF10205E.toInt() else 0xFF642000.toInt()
                    g.text(c, ch, pad + w / 2f + d * 0.72f, letterAbove + d * 0.78f,
                        size, shade, Theme.fontBlack, shadow = false)
                }
                g.text(c, ch, pad + w / 2f, letterAbove + 5f, size, 0,
                    outline = if (row == 0) 0xFF101C5C.toInt() else 0xFF642000.toInt())
                g.gradientText(c, ch, pad + w / 2f, letterAbove, size, fill, edge)
                // Верхний световой кант подчёркивает толщину букв на любом масштабе.
                g.text(c, ch, pad + w / 2f - 0.8f, letterAbove - 1.1f, size,
                    Theme.alpha(Theme.WHITE, 42), Theme.fontBlack, shadow = false)
                out.add(Letter(bmp, x + w / 2f, base, bw / 2f, row))
                x += w + gap
            }
        }
        return out.toTypedArray()
    }

    private val letterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // лучи света за заголовком: запекаются один раз, в кадре — только поворот одного битмапа
    private val rays: Bitmap = bakeRays()
    private val raysPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Theme.alpha(Theme.GOLD, if (Theme.dark) 120 else 190), PorterDuff.Mode.SRC_IN)
    }

    private fun bakeRays(): Bitmap {
        val k = g.a.k
        val half = 150f
        val bmp = Bitmap.createBitmap(ceil(2 * half * k).toInt(), ceil(2 * half * k).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(k, k); c.translate(half, half)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.shader = RadialGradient(0f, 0f, half, intArrayOf(Theme.alpha(Theme.WHITE, 120), Theme.alpha(Theme.WHITE, 0)), null, Shader.TileMode.CLAMP)
        val n = 14
        val pth = Path()
        for (i in 0 until n) {
            val a0 = i * 2 * PI / n; val a1 = a0 + PI / n
            pth.reset(); pth.moveTo(0f, 0f)
            pth.lineTo((cos(a0) * half * 1.5).toFloat(), (sin(a0) * half * 1.5).toFloat())
            pth.lineTo((cos(a1) * half * 1.5).toFloat(), (sin(a1) * half * 1.5).toFloat())
            pth.close(); c.drawPath(pth, q)
        }
        return bmp
    }

    private val shinePath = Path()
    private val clipPath = Path()

    private val levelNames = Array(LEVEL_COUNT + 1) { it.toString() }

    private var playKey = -1
    private var labelsVer = -1
    private var playLabel = ""
    private var levelsLabel = ""

    // плавающие шарики на заднем плане меню: x, радиус, скорость подъёма, сдвиг по фазе
    private val flX = floatArrayOf(40f, 300f, 110f, 250f, 330f, 70f, 190f)
    private val flR = floatArrayOf(18f, 30f, 22f, 16f, 24f, 26f, 14f)
    private val flV = floatArrayOf(14f, 9f, 18f, 12f, 16f, 8f, 20f)
    private val flP = floatArrayOf(0f, 90f, 200f, 310f, 420f, 520f, 600f)
    private val sparkX = floatArrayOf(62f, 300f, 238f, 92f, 292f)
    private val sparkY = floatArrayOf(132f, 150f, 104f, 238f, 244f)
    private val brandW = g.textWidth("DataLaw DesignLab", 12f, Theme.fontMedium)

    /** «DataLaw DesignLab» со знаком — подпись материнской компании. */
    private fun brand(cv: Canvas, y: Float) {
        Logo.draw(cv, 180f - brandW / 2f - 14f, y - 4f, 22f, Theme.logoBody)
        g.text(cv, "DataLaw DesignLab", 180f + 12f, y, 12f, Theme.textDim, Theme.fontMedium, shadow = false)
    }

    // ---------- анимации меню ----------

    private fun bounce(t0: Float): Float {
        var t = t0.coerceIn(0f, 1f)
        return when {
            t < 1f / 2.75f -> 7.5625f * t * t
            t < 2f / 2.75f -> { t -= 1.5f / 2.75f; 7.5625f * t * t + 0.75f }
            t < 2.5f / 2.75f -> { t -= 2.25f / 2.75f; 7.5625f * t * t + 0.9375f }
            else -> { t -= 2.625f / 2.75f; 7.5625f * t * t + 0.984375f }
        }
    }

    /** Прогресс появления: 0 до начала `at`, 1 после `at + len`. */
    private fun appear(e: Float, at: Float, len: Float) = ((e - at) / len).coerceIn(0f, 1f)

    // мерцающие звёздочки по всему экрану меню
    private val twX = floatArrayOf(28f, 332f, 60f, 300f, 20f, 340f, 120f, 250f, 176f)
    private val twY = floatArrayOf(150f, 130f, 300f, 270f, 470f, 480f, 560f, 575f, 60f)
    private val twP = floatArrayOf(0f, 1.3f, 2.1f, 0.7f, 3.3f, 1.9f, 2.7f, 0.4f, 3.9f)

    /** Строка «DataLaw DesignLab» со знаком над заголовком: плашка выезжает сверху. */
    private val ribbonText = "DataLaw DesignLab"
    private val ribbonW = g.textWidth(ribbonText, 13f, Theme.fontMedium) * 1.08f

    private fun ribbon(cv: Canvas, e: Float, t: Float) {
        val a = appear(e, 0.05f, 0.45f)
        if (a <= 0f) return
        val w = 24f + 10f + ribbonW + 26f
        val x0 = 180f - w / 2f; val y = 96f - (1f - easeOutBack(a)) * 26f
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, (60 * a).toInt()); g.rect.set(x0, y - 12f + 3f, x0 + w, y + 13f + 3f); cv.drawRoundRect(g.rect, 13f, 13f, p)
        p.color = Theme.alpha(Theme.pillBg, (235 * a).toInt()); g.rect.set(x0, y - 12f, x0 + w, y + 13f); cv.drawRoundRect(g.rect, 13f, 13f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Theme.alpha(Theme.GOLD, (150 * a).toInt()); cv.drawRoundRect(g.rect, 13f, 13f, p)
        Logo.draw(cv, x0 + 22f, y, 20f, Theme.WHITE, 1f, (255 * a).toInt())
        g.text(cv, ribbonText, x0 + 38f, y + 4.6f, 13f, Theme.alpha(Theme.WHITE, (240 * a).toInt()), Theme.fontMedium, Paint.Align.LEFT, shadow = false, spacing = 0.08f)
        // блик пробегает по плашке раз в несколько секунд
        val sh = ((t % 4.5f) - 1f) / 0.9f
        if (sh in 0f..1f) {
            cv.save(); clipPath.reset(); clipPath.addRoundRect(g.rect, 13f, 13f, Path.Direction.CW); cv.clipPath(clipPath)
            p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.WHITE, (70 * a).toInt())
            val sx = x0 - 20f + (w + 40f) * sh
            shinePath.reset(); shinePath.moveTo(sx, y - 14f); shinePath.lineTo(sx + 10f, y - 14f); shinePath.lineTo(sx - 4f, y + 14f); shinePath.lineTo(sx - 14f, y + 14f); shinePath.close()
            cv.drawPath(shinePath, p); cv.restore()
        }
    }

    /** Блик, пробегающий по кнопке: клип по её форме и наклонная светлая полоса. */
    private fun buttonShine(cv: Canvas, l: Float, t: Float, r: Float, b: Float, clock: Float, phase: Float) {
        val u = ((clock + phase) % 3.6f) / 0.8f
        if (u > 1f) return
        val rad = (b - t) * 0.32f
        cv.save()
        g.rect.set(l, t, r, b); clipPath.reset(); clipPath.addRoundRect(g.rect, rad, rad, Path.Direction.CW); cv.clipPath(clipPath)
        g.p.style = Paint.Style.FILL; g.p.shader = null; g.p.color = Theme.alpha(Theme.WHITE, 85)
        val sx = l - 30f + (r - l + 60f) * u; val h = b - t
        shinePath.reset(); shinePath.moveTo(sx, t); shinePath.lineTo(sx + 22f, t); shinePath.lineTo(sx + 22f - h * 0.5f, b); shinePath.lineTo(sx - h * 0.5f, b); shinePath.close()
        cv.drawPath(shinePath, g.p)
        cv.restore()
    }

    fun menu(cv: Canvas, ui: Ui) {
        val t = ui.clock
        val e = t - ui.menuAt                   // сколько секунд назад открыли меню: от этого идут анимации появления
        val allDone = progress.starsOf(LEVEL_COUNT) > 0
        val key = if (allDone) -1 else progress.unlocked
        if (playKey != key || labelsVer != Lang.version) {
            playKey = key; labelsVer = Lang.version
            playLabel = when {
                allDone -> tr("Все уровни пройдены", "All levels complete")
                key == 1 -> tr("Играть", "Play")
                else -> tr("Играть · уровень $key", "Play · level $key")
            }
            levelsLabel = if (allDone) tr("Уровни  $LEVEL_COUNT/$LEVEL_COUNT ★", "Levels  $LEVEL_COUNT/$LEVEL_COUNT ★")
                          else tr("Уровни  $key/$LEVEL_COUNT", "Levels  $key/$LEVEL_COUNT")
        }
        // фон: медленно поднимающиеся шарики
        for (i in flX.indices) {
            val y = H + 40f - ((t * flV[i] + flP[i]) % (H + 80f))
            val x = flX[i] + sin(t * 0.6f + i) * 12f
            g.marble(cv, x, y, flR[i], i % 6, if (Theme.dark) 110 else 80)
        }
        // мерцающие звёздочки
        for (i in twX.indices) {
            val a = sin(t * 1.7f + twP[i] * 2.3f)
            if (a > 0.1f) g.glint(cv, twX[i], twY[i], 5f + 5f * a, (a * a * (if (Theme.dark) 200 else 255)).toInt())
        }
        // тёплое свечение и вращающиеся лучи за заголовком
        val glowA = appear(e, 0f, 0.8f)
        val gp = (1f + 0.03f * sin(t * 1.3f)) * (0.6f + 0.4f * glowA)
        g.rect.set(180f - 190f * gp, 180f - 150f * gp, 180f + 190f * gp, 180f + 150f * gp)
        cv.drawBitmap(g.a.glow, null, g.rect, g.a.goldGlow)
        if (glowA > 0f) {
            cv.save(); cv.translate(180f, 186f); cv.rotate(t * 7f); cv.scale(1.35f * glowA, 1.35f * glowA)
            g.rect.set(-150f, -150f, 150f, 150f); cv.drawBitmap(rays, null, g.rect, raysPaint)
            cv.restore()
        }

        g.coinPill(cv, 284f, 36f, progress.coins)
        g.roundButton(cv, Layout.MENU_GEAR_X, Layout.MENU_GEAR_Y, 22f, ui.pr(BTN_SETTINGS))
        cv.save(); cv.translate(Layout.MENU_GEAR_X, Layout.MENU_GEAR_Y); cv.rotate(t * 22f); g.gear(cv, 0f, 0f); cv.restore()

        ribbon(cv, e, t)

        // заголовок: буквы по очереди падают и пружинят, потом плавно «дышат» волной, а раз в несколько секунд по ним бежит прыжок
        for (i in letters.indices) {
            val l = letters[i]
            val u = appear(e, 0.18f + (i % 4) * 0.08f + (i / 4) * 0.22f, 0.7f)
            if (u <= 0f) continue
            var dy = -(1f - bounce(u)) * 190f
            if (u >= 1f) {
                dy += sin(t * 2.3f + i * 0.7f + l.row * 1.1f) * 2.2f
                val ph = (t * 5.5f) % 38f - i * 1.2f
                if (ph in 0f..PI.toFloat()) dy -= sin(ph) * 9f
            }
            letterPaint.alpha = (min(1f, u * 5f) * 255).toInt()
            val top = snapY(l.base - letterAbove + dy)
            val left = snapX(l.cx - l.hw)
            g.rect.set(left, top, left + l.hw * 2f, top + letterAbove + letterBelow)
            cv.drawBitmap(l.bmp, null, g.rect, letterPaint)
        }
        // блеск, пробегающий по буквам: сперва верхнее слово, потом нижнее
        val sw0 = (t % 5.2f) / 1.0f
        if (e > 1.3f && sw0 < 2f) {
            val row = sw0.toInt(); val f = sw0 - row
            g.glint(cv, 70f + 220f * f, 150f + row * 68f + sin(f * 6.28f) * 4f, 9f + 5f * sin(f * 3.14f), (sin(f * 3.14f) * 255).toInt())
        }
        for (i in sparkX.indices) {                // искры на заголовке
            val a = sin(t * 2.1f + i * 1.9f)
            if (a > 0f && e > 1.2f) g.glint(cv, sparkX[i], sparkY[i], 8f + 6f * a, (a * a * 255).toInt())
        }

        for (i in 0 until 7) {                     // прыгающие шарики: появляются по очереди, потом скачут волной
            val ua = appear(e, 0.6f + i * 0.07f, 0.4f)
            if (ua <= 0f) continue
            val ph = t * 3.2f + i * 0.75f
            val bob2 = abs(sin(ph))
            val x = 48f + i * 44f
            val sc = easeOutBack(ua)
            g.shadowBlob(cv, x, 322f, (14f - bob2 * 5f) * sc, ((150 - bob2 * 80) * ua).toInt())
            g.marble(cv, x, 298f - bob2 * 22f - (1f - ua) * 60f, 16f * sc, (i + (t / 2.4f).toInt()) % 6)
        }

        // кнопки стоят на своих местах с первого кадра (без выезда и пульса), по главной бегает блик
        val r = Layout.MENU_PLAY
        g.button(cv, r[0], r[1], r[2], r[3], playLabel, 0, ui.pr(BTN_PLAY))
        buttonShine(cv, r[0], r[1], r[2], r[3], t, 0.6f)
        val l = Layout.MENU_LEVELS
        g.button(cv, l[0], l[1], l[2], l[3], levelsLabel, 1, ui.pr(BTN_LEVELS))
        val ry = Layout.MENU_SOUND_Y
        g.roundButton(cv, Layout.MENU_SOUND_X, ry, 24f, ui.pr(BTN_SOUND))
        g.speaker(cv, Layout.MENU_SOUND_X, ry, progress.sound)
        val ready = progress.giftReady()
        g.roundButton(cv, Layout.MENU_GIFT_X, ry, 24f, ui.pr(BTN_GIFT), ready)
        g.gift(cv, Layout.MENU_GIFT_X, ry + 1f, 1f + (if (ready) 0.07f * sin(t * 5f) else 0f))
        if (ready) {                               // красная точка: подарок ждёт
            g.p.style = Paint.Style.FILL; g.p.shader = null
            g.p.color = Theme.INK; cv.drawCircle(Layout.MENU_GIFT_X + 17f, ry - 17f, 8.5f, g.p)
            g.p.color = Theme.RED; cv.drawCircle(Layout.MENU_GIFT_X + 17f, ry - 17f, 7f, g.p)
        }
        g.roundButton(cv, Layout.MENU_HELP_X, ry, 24f, ui.pr(BTN_HELP))
        g.text(cv, "?", Layout.MENU_HELP_X, ry + 9f, 26f, Theme.WHITE)
        g.roundButton(cv, Layout.MENU_SHOP_X, ry, 24f, ui.pr(BTN_SHOP))
        g.coin(cv, Layout.MENU_SHOP_X, ry, 13f)
        g.p.style = Paint.Style.FILL; g.p.shader = null          // зелёный плюс: пополнить запасы
        g.p.color = Theme.INK; cv.drawCircle(Layout.MENU_SHOP_X + 16f, ry - 16f, 9f, g.p)
        g.p.color = Theme.GREEN; cv.drawCircle(Layout.MENU_SHOP_X + 16f, ry - 16f, 7.5f, g.p)
        g.text(cv, "+", Layout.MENU_SHOP_X + 16f, ry - 11.5f, 14f, Theme.INK, shadow = false)
        val msgA = (3.5f - (ui.clock - ui.menuMsgAt)).coerceIn(0f, 1f)
        if (msgA > 0f) g.text(cv, ui.menuMsg, 180f, 586f, 15f, Theme.alpha(Theme.accentText, (msgA * 255).toInt()), Theme.fontMedium)
        // версия внизу
        g.text(cv, "v$version", 180f, 624f, 13f, Theme.alpha(Theme.text, (140 * appear(e, 1.1f, 0.5f)).toInt()), Theme.fontMedium, shadow = false, spacing = 0.06f)
    }

    // ---------- заставка при запуске ----------

    private fun easeOut(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u * u }

    /** Знак DataLaw «проявляется», шарики падают на свои места, затем подпись студии. */
    fun splash(cv: Canvas, t: Float) {
        val k = 0.21f
        val cy = 236f
        val ga = easeOut(t / 1.2f)
        val gp = 1f + 0.04f * sin(t * 3f)
        g.rect.set(180f - 200f * ga * gp, cy - 200f * ga * gp, 180f + 200f * ga * gp, cy + 200f * ga * gp)
        cv.drawBitmap(g.a.glow, null, g.rect, g.a.goldGlow)

        Logo.draw(cv, 180f, cy, 1000f * k, Theme.logoBody, (t - 0.15f) / 1.0f)
        // три шарика падают на золотую «ленту» и пружинят
        for (i in 0 until 3) {
            val d = 0.9f + i * 0.22f
            val u = ((t - d) / 0.55f).coerceIn(0f, 1f)
            if (u <= 0f) continue
            val bx = 180f + (SPLASH_BX[i] + 54.5f) * k
            val by = cy + SPLASH_BY[i] * k
            val after = t - d - 0.55f
            val bounce = if (after > 0f) maxOf(0f, 1f - after * 2.6f) * abs(sin(after * 11f)) * 9f else 0f
            val yo = -(1f - u) * (1f - u) * 190f - bounce
            g.shadowBlob(cv, bx, by + 18f, 14f * u, (110 * u).toInt())
            g.marble(cv, bx, by + yo, 84f * k, i, (minOf(1f, u * 3f) * 255).toInt())
        }
        if (t > 1.3f) for (i in 0 until 4) {
            val a = sin(t * 3f + i * 1.7f)
            if (a > 0f) g.glint(cv, 180f + (i - 1.5f) * 70f, cy - 120f + (i % 2) * 230f, 7f + 6f * a, (a * a * 255).toInt())
        }
        val ta = ((t - 1.5f) / 0.6f).coerceIn(0f, 1f)
        if (ta > 0f) g.text(cv, "DataLaw DesignLab", 180f, 408f - (1f - ta) * 10f, 24f, Theme.alpha(Theme.text, (ta * 255).toInt()), Theme.fontMedium, shadow = false, spacing = 0.06f)
        val tb = ((t - 2.0f) / 0.5f).coerceIn(0f, 1f)
        if (tb > 0f) g.text(cv, tr("представляет", "presents"), 180f, 436f, 14f, Theme.alpha(Theme.accentText, (tb * 200).toInt()), Theme.fontMedium, shadow = false, spacing = 0.1f)
    }

    // ---------- настройки ----------

    fun settings(cv: Canvas, ui: Ui) {
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_BACK)); g.back(cv, Layout.HUD_L_X, Layout.HUD_Y)
        g.text(cv, tr("Настройки", "Settings"), 180f, 49f, 26f)
        cardFrame(cv, Layout.SET_CARD)
        val lab = Theme.alpha(Theme.WHITE, 230)
        g.text(cv, tr("Язык", "Language"), 36f, 130f, 16f, lab, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
        g.chip(cv, Layout.LANG_CHIPS[0], tr("Авто", "Auto"), progress.langMode == Lang.AUTO, ui.pr(BTN_LANG))
        g.chip(cv, Layout.LANG_CHIPS[1], "Русский", progress.langMode == Lang.RU, ui.pr(BTN_LANG + 1))
        g.chip(cv, Layout.LANG_CHIPS[2], "English", progress.langMode == Lang.EN, ui.pr(BTN_LANG + 2))
        g.text(cv, tr("Тема", "Theme"), 36f, 238f, 16f, lab, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
        g.chip(cv, Layout.THEME_CHIPS[0], tr("Тёмная", "Dark"), Theme.dark, ui.pr(BTN_THEME))
        g.chip(cv, Layout.THEME_CHIPS[1], tr("Светлая", "Light"), !Theme.dark, ui.pr(BTN_THEME + 1))
        g.text(cv, tr("Звук", "Sound"), 36f, 346f, 16f, lab, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
        g.chip(cv, Layout.SOUND_CHIPS[0], tr("Вкл", "On"), progress.sound, ui.pr(BTN_SOUND_ON))
        g.chip(cv, Layout.SOUND_CHIPS[1], tr("Выкл", "Off"), !progress.sound, ui.pr(BTN_SOUND_OFF))
        g.text(cv, "Bead Belt  v$version", 180f, 566f, 14f, Theme.textDim, Theme.fontMedium, shadow = false)
        brand(cv, 598f)
    }

    // ---------- окно «что это за подсказка» ----------

    private var infoHead: List<String> = emptyList()
    private var infoBody: List<String> = emptyList()

    private var infoTag: String? = null
    private var infoOk: String? = null

    /** `tag` и `ok` — подпись над заголовком и текст кнопки; по умолчанию «Подсказка» и «Понятно». */
    fun prepareInfo(head: String, body: String, tag: String? = null, ok: String? = null) {
        infoTag = tag; infoOk = ok
        infoHead = g.wrap(head, 17f, Theme.fontBlack, 264f).take(3)
        infoBody = g.wrap(body, 14f, Theme.fontMedium, 264f).take(7)
    }

    fun info(cv: Canvas, ui: Ui) {
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.overlay, 190); cv.drawRect(-500f, -500f, W + 500f, H + 500f, p)
        val c = Layout.INFO_CARD
        cardFrame(cv, c)
        g.info(cv, 48f, 169f, 1.25f)
        g.text(cv, infoTag ?: tr("Подсказка", "Hint"), 66f, 175f, 13f, Theme.GOLD, Theme.fontBlack, Paint.Align.LEFT, shadow = false, spacing = 0.08f)
        var y = 206f
        for (line in infoHead) { g.text(cv, line, 44f, y, 17f, Theme.WHITE, Theme.fontBlack, Paint.Align.LEFT, shadow = false); y += 22f }
        y += 2f
        p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.WHITE, 40); cv.drawRect(44f, y, 316f, y + 1.5f, p)
        y += 26f
        for (line in infoBody) { g.text(cv, line, 44f, y, 14f, Theme.alpha(Theme.WHITE, 225), Theme.fontMedium, Paint.Align.LEFT, shadow = false); y += 20f }
        val b = Layout.INFO_OK
        g.button(cv, b[0], b[1], b[2], b[3], infoOk ?: tr("Понятно", "Got it"), 0, ui.pr(BTN_INFO_OK))
    }

    // ---------- уровни ----------

    fun levels(cv: Canvas, ui: Ui) {
        val unlocked = progress.unlocked
        cv.save()
        cv.clipRect(0f, Layout.LV_TOP - 10f, W, Layout.LV_BOTTOM)
        val first = ((ui.scroll - 10f) / Layout.LV_PITCH).toInt().coerceAtLeast(0) * Layout.LV_COLS
        val last = minOf(LEVEL_COUNT, first + (Layout.LV_COLS * ((Layout.LV_BOTTOM - Layout.LV_TOP) / Layout.LV_PITCH + 3).toInt()))
        for (i in first until last) card(cv, ui, i, unlocked)
        cv.restore()

        // шапка
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_BACK)); g.back(cv, Layout.HUD_L_X, Layout.HUD_Y)
        if (progress.starsOf(LEVEL_COUNT) > 0) {          // всё пройдено: это видно прямо в шапке
            g.text(cv, tr("Все уровни пройдены!", "All levels complete!"), 180f, 44f, 20f, Theme.GOLD)
            g.text(cv, "★ ${progress.totalStars}/${LEVEL_COUNT * 3}", 180f, 66f, 13f, Theme.alpha(Theme.WHITE, 210), Theme.fontMedium, shadow = false)
        } else g.text(cv, tr("Уровни", "Levels"), 180f, 49f, 26f)
        g.coinPill(cv, 296f, 40f, progress.coins)
    }

    private fun card(cv: Canvas, ui: Ui, i: Int, unlocked: Int) {
        val id = i + 1
        val x = Layout.levelX(i); val y = Layout.levelY(i, ui.scroll)
        if (y > Layout.LV_BOTTOM || y + Layout.LV_H < Layout.LV_TOP - 10f) return
        val p = g.p
        val press = ui.pr(BTN_LEVEL + id)
        val dy = 4f * press
        val cur = id == unlocked
        val done = id < unlocked
        val grow = if (cur) 1f + 0.035f * sin(ui.clock * 4f) else 1f
        cv.save()
        cv.translate(x + Layout.LV_W / 2, y + Layout.LV_H / 2); cv.scale(grow, grow); cv.translate(-Layout.LV_W / 2, -Layout.LV_H / 2)
        p.style = Paint.Style.FILL; p.shader = null
        if (cur) {      // мягкое свечение вокруг текущего уровня
            p.color = Theme.alpha(Theme.GOLD, 120); g.rect.set(-6f, -6f, Layout.LV_W + 6f, Layout.LV_H + 12f); cv.drawRoundRect(g.rect, 20f, 20f, p)
        }
        val face = when { done -> 0xFF2FD060.toInt(); cur -> 0xFFFFD21F.toInt(); else -> Theme.lockedFace }
        val lip = when { done -> 0xFF168A3C.toInt(); cur -> 0xFFC48A00.toInt(); else -> Theme.lockedLip }
        p.color = lip; g.rect.set(0f, 5f, Layout.LV_W, Layout.LV_H + 5f); cv.drawRoundRect(g.rect, 15f, 15f, p)
        p.color = face; g.rect.set(0f, dy, Layout.LV_W, Layout.LV_H + dy); cv.drawRoundRect(g.rect, 15f, 15f, p)
        if (id <= unlocked) {
            p.color = Theme.alpha(Theme.WHITE, 60); g.rect.set(4f, dy + 3f, Layout.LV_W - 4f, Layout.LV_H * 0.5f + dy); cv.drawRoundRect(g.rect, 12f, 12f, p)
            g.text(cv, levelNames[id], Layout.LV_W / 2, Layout.LV_H / 2 + 8f + dy, if (id < 100) 24f else 20f, if (cur) Theme.INK else Theme.WHITE, shadow = !cur)
            if (done) {
                g.check(cv, Layout.LV_W - 12f, 13f + dy, 0.55f, Theme.alpha(Theme.WHITE, 230))
                val st = progress.starsOf(id)
                for (k in 0 until 3) g.star(cv, Layout.LV_W / 2 + (k - 1) * 14f, Layout.LV_H - 7f + dy, 5.5f,
                    if (k < st) Theme.GOLD else Theme.alpha(Theme.BLACK, 70))
            }
        } else {
            g.text(cv, levelNames[id], Layout.LV_W / 2, Layout.LV_H / 2 + 4f, 19f, Theme.lockedText, shadow = false)
            g.lock(cv, Layout.LV_W / 2, Layout.LV_H - 5f, 0.5f)
        }
        cv.restore()
    }

    // ---------- конец уровня ----------

    private fun easeOutBack(t: Float): Float { val c = 1.7f; val u = t - 1f; return 1f + (c + 1f) * u * u * u + c * u * u }

    fun end(cv: Canvas, game: Game, ui: Ui, reward: Int, last: Boolean, stars: Int, prize: String = "", adReady: Boolean = false, doubled: Boolean = false) {
        val a = ((ui.endT - 0.4f) / 0.35f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.overlay, (a * 170).toInt()); cv.drawRect(-500f, -500f, W + 500f, H + 500f, p)

        val won = game.state == State.WON
        val sc = 0.6f + 0.4f * easeOutBack(a)
        val c = Layout.END_CARD
        cv.save(); cv.translate(180f, (c[1] + c[3]) / 2f); cv.scale(sc, sc); cv.translate(-180f, -(c[1] + c[3]) / 2f)
        cardFrame(cv, c)
        p.style = Paint.Style.FILL

        // значок: звезда победы или тревога
        val iy = c[1] + 48f
        if (won) {
            p.color = Theme.alpha(Theme.GOLD, 60); cv.drawCircle(180f, iy, 44f + 3f * sin(ui.clock * 3f), p)
            p.color = Theme.alpha(Theme.GOLD, 80); cv.drawCircle(180f, iy, 34f, p)
            // три звезды: полученные золотые, остальные тёмные; появляются по очереди
            for (k in 0 until 3) {
                val ap = easeOutBack(((ui.endT - 0.5f - k * 0.18f) / 0.3f).coerceIn(0f, 1f))
                val r = (if (k == 1) 28f else 21f) * ap
                if (r > 1f) g.star(cv, 180f + (k - 1) * 58f, iy + (if (k == 1) -2f else 8f), r, if (k < stars) Theme.GOLD else Theme.alpha(0xFF0A1535.toInt(), 200))
            }
        } else {
            p.color = Theme.alpha(Theme.RED, 70); cv.drawCircle(180f, iy, 40f, p)
            p.color = Theme.RED; cv.drawCircle(180f, iy, 29f, p)
            p.color = Theme.alpha(Theme.WHITE, 50); g.rect.set(156f, iy - 27f, 204f, iy - 4f); cv.drawOval(g.rect, p)
            g.text(cv, "!", 180f, iy + 13f, 40f, Theme.WHITE, shadow = false)
        }
        g.text(cv, if (won && last) tr("Все уровни пройдены!", "All levels complete!") else if (won) tr("Готово!", "Done!") else tr("Конвейер забит", "Belt jammed"), 180f, c[1] + 124f, if (won && last) 25f else if (won) 32f else 27f, if (won && last) Theme.GOLD else Theme.WHITE)
        val sub = when {
            !won -> tr("Нет подходящих коробок", "No matching boxes")
            last -> tr("Ты прошёл все $LEVEL_COUNT уровней · ★ ${progress.totalStars}/${LEVEL_COUNT * 3}", "You beat all $LEVEL_COUNT levels · ★ ${progress.totalStars}/${LEVEL_COUNT * 3}")
            stars >= 3 -> tr("Чисто! Без помощи", "Clean! No help used")
            else -> tr("Уровень ${game.level.id} пройден", "Level ${game.level.id} complete")
        }
        val hasPrize = won && prize.isNotEmpty()
        g.text(cv, if (hasPrize) tr("Урок пройден!", "Lesson complete!") else sub, 180f, c[1] + (if (hasPrize) 148f else 152f), 15f, Theme.alpha(Theme.WHITE, 200), Theme.fontMedium, shadow = false)
        if (hasPrize) {     // приз за урок: появляется после звёзд
            val pa = ((ui.endT - 0.9f) / 0.3f).coerceIn(0f, 1f)
            g.text(cv, prize, 180f, c[1] + 180f, if (prize.length > 22) 14f else 17f, Theme.alpha(Theme.GOLD, (255 * pa).toInt()), Theme.fontBlack, shadow = false)
        }
        if (won) {      // награда «набегает»
            val shown = (reward * ((ui.endT - 0.8f) / 0.7f).coerceIn(0f, 1f)).toInt()
            g.coin(cv, 150f, c[1] + (if (hasPrize) 207f else 182f), 13f)
            g.text(cv, "+$shown", 170f, c[1] + (if (hasPrize) 215f else 190f), 24f, Theme.GOLD, align = Paint.Align.LEFT)
        }
        val coinOk = progress.canRevive()
        if (!won && game.canRevive() && (coinOk || adReady)) {
            if (coinOk && adReady) {         // ещё шанс и за монеты, и за видео: кнопка делится пополам
                val l = Layout.END_REVIVE_L; val r = Layout.END_REVIVE_R
                g.button(cv, l[0], l[1], l[2], l[3], tr("Шанс · ${Progress.REVIVE_PRICE}", "Chance · ${Progress.REVIVE_PRICE}"), 0, ui.pr(BTN_REVIVE))
                g.button(cv, r[0], r[1], r[2], r[3], tr("Шанс · видео", "Chance · video"), 1, ui.pr(BTN_AD_REVIVE))
            } else {
                val rv = Layout.END_REVIVE
                if (coinOk) g.button(cv, rv[0], rv[1], rv[2], rv[3], tr("Ещё шанс  ·  ${Progress.REVIVE_PRICE}", "Second chance  ·  ${Progress.REVIVE_PRICE}"), 0, ui.pr(BTN_REVIVE))
                else g.button(cv, rv[0], rv[1], rv[2], rv[3], tr("Ещё шанс  ·  видео", "Second chance  ·  video"), 0, ui.pr(BTN_AD_REVIVE))
            }
        }
        if (won && adReady && !doubled && prize.isEmpty() && reward > 0 && ui.endT > 1.5f) {      // удвоить награду за видео
            val d = Layout.END_DOUBLE
            g.button(cv, d[0], d[1], d[2], d[3], tr("×2 видео", "×2 video"), 1, ui.pr(BTN_AD_DOUBLE))
        }
        val n = Layout.END_NEXT; val m = Layout.END_MENU
        g.button(cv, n[0], n[1], n[2], n[3], if (won && !last) tr("Дальше", "Next") else if (won) tr("К уровням", "Levels") else tr("Ещё раз", "Retry"), 0, ui.pr(BTN_NEXT))
        g.button(cv, m[0], m[1], m[2], m[3], tr("Меню", "Menu"), 1, ui.pr(BTN_MENU))
        cv.restore()
    }

    private fun cardFrame(cv: Canvas, c: FloatArray) {
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 80); g.rect.set(c[0], c[1] + 8f, c[2], c[3] + 8f); cv.drawRoundRect(g.rect, 30f, 30f, p)
        p.color = Theme.cardFace; g.rect.set(c[0], c[1], c[2], c[3]); cv.drawRoundRect(g.rect, 30f, 30f, p)
        p.color = Theme.alpha(Theme.WHITE, 22); g.rect.set(c[0] + 5f, c[1] + 5f, c[2] - 5f, c[1] + (c[3] - c[1]) * 0.35f); cv.drawRoundRect(g.rect, 26f, 26f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Theme.cardEdge; g.rect.set(c[0], c[1], c[2], c[3]); cv.drawRoundRect(g.rect, 30f, 30f, p)
    }

    // ---------- пауза ----------

    fun pause(cv: Canvas, ui: Ui) {
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.overlay, 190); cv.drawRect(-500f, -500f, W + 500f, H + 500f, p)
        g.text(cv, tr("Пауза", "Paused"), 180f, 250f, 40f, Theme.WHITE)
        val b = Layout.PAUSE_BTN
        g.button(cv, b[0], b[1], b[2], b[3], tr("Продолжить", "Resume"), 0, ui.pr(BTN_RESUME))
    }

    // ---------- магазин ----------

    private fun shopIcon(cv: Canvas, icon: Int, x: Float, y: Float) {
        when (icon) {
            ICON_COIN -> g.coin(cv, x, y, 15f)
            ICON_HINT -> g.aid(cv, AID_HINT, x, y, 40f)
            ICON_UNDO -> g.aid(cv, AID_UNDO, x, y, 40f)
            ICON_SHUFFLE -> g.aid(cv, AID_SHUFFLE, x, y, 40f)
            ICON_CLEAR -> g.aid(cv, AID_CLEAR, x, y, 40f)
            ICON_GIFT -> g.gift(cv, x, y, 1.25f)
            else -> {          // «без рекламы»: перечёркнутый AD
                g.text(cv, "AD", x, y + 6f, 17f, Theme.WHITE, shadow = false)
                g.p.style = Paint.Style.STROKE; g.p.shader = null; g.p.strokeWidth = 3.4f; g.p.strokeCap = Paint.Cap.ROUND; g.p.color = Theme.RED
                cv.drawLine(x - 15f, y + 13f, x + 15f, y - 13f, g.p); g.p.strokeCap = Paint.Cap.BUTT
            }
        }
    }

    private fun shopRow(cv: Canvas, ui: Ui, i: Int, icon: Int, title: String, sub: String, right: String, kind: Int) {
        val r = Layout.SHOP_ROWS[i]
        val dy = 3f * ui.pr(BTN_SHOP_ITEM + i)
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 70); g.rect.set(r[0], r[1] + 5f, r[2], r[3] + 5f); cv.drawRoundRect(g.rect, 18f, 18f, p)
        p.color = Theme.cardFace; g.rect.set(r[0], r[1] + dy, r[2], r[3] + dy); cv.drawRoundRect(g.rect, 18f, 18f, p)
        p.color = Theme.alpha(Theme.WHITE, 22); g.rect.set(r[0] + 3f, r[1] + dy + 3f, r[2] - 3f, r[1] + dy + (r[3] - r[1]) * 0.45f); cv.drawRoundRect(g.rect, 15f, 15f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Theme.cardEdge; g.rect.set(r[0], r[1] + dy, r[2], r[3] + dy); cv.drawRoundRect(g.rect, 18f, 18f, p)
        val cy = (r[1] + r[3]) / 2f + dy
        shopIcon(cv, icon, 42f, cy)
        g.text(cv, title, 68f, cy - 3f, 15f, Theme.WHITE, Theme.fontBlack, Paint.Align.LEFT, shadow = false)
        g.text(cv, sub, 68f, cy + 14f, 11.5f, Theme.alpha(Theme.WHITE, 200), Theme.fontMedium, Paint.Align.LEFT, shadow = false)
        g.button(cv, 248f, cy - 15f, 338f, cy + 15f, right, kind, 0f)
    }

    private fun offerTitle(o: Offer) = when (o.sku) {
        SKU_STARTER -> tr("Стартовый набор", "Starter pack")
        SKU_NOADS -> tr("Без рекламы", "No ads")
        SKU_HINTS -> tr("Авторасклад ×${o.prize.aids[AID_HINT]}", "Auto-play ×${o.prize.aids[AID_HINT]}")
        SKU_UNDOS -> tr("Отмены хода ×${o.prize.aids[AID_UNDO]}", "Undos ×${o.prize.aids[AID_UNDO]}")
        SKU_SHUFFLES -> tr("Перемешать ×${o.prize.aids[AID_SHUFFLE]}", "Shuffles ×${o.prize.aids[AID_SHUFFLE]}")
        SKU_CLEARS -> tr("Очистка ленты ×${o.prize.aids[AID_CLEAR]}", "Belt clears ×${o.prize.aids[AID_CLEAR]}")
        else -> tr("${o.prize.coins} монет", "${o.prize.coins} coins")
    }

    private fun offerSub(o: Offer) = when (o.sku) {
        SKU_STARTER -> tr("300 монет и все помощники", "300 coins and every helper")
        SKU_NOADS -> tr("Без рекламы между уровнями", "No ads between levels")
        SKU_COINS_S -> tr("Горсть монет", "A handful of coins")
        SKU_COINS_M -> tr("Выгоднее", "Better value")
        SKU_COINS_L -> tr("Самый выгодный", "Best value")
        SKU_HINTS -> tr("Сам высыпает половину лотков", "Pours half of the trays for you")
        SKU_UNDOS -> tr("Вернуть ход назад", "Take a move back")
        SKU_SHUFFLES -> tr("Новая раздача лотков", "A fresh deal of trays")
        else -> tr("Метла для ленты", "A broom for the belt")
    }

    fun shop(cv: Canvas, ui: Ui, billing: Billing, adReady: Boolean, freeLeft: Int) {
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_BACK)); g.back(cv, Layout.HUD_L_X, Layout.HUD_Y)
        g.text(cv, tr("Магазин", "Shop"), 180f, 49f, 26f)
        g.coinPill(cv, 296f, 40f, progress.coins)
        val msgA = (3.5f - (ui.clock - ui.menuMsgAt)).coerceIn(0f, 1f)
        if (msgA > 0f) g.text(cv, ui.menuMsg, 180f, 76f, 13f, Theme.alpha(Theme.accentText, (msgA * 255).toInt()), Theme.fontMedium, shadow = false)

        val videoSub = when {
            !adReady -> tr("Видео пока недоступно", "No video right now")
            freeLeft <= 0 -> tr("Лимит на сегодня, приходи завтра", "Daily limit reached, come back tomorrow")
            else -> tr("Смотри видео: +${Shop.FREE_COINS} · ещё $freeLeft", "Watch a video: +${Shop.FREE_COINS} · $freeLeft left")
        }
        val videoOn = adReady && freeLeft > 0
        shopRow(cv, ui, 0, ICON_COIN, tr("Бесплатные монеты", "Free coins"), videoSub, if (videoOn) tr("Видео", "Video") else "—", if (videoOn) 0 else 1)
        for ((k, o) in Shop.offers.withIndex()) {
            val price = billing.price(o.sku)
            val owned = progress.owned(o.sku)
            val right = if (owned) tr("Есть", "Owned") else price ?: "…"
            shopRow(cv, ui, k + 1, o.icon, offerTitle(o), offerSub(o), right, if (owned || price == null) 1 else 0)
        }
        val b = Layout.SHOP_RESTORE
        g.button(cv, b[0], b[1], b[2], b[3], tr("Восстановить покупки", "Restore purchases"), 1, ui.pr(BTN_RESTORE))
    }

    // ---------- тестовая реклама (только в отладочных сборках) ----------

    fun fakeAd(cv: Canvas, ui: Ui, t: Float, interstitial: Boolean) {
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(0xFF0A1535.toInt(), 245); cv.drawRect(-500f, -500f, W + 500f, H + 500f, p)
        g.text(cv, tr("Тестовая реклама", "Test ad"), 180f, 250f, 30f, Theme.WHITE)
        g.text(cv, tr("В релизной сборке здесь настоящая", "A real ad plays here in the"), 180f, 290f, 14f, Theme.alpha(Theme.WHITE, 200), Theme.fontMedium, shadow = false)
        g.text(cv, tr("реклама. Эта заглушка денег не приносит.", "release build. This stub earns nothing."), 180f, 312f, 14f, Theme.alpha(Theme.WHITE, 200), Theme.fontMedium, shadow = false)
        val left = 3f - t
        if (left > 0f) g.text(cv, tr("Можно закрыть через ${(left + 0.99f).toInt()}", "You can close it in ${(left + 0.99f).toInt()}"), 180f, 470f, 17f, Theme.GOLD, shadow = false)
        else {
            val b = Layout.AD_CLOSE
            g.button(cv, b[0], b[1], b[2], b[3], if (interstitial) tr("Закрыть", "Close") else tr("Забрать награду", "Claim reward"), 0, ui.pr(BTN_AD_CLOSE))
        }
    }

    // ---------- как играть ----------

    private val rulesRu = listOf(
        "Нажми на лоток: шарики посыплются" to "в воронку и поедут по конвейеру.",
        "Шарик сам влетит в коробку своего" to "цвета, когда проедет мимо. Нужно по 3.",
        "Серый лоток «?»: цвет станет виден," to "когда он выйдет вперёд колонки.",
        "Золотая стрелка между лотками: тап по" to "ведущему запускает и ведомого. Назад нет.",
        "Лоток из двух цветов: шарики обоих" to "цветов ссыпаются на ленту вместе.",
        "Лоток в цепи: откроется, когда соберёшь" to "столько коробок, сколько на значке.",
        "Серая коробка «?»: цвет откроется," to "когда она выйдет вперёд своей стопки.",
        "Лента забита, и ничего не подходит —" to "проигрыш. Думай, что и когда сыпать.",
        "Внизу: авторасклад (палочка), отмена, перемешать" to "лотки, очистить ленту. Открываются уроками.",
    )
    private val rulesEn = listOf(
        "Tap a tray: its beads pour" to "into the funnel and ride the belt.",
        "A bead jumps into the box of its" to "color as it passes by. Each needs 3.",
        "Grey tray with ?: its color shows" to "once it reaches the front of the column.",
        "Golden arrow between trays: tapping" to "the leader launches the follower, never back.",
        "Two-color tray: beads of both colors" to "pour onto the belt together.",
        "Chained tray: opens once you collect" to "as many boxes as the badge says.",
        "Grey box with ?: its color shows" to "once it reaches the front of its stack.",
        "Belt full and nothing matches —" to "you lose. Plan what to pour and when.",
        "Below: auto-play (wand), undo, shuffle trays," to "clear the belt. Unlocked by lessons.",
    )

    fun help(cv: Canvas, ui: Ui) {
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_BACK)); g.back(cv, Layout.HUD_L_X, Layout.HUD_Y)
        g.text(cv, tr("Как играть", "How to play"), 180f, 49f, 26f)
        val c = floatArrayOf(20f, 86f, 340f, 604f)
        cardFrame(cv, c)
        var y = 122f
        for ((i, r) in (if (Lang.ru) rulesRu else rulesEn).withIndex()) {
            g.p.style = Paint.Style.FILL; g.p.shader = null
            g.p.color = Theme.GOLD; cv.drawCircle(42f, y - 5f, 11f, g.p)
            g.text(cv, "${i + 1}", 42f, y, 14f, Theme.INK, shadow = false)
            g.text(cv, r.first, 62f, y - 4f, 13f, Theme.WHITE, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
            g.text(cv, r.second, 62f, y + 15f, 13f, Theme.WHITE, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
            y += 56f
        }
    }
}
