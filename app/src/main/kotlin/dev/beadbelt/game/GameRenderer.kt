package dev.beadbelt.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Рисует игровое поле: HUD, лотки, воронку, ленту, коробки, эффекты и бустеры. */
class GameRenderer(private val g: Painter, private val a: Assets, private val progress: Progress) {
    private companion object {
        const val ROCKET_LABEL = "${Progress.ROCKET_PRICE}"
        const val RETURN_LABEL = "${Progress.RETURN_PRICE}"
        const val HINT_LABEL = "${Progress.HINT_PRICE}"
        const val FALL_T = 0.26f        // сколько шарик падает с горловины на ленту
    }

    private val pt = FloatArray(3)
    private val boardPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val occText = NumText()
    private val doneText = NumText()
    private val rocketText = NumText()
    private val returnText = NumText()
    private val hintText = NumText()
    private var titleId = -1
    private var titleVer = -1
    private var title = ""

    fun draw(cv: Canvas, game: Game, ui: Ui, hint: String, hintAge: Float, hasInfo: Boolean) {
        cv.save()
        if (game.shake > 0f) cv.translate(sin(ui.clock * 95f) * game.shake * 3f, cos(ui.clock * 81f) * game.shake * 2.5f)

        hud(cv, game, ui)
        cv.drawBitmap(a.board, null, g.rect.also { it.set(0f, Assets.BOARD_Y0, W, Assets.BOARD_Y0 + Assets.BOARD_H) }, boardPaint)
        cv.save(); cv.translate(0f, -LIFT)           // лотки и воронка подняты над лентой
        trays(cv, game, ui)
        hopperMarbles(cv, game)
        cv.restore()
        beltMarbles(cv, game, ui)
        boxes(cv, game)
        flyers(cv, game)
        rockets(cv, game)
        effects(cv, game, confetti = false)
        popups(cv, game)
        boosters(cv, game, ui)
        hintPopup(cv, hint, hintAge, hasInfo, ui)
        cv.restore()
    }

    // ---------- HUD ----------

    private fun hud(cv: Canvas, game: Game, ui: Ui) {
        if (titleId != game.level.id || titleVer != Lang.version) { titleId = game.level.id; titleVer = Lang.version; title = tr("Уровень $titleId", "Level $titleId") }
        g.text(cv, title, 180f, 31f, 21f)
        g.progressBar(cv, 80f, 38f, 280f, 54f, game.progress)
        g.text(cv, doneText.of(game.done, game.total), 180f, 50f, 10.5f, Theme.WHITE, shadow = false)
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_SOUND)); g.speaker(cv, Layout.HUD_L_X, Layout.HUD_Y, progress.sound)
        g.roundButton(cv, Layout.HUD_R_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_RESTART)); g.restart(cv, Layout.HUD_R_X, Layout.HUD_Y)
    }

    // ---------- всплывающая подсказка ----------

    /** Время показа подсказки, секунд (последняя секунда — затухание). */
    private val hintShow = 8f
    private val popupLines = ArrayList<String>(3)
    private var popupKey = ""
    private var popupVer = -1
    private var popupInfo = false
    /** Высота окна подсказки сейчас (0 — нечего показывать): по ней игра понимает, куда нажали. */
    var hintH = 0f; private set

    private fun easeOutBack(t: Float): Float { val c = 1.7f; val u = t.coerceIn(0f, 1f) - 1f; return 1f + (c + 1f) * u * u * u + c * u * u }

    /** Переносит текст подсказки по словам в две строки (лишнее — многоточием; полный текст — в окне по значку «i»). */
    private fun layoutHint(hint: String, hasInfo: Boolean) {
        if (hint == popupKey && popupVer == Lang.version && popupInfo == hasInfo) return
        popupKey = hint; popupVer = Lang.version; popupInfo = hasInfo
        popupLines.clear()
        val maxW = Layout.HINT_TEXT_W - (if (hasInfo) 26f else 0f)
        val lines = g.wrap(hint, Layout.HINT_SIZE, Theme.fontMedium, maxW)
        for (k in 0 until minOf(2, lines.size)) popupLines.add(lines[k])
        if (lines.size > 2) {
            var last = popupLines[1]
            while (last.length > 1 && g.textWidth("$last…", Layout.HINT_SIZE, Theme.fontMedium) > maxW) last = last.dropLast(1).trimEnd()
            popupLines[1] = "$last…"
        }
        hintH = if (popupLines.size >= 2) Layout.HINT_H2 else Layout.HINT_H1
    }

    /** Окно подсказки: карточка с лампочкой, текстом в одну-две строки и значком «i»; выезжает сверху и гаснет само. */
    private fun hintPopup(cv: Canvas, hint: String, age: Float, hasInfo: Boolean, ui: Ui) {
        val fade = (hintShow - age).coerceIn(0f, 1f) * min(1f, age / 0.12f)
        if (hint.isEmpty() || fade <= 0f) { hintH = 0f; return }
        layoutHint(hint, hasInfo)
        val h = hintH
        val l = Layout.HINT_X0; val r = Layout.HINT_X1; val top = Layout.HINT_TOP
        val e = easeOutBack(age / 0.32f)
        val cy = top + h / 2f
        val p = g.p
        cv.save()
        cv.translate(180f, cy - (1f - e) * 18f); cv.scale(0.9f + 0.1f * e, 0.9f + 0.1f * e); cv.translate(-180f, -cy)
        p.style = Paint.Style.FILL; p.shader = null
        p.color = Theme.alpha(Theme.BLACK, (90 * fade).toInt()); g.rect.set(l, top + 5f, r, top + h + 5f); cv.drawRoundRect(g.rect, 16f, 16f, p)
        p.color = Theme.alpha(Theme.cardFace, (250 * fade).toInt()); g.rect.set(l, top, r, top + h); cv.drawRoundRect(g.rect, 16f, 16f, p)
        p.color = Theme.alpha(Theme.WHITE, (24 * fade).toInt()); g.rect.set(l + 3f, top + 3f, r - 3f, top + h * 0.48f); cv.drawRoundRect(g.rect, 13f, 13f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.color = Theme.alpha(Theme.cardEdge, (255 * fade).toInt()); g.rect.set(l, top, r, top + h); cv.drawRoundRect(g.rect, 16f, 16f, p)
        // лампочка в золотом круге
        val pulse = 0.5f + 0.5f * sin(ui.clock * 3.2f)
        p.style = Paint.Style.FILL
        p.color = Theme.alpha(Theme.GOLD, ((50 + 40 * pulse) * fade).toInt()); cv.drawCircle(Layout.HINT_X0 + 24f, cy, 17f + 2f * pulse, p)
        p.color = Theme.alpha(Theme.INK, (200 * fade).toInt()); cv.drawCircle(Layout.HINT_X0 + 24f, cy, 14f, p)
        g.bulb(cv, Layout.HINT_X0 + 24f, cy, 0.78f, (255 * fade).toInt())
        // текст
        val tx = Layout.HINT_TEXT_X
        val col = Theme.alpha(Theme.WHITE, (245 * fade).toInt())
        if (popupLines.size >= 2) {
            g.text(cv, popupLines[0], tx, cy - 3.5f, Layout.HINT_SIZE, col, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
            g.text(cv, popupLines[1], tx, cy + 13f, Layout.HINT_SIZE, col, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
        } else if (popupLines.size == 1) g.text(cv, popupLines[0], tx, cy + 4.7f, Layout.HINT_SIZE, col, Theme.fontMedium, Paint.Align.LEFT, shadow = false)
        if (hasInfo) g.info(cv, Layout.HINT_INFO_X, cy, 1f + 0.06f * pulse, (255 * fade).toInt())
        // тонкая полоска: сколько ещё будет висеть окно
        val left = ((hintShow - 1f - age) / (hintShow - 1f)).coerceIn(0f, 1f)
        if (left > 0f) {
            p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.GOLD, (170 * fade).toInt())
            g.rect.set(l + 16f, top + h - 5f, l + 16f + (r - l - 32f) * left, top + h - 3f); cv.drawRoundRect(g.rect, 1f, 1f, p)
        }
        cv.restore()
    }

    /** Попало ли нажатие в окно подсказки. */
    fun hintHit(x: Float, y: Float): Boolean = hintH > 0f && x >= Layout.HINT_X0 && x <= Layout.HINT_X1 && y >= Layout.HINT_TOP && y <= Layout.HINT_TOP + hintH

    // ---------- лента ----------

    private fun hopperMarbles(cv: Canvas, game: Game) {
        val h = game.hopper
        for (i in 0 until h.size) { val b = h[i]; g.marble(cv, b.rx, b.ry, BODY_R, b.c) }
    }

    private var px = FloatArray(0)
    private var py = FloatArray(0)

    /** Ячейки ленты: у каждого места своё гнездо, шарик лежит в нём. */
    private fun beltMarbles(cv: Canvas, game: Game, ui: Ui) {
        if (px.size < game.n) { px = FloatArray(game.n); py = FloatArray(game.n) }
        val pr = min(14f, game.sp * 0.5f - 1f)         // на очень длинных лентах гнёзда уменьшаются, чтобы не слипаться
        val mr = min(10.6f, pr - 3.4f)
        for (j in 0 until game.n) {
            Geo.point(game.phase + j * game.sp, pt); px[j] = pt[0]; py[j] = pt[1]
            g.pocket(cv, pt[0], pt[1], pr)
        }
        for (j in 0 until game.n) {
            val c = game.slots[j]; if (c < 0) continue
            val u = ((game.time - game.born[j]) / FALL_T).coerceIn(0f, 1f)
            if (u >= 1f) { g.marble(cv, px[j], py[j], mr, c); continue }
            // шарик падает с горловины воронки и ложится в проезжающее гнездо: ускоряется, как под тяжестью
            val e = u * u
            val x0 = game.bornX[j]; val y0 = game.bornY[j]
            g.marble(cv, x0 + (px[j] - x0) * e, y0 + (py[j] - y0) * e, BODY_R + (mr - BODY_R) * e, c)
        }
        val full = game.occupied >= game.n - 2
        val pulse = if (full) 1f + 0.06f * sin(ui.clock * 10f) else 1f
        cv.save(); cv.translate(Geo.CX, Geo.CY + 8f); cv.scale(pulse, pulse)
        g.text(cv, occText.of(game.occupied, game.n), 0f, 0f, 24f, if (full) Theme.RED else Theme.alpha(Theme.WHITE, 215))
        cv.restore()
    }

    // ---------- лотки ----------

    private fun drawTray(cv: Canvas, x: Float, y: Float, sc: Float, t: Tray, alpha: Int) {
        val w = Assets.TRAY_W * sc; val h = Assets.TRAY_H * sc
        g.crate(cv, a.tray[t.c], x, y, Assets.TRAY_W, Assets.TRAY_H, sc, alpha)
        val r = 8.5f * sc
        for (k in 0 until t.n) g.marble(cv, x + ((k % 3) - 1) * 20f * sc, y - h / 2 + 12f + 12f * sc + (k / 3) * 20f * sc, r, t.c, alpha)
    }

    private fun trays(cv: Canvas, game: Game, ui: Ui) {
        for (i in game.cols.indices) {
            val col = game.cols[i]
            for (k in minOf(col.size - 1, 2) downTo 0) {
                val t = col[k]; val d = t.d
                drawTray(cv, game.colX[i], TRAY_Y - 17f * d, 1f - 0.06f * d, t, if (k == 0) 255 else 235)
            }
        }
        links(cv, game, ui)
        hintPointer(cv, game, ui)
        for (li in 0 until game.leaving.size) {
            val l = game.leaving[li]
            val e = l.t * l.t
            val cx = l.x + (180f - l.x) * e; val cy = TRAY_Y + (250f - TRAY_Y) * e
            val sc = 1f - 0.55f * e
            cv.save(); cv.translate(cx, cy); cv.rotate((if (l.x < 180f) 1f else -1f) * 40f * e)
            drawTray(cv, 0f, 0f, sc, l.tray, (255 * (1f - e)).toInt())
            cv.restore()
        }
    }

    private val linked = BooleanArray(8)

    /**
     * Связанные лотки. Один понятный знак на всех: оба лотка пары обведены одинаковой золотой рамкой,
     * а между ними стоит золотая «таблетка» со стрелкой от ведущего лотка к ведомому. Тап по ведущему высыпает и ведомого.
     * Никаких других отметок (ни скоб, ни стрелок на самих лотках) нет, чтобы знак не дублировался.
     */
    private fun links(cv: Canvas, game: Game, ui: Ui) {
        val pulse = 0.5f + 0.5f * sin(ui.clock * 4f)
        val p = g.p
        java.util.Arrays.fill(linked, false)
        eachLink(game) { from, to, _ -> linked[from] = true; linked[to] = true }
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeWidth = 3f; p.color = Theme.alpha(Theme.GOLD, (165 + 90 * pulse).toInt())
        for (i in game.cols.indices) if (linked[i]) outline(cv, game, i, game.cols[i][0].d, p)
        eachLink(game) { from, to, right ->
            g.linkBadge(cv, (game.colX[from] + game.colX[to]) / 2f, TRAY_Y - 17f * game.cols[from][0].d + 14f, right, 1f + 0.07f * pulse)
        }
    }

    /** Подсказка: пульсирующая белая рамка лотка и круги «нажми сюда», расходящиеся от его центра. */
    private fun hintPointer(cv: Canvas, game: Game, ui: Ui) {
        val ci = ui.hintCol
        if (ci !in game.cols.indices || game.cols[ci].isEmpty() || ui.hintT <= 0f) return
        val fade = min(1f, ui.hintT * 2f)
        val pulse = 0.5f + 0.5f * sin(ui.clock * 7f)
        val d = game.cols[ci][0].d
        val p = g.p
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeWidth = 4.5f
        p.color = Theme.alpha(Theme.pointer, ((150 + 105 * pulse) * fade).toInt())
        outline(cv, game, ci, d, p)
        val cx = game.colX[ci]; val cy = TRAY_Y - 17f * d
        for (k in 0 until 2) {
            val u = (ui.clock * 1.2f + k * 0.5f) % 1f
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f + 3f * (1f - u)
            p.color = Theme.alpha(Theme.pointer, ((1f - u) * 210 * fade).toInt())
            cv.drawCircle(cx, cy, 8f + 30f * u, p)
        }
        p.style = Paint.Style.FILL; p.color = Theme.alpha(Theme.pointer, (200 * fade).toInt())
        cv.drawCircle(cx, cy, 6f + 1.5f * pulse, p)
    }

    private inline fun eachLink(game: Game, f: (from: Int, to: Int, right: Boolean) -> Unit) {
        for (i in game.cols.indices) {
            val lead = game.cols[i].firstOrNull() ?: continue
            // та же функция, что решает, кого запустит тап: значок есть ровно там, где связь действует, и только от ведущего
            val to = Links.follower(lead.arrow, lead.pid, i, game.cols.size) { c -> game.cols[c].firstOrNull()?.pid ?: 0 }
            if (to >= 0) f(i, to, to > i)
        }
    }

    private fun outline(cv: Canvas, game: Game, col: Int, d: Float, p: Paint) {
        val y = TRAY_Y - 17f * d
        g.rect.set(game.colX[col] - 40f, y - 45f, game.colX[col] + 40f, y + 46f)
        cv.drawRoundRect(g.rect, 18f, 18f, p)
    }

    // ---------- коробки ----------

    private fun boxes(cv: Canvas, game: Game) {
        for (pi in 0 until game.popping.size) {          // собранные коробки уходят вверх дымком
            val b = game.popping[pi]
            val x = game.boxX[b.slot]
            if (b.pop >= 0f) {
                val f = b.pop / Game.POP_T
                val fade = 1f - smooth01((f - 0.3f) / 0.7f)
                val sway = sin(f * 7f + b.slot * 1.7f)
                cv.save()
                cv.translate(x + sway * 9f * f, BOX_Y - Game.rise(f))
                cv.rotate(sway * 5f * f)
                drawBox(cv, 0f, 0f, 1f + 0.3f * f, b, (255 * fade * fade).toInt())
                cv.restore()
            } else drawBox(cv, x, BOX_Y, 1f, b, 255)
        }
        for (i in 0 until SLOTS) {
            val x = game.boxX[i]
            val q = game.queues[i]
            for (k in minOf(q.size, 1) downTo 1) g.crate(cv, a.boxDim[q[k - 1].c], x, BOX_Y + 13f * k, BOX_W, BOX_H, 1f - 0.07f * k, 255)
            val b = game.boxes[i] ?: continue
            drawBox(cv, x, BOX_Y + b.enter * 14f, 0.94f + 0.06f * (1f - b.enter), b, 255)
        }
    }

    private fun smooth01(t: Float): Float { val u = t.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun drawBox(cv: Canvas, x: Float, y: Float, sc: Float, b: Box, alpha: Int) {
        // коробка проседает и пружинит от упавшего шарика
        val dip = sin(b.bump * PI.toFloat()) * 3.2f
        val squash = 1f + 0.03f * sin(b.bump * PI.toFloat())
        g.crate(cv, if (b.locked) a.boxDim[b.c] else a.box[b.c], x, y + dip, BOX_W, BOX_H, sc * squash, alpha)
        for (k in 0 until b.n) {
            val last = k == b.n - 1 && b.bump > 0f      // только что упавший шарик «плюхается» и оседает
            g.marble(cv, x + (k - 1) * 22f * sc, y + dip - 12f * sc, 9.5f * sc * (if (last) 1f + 0.3f * b.bump else 1f), b.c, alpha)
        }
        if (b.locked) g.lock(cv, x, y - 4f)
    }

    /** Шарик вылетает из ячейки лёгкой дугой, падает с ускорением, вытягивается по скорости и оставляет след. */
    private fun flyers(cv: Canvas, game: Game) {
        for (fi in 0 until game.flyers.size) {
            val f = game.flyers[fi]
            val tx = game.boxX[f.boxIdx] + (f.hole - 1) * 22f; val ty = BOX_Y - 12f
            fun px(t: Float): Float { val u = t.coerceIn(0f, 1f); return f.x0 + (tx - f.x0) * (1f - (1f - u) * (1f - u)) }
            fun py(t: Float): Float { val u = t.coerceIn(0f, 1f); return f.y0 + (ty - f.y0) * u * u - 15f * sin(u * PI.toFloat()) * (1f - u) }
            val t = f.t.coerceIn(0f, 1f)
            val r = 11f - 1.5f * t
            for (k in 2 downTo 1) {                              // след: два бледных «призрака» позади
                val tt = t - k * 0.07f
                if (tt > 0f) g.marble(cv, px(tt), py(tt), r * (1f - 0.12f * k), f.c, 110 / k)
            }
            val dx = px(t + 0.02f) - px(t - 0.02f); val dy = py(t + 0.02f) - py(t - 0.02f)
            val speed = sqrt(dx * dx + dy * dy) / 0.04f / Game.FLY_T
            val stretch = 1f + min(0.4f, speed / 1100f)
            cv.save()
            cv.translate(px(t), py(t))
            cv.rotate(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat())
            cv.scale(stretch, 1f / sqrt(stretch))                // вытягивается вдоль полёта, площадь сохраняется
            g.marble(cv, 0f, 0f, r, f.c)
            cv.restore()
        }
    }

    private fun rockets(cv: Canvas, game: Game) {
        for (ri in 0 until game.rockets.size) {   // ракета летит от кнопки к коробке
            val r = game.rockets[ri]
            val e = r.t.coerceIn(0f, 1f)
            val x = ROCKET_X + (game.boxX[r.boxIdx] - ROCKET_X) * e
            val y = BOOST_Y + (BOX_Y - BOOST_Y) * e
            cv.save(); cv.translate(x, y); cv.rotate(game.rocketAngle(r))
            g.rocket(cv, 1.3f, true); cv.restore()
        }
    }

    // ---------- эффекты ----------

    /** Конфетти рисуется отдельно — поверх итоговой карточки. */
    fun confetti(cv: Canvas, game: Game) = effects(cv, game, confetti = true)

    private fun effects(cv: Canvas, game: Game, confetti: Boolean) {
        val fx = game.fx
        val p = g.p
        p.shader = null
        for (i in 0 until fx.n) {
            val isConfetti = fx.kind[i].toInt() == Particles.CONFETTI
            if (isConfetti != confetti) continue
            val life = fx.life[i].coerceIn(0f, 1f)
            val col = Theme.pal[fx.c[i]]
            when (fx.kind[i].toInt()) {
                Particles.SPARK -> {
                    p.style = Paint.Style.FILL; p.color = Theme.alpha(col[0], (life * 255).toInt())
                    cv.drawCircle(fx.x[i], fx.y[i], fx.size[i] * (0.4f + 0.6f * life), p)
                }
                Particles.CONFETTI -> {
                    p.style = Paint.Style.FILL; p.color = Theme.alpha(if (fx.c[i] % 2 == 0) col[1] else col[0], (min(1f, life * 4f) * 255).toInt())
                    cv.save(); cv.translate(fx.x[i], fx.y[i]); cv.rotate(Math.toDegrees(fx.rot[i].toDouble()).toFloat())
                    val s = fx.size[i] * (0.35f + 0.65f * abs(cos(fx.rot[i] * 1.7f)))    // «переворачивается» в полёте
                    g.rect.set(-fx.size[i], -s * 0.5f, fx.size[i], s * 0.5f); cv.drawRect(g.rect, p)
                    cv.restore()
                }
                Particles.PUFF -> {
                    val soft = min(1f, life * 2.2f)
                    p.style = Paint.Style.FILL
                    p.color = Theme.alpha(Theme.mix(col[1], Theme.WHITE, 0.55f), (soft * 84).toInt())
                    cv.drawCircle(fx.x[i], fx.y[i], fx.size[i], p)
                    p.color = Theme.alpha(Theme.WHITE, (soft * 38).toInt())
                    cv.drawCircle(fx.x[i] - fx.size[i] * 0.25f, fx.y[i] - fx.size[i] * 0.25f, fx.size[i] * 0.55f, p)
                }
                Particles.RING -> {
                    p.style = Paint.Style.STROKE; p.strokeWidth = 1f + 4f * life; p.color = Theme.alpha(col[0], (life * 230).toInt())
                    cv.drawCircle(fx.x[i], fx.y[i], fx.size[i], p)
                }
                Particles.GLINT -> {
                    val s = fx.size[i] * sin(life * PI.toFloat())
                    if (s > 0.2f) g.glint(cv, fx.x[i], fx.y[i], s * 1.6f, (life * 255).toInt())
                }
            }
        }
    }

    private fun popups(cv: Canvas, game: Game) {
        for (pi in 0 until game.popups.size) {
            val pp = game.popups[pi]
            val t = pp.t
            val pop = if (t < 0.15f) t / 0.15f * 1.25f else 1.25f - 0.25f * min(1f, (t - 0.15f) / 0.15f)
            cv.save(); cv.translate(pp.x, pp.y - 46f * t); cv.scale(pop, pop)
            g.text(cv, pp.text, 0f, 0f, 26f, Theme.alpha(Theme.pal[pp.c][if (Theme.dark) 0 else 1], ((1f - t * t) * 255).toInt()))
            cv.restore()
        }
    }

    // ---------- бустеры ----------

    private fun boosters(cv: Canvas, game: Game, ui: Ui) {
        booster(cv, ROCKET_X, AID_ROCKET, game, ui)
        booster(cv, Layout.HINT_BX, AID_HINT, game, ui)
        booster(cv, RETURN_X, AID_RETURN, game, ui)
        g.coinPill(cv, Layout.COIN_BX, BOOST_Y, progress.coins)
    }

    private fun booster(cv: Canvas, x: Float, aid: Int, game: Game, ui: Ui) {
        val free = ui.freeAid == aid                        // на учебном уровне помощник бесплатный
        if (!free && !progress.lessonDone(Lessons.forAid(aid).id)) {      // ещё не открыт: тусклая кнопка с замком
            g.roundButton(cv, x, BOOST_Y, Layout.BOOST_R, 0f, false)
            g.lock(cv, x, BOOST_Y, 0.7f)
            return
        }
        val usable = game.state == State.PLAY && (free || progress.canAfford(aid))
        val id = when (aid) { AID_ROCKET -> BTN_ROCKET; AID_RETURN -> BTN_RETURN; else -> BTN_HINT }
        g.roundButton(cv, x, BOOST_Y, Layout.BOOST_R, ui.pr(id), usable)
        cv.save(); cv.translate(x, BOOST_Y)
        when (aid) {
            AID_ROCKET -> { cv.rotate(-20f); g.rocket(cv, 1f, false) }
            AID_RETURN -> g.returnIcon(cv)
            else -> g.bulb(cv, 0f, -1f)
        }
        cv.restore()
        if (!usable) { g.p.style = Paint.Style.FILL; g.p.shader = null; g.p.color = Theme.alpha(0xFF0A1535.toInt(), 120); cv.drawCircle(x, BOOST_Y, Layout.BOOST_R, g.p) }
        if (ui.focusAid == aid && game.state == State.PLAY) {   // урок указывает на эту кнопку
            val pulse = 0.5f + 0.5f * sin(ui.clock * 6f)
            g.p.style = Paint.Style.STROKE; g.p.shader = null; g.p.strokeWidth = 4f
            g.p.color = Theme.alpha(Theme.pointer, (150 + 105 * pulse).toInt())
            cv.drawCircle(x, BOOST_Y, Layout.BOOST_R + 5f + 3f * pulse, g.p)
        }
        val have = if (free) 1 else progress.have(aid)
        val label = if (have > 0) (when (aid) { AID_ROCKET -> rocketText; AID_RETURN -> returnText; else -> hintText }).of(have)
                    else when (aid) { AID_ROCKET -> ROCKET_LABEL; AID_RETURN -> RETURN_LABEL; else -> HINT_LABEL }
        g.p.style = Paint.Style.FILL; g.p.shader = null
        g.p.color = Theme.INK; cv.drawCircle(x + 19f, BOOST_Y - 19f, 12.5f, g.p)
        g.p.color = if (have > 0) Theme.GREEN else Theme.GOLD; cv.drawCircle(x + 19f, BOOST_Y - 19f, 11f, g.p)
        g.text(cv, label, x + 19f, BOOST_Y - 15f, if (label.length > 1) 10f else 12.5f, Theme.INK, shadow = false)
    }
}
