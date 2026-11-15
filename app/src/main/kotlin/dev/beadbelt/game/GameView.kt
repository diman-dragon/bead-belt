package dev.beadbelt.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin

/** Весь интерфейс рисуется на Canvas в логических координатах 360x640. */
class GameView(context: Context) : View(context) {
    private enum class Screen { MENU, LEVELS, PLAY }

    companion object {
        const val ROCKET_PRICE = 60
        const val RETURN_PRICE = 40
        const val ROCKET_X = 90f
        const val RETURN_X = 270f
        const val BOOST_Y = 614f
    }

    private val prefs = context.getSharedPreferences("bead", Context.MODE_PRIVATE)
    private val sound = Sound().also { it.enabled = prefs.getBoolean("sound", true) }
    private var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, LEVEL_COUNT)
    private var coins = prefs.getInt("coins", 100)
    private var rocketsLeft = prefs.getInt("rockets", 2)
    private var returnsLeft = prefs.getInt("returns", 2)
    private var current = unlocked
    private var reward = 0
    private var screen = Screen.MENU
    private var game: Game? = null
    private var endT = 0f
    private var paused = false
    private var clock = 0f
    private var hintAt = 0f
    private var hint = ""
    private var lastNs = 0L
    private var s = 1f
    private var ox = 0f
    private var oy = 0f

    private val version = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val pt = FloatArray(2)
    private val path = Path()

    // светлый, основной, тёмный оттенок каждого цвета
    private val pal = arrayOf(
        longArrayOf(0xFFFF9A9A, 0xFFFF2D4A, 0xFF9E0F26), longArrayOf(0xFF9CC2FF, 0xFF2F6BFF, 0xFF123A9E),
        longArrayOf(0xFFA6F5BB, 0xFF22C55E, 0xFF0B7A33), longArrayOf(0xFFFFF3A0, 0xFFFFD21F, 0xFFB88A00),
        longArrayOf(0xFFE3B0FF, 0xFFB04BFF, 0xFF6A18A8), longArrayOf(0xFFA2F2FF, 0xFF14C8E8, 0xFF0A7C94),
        longArrayOf(0xFFFFC994, 0xFFFF8A1F, 0xFFB04F00), longArrayOf(0xFFFFB6DC, 0xFFFF5FAE, 0xFFA8286A),
    ).map { row -> IntArray(3) { row[it].toInt() } }

    private val sprites = Array(COLOR_COUNT) { c ->
        val sz = 64
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = sz / 2f
        q.shader = RadialGradient(r * 0.7f, r * 0.6f, r * 1.25f, pal[c], floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(r, r, r - 1f, q)
        q.shader = null
        q.color = 0x99FFFFFF.toInt()
        cv.drawOval(RectF(r * 0.45f, r * 0.28f, r * 0.95f, r * 0.62f), q)
        bmp
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            val dt = if (lastNs == 0L) 0f else min((ns - lastNs) / 1e9f, 0.05f)
            lastNs = ns
            clock += dt
            val g = game
            if (screen == Screen.PLAY && g != null && !paused) {
                g.update(dt)
                if (g.state != State.PLAY) endT += dt
            }
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); lastNs = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    override fun onDetachedFromWindow() { Choreographer.getInstance().removeFrameCallback(frame); super.onDetachedFromWindow() }

    fun pauseGame() { paused = true; lastNs = 0L }
    fun release() = sound.release()

    /** Назад: из игры в меню, из списка уровней в меню. Из меню — выход. */
    fun onBack(): Boolean {
        if (screen != Screen.MENU) { screen = Screen.MENU; return true }
        return false
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) { paused = false; lastNs = 0L }
    }

    private fun save() {
        prefs.edit().putInt("unlocked", unlocked).putInt("coins", coins)
            .putInt("rockets", rocketsLeft).putInt("returns", returnsLeft).apply()
    }

    private fun startLevel(id: Int) {
        current = id.coerceIn(1, LEVEL_COUNT)
        endT = 0f; reward = 0
        hint = when (current) {
            1 -> "Нажми на лоток: шарики поедут по конвейеру"
            3 -> "Ракета внизу мгновенно закрывает коробку"
            8 -> "Стрелки: тап запускает и соседние лотки"
            15 -> "Замок: коробка откроется после первой собранной"
            else -> ""
        }
        hintAt = clock
        game = Game(Levels.make(current)) { ev, arg ->
            sound.event(ev, arg)
            if (ev == EV_POP || ev == EV_WIN || ev == EV_LOSE || ev == EV_UNLOCK) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (ev == EV_WIN) onWin()
        }
        screen = Screen.PLAY
    }

    private fun onWin() {
        reward = 20 + current / 10 * 5
        coins += reward
        if (current == unlocked && unlocked < LEVEL_COUNT) unlocked++
        save()
    }

    private fun toggleSound() {
        sound.enabled = !sound.enabled
        prefs.edit().putBoolean("sound", sound.enabled).apply()
    }

    // ---------- ввод ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        val x = (e.x - ox) / s
        val y = (e.y - oy) / s
        val g = game
        when (screen) {
            Screen.MENU -> when {
                inside(x, y, 50f, 330f, 310f, 400f) -> startLevel(unlocked)
                inside(x, y, 50f, 414f, 310f, 468f) -> screen = Screen.LEVELS
                inside(x, y, 100f, 482f, 260f, 526f) -> toggleSound()
            }
            Screen.LEVELS -> {
                if (dist(x, y, 38f, 40f) < 26f) screen = Screen.MENU
                else {
                    val col = ((x - 12f) / 34f).toInt()
                    val row = ((y - 120f) / 38f).toInt()
                    val id = row * 10 + col + 1
                    if (x >= 12f && y >= 120f && col in 0..9 && row in 0..9 && id <= unlocked) startLevel(id)
                }
            }
            Screen.PLAY -> when {
                g != null && g.state != State.PLAY && endT > 0.7f -> {
                    val won = g.state == State.WON
                    if (inside(x, y, 60f, 300f, 300f, 356f)) startLevel(if (won && current < LEVEL_COUNT) current + 1 else current)
                    else if (inside(x, y, 60f, 372f, 300f, 420f)) screen = Screen.MENU
                }
                dist(x, y, 322f, 40f) < 26f -> startLevel(current)
                dist(x, y, 38f, 40f) < 26f -> toggleSound()
                g != null && dist(x, y, ROCKET_X, BOOST_Y) < 26f -> useBooster(true, g)
                g != null && dist(x, y, RETURN_X, BOOST_Y) < 26f -> useBooster(false, g)
                else -> g?.tap(x, y)
            }
        }
        return true
    }

    private fun useBooster(rocket: Boolean, g: Game) {
        if (g.state != State.PLAY) return
        val have = if (rocket) rocketsLeft else returnsLeft
        val price = if (rocket) ROCKET_PRICE else RETURN_PRICE
        if (have <= 0 && coins < price) return
        val ok = if (rocket) g.useRocket() else g.returnMarbles()
        if (!ok) return
        if (have > 0) { if (rocket) rocketsLeft-- else returnsLeft-- } else coins -= price
        save()
    }

    private fun inside(x: Float, y: Float, l: Float, t: Float, r: Float, b: Float) = x in l..r && y in t..b
    private fun dist(x: Float, y: Float, cx: Float, cy: Float) = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat()

    // ---------- рисование ----------

    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    override fun onDraw(cv: Canvas) {
        p.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF3FA9FF.toInt(), 0xFF1D3FB5.toInt(), Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null

        s = min(width / W, height / H)
        ox = (width - W * s) / 2f
        oy = (height - H * s) / 2f
        cv.save(); cv.translate(ox, oy); cv.scale(s, s)
        when (screen) {
            Screen.MENU -> drawMenu(cv)
            Screen.LEVELS -> drawLevels(cv)
            Screen.PLAY -> game?.let { drawGame(cv, it) }
        }
        cv.restore()
    }

    private fun text(cv: Canvas, str: String, x: Float, y: Float, size: Float, color: Int = 0xFFFFFFFF.toInt()) {
        p.style = Paint.Style.FILL; p.color = color; p.textSize = size
        p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD
        cv.drawText(str, x, y, p)
    }

    private fun button(cv: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, primary: Boolean) {
        rect.set(l, t + 5f, r, b + 5f); p.style = Paint.Style.FILL
        p.color = if (primary) 0xFF1B8F3E.toInt() else 0xFF14275E.toInt()
        cv.drawRoundRect(rect, 18f, 18f, p)
        rect.set(l, t, r, b)
        p.color = if (primary) 0xFF2FD060.toInt() else 0xFF2C4DA8.toInt()
        cv.drawRoundRect(rect, 18f, 18f, p)
        text(cv, label, (l + r) / 2f, (t + b) / 2f + (b - t) * 0.17f, (b - t) * 0.42f)
    }

    private fun ball(cv: Canvas, x: Float, y: Float, r: Float, c: Int, a: Int = 255) {
        rect.set(x - r, y - r, x + r, y + r)
        bp.alpha = a
        cv.drawBitmap(sprites[c], null, rect, bp)
    }

    private fun drawMenu(cv: Canvas) {
        text(cv, "Bead Belt", 180f, 150f, 52f)
        for (c in 0 until 6) ball(cv, 60f + c * 48f, 230f + sin(clock * 3f + c * 0.7f) * 10f, 17f, c)
        button(cv, 50f, 330f, 310f, 400f, if (unlocked == 1) "Играть" else "Играть · уровень $unlocked", true)
        button(cv, 50f, 414f, 310f, 468f, "Уровни  $unlocked/$LEVEL_COUNT", false)
        button(cv, 100f, 482f, 260f, 526f, if (sound.enabled) "Звук: вкл" else "Звук: выкл", false)
        coinBadge(cv, 180f, 568f)
        text(cv, "v$version", 180f, 624f, 14f, 0x88FFFFFF.toInt())
    }

    private fun drawLevels(cv: Canvas) {
        hudButton(cv, 38f, 40f); drawBack(cv, 38f, 40f)
        text(cv, "Уровни", 180f, 50f, 26f)
        for (id in 1..LEVEL_COUNT) {
            val col = (id - 1) % 10; val row = (id - 1) / 10
            val x = 12f + col * 34f; val y = 120f + row * 38f
            rect.set(x, y, x + 30f, y + 33f)
            p.style = Paint.Style.FILL
            p.color = when { id < unlocked -> 0xFF2FD060.toInt(); id == unlocked -> 0xFFFFD21F.toInt(); else -> 0x55102050 }
            cv.drawRoundRect(rect, 8f, 8f, p)
            text(cv, "$id", x + 15f, y + 22f, if (id < 100) 13f else 11f, if (id <= unlocked) 0xFF0B1B3A.toInt() else 0x66FFFFFF)
        }
        coinBadge(cv, 180f, 560f)
    }

    private fun coinBadge(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL; p.color = 0xFFFFC21F.toInt(); cv.drawCircle(x - 28f, y - 7f, 11f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = 0xFFB87800.toInt(); cv.drawCircle(x - 28f, y - 7f, 7f, p)
        text(cv, "$coins", x + 8f, y, 22f)
    }

    private fun crate(cv: Canvas, x: Float, y: Float, w: Float, h: Float, c: Int, a: Int, dim: Boolean = false) {
        val col = pal[c]
        p.style = Paint.Style.FILL
        rect.set(x - w / 2, y - h / 2 + 6f, x + w / 2, y + h / 2 + 6f)
        p.color = alpha(col[2], a); cv.drawRoundRect(rect, 14f, 14f, p)
        rect.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        p.color = alpha(col[1], a); cv.drawRoundRect(rect, 14f, 14f, p)
        rect.set(x - w / 2 + 5f, y - h / 2 + 4f, x + w / 2 - 5f, y - h / 2 + h * 0.28f)
        p.color = alpha(0xFFFFFFFF.toInt(), a / 4); cv.drawRoundRect(rect, 10f, 10f, p)
        rect.set(x - w / 2 + 8f, y - h / 2 + 12f, x + w / 2 - 8f, y + h / 2 - 22f)
        p.color = alpha(0xFF000000.toInt(), a * 40 / 100); cv.drawRoundRect(rect, 10f, 10f, p)
        if (dim) {
            rect.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
            p.color = alpha(0xFF0A1535.toInt(), 120); cv.drawRoundRect(rect, 14f, 14f, p)
        }
    }

    private fun drawTray(cv: Canvas, x: Float, y: Float, sc: Float, t: Tray, a: Int) {
        val w = 76f * sc; val h = 84f * sc
        crate(cv, x, y, w, h, t.c, a)
        val r = 8.5f * sc
        for (k in 0 until t.n) ball(cv, x + ((k % 3) - 1) * 20f * sc, y - h / 2 + 12f + 12f * sc + (k / 3) * 20f * sc, r, t.c, a)
        if (t.arrow != 0) {                      // стрелки на нижней кромке лотка
            p.style = Paint.Style.FILL; p.color = alpha(0xFFFFFFFF.toInt(), a)
            val yy = y + h / 2 - 12f * sc; val k = 7f * sc
            if (t.arrow and 1 != 0) { path.reset(); path.moveTo(x - w / 2 + 5f * sc, yy); path.lineTo(x - w / 2 + 5f * sc + k * 1.4f, yy - k); path.lineTo(x - w / 2 + 5f * sc + k * 1.4f, yy + k); path.close(); cv.drawPath(path, p) }
            if (t.arrow and 2 != 0) { path.reset(); path.moveTo(x + w / 2 - 5f * sc, yy); path.lineTo(x + w / 2 - 5f * sc - k * 1.4f, yy - k); path.lineTo(x + w / 2 - 5f * sc - k * 1.4f, yy + k); path.close(); cv.drawPath(path, p) }
        }
    }

    private fun drawBox(cv: Canvas, x: Float, y: Float, sc: Float, b: Box, a: Int) {
        crate(cv, x, y, BOX_W * sc, BOX_H * sc, b.c, a, b.locked)
        for (k in 0 until BOX) {
            val hx = x + (k - 1) * 22f * sc; val hy = y - 12f * sc
            if (k < b.n) ball(cv, hx, hy, 9.5f * sc, b.c, a)
            else { p.style = Paint.Style.FILL; p.color = alpha(0xFF000000.toInt(), a / 3); cv.drawCircle(hx, hy, 9.5f * sc, p) }
        }
        if (b.locked) drawLock(cv, x, y - 4f)
    }

    private fun drawLock(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFFE6ECFF.toInt()
        rect.set(x - 8f, y - 20f, x + 8f, y - 2f); cv.drawArc(rect, 180f, 180f, false, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFC21F.toInt()
        rect.set(x - 13f, y - 11f, x + 13f, y + 13f); cv.drawRoundRect(rect, 5f, 5f, p)
        p.color = 0xFF7A4F00.toInt(); cv.drawCircle(x, y + 1f, 3.2f, p)
    }

    private fun drawGame(cv: Canvas, g: Game) {
        // HUD
        text(cv, "Уровень ${g.level.id}", 180f, 36f, 22f)
        rect.set(80f, 50f, 280f, 60f); p.style = Paint.Style.FILL; p.color = 0x33000000
        cv.drawRoundRect(rect, 5f, 5f, p)
        if (g.progress > 0f) { rect.set(80f, 50f, 80f + 200f * g.progress, 60f); p.color = 0xFF2FD060.toInt(); cv.drawRoundRect(rect, 5f, 5f, p) }
        hudButton(cv, 38f, 40f); drawSpeaker(cv, 38f, 40f)
        hudButton(cv, 322f, 40f); drawRestart(cv, 322f, 40f)
        if (hint.isNotEmpty() && clock - hintAt < 6f) text(cv, hint, 180f, 82f, 15f, 0xDDFFFFFF.toInt())

        // лотки: сначала глубокие
        for (i in g.cols.indices) {
            val col = g.cols[i]
            for (k in minOf(col.size - 1, 3) downTo 0) {
                val t = col[k]; val d = t.d
                drawTray(cv, g.colX[i], TRAY_Y - 17f * d, 1f - 0.06f * d, t, if (k == 0) 255 else 235)
            }
        }
        for (l in g.leaving) {
            val e = l.t * l.t
            drawTray(cv, l.x + (180f - l.x) * e, TRAY_Y + (250f - TRAY_Y) * e, 1f - 0.55f * e, l.tray, (255 * (1f - e)).toInt())
        }

        drawBelt(cv, g)

        // коробки и очереди за ними
        for (i in 0 until SLOTS) {
            val x = g.boxX[i]
            val q = g.stacks[i]
            for (k in minOf(q.size, 2) downTo 1) crate(cv, x, BOX_Y + 11f * k, (BOX_W - 6f * k), BOX_H - 4f * k, q[k - 1], 255, true)
            val b = g.boxes[i] ?: continue
            if (b.pop >= 0f) {
                val f = b.pop / Game.POP_T
                drawBox(cv, x, BOX_Y - 10f * f, 1f + 0.25f * sin(f * PI.toFloat()), b, (255 * (1f - f * f)).toInt())
            } else {
                drawBox(cv, x, BOX_Y + b.enter * 14f, 0.94f + 0.06f * (1f - b.enter), b, 255)
            }
        }
        for (f in g.flyers) {
            val e = f.t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            val tx = g.boxX[f.boxIdx] + (f.hole - 1) * 22f; val ty = BOX_Y - 12f
            ball(cv, f.x0 + (tx - f.x0) * e, f.y0 + (ty - f.y0) * e - sin(e * PI.toFloat()) * 10f, 11f - 1.5f * e, f.c)
        }
        for (r in g.rockets) {                    // ракета летит от кнопки к коробке
            val tx = g.boxX[r.boxIdx]; val ty = BOX_Y
            val e = r.t.coerceIn(0f, 1f)
            val x = ROCKET_X + (tx - ROCKET_X) * e; val y = BOOST_Y + (ty - BOOST_Y) * e
            cv.save(); cv.translate(x, y)
            cv.rotate(Math.toDegrees(atan2((ty - BOOST_Y).toDouble(), (tx - ROCKET_X).toDouble())).toFloat() + 90f)
            drawRocketShape(cv, 1.3f, true); cv.restore()
        }
        for (sp in g.sparks) { p.style = Paint.Style.FILL; p.color = alpha(pal[sp.c][0], (sp.life * 255).toInt()); cv.drawCircle(sp.x, sp.y, 4f, p) }

        drawBoosters(cv, g)
        if (g.state != State.PLAY && endT > 0.7f) drawEnd(cv, g)
    }

    private fun drawBelt(cv: Canvas, g: Game) {
        // панель
        rect.set(8f, 312f, 352f, 472f); p.style = Paint.Style.FILL; p.color = 0xFF1A2A52.toInt()
        cv.drawRoundRect(rect, 28f, 28f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFF3E5694.toInt(); cv.drawRoundRect(rect, 28f, 28f, p)
        // лента
        rect.set(Geo.CX - Geo.A - Geo.R, Geo.CY - Geo.R, Geo.CX + Geo.A + Geo.R, Geo.CY + Geo.R)
        path.reset(); path.addRoundRect(rect, Geo.R, Geo.R, Path.Direction.CW)
        p.strokeWidth = 38f; p.color = 0xFF0C1630.toInt(); cv.drawPath(path, p)
        p.strokeWidth = 30f; p.color = 0xFF22335F.toInt(); cv.drawPath(path, p)
        p.strokeWidth = 30f; p.color = 0x22FFFFFF; p.pathEffect = DashPathEffect(floatArrayOf(3f, 15f), -g.phase)
        cv.drawPath(path, p); p.pathEffect = null

        // воронка: стекло и стенки, внутри физические шарики
        path.reset(); path.moveTo(12f, 120f)
        for (w in WALLS) path.lineTo(w[2], w[3])
        p.style = Paint.Style.FILL; p.color = 0x18FFFFFF; cv.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF8FA3D6.toInt()
        path.reset(); path.moveTo(WALLS[0][0], WALLS[0][1])
        for (w in WALLS) path.lineTo(w[2], w[3])
        cv.drawPath(path, p); p.strokeCap = Paint.Cap.BUTT
        for (b in g.hopper) ball(cv, b.x, b.y, BODY_R, b.c)

        // шарики на ленте
        for (j in 0 until g.n) {
            val c = g.slots[j]; if (c < 0) continue
            Geo.point(g.phase + j * g.sp, pt)
            val drop = (1f - (g.time - g.born[j]) / 0.2f).coerceIn(0f, 1f)
            ball(cv, pt[0], pt[1] - drop * 18f, 11f, c)
        }
        text(cv, "${g.occupied}/${g.n}", Geo.CX, Geo.CY + 8f, 22f, if (g.occupied >= g.n - 2) 0xFFFF8A8A.toInt() else 0xCCFFFFFF.toInt())
    }

    private fun drawBoosters(cv: Canvas, g: Game) {
        boosterButton(cv, ROCKET_X, true, rocketsLeft, ROCKET_PRICE, g)
        boosterButton(cv, RETURN_X, false, returnsLeft, RETURN_PRICE, g)
        coinBadge(cv, 180f, BOOST_Y + 6f)
    }

    private fun boosterButton(cv: Canvas, x: Float, rocket: Boolean, have: Int, price: Int, g: Game) {
        val usable = g.state == State.PLAY && (have > 0 || coins >= price)
        p.style = Paint.Style.FILL; p.color = if (usable) 0xFF2C4DA8.toInt() else 0xFF1A2A52.toInt(); cv.drawCircle(x, BOOST_Y, 25f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = 0x66FFFFFF; cv.drawCircle(x, BOOST_Y, 25f, p)
        cv.save(); cv.translate(x, BOOST_Y)
        if (rocket) drawRocketShape(cv, 1f, false) else drawReturnIcon(cv)
        cv.restore()
        val label = if (have > 0) "$have" else "$price"
        p.style = Paint.Style.FILL; p.color = if (have > 0) 0xFF2FD060.toInt() else 0xFFFFC21F.toInt()
        cv.drawCircle(x + 19f, BOOST_Y - 19f, 11f, p)
        text(cv, label, x + 19f, BOOST_Y - 14.5f, if (label.length > 1) 10f else 12f, 0xFF0B1B3A.toInt())
    }

    /** Ракета носом вверх, центр в (0,0). */
    private fun drawRocketShape(cv: Canvas, sc: Float, flame: Boolean) {
        p.style = Paint.Style.FILL
        if (flame) { p.color = 0xFFFFA11F.toInt(); path.reset(); path.moveTo(-4f * sc, 9f * sc); path.lineTo(0f, 20f * sc); path.lineTo(4f * sc, 9f * sc); path.close(); cv.drawPath(path, p) }
        p.color = 0xFFE9EEFF.toInt()
        rect.set(-6f * sc, -12f * sc, 6f * sc, 11f * sc); cv.drawRoundRect(rect, 6f * sc, 6f * sc, p)
        p.color = 0xFFFF2D4A.toInt()
        path.reset(); path.moveTo(0f, -17f * sc); path.lineTo(-6f * sc, -8f * sc); path.lineTo(6f * sc, -8f * sc); path.close(); cv.drawPath(path, p)
        path.reset(); path.moveTo(-6f * sc, 4f * sc); path.lineTo(-11f * sc, 12f * sc); path.lineTo(-6f * sc, 10f * sc); path.close(); cv.drawPath(path, p)
        path.reset(); path.moveTo(6f * sc, 4f * sc); path.lineTo(11f * sc, 12f * sc); path.lineTo(6f * sc, 10f * sc); path.close(); cv.drawPath(path, p)
        p.color = 0xFF2F6BFF.toInt(); cv.drawCircle(0f, -2f * sc, 3f * sc, p)
    }

    private fun drawReturnIcon(cv: Canvas) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.2f; p.strokeCap = Paint.Cap.ROUND; p.color = 0xFFFFFFFF.toInt()
        rect.set(-9f, -6f, 9f, 12f); cv.drawArc(rect, 200f, 260f, false, p)
        p.style = Paint.Style.FILL
        path.reset(); path.moveTo(-14f, -4f); path.lineTo(-4f, -5f); path.lineTo(-9f, -13f); path.close(); cv.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun hudButton(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL; p.color = 0x4415275E; cv.drawCircle(x, y, 22f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x55FFFFFF; cv.drawCircle(x, y, 22f, p)
    }

    private fun drawBack(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFFFFFFFF.toInt()
        path.reset(); path.moveTo(x + 4f, y - 9f); path.lineTo(x - 5f, y); path.lineTo(x + 4f, y + 9f); cv.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawSpeaker(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
        path.reset(); path.moveTo(x - 9f, y - 4f); path.lineTo(x - 4f, y - 4f); path.lineTo(x + 3f, y - 10f)
        path.lineTo(x + 3f, y + 10f); path.lineTo(x - 4f, y + 4f); path.lineTo(x - 9f, y + 4f); path.close()
        cv.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.strokeCap = Paint.Cap.ROUND
        if (sound.enabled) { rect.set(x - 2f, y - 7f, x + 12f, y + 7f); cv.drawArc(rect, -50f, 100f, false, p) }
        else { cv.drawLine(x + 6f, y - 5f, x + 13f, y + 5f, p); cv.drawLine(x + 13f, y - 5f, x + 6f, y + 5f, p) }
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawRestart(cv: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFFFFFFFF.toInt(); p.strokeCap = Paint.Cap.ROUND
        rect.set(x - 9f, y - 9f, x + 9f, y + 9f); cv.drawArc(rect, 40f, 280f, false, p)
        p.style = Paint.Style.FILL
        path.reset(); path.moveTo(x + 9f, y - 11f); path.lineTo(x + 13f, y - 1f); path.lineTo(x + 3f, y - 2f); path.close()
        cv.drawPath(path, p); p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawEnd(cv: Canvas, g: Game) {
        p.style = Paint.Style.FILL; p.color = 0xAA050B1E.toInt(); cv.drawRect(-ox / s, -oy / s, W + ox / s, H + oy / s, p)
        rect.set(36f, 190f, 324f, 440f); p.color = 0xFF1A2A52.toInt(); cv.drawRoundRect(rect, 28f, 28f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFF3E5694.toInt(); cv.drawRoundRect(rect, 28f, 28f, p)
        val won = g.state == State.WON
        text(cv, if (won) "Готово!" else "Конвейер забит", 180f, 250f, 30f)
        val sub = when {
            !won -> "Нет подходящих коробок"
            current == LEVEL_COUNT -> "Все $LEVEL_COUNT уровней пройдены!  +$reward"
            else -> "Уровень ${g.level.id} пройден  +$reward монет"
        }
        text(cv, sub, 180f, 280f, 15f, 0xBBFFFFFF.toInt())
        button(cv, 60f, 300f, 300f, 356f, if (won && current < LEVEL_COUNT) "Дальше" else "Ещё раз", true)
        button(cv, 60f, 372f, 300f, 420f, "Меню", false)
    }
}
