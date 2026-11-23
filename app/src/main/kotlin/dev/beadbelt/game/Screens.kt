package dev.beadbelt.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin

private val SPLASH_BX = floatArrayOf(152f, 216f, 152f)
private val SPLASH_BY = floatArrayOf(-222f, 0f, 222f)

/** Заставка, меню, настройки, список уровней, итоговая карточка и пауза. */
class Screens(private val g: Painter, private val progress: Progress, private val version: String) {
    private val titleTop = LinearGradient(0f, 122f, 0f, 178f, Theme.WHITE, 0xFFBDE4FF.toInt(), Shader.TileMode.CLAMP)
    private val titleBottom = LinearGradient(0f, 192f, 0f, 246f, 0xFFFFF27A.toInt(), 0xFFFF9A1F.toInt(), Shader.TileMode.CLAMP)
    // Заголовок с обводкой и градиентом запечён один раз: текст с обводкой в каждом кадре — самый дорогой вид текста.
    private val title: android.graphics.Bitmap = bakeTitle()

    private fun bakeTitle(): android.graphics.Bitmap {
        val k = g.a.k
        val bmp = android.graphics.Bitmap.createBitmap(Math.ceil(280.0 * k).toInt(), Math.ceil(170.0 * k).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(k, k); c.translate(-40f, -96f)
        g.gradientText(c, "BEAD", 180f, 176f, 68f, titleTop, 0xFF1B2F9E.toInt())
        g.gradientText(c, "BELT", 180f, 244f, 68f, titleBottom, 0xFF8A2A00.toInt())
        return bmp
    }

    private val titlePaint = Paint(Paint.FILTER_BITMAP_FLAG)
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

    fun menu(cv: Canvas, ui: Ui) {
        val t = ui.clock
        if (playKey != progress.unlocked || labelsVer != Lang.version) {
            playKey = progress.unlocked; labelsVer = Lang.version
            playLabel = if (playKey == 1) tr("Играть", "Play") else tr("Играть · уровень $playKey", "Play · level $playKey")
            levelsLabel = tr("Уровни  $playKey/$LEVEL_COUNT", "Levels  $playKey/$LEVEL_COUNT")
        }
        // фон: медленно поднимающиеся шарики и тёплое свечение за заголовком
        for (i in flX.indices) {
            val y = H + 40f - ((t * flV[i] + flP[i]) % (H + 80f))
            val x = flX[i] + sin(t * 0.6f + i) * 12f
            g.marble(cv, x, y, flR[i], i % 6, if (Theme.dark) 110 else 80)
        }
        val gp = 1f + 0.03f * sin(t * 1.3f)
        g.rect.set(180f - 190f * gp, 180f - 150f * gp, 180f + 190f * gp, 180f + 150f * gp)
        cv.drawBitmap(g.a.glow, null, g.rect, g.a.goldGlow)

        g.coinPill(cv, 284f, 36f, progress.coins)
        g.roundButton(cv, Layout.MENU_GEAR_X, Layout.MENU_GEAR_Y, 22f, ui.pr(BTN_SETTINGS))
        g.gear(cv, Layout.MENU_GEAR_X, Layout.MENU_GEAR_Y)

        val bob = sin(t * 1.6f) * 3f
        g.rect.set(40f, 96f + bob, 320f, 266f + bob)
        cv.drawBitmap(title, null, g.rect, titlePaint)
        for (i in sparkX.indices) {                // искры на заголовке
            val a = sin(t * 2.1f + i * 1.9f)
            if (a > 0f) g.glint(cv, sparkX[i], sparkY[i] + bob, 8f + 6f * a, (a * a * 255).toInt())
        }

        // конвейерная лента под прыгающими шариками
        g.p.style = Paint.Style.FILL; g.p.shader = null
        g.p.color = Theme.beltFace; g.rect.set(34f, 330f, 326f, 344f); cv.drawRoundRect(g.rect, 7f, 7f, g.p)
        g.p.color = Theme.alpha(Theme.WHITE, 46)
        for (k in 0 until 15) {
            val x = 40f + (k * 20f + t * 40f) % 300f
            if (x + 8f < 322f) { g.rect.set(x, 334f, x + 8f, 340f); cv.drawRoundRect(g.rect, 2f, 2f, g.p) }
        }
        g.p.style = Paint.Style.STROKE; g.p.strokeWidth = 2f; g.p.color = Theme.troughRim
        g.rect.set(34f, 330f, 326f, 344f); cv.drawRoundRect(g.rect, 7f, 7f, g.p)

        for (i in 0 until 7) {                     // прыгающие шарики
            val ph = t * 3.2f + i * 0.75f
            val bob2 = abs(sin(ph))
            val x = 48f + i * 44f
            g.shadowBlob(cv, x, 322f, 14f - bob2 * 5f, (150 - bob2 * 80).toInt())
            g.marble(cv, x, 298f - bob2 * 22f, 16f, i % 6)
        }

        val pulse = 1f + 0.022f * sin(t * 3.4f)
        cv.save(); cv.translate(180f, (Layout.MENU_PLAY[1] + Layout.MENU_PLAY[3]) / 2f); cv.scale(pulse, pulse)
        val r = Layout.MENU_PLAY; val hh = (r[3] - r[1]) / 2f
        g.button(cv, r[0] - 180f, -hh, r[2] - 180f, hh, playLabel, 0, ui.pr(BTN_PLAY))
        cv.restore()
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
        val msgA = (3.5f - (ui.clock - ui.menuMsgAt)).coerceIn(0f, 1f)
        if (msgA > 0f) g.text(cv, ui.menuMsg, 180f, 586f, 15f, Theme.alpha(Theme.accentText, (msgA * 255).toInt()), Theme.fontMedium)
        brand(cv, 622f)
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

    fun prepareInfo(head: String, body: String) {
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
        g.text(cv, tr("Подсказка", "Hint"), 66f, 175f, 13f, Theme.GOLD, Theme.fontBlack, Paint.Align.LEFT, shadow = false, spacing = 0.08f)
        var y = 206f
        for (line in infoHead) { g.text(cv, line, 44f, y, 17f, Theme.WHITE, Theme.fontBlack, Paint.Align.LEFT, shadow = false); y += 22f }
        y += 2f
        p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.WHITE, 40); cv.drawRect(44f, y, 316f, y + 1.5f, p)
        y += 26f
        for (line in infoBody) { g.text(cv, line, 44f, y, 14f, Theme.alpha(Theme.WHITE, 225), Theme.fontMedium, Paint.Align.LEFT, shadow = false); y += 20f }
        val b = Layout.INFO_OK
        g.button(cv, b[0], b[1], b[2], b[3], tr("Понятно", "Got it"), 0, ui.pr(BTN_INFO_OK))
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
        g.text(cv, tr("Уровни", "Levels"), 180f, 49f, 26f)
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

    fun end(cv: Canvas, game: Game, ui: Ui, reward: Int, last: Boolean, stars: Int) {
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
        g.text(cv, if (won) tr("Готово!", "Done!") else tr("Конвейер забит", "Belt jammed"), 180f, c[1] + 124f, if (won) 32f else 27f, Theme.WHITE)
        val sub = when {
            !won -> tr("Нет подходящих коробок", "No matching boxes")
            last -> tr("Все $LEVEL_COUNT уровней пройдены!", "All $LEVEL_COUNT levels complete!")
            stars >= 3 -> tr("Чисто! Без подсказок", "Clean! No hints used")
            else -> tr("Уровень ${game.level.id} пройден", "Level ${game.level.id} complete")
        }
        g.text(cv, sub, 180f, c[1] + 152f, 15f, Theme.alpha(Theme.WHITE, 200), Theme.fontMedium, shadow = false)
        if (won) {      // награда «набегает»
            val shown = (reward * ((ui.endT - 0.8f) / 0.7f).coerceIn(0f, 1f)).toInt()
            g.coin(cv, 150f, c[1] + 182f, 13f)
            g.text(cv, "+$shown", 170f, c[1] + 190f, 24f, Theme.GOLD, align = Paint.Align.LEFT)
        }
        if (!won && progress.canRevive()) {
            val rv = Layout.END_REVIVE
            g.button(cv, rv[0], rv[1], rv[2], rv[3], tr("Ещё шанс  ·  ${Progress.REVIVE_PRICE}", "Second chance  ·  ${Progress.REVIVE_PRICE}"), 0, ui.pr(BTN_REVIVE))
        }
        val n = Layout.END_NEXT; val m = Layout.END_MENU
        g.button(cv, n[0], n[1], n[2], n[3], if (won && !last) tr("Дальше", "Next") else tr("Ещё раз", "Retry"), 0, ui.pr(BTN_NEXT))
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

    // ---------- как играть ----------

    private val rulesRu = listOf(
        "Нажми на лоток: шарики посыплются" to "в воронку и поедут по конвейеру.",
        "Шарик сам влетит в коробку своего" to "цвета, когда проедет мимо. Нужно по 3.",
        "Золотая скоба: тап запускает сразу" to "оба лотка, связанных стрелкой.",
        "Замок: коробка откроется после того," to "как соберётся первая любая коробка.",
        "Лента забита, и ничего не подходит —" to "проигрыш. Думай, что и когда сыпать.",
        "Лампочка подскажет лучший ход, ракета" to "закроет коробку, «Вернуть» разгрузит ленту.",
        "Звёзды: 3 — без помощи, 2 — до двух" to "раз (подсказки, бустеры), иначе 1.",
    )
    private val rulesEn = listOf(
        "Tap a tray: its beads pour" to "into the funnel and ride the belt.",
        "A bead jumps into the box of its" to "color as it passes by. Each needs 3.",
        "Golden clasp: one tap launches" to "both trays linked by the arrow.",
        "Padlock: the box opens once any" to "other box has been filled.",
        "Belt full and nothing matches —" to "you lose. Plan what to pour and when.",
        "The bulb shows the best move, the" to "rocket fills a box, Return clears the belt.",
        "Stars: 3 — no help, 2 — up to two" to "uses (hints, boosters), otherwise 1.",
    )

    fun help(cv: Canvas, ui: Ui) {
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_BACK)); g.back(cv, Layout.HUD_L_X, Layout.HUD_Y)
        g.text(cv, tr("Как играть", "How to play"), 180f, 49f, 26f)
        val c = floatArrayOf(20f, 86f, 340f, 604f)
        cardFrame(cv, c)
        var y = 128f
        for ((i, r) in (if (Lang.ru) rulesRu else rulesEn).withIndex()) {
            g.p.style = Paint.Style.FILL; g.p.shader = null
            g.p.color = Theme.GOLD; cv.drawCircle(42f, y - 5f, 11f, g.p)
            g.text(cv, "${i + 1}", 42f, y, 14f, Theme.INK, shadow = false)
            g.text(cv, r.first, 62f, y - 4f, 13f, Theme.WHITE, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
            g.text(cv, r.second, 62f, y + 15f, 13f, Theme.WHITE, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
            y += 68f
        }
    }
}
