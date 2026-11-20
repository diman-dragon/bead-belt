package dev.beadbelt.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Рисует игровое поле: HUD, лотки, воронку, ленту, коробки, эффекты и бустеры. */
class GameRenderer(private val g: Painter, private val a: Assets, private val progress: Progress) {
    private val pt = FloatArray(3)
    private val boardPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val occText = NumText()
    private val doneText = NumText()
    private val rocketText = NumText()
    private val returnText = NumText()
    private val undoText = NumText()
    private val hintText = NumText()
    private var titleId = -1
    private var title = ""

    fun draw(cv: Canvas, game: Game, ui: Ui, hint: String, hintAlpha: Float) {
        cv.save()
        if (game.shake > 0f) cv.translate(sin(ui.clock * 95f) * game.shake * 3f, cos(ui.clock * 81f) * game.shake * 2.5f)

        hud(cv, game, ui, hint, hintAlpha)
        cv.drawBitmap(a.board, null, g.rect.also { it.set(0f, Assets.BOARD_Y0, W, Assets.BOARD_Y0 + Assets.BOARD_H) }, boardPaint)
        trays(cv, game, ui)
        val hc = game.hintedCol
        if (hc != null && hc in game.cols.indices && game.cols[hc].isNotEmpty()) {
            val hx = game.colX[hc]
            val hy = TRAY_Y - 17f * game.cols[hc][0].d
            val p = g.p
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.shader = null
            p.color = Theme.alpha(Theme.GOLD, (150 + 105 * sin(ui.clock * 8f)).toInt())
            g.rect.set(hx - 45f, hy - 48f, hx + 45f, hy + 48f)
            cv.drawRoundRect(g.rect, 20f, 20f, p)
        }
        hopperMarbles(cv, game)
        beltMarbles(cv, game, ui)
        boxes(cv, game)
        flyers(cv, game)
        rockets(cv, game)
        effects(cv, game, confetti = false)
        popups(cv, game)
        boosters(cv, game, ui)
        cv.restore()
    }

    // ---------- HUD ----------

    private fun hud(cv: Canvas, game: Game, ui: Ui, hint: String, hintAlpha: Float) {
        if (titleId != game.level.id) { titleId = game.level.id; title = "Уровень $titleId" }
        g.text(cv, title, 180f, 34f, 22f)
        g.progressBar(cv, 80f, 46f, 280f, 62f, game.progress)
        g.text(cv, doneText.of(game.done, game.total), 180f, 58f, 10.5f, shadow = false)
        g.roundButton(cv, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_SOUND)); g.speaker(cv, Layout.HUD_L_X, Layout.HUD_Y, progress.sound)
        g.roundButton(cv, Layout.HUD_R_X, Layout.HUD_Y, Layout.HUD_R, ui.pr(BTN_RESTART)); g.restart(cv, Layout.HUD_R_X, Layout.HUD_Y)
        if (hint.isNotEmpty() && hintAlpha > 0f) {
            g.text(cv, hint, 180f, 88f, 13f, Theme.alpha(Theme.WHITE, (hintAlpha * 235).toInt()), Theme.fontMedium)
        }
    }

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
            val drop = (1f - (game.time - game.born[j]) / 0.15f).coerceIn(0f, 1f)
            g.marble(cv, px[j], py[j] - drop * 14f, mr, c)
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
        if (t.arrow != 0) {                      // стрелки на табличке внизу лотка
            val yy = y + h / 2 - 19f * sc
            if (t.arrow and 1 != 0) g.arrow(cv, x - 14f * sc, yy, true, sc, alpha)
            if (t.arrow and 2 != 0) g.arrow(cv, x + 14f * sc, yy, false, sc, alpha)
        }
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

    /**
     * Связанные лотки: лоток со стрелкой (ведущий) обводится толстым золотом, лотки, которые он потянет за собой, —
     * тонким, а между ними ставится скоба со стрелкой. Три прохода, чтобы обводка соседа не перекрывала скобу.
     */
    private fun links(cv: Canvas, game: Game, ui: Ui) {
        val pulse = 0.5f + 0.5f * sin(ui.clock * 4f)
        val p = g.p
        p.style = Paint.Style.STROKE; p.shader = null
        // 1) ведомые: тонкая обводка
        p.strokeWidth = 2f; p.color = Theme.alpha(Theme.GOLD, 150)
        eachLink(game) { from, to, _ -> outline(cv, game, to, game.cols[from][0].d, p) }
        // 2) ведущие: толстая пульсирующая обводка
        p.strokeWidth = 3.6f; p.color = Theme.alpha(Theme.GOLD, (170 + 85 * pulse).toInt())
        eachLink(game) { from, _, _ -> outline(cv, game, from, game.cols[from][0].d, p) }
        // 3) скобы поверх всего
        eachLink(game) { from, to, right ->
            g.linkClamp(cv, (game.colX[from] + game.colX[to]) / 2f, TRAY_Y - 17f * game.cols[from][0].d, right, 1f + 0.08f * pulse)
        }
    }

    private inline fun eachLink(game: Game, f: (from: Int, to: Int, right: Boolean) -> Unit) {
        for (i in game.cols.indices) {
            val col = game.cols[i]
            if (col.isEmpty() || col[0].arrow == 0) continue
            val arrow = col[0].arrow
            if (arrow and 2 != 0 && i + 1 < game.cols.size && game.cols[i + 1].isNotEmpty()) f(i, i + 1, true)
            if (arrow and 1 != 0 && i > 0 && game.cols[i - 1].isNotEmpty()) f(i, i - 1, false)
        }
    }

    private fun outline(cv: Canvas, game: Game, col: Int, d: Float, p: Paint) {
        val y = TRAY_Y - 17f * d
        g.rect.set(game.colX[col] - 41f, y - 45f, game.colX[col] + 41f, y + 45f)
        cv.drawRoundRect(g.rect, 18f, 18f, p)
    }

    // ---------- коробки ----------

    private fun boxes(cv: Canvas, game: Game) {
        for (i in 0 until SLOTS) {
            val x = game.boxX[i]
            val q = game.stacks[i]
            for (k in minOf(q.size, 1) downTo 1) g.crate(cv, a.boxDim[q[k - 1]], x, BOX_Y + 13f * k, BOX_W, BOX_H, 1f - 0.07f * k, 255)
            val b = game.boxes[i] ?: continue
            if (b.pop >= 0f) {
                val f = b.pop / Game.POP_T
                drawBox(cv, x, BOX_Y - 12f * f, 1f + 0.25f * sin(f * PI.toFloat()), b, (255 * (1f - f * f)).toInt())
            } else {
                drawBox(cv, x, BOX_Y + b.enter * 14f, 0.94f + 0.06f * (1f - b.enter), b, 255)
            }
        }
    }

    private fun drawBox(cv: Canvas, x: Float, y: Float, sc: Float, b: Box, alpha: Int) {
        g.crate(cv, if (b.locked) a.boxDim[b.c] else a.box[b.c], x, y, BOX_W, BOX_H, sc, alpha)
        for (k in 0 until b.n) g.marble(cv, x + (k - 1) * 22f * sc, y - 12f * sc, 9.5f * sc, b.c, alpha)
        if (b.locked) g.lock(cv, x, y - 4f)
    }

    private fun flyers(cv: Canvas, game: Game) {
        for (fi in 0 until game.flyers.size) {
            val f = game.flyers[fi]
            val e = f.t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            val tx = game.boxX[f.boxIdx] + (f.hole - 1) * 22f; val ty = BOX_Y - 12f
            g.marble(cv, f.x0 + (tx - f.x0) * e, f.y0 + (ty - f.y0) * e - sin(e * PI.toFloat()) * 10f, 11f - 1.5f * e, f.c)
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
            g.text(cv, pp.text, 0f, 0f, 26f, Theme.alpha(Theme.pal[pp.c][0], ((1f - t * t) * 255).toInt()))
            cv.restore()
        }
    }

    // ---------- бустеры ----------

    private fun boosters(cv: Canvas, game: Game, ui: Ui) {
        booster(cv, ROCKET_X, BTN_ROCKET, progress.rockets, Progress.ROCKET_PRICE, game, ui) { cv.rotate(-20f); g.rocket(cv, 1f, false) }
        booster(cv, UNDO_X, BTN_UNDO, progress.undos, Progress.UNDO_PRICE, game, ui) { g.undoIcon(cv) }
        booster(cv, HINT_X, BTN_HINT, progress.hints, Progress.HINT_PRICE, game, ui) { g.hintIcon(cv) }
        booster(cv, RETURN_X, BTN_RETURN, progress.returns, Progress.RETURN_PRICE, game, ui) { g.returnIcon(cv) }
        g.coinPill(cv, 180f, BOOST_Y, progress.coins)
    }

    private fun booster(cv: Canvas, x: Float, btnId: Int, have: Int, price: Int, game: Game, ui: Ui, drawIcon: () -> Unit) {
        val typeStr = when (btnId) {
            BTN_ROCKET -> "rocket"
            BTN_UNDO -> "undo"
            BTN_HINT -> "hint"
            BTN_RETURN -> "return"
            else -> ""
        }
        val usable = game.state == State.PLAY && progress.canAfford(typeStr)
        g.roundButton(cv, x, BOOST_Y, Layout.BOOST_R, ui.pr(btnId), usable)
        cv.save(); cv.translate(x, BOOST_Y)
        drawIcon()
        cv.restore()
        if (!usable) { g.p.style = Paint.Style.FILL; g.p.shader = null; g.p.color = Theme.alpha(0xFF0A1535.toInt(), 120); cv.drawCircle(x, BOOST_Y, Layout.BOOST_R, g.p) }
        val label = if (have > 0) when (btnId) {
            BTN_ROCKET -> rocketText.of(have)
            BTN_UNDO -> undoText.of(have)
            BTN_HINT -> hintText.of(have)
            else -> returnText.of(have)
        } else "$price"
        g.p.style = Paint.Style.FILL; g.p.shader = null
        g.p.color = Theme.INK; cv.drawCircle(x + 19f, BOOST_Y - 19f, 12.5f, g.p)
        g.p.color = if (have > 0) Theme.GREEN else Theme.GOLD; cv.drawCircle(x + 19f, BOOST_Y - 19f, 11f, g.p)
        g.text(cv, label, x + 19f, BOOST_Y - 15f, if (label.length > 1) 10f else 12.5f, Theme.INK, shadow = false)
    }
}
