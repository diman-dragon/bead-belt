package dev.beadbelt.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

private const val BTN_BLOCK = -1     // нажатие поглощено (диалог, пауза), но действия нет

/**
 * Вид игры: цикл кадров, ввод и переходы между экранами.
 * Рисование вынесено в [GameRenderer] и [Screens], тяжёлая графика запекается в [Assets].
 */
class GameView(context: Context) : View(context) {
    private enum class Screen { MENU, LEVELS, PLAY }

    private val progress = Progress(context)
    private val sound = Sound(context).also { it.enabled = progress.sound }
    private val ui = Ui()
    private val version = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    private val touchSlop = 12f * resources.displayMetrics.density
    private val fadePaint = Paint()

    private var screen = Screen.MENU
    private var game: Game? = null
    private var current = progress.unlocked
    private var reward = 0
    private var paused = false
    private var fade = 0f
    private var hint = ""
    private var hintAt = 0f

    private var running = false
    private var lastNs = 0L
    private var smoothDt = 0f

    // геометрия: поле 360x640 вписано в экран
    private var s = 1f
    private var ox = 0f
    private var oy = 0f
    private var painter: Painter? = null
    private var renderer: GameRenderer? = null
    private var screens: Screens? = null

    // касания и прокрутка списка уровней
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var lastMoveY = 0f
    private var lastMoveT = 0L
    private var flingV = 0f

    init { LevelCache.prefetch(progress.unlocked) }

    // ---------- размер ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        s = min(w / W, h / H)
        ox = (w - W * s) / 2f
        oy = (h - H * s) / 2f
        val assets = Assets(s)
        val p = Painter(assets, w.toFloat(), h.toFloat())
        painter = p
        renderer = GameRenderer(p, assets, progress)
        screens = Screens(p, progress, version)
    }

    // ---------- цикл кадров ----------

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            if (!running) return
            val raw = if (lastNs == 0L) 0f else min((ns - lastNs) / 1e9f, 0.05f)
            lastNs = ns
            // Сглаживание шага времени: шум таймстемпов не превращается в рывки, а настоящий пропуск кадра проходит как есть
            smoothDt = if (raw == 0f) smoothDt else if (smoothDt == 0f || abs(raw - smoothDt) > smoothDt * 0.5f) raw else smoothDt + (raw - smoothDt) * 0.15f
            val dt = if (raw == 0f) 0f else smoothDt
            update(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun update(dt: Float) {
        ui.clock += dt
        fade = max(0f, fade - dt * 3.5f)
        if (ui.pressId != BTN_NONE) ui.press = min(1f, ui.press + dt * 14f)
        if (screen == Screen.LEVELS && !dragging && abs(flingV) > 4f) {
            ui.scroll = (ui.scroll + flingV * dt).coerceIn(0f, Layout.maxScroll())
            flingV *= exp(-3.2f * dt)
        }
        val g = game
        if (screen == Screen.PLAY && g != null && !paused) {
            g.update(dt)
            if (g.state != State.PLAY) ui.endT += dt
        }
    }

    private fun startLoop() {
        if (running || !isAttachedToWindow) return
        running = true; lastNs = 0L; smoothDt = 0f
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startLoop() }
    override fun onDetachedFromWindow() { stopLoop(); super.onDetachedFromWindow() }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) startLoop() else autoPause()
    }

    private fun autoPause() { if (screen == Screen.PLAY && game?.state == State.PLAY) paused = true }

    /** Активность ушла в фон: ставим паузу и перестаём гонять кадры. */
    fun pauseGame() { autoPause(); stopLoop() }
    fun resumeGame() { startLoop() }
    fun release() = sound.release()

    /** Назад: из игры и списка — в меню. Из меню — выход из приложения. */
    fun onBack(): Boolean {
        if (screen == Screen.MENU) return false
        go(Screen.MENU)
        return true
    }

    // ---------- переходы ----------

    private fun go(next: Screen) {
        screen = next; fade = 1f; ui.pressId = BTN_NONE; flingV = 0f
        if (next == Screen.LEVELS) {
            val row = (progress.unlocked - 1) / Layout.LV_COLS
            ui.scroll = (row * Layout.LV_PITCH - 180f).coerceIn(0f, Layout.maxScroll())
        }
    }

    private var loading = false

    private fun startLevel(id: Int) {
        val target = id.coerceIn(1, LEVEL_COUNT)
        if (loading) return
        if (LevelCache.peek(target) == null) {        // генерация уровня не должна фризить кадры
            loading = true; fade = 1f
            LevelCache.load(target) { post { loading = false; startLevel(target) } }
            return
        }
        current = target
        ui.endT = 0f; reward = 0; paused = false
        hint = when (current) {
            1 -> "Нажми на лоток: шарики поедут по конвейеру"
            3 -> "Ракета внизу мгновенно закрывает коробку"
            8 -> "Золотая скоба: тап запустит оба лотка сразу"
            15 -> "Замок: коробка откроется после первой собранной"
            else -> ""
        }
        hintAt = ui.clock
        game = Game(LevelCache.get(current)) { ev, arg ->
            sound.event(ev, arg)
            if (ev == EV_WIN || ev == EV_LOSE) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (ev == EV_WIN) reward = progress.win(current)
        }
        LevelCache.prefetch(current + 1)
        go(Screen.PLAY)
    }

    // ---------- ввод ----------

    private fun hit(x: Float, y: Float): Int {
        when (screen) {
            Screen.MENU -> return when {
                Layout.inRect(Layout.MENU_PLAY, x, y) -> BTN_PLAY
                Layout.inRect(Layout.MENU_LEVELS, x, y) -> BTN_LEVELS
                Layout.inCircle(x, y, Layout.MENU_SOUND_X, Layout.MENU_SOUND_Y, 30f) -> BTN_SOUND
                else -> BTN_NONE
            }
            Screen.LEVELS -> {
                if (Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f)) return BTN_BACK
                if (y < Layout.LV_TOP || y > Layout.LV_BOTTOM) return BTN_NONE
                val cell = Layout.LV_W + Layout.LV_GAP_X
                val col = ((x - Layout.LV_X0) / cell).toInt()
                val cx = x - Layout.LV_X0 - col * cell
                val ry = y + ui.scroll - Layout.LV_TOP
                val row = (ry / Layout.LV_PITCH).toInt()
                val id = row * Layout.LV_COLS + col + 1
                if (x >= Layout.LV_X0 && col in 0 until Layout.LV_COLS && cx <= Layout.LV_W && ry >= 0f &&
                    ry - row * Layout.LV_PITCH <= Layout.LV_H && id in 1..progress.unlocked) return BTN_LEVEL + id
                return BTN_NONE
            }
            Screen.PLAY -> {
                val g = game ?: return BTN_NONE
                if (paused) return if (Layout.inRect(Layout.PAUSE_BTN, x, y)) BTN_RESUME else BTN_BLOCK
                if (g.state != State.PLAY && ui.endT > 0.4f) {
                    if (ui.endT < 0.9f) return BTN_BLOCK
                    return when {
                        Layout.inRect(Layout.END_NEXT, x, y) -> BTN_NEXT
                        Layout.inRect(Layout.END_MENU, x, y) -> BTN_MENU
                        else -> BTN_BLOCK
                    }
                }
                return when {
                    Layout.inCircle(x, y, Layout.HUD_R_X, Layout.HUD_Y, Layout.HUD_R + 6f) -> BTN_RESTART
                    Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f) -> BTN_SOUND
                    Layout.inCircle(x, y, ROCKET_X, BOOST_Y, Layout.BOOST_R + 6f) -> BTN_ROCKET
                    Layout.inCircle(x, y, RETURN_X, BOOST_Y, Layout.BOOST_R + 6f) -> BTN_RETURN
                    else -> BTN_NONE
                }
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = (e.x - ox) / s
        val y = (e.y - oy) / s
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y; downScroll = ui.scroll; dragging = false; flingV = 0f
                lastMoveY = e.y; lastMoveT = e.eventTime
                val id = hit(x, y)
                ui.pressId = if (id > 0) id else BTN_NONE; ui.press = 0f
                // лотки реагируют сразу на касание — так отзывчивее, чем на отпускание
                if (id == BTN_NONE && screen == Screen.PLAY) game?.tap(x, y)
            }
            MotionEvent.ACTION_MOVE -> if (screen == Screen.LEVELS) {
                val dy = e.y - downY
                if (!dragging && abs(dy) > touchSlop) { dragging = true; ui.pressId = BTN_NONE }
                if (dragging) {
                    ui.scroll = (downScroll - dy / s).coerceIn(0f, Layout.maxScroll())
                    val dt = (e.eventTime - lastMoveT) / 1000f
                    if (dt > 0.001f) flingV = 0.7f * flingV + 0.3f * (-(e.y - lastMoveY) / s / dt)
                    lastMoveY = e.y; lastMoveT = e.eventTime
                }
            }
            MotionEvent.ACTION_UP -> {
                val id = ui.pressId
                ui.pressId = BTN_NONE
                if (dragging) { if (e.eventTime - lastMoveT > 90) flingV = 0f }
                else if (id > 0 && hit(x, y) == id) activate(id)
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> { ui.pressId = BTN_NONE; dragging = false }
        }
        return true
    }

    private fun activate(id: Int) {
        if (id >= BTN_LEVEL) { startLevel(id - BTN_LEVEL); return }
        val g = game
        when (id) {
            BTN_PLAY -> startLevel(progress.unlocked)
            BTN_LEVELS -> go(Screen.LEVELS)
            BTN_SOUND -> { progress.sound = !progress.sound; sound.enabled = progress.sound }
            BTN_BACK, BTN_MENU -> go(Screen.MENU)
            BTN_RESTART -> startLevel(current)
            BTN_RESUME -> { paused = false; lastNs = 0L; smoothDt = 0f }
            BTN_ROCKET -> if (g != null) useBooster(true, g)
            BTN_RETURN -> if (g != null) useBooster(false, g)
            BTN_NEXT -> if (g != null) startLevel(if (g.state == State.WON && current < LEVEL_COUNT) current + 1 else current)
        }
    }

    private fun useBooster(rocket: Boolean, g: Game) {
        if (g.state != State.PLAY || !progress.canAfford(rocket)) return
        if (if (rocket) g.useRocket() else g.returnMarbles()) progress.spend(rocket)
    }

    // ---------- рисование ----------

    override fun onDraw(cv: Canvas) {
        val p = painter ?: return
        p.background(cv, ui.clock)
        cv.save(); cv.translate(ox, oy); cv.scale(s, s)
        when (screen) {
            Screen.MENU -> screens?.menu(cv, ui)
            Screen.LEVELS -> screens?.levels(cv, ui)
            Screen.PLAY -> game?.let { g ->
                val hintAlpha = if (hint.isEmpty()) 0f else (6f - (ui.clock - hintAt)).coerceIn(0f, 1f)
                renderer?.draw(cv, g, ui, hint, hintAlpha)
                if (g.state != State.PLAY) { screens?.end(cv, g, ui, reward, current == LEVEL_COUNT); renderer?.confetti(cv, g) }
                if (paused) screens?.pause(cv, ui)
            }
        }
        cv.restore()
        if (fade > 0f) {
            fadePaint.color = Theme.alpha(Theme.BLACK, (fade * fade * 255).toInt())
            cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
    }
}
