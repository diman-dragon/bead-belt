package dev.beadbelt.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin

/** Меню, список уровней, итоговая карточка и пауза. */
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
    private var playLabel = ""
    private var levelsLabel = ""

    // ---------- меню ----------

    fun menu(cv: Canvas, ui: Ui) {
        val t = ui.clock
        if (playKey != progress.unlocked) {
            playKey = progress.unlocked
            playLabel = if (playKey == 1) "Играть" else "Играть · уровень $playKey"
            levelsLabel = "Уровни  $playKey/$LEVEL_COUNT"
        }
        g.coinPill(cv, 284f, 36f, progress.coins)

        val bob = sin(t * 1.6f) * 3f
        g.rect.set(40f, 96f + bob, 320f, 266f + bob)
        cv.drawBitmap(title, null, g.rect, titlePaint)

        for (i in 0 until 7) {                     // прыгающие шарики
            val ph = t * 3.2f + i * 0.75f
            val bob = abs(sin(ph))
            val x = 48f + i * 44f
            g.shadowBlob(cv, x, 322f, 14f - bob * 5f, (150 - bob * 80).toInt())
            g.marble(cv, x, 298f - bob * 22f, 16f, i % 6)
        }

        val pulse = 1f + 0.022f * sin(t * 3.4f)
        cv.save(); cv.translate(180f, (Layout.MENU_PLAY[1] + Layout.MENU_PLAY[3]) / 2f); cv.scale(pulse, pulse)
        val r = Layout.MENU_PLAY; val hh = (r[3] - r[1]) / 2f
        g.button(cv, r[0] - 180f, -hh, r[2] - 180f, hh, playLabel, 0, ui.pr(BTN_PLAY))
        cv.restore()
        val l = Layout.MENU_LEVELS
        g.button(cv, l[0], l[1], l[2], l[3], levelsLabel, 1, ui.pr(BTN_LEVELS))

        g.roundButton(cv, Layout.MENU_SOUND_X, Layout.MENU_SOUND_Y, 24f, ui.pr(BTN_SOUND))
        g.speaker(cv, Layout.MENU_SOUND_X, Layout.MENU_SOUND_Y, progress.sound)
        g.text(cv, "v$version", 180f, 624f, 13f, Theme.alpha(Theme.WHITE, 130), Theme.fontMedium, shadow = false)
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
        g.text(cv, "Уровни", 180f, 49f, 26f)
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
        val face = when { done -> 0xFF2FD060.toInt(); cur -> 0xFFFFD21F.toInt(); else -> Theme.alpha(0xFF0F2060.toInt(), 150) }
        val lip = when { done -> 0xFF168A3C.toInt(); cur -> 0xFFC48A00.toInt(); else -> Theme.alpha(0xFF08123C.toInt(), 170) }
        p.color = lip; g.rect.set(0f, 5f, Layout.LV_W, Layout.LV_H + 5f); cv.drawRoundRect(g.rect, 15f, 15f, p)
        p.color = face; g.rect.set(0f, dy, Layout.LV_W, Layout.LV_H + dy); cv.drawRoundRect(g.rect, 15f, 15f, p)
        if (id <= unlocked) {
            p.color = Theme.alpha(Theme.WHITE, 60); g.rect.set(4f, dy + 3f, Layout.LV_W - 4f, Layout.LV_H * 0.5f + dy); cv.drawRoundRect(g.rect, 12f, 12f, p)
            g.text(cv, levelNames[id], Layout.LV_W / 2, Layout.LV_H / 2 + 8f + dy, if (id < 100) 24f else 20f, if (cur) Theme.INK else Theme.WHITE, shadow = !cur)
            if (done) g.check(cv, Layout.LV_W - 12f, 13f + dy, 0.55f, Theme.alpha(Theme.WHITE, 230))
        } else {
            g.text(cv, levelNames[id], Layout.LV_W / 2, Layout.LV_H / 2 + 4f, 19f, Theme.alpha(Theme.WHITE, 90), shadow = false)
            g.lock(cv, Layout.LV_W / 2, Layout.LV_H - 5f, 0.5f)
        }
        cv.restore()
    }

    // ---------- конец уровня ----------

    private fun easeOutBack(t: Float): Float { val c = 1.7f; val u = t - 1f; return 1f + (c + 1f) * u * u * u + c * u * u }

    fun end(cv: Canvas, game: Game, ui: Ui, reward: Int, last: Boolean) {
        val a = ((ui.endT - 0.4f) / 0.35f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(0xFF050B24.toInt(), (a * 170).toInt()); cv.drawRect(-500f, -500f, W + 500f, H + 500f, p)

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
            g.star(cv, 180f, iy + 2f, 30f, Theme.GOLD)
        } else {
            p.color = Theme.alpha(Theme.RED, 70); cv.drawCircle(180f, iy, 40f, p)
            p.color = Theme.RED; cv.drawCircle(180f, iy, 29f, p)
            p.color = Theme.alpha(Theme.WHITE, 50); g.rect.set(156f, iy - 27f, 204f, iy - 4f); cv.drawOval(g.rect, p)
            g.text(cv, "!", 180f, iy + 13f, 40f, Theme.WHITE, shadow = false)
        }
        g.text(cv, if (won) "Готово!" else "Конвейер забит", 180f, c[1] + 124f, if (won) 32f else 27f)
        val sub = when {
            !won -> "Нет подходящих коробок"
            last -> "Все $LEVEL_COUNT уровней пройдены!"
            else -> "Уровень ${game.level.id} пройден"
        }
        g.text(cv, sub, 180f, c[1] + 152f, 15f, Theme.alpha(Theme.WHITE, 200), Theme.fontMedium, shadow = false)
        if (won) {      // награда «набегает»
            val shown = (reward * ((ui.endT - 0.8f) / 0.7f).coerceIn(0f, 1f)).toInt()
            g.coin(cv, 150f, c[1] + 182f, 13f)
            g.text(cv, "+$shown", 170f, c[1] + 190f, 24f, Theme.GOLD, align = Paint.Align.LEFT)
        }
        val n = Layout.END_NEXT; val m = Layout.END_MENU
        g.button(cv, n[0], n[1], n[2], n[3], if (won && !last) "Дальше" else "Ещё раз", 0, ui.pr(BTN_NEXT))
        g.button(cv, m[0], m[1], m[2], m[3], "Меню", 1, ui.pr(BTN_MENU))
        cv.restore()
    }

    private fun cardFrame(cv: Canvas, c: FloatArray) {
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, 80); g.rect.set(c[0], c[1] + 8f, c[2], c[3] + 8f); cv.drawRoundRect(g.rect, 30f, 30f, p)
        p.color = 0xFF1C2E6E.toInt(); g.rect.set(c[0], c[1], c[2], c[3]); cv.drawRoundRect(g.rect, 30f, 30f, p)
        p.color = Theme.alpha(Theme.WHITE, 22); g.rect.set(c[0] + 5f, c[1] + 5f, c[2] - 5f, c[1] + (c[3] - c[1]) * 0.35f); cv.drawRoundRect(g.rect, 26f, 26f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFF5873BC.toInt(); g.rect.set(c[0], c[1], c[2], c[3]); cv.drawRoundRect(g.rect, 30f, 30f, p)
    }

    // ---------- пауза ----------

    fun pause(cv: Canvas, ui: Ui) {
        val p = g.p
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(0xFF050B24.toInt(), 170); cv.drawRect(-500f, -500f, W + 500f, H + 500f, p)
        g.text(cv, "Пауза", 180f, 250f, 40f)
        val b = Layout.PAUSE_BTN
        g.button(cv, b[0], b[1], b[2], b[3], "Продолжить", 0, ui.pr(BTN_RESUME))
    }

}
