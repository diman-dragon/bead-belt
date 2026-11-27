package dev.beadbelt.game

import android.content.Context
import android.content.pm.ApplicationInfo
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
private const val SPLASH_LEN = 3.1f     // сколько секунд длится заставка
private const val VERSION_FALLBACK = "0.9.3"

/**
 * Вид игры: цикл кадров, ввод и переходы между экранами.
 * Рисование вынесено в [GameRenderer] и [Screens], тяжёлая графика запекается в [Assets].
 */
class GameView(context: Context) : View(context) {
    private enum class Screen { SPLASH, MENU, LEVELS, PLAY, HELP, SETTINGS, SHOP }

    private val progress = Progress(context).also { Lang.set(it.langMode); Theme.dark = it.darkTheme }
    private val sound = Sound(context).also { it.enabled = progress.sound }
    private val ui = Ui()
    private val version = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: VERSION_FALLBACK } catch (e: Exception) { VERSION_FALLBACK }
    private val touchSlop = 12f * resources.displayMetrics.density
    private val fadePaint = Paint()

    private var screen = Screen.SPLASH
    private var splashT = 0f
    private var infoOpen = false       // открыта карточка урока
    private var viewW = 0
    private var viewH = 0
    private var game: Game? = null
    private var current = progress.unlocked
    private var reward = 0
    private var paused = false
    private var fade = 0f
    private var stars = 0
    private var hintsUsed = 0          // подсказки, взятые на этом уровне (влияют на звёзды)
    private var idle = 0f              // сколько секунд игрок ничего не нажимал

    // реклама и покупки: в отладочных сборках — тестовые заглушки, в релизе — то, что подключено в [Monetization]
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val ads: Ads = Monetization.ads(debuggable).also { it.load() }
    private val billing: Billing = Monetization.billing(debuggable)
    private val simAds = ads as? SimAds
    private var simAdT = 0f            // сколько секунд показывается тестовая заглушка
    private var busy = false           // идёт показ рекламы или оплата: повторные нажатия игнорируются
    private var winsSinceAd = 0        // побед с последней рекламы между уровнями
    private var lastAdMs = System.currentTimeMillis()   // время последней рекламы любого вида
    private var doubled = false        // награда за этот уровень уже удвоена видео
    private var shopFrom = Screen.MENU // откуда открыт магазин (туда вернёмся)

    // урок текущего уровня (null — обычный уровень или урок уже пройден)
    private var lesson: Lesson? = null
    private var lessonOk = false       // игрок уже сделал то, чему учит урок
    private var freeUsed = 0           // бесплатные применения помощников на учебном уровне (не влияют на звёзды)
    private var prizeText = ""         // приз за урок для итоговой карточки

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
        viewW = w; viewH = h
        build()
    }

    /** Запекает графику под текущие размер и тему. Вызывается при смене размера и при смене темы. */
    private fun build() {
        if (viewW <= 0 || viewH <= 0) return
        s = min(viewW / W, viewH / H)
        ox = (viewW - W * s) / 2f
        oy = (viewH - H * s) / 2f
        val assets = Assets(s)
        val p = Painter(assets, viewW.toFloat(), viewH.toFloat())
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
        if (screen == Screen.SPLASH) { splashT += dt; if (splashT >= SPLASH_LEN) go(Screen.MENU) }
        fade = max(0f, fade - dt * 3.5f)
        if (ui.pressId != BTN_NONE) ui.press = min(1f, ui.press + dt * 14f)
        if (screen == Screen.LEVELS && !dragging && abs(flingV) > 4f) {
            ui.scroll = (ui.scroll + flingV * dt).coerceIn(0f, Layout.maxScroll())
            flingV *= exp(-3.2f * dt)
        }
        val sa = simAds
        if (sa != null && sa.pending != null) simAdT += dt else simAdT = 0f
        if (ui.nudgeT > 0f) { ui.nudgeT -= dt; if (ui.nudgeT <= 0f) ui.nudgeAid = -1 }
        val g = game
        if (screen == Screen.PLAY && g != null && !paused && !infoOpen && sa?.pending == null) {
            g.update(dt)
            if (g.state != State.PLAY) ui.endT += dt else tutorialTick(g, dt)
        }
    }

    /** Ведение игрока: указатель на лоток гаснет сам; на первых уровнях и на учебных уровнях подсказывает пальцем. Текстовых подсказок в игре нет. */
    private fun tutorialTick(g: Game, dt: Float) {
        if (ui.hintT > 0f) { ui.hintT -= dt; if (ui.hintT <= 0f) ui.hintCol = -1 }
        idle += dt
        val ls = lesson
        if (ls != null) { lessonTick(g, ls); return }
        if (current <= 2 && idle > 3.5f && ui.hintCol < 0 && Hints.calm(g)) {   // самые первые уровни: показываем пальцем
            val a = Hints.advise(g, 5_000)
            if (a.kind == Advice.TAP) point(a.col)
            idle = 0f
        }
    }

    // ---------- уроки ----------

    /** Ведущий лоток действующей связи: колонка, на которую надо нажать, или -1. */
    private fun linkLeader(g: Game): Int {
        for (i in g.cols.indices) {
            val h = g.cols[i].firstOrNull() ?: continue
            if (Links.follower(h.arrow, h.pid, i, g.cols.size) { c -> g.cols[c].firstOrNull()?.pid ?: 0 } >= 0) return i
        }
        return -1
    }

    private fun traysLeft(g: Game): Int { var k = 0; for (c in g.cols) k += c.size; return k }

    private fun openLesson(ls: Lesson) {
        screens?.prepareInfo(LessonText.title(ls), LessonText.body(ls), LessonText.tag(), LessonText.ok(ls))
        infoOpen = true
    }

    /** Урок усвоен: убираем подсветку. */
    private fun lessonPassed() {
        if (lesson == null || lessonOk) return
        lessonOk = true; ui.focusAid = -1; ui.hintCol = -1
    }

    /** Игрок нажал на лоток: `launched` — сколько лотков высыпалось этим нажатием. */
    private fun lessonTap(launched: Int) {
        val ls = lesson ?: return
        if (lessonOk) return
        if (ls.id == LS_BASICS || ls.id == LS_MIXED || ls.id == LS_MYSTERY || (ls.id == LS_LINKS && launched >= 2)) lessonPassed()
    }

    /** Пока урок не усвоен, указывает пальцем, что нажать (для помощников подсвечена сама кнопка). */
    private fun lessonTick(g: Game, ls: Lesson) {
        if (ls.id == LS_LIFT && g.elevatorIsOpen) lessonPassed()
        if (ls.id == LS_HIDDEN && g.cols.any { it.firstOrNull()?.hidden == true }) lessonPassed()      // скрытый лоток вышел вперёд
        if (ls.id == LS_LINKS && !lessonOk && linkLeader(g) < 0) lessonPassed()                       // связи уже нет: нажать нечего
        if (ls.id == LS_CHAIN && g.cols.none { c -> c.any { it.lock > g.done } }) lessonPassed()    // все цепи спали
        if (ui.hintCol >= 0 || !Hints.calm(g)) return
        when (ls.id) {
            LS_BASICS -> if (idle > (if (lessonOk) 3.5f else 1.2f)) {
                val a = Hints.advise(g, 5_000)
                if (a.kind == Advice.TAP) point(a.col)
                idle = 0f
            }
            LS_MIXED, LS_CHAIN, LS_MYSTERY -> if (!lessonOk && idle > 1.5f) {
                val a = Hints.advise(g, 5_000)
                if (a.kind == Advice.TAP) point(a.col)
                idle = 0f
            }
            LS_LINKS -> if (!lessonOk && idle > 0.8f) { val c = linkLeader(g); if (c >= 0) point(c); idle = 0f }
            LS_HIDDEN -> if (!lessonOk && idle > 1.2f) {
                val c = g.cols.indices.firstOrNull { g.cols[it].size > 1 && g.cols[it][1].hidden }
                if (c != null) point(c)
                idle = 0f
            }
            // отмене и очистке нужен ход: сначала показываем лоток, потом кнопка подсвечена кольцом
            LS_UNDO, LS_CLEAR -> if (!lessonOk && idle > 1.2f && !(g.canUndo && ls.id == LS_UNDO) && !(g.canClear && ls.id == LS_CLEAR)) {
                val a = Hints.advise(g, 5_000)
                if (a.kind == Advice.TAP) point(a.col)
                idle = 0f
            }
        }
    }

    private fun point(col: Int) { ui.hintCol = col; ui.hintT = 4.5f }

    private fun now() = System.currentTimeMillis()

    /** Короткое сообщение в строке магазина и меню. */
    private fun notify(msg: String) { ui.menuMsg = msg; ui.menuMsgAt = ui.clock }

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
        if (simAds?.pending != null) return true            // тестовую рекламу закрывают её кнопкой
        if (infoOpen) { infoOpen = false; return true }
        if (screen == Screen.SHOP) { closeShop(); return true }
        if (screen == Screen.MENU) return false
        go(Screen.MENU)
        return true
    }

    // ---------- переходы ----------

    private fun go(next: Screen) {
        screen = next; fade = 1f; ui.pressId = BTN_NONE; flingV = 0f; ui.hintCol = -1; infoOpen = false
        if (next == Screen.MENU) ui.menuAt = ui.clock
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
        val lv = LevelCache.get(current)
        infoOpen = false; hintsUsed = 0; idle = 0f; doubled = false
        stars = 0; ui.hintCol = -1; ui.hintT = 0f; ui.nudgeAid = -1; ui.nudgeT = 0f
        // урок: новая механика появляется на своём уровне (карточка, тренировка, приз)
        lesson = Lessons.atLevel(current)?.takeIf { !progress.lessonDone(it.id) }
        lessonOk = false; freeUsed = 0; prizeText = ""
        ui.freeAid = lesson?.aid ?: -1; ui.focusAid = ui.freeAid
        game = Game(lv) { ev, arg ->
            sound.event(ev, arg)
            if (ev == EV_WIN || ev == EV_LOSE) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (ev == EV_WIN) {
                winsSinceAd++
                val usage = ((game?.assists ?: 0) + hintsUsed - freeUsed).coerceAtLeast(0)
                stars = Progress.starsFor(usage)
                reward = progress.win(current, usage)
                lesson?.let { progress.completeLesson(it.id); prizeText = LessonText.prize(it) }
            }
        }
        LevelCache.prefetch(current + 1)
        go(Screen.PLAY)
        lesson?.let { openLesson(it) }       // go() сбрасывает окна, поэтому открываем после перехода
    }

    // ---------- ввод ----------

    /** Помощник открыт: урок пройден или это учебный уровень именно про него. */
    private fun boosterOn(aid: Int) = ui.freeAid == aid || progress.lessonDone(Lessons.forAid(aid).id)

    private fun aidButtonId(aid: Int) = when (aid) { AID_UNDO -> BTN_UNDO; AID_SHUFFLE -> BTN_SHUFFLE; AID_CLEAR -> BTN_CLEAR; else -> BTN_HINT }

    private fun hit(x: Float, y: Float): Int {
        val sa = simAds
        if (sa != null && sa.pending != null) return if (simAdT >= 3f && Layout.inRect(Layout.AD_CLOSE, x, y)) BTN_AD_CLOSE else BTN_BLOCK
        when (screen) {
            Screen.SPLASH -> return BTN_NONE
            Screen.SHOP -> {
                if (Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f)) return BTN_BACK
                if (Layout.inRect(Layout.SHOP_RESTORE, x, y)) return BTN_RESTORE
                for (i in Layout.SHOP_ROWS.indices) if (Layout.inRect(Layout.SHOP_ROWS[i], x, y)) return BTN_SHOP_ITEM + i
                return BTN_NONE
            }
            Screen.SETTINGS -> {
                if (Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f)) return BTN_BACK
                for (i in 0..2) if (Layout.inRect(Layout.LANG_CHIPS[i], x, y)) return BTN_LANG + i
                for (i in 0..1) {
                    if (Layout.inRect(Layout.THEME_CHIPS[i], x, y)) return BTN_THEME + i
                }
                if (Layout.inRect(Layout.SOUND_CHIPS[0], x, y)) return BTN_SOUND_ON
                if (Layout.inRect(Layout.SOUND_CHIPS[1], x, y)) return BTN_SOUND_OFF
                return BTN_NONE
            }
            Screen.MENU -> return when {
                Layout.inCircle(x, y, Layout.MENU_GEAR_X, Layout.MENU_GEAR_Y, 28f) -> BTN_SETTINGS
                Layout.inRect(Layout.MENU_PLAY, x, y) -> BTN_PLAY
                Layout.inRect(Layout.MENU_LEVELS, x, y) -> BTN_LEVELS
                Layout.inCircle(x, y, Layout.MENU_SOUND_X, Layout.MENU_SOUND_Y, 30f) -> BTN_SOUND
                Layout.inCircle(x, y, Layout.MENU_GIFT_X, Layout.MENU_SOUND_Y, 30f) -> BTN_GIFT
                Layout.inCircle(x, y, Layout.MENU_HELP_X, Layout.MENU_SOUND_Y, 30f) -> BTN_HELP
                Layout.inCircle(x, y, Layout.MENU_SHOP_X, Layout.MENU_SOUND_Y, 30f) -> BTN_SHOP
                Layout.inRect(Layout.MENU_COIN, x, y) -> BTN_SHOP
                else -> BTN_NONE
            }
            Screen.HELP -> return if (Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f)) BTN_BACK else BTN_NONE
            Screen.LEVELS -> {
                if (Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f)) return BTN_BACK
                if (Layout.inRect(Layout.BAR_COIN, x, y)) return BTN_SHOP
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
                if (infoOpen) return if (Layout.inRect(Layout.INFO_OK, x, y) || !Layout.inRect(Layout.INFO_CARD, x, y)) BTN_INFO_OK else BTN_BLOCK
                if (paused) return if (Layout.inRect(Layout.PAUSE_BTN, x, y)) BTN_RESUME else BTN_BLOCK
                if (g.state != State.PLAY && ui.endT > 0.4f) {
                    if (ui.endT < 0.9f) return BTN_BLOCK
                    val coinOk = progress.canRevive(); val adOk = ads.rewardedReady
                    val canRev = g.state == State.LOST && g.canRevive()
                    return when {
                        canRev && coinOk && adOk && Layout.inRect(Layout.END_REVIVE_L, x, y) -> BTN_REVIVE
                        canRev && coinOk && adOk && Layout.inRect(Layout.END_REVIVE_R, x, y) -> BTN_AD_REVIVE
                        canRev && coinOk && !adOk && Layout.inRect(Layout.END_REVIVE, x, y) -> BTN_REVIVE
                        canRev && !coinOk && adOk && Layout.inRect(Layout.END_REVIVE, x, y) -> BTN_AD_REVIVE
                        g.state == State.WON && adOk && !doubled && prizeText.isEmpty() && reward > 0 && ui.endT > 1.5f && Layout.inRect(Layout.END_DOUBLE, x, y) -> BTN_AD_DOUBLE
                        Layout.inRect(Layout.END_NEXT, x, y) -> BTN_NEXT
                        Layout.inRect(Layout.END_MENU, x, y) -> BTN_MENU
                        else -> BTN_BLOCK
                    }
                }
                for (aid in 0 until AID_COUNT) if (Layout.inCircle(x, y, Layout.aidX(aid), BOOST_Y, Layout.BOOST_R + 6f) && boosterOn(aid)) return aidButtonId(aid)
                return when {
                    Layout.inCircle(x, y, Layout.HUD_R_X, Layout.PLAY_Y, Layout.PLAY_R + 8f) -> BTN_RESTART
                    Layout.inCircle(x, y, Layout.PLAY_BACK_X, Layout.PLAY_Y, Layout.PLAY_R + 4f) -> BTN_BACK
                    Layout.inCircle(x, y, Layout.PLAY_SOUND_X, Layout.PLAY_Y, Layout.PLAY_R + 4f) -> BTN_SOUND
                    Layout.inRect(Layout.PLAY_COIN, x, y) -> BTN_SHOP
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
                if (screen == Screen.SPLASH) { if (splashT > 0.5f) go(Screen.MENU); return true }
                downY = e.y; downScroll = ui.scroll; dragging = false; flingV = 0f
                lastMoveY = e.y; lastMoveT = e.eventTime
                val id = hit(x, y)
                ui.pressId = if (id > 0) id else BTN_NONE; ui.press = 0f
                // лотки реагируют сразу на касание — так отзывчивее, чем на отпускание
                if (id == BTN_NONE && screen == Screen.PLAY) {
                    val g = game
                    if (g != null) {
                        val before = traysLeft(g)
                        if (g.tap(x, y + LIFT)) { idle = 0f; ui.hintCol = -1; lessonTap(before - traysLeft(g)) }
                    }
                }
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
        if (id in BTN_SHOP_ITEM until BTN_SHOP_ITEM + Layout.SHOP_ROWS.size) { shopItem(id - BTN_SHOP_ITEM); return }
        if (id in BTN_LANG..BTN_LANG + 2) { progress.langMode = id - BTN_LANG; Lang.set(progress.langMode); return }
        if (id == BTN_THEME || id == BTN_THEME + 1) {
            val dark = id == BTN_THEME
            if (dark != Theme.dark) { progress.darkTheme = dark; Theme.dark = dark; build(); fade = 1f }
            return
        }
        val g = game
        when (id) {
            BTN_PLAY -> if (progress.starsOf(LEVEL_COUNT) > 0) go(Screen.LEVELS) else startLevel(progress.unlocked)
            BTN_LEVELS -> go(Screen.LEVELS)
            BTN_SOUND -> { progress.sound = !progress.sound; sound.enabled = progress.sound }
            BTN_BACK, BTN_MENU -> if (screen == Screen.SHOP) closeShop() else go(Screen.MENU)
            BTN_RESTART -> startLevel(current)
            BTN_RESUME -> { paused = false; lastNs = 0L; smoothDt = 0f }
            BTN_UNDO -> if (g != null) useAid(AID_UNDO, g)
            BTN_CLEAR -> if (g != null) useAid(AID_CLEAR, g)
            BTN_SHUFFLE -> if (g != null) useAid(AID_SHUFFLE, g)
            BTN_HINT -> if (g != null) useHint(g)
            BTN_REVIVE -> if (g != null && g.state == State.LOST && progress.canRevive() && g.revive()) {
                progress.spendRevive(); ui.endT = 0f; idle = 0f
            }
            BTN_AD_REVIVE -> if (g != null && g.canRevive()) watchRewarded { if (g.revive()) { ui.endT = 0f; idle = 0f } }
            BTN_AD_DOUBLE -> if (g != null && g.state == State.WON && !doubled) watchRewarded { progress.addCoins(reward); reward *= 2; doubled = true }
            BTN_SHOP -> openShop()
            BTN_RESTORE -> restorePurchases()
            BTN_AD_CLOSE -> simAds?.finish(true)
            BTN_HELP -> go(Screen.HELP)
            BTN_SETTINGS -> go(Screen.SETTINGS)
            BTN_INFO_OK -> infoOpen = false
            BTN_SOUND_ON -> { progress.sound = true; sound.enabled = true }
            BTN_SOUND_OFF -> { progress.sound = false; sound.enabled = false }
            BTN_GIFT -> {
                val c = progress.claimGift()
                ui.menuMsg = if (c > 0) tr("Подарок: +$c монет · день ${progress.giftStreak} подряд", "Gift: +$c coins · day ${progress.giftStreak} in a row") else tr("Следующий подарок будет завтра", "Next gift is tomorrow")
                ui.menuMsgAt = ui.clock
            }
            BTN_NEXT -> if (g != null && !busy) {
                if (g.state == State.WON && current >= LEVEL_COUNT) { go(Screen.LEVELS); return }       // последний уровень пройден
                val won = g.state == State.WON && current < LEVEL_COUNT
                val next = if (won) current + 1 else current
                if (won && ads.interstitialReady && AdPolicy.interstitialDue(current, winsSinceAd, lastAdMs, now(), progress.adsRemoved)) {
                    busy = true
                    ads.showInterstitial { post { busy = false; winsSinceAd = 0; lastAdMs = now(); ads.load(); startLevel(next) } }
                } else startLevel(next)
            }
        }
    }

    /** Помощник: отменить ход, очистить ленту или перемешать. Нет запаса и монет — открывается магазин. */
    private fun useAid(aid: Int, g: Game) {
        if (g.state != State.PLAY) return
        val free = ui.freeAid == aid                 // на учебном уровне помощник бесплатный и не портит звёзды
        if (!free && !progress.canAfford(aid)) { openShop(); return }
        val ok = when (aid) { AID_UNDO -> g.undo(); AID_CLEAR -> g.clearBelt(); else -> g.shuffle() }
        if (!ok) return
        idle = 0f; ui.hintCol = -1
        if (free) { freeUsed++; lessonPassed() } else progress.spend(aid)
    }

    /** Мягко подсвечивает кольцом кнопку помощника: подсказка без текста. */
    private fun nudge(aid: Int) { if (boosterOn(aid)) { ui.nudgeAid = aid; ui.nudgeT = 3f } }

    /**
     * Умная подсказка: игра переводится в модель и ищется выигрышная последовательность от *текущего* положения.
     * Платим только за ход, который реально показали; «подожди» и «тупик» бесплатны. Текста нет: ход показывает указатель на лотке,
     * а в тупике кольцом подсвечивается помощник, который может помочь.
     */
    private fun useHint(g: Game) {
        if (g.state != State.PLAY) return
        val free = ui.freeAid == AID_HINT
        if (!free && !progress.canAfford(AID_HINT)) { openShop(); return }
        val a = Hints.advise(g, 30_000)
        when (a.kind) {
            Advice.TAP, Advice.UNSURE -> {
                if (free) lessonPassed() else { progress.spend(AID_HINT); hintsUsed++ }
                idle = 0f
                point(a.col)
            }
            Advice.STUCK -> nudge(if (g.canClear && boosterOn(AID_CLEAR)) AID_CLEAR else AID_UNDO)
            else -> { ui.nudgeAid = AID_HINT; ui.nudgeT = 1.2f }      // шарики ещё едут: совет был бы неточным, ничего не списываем; кнопка коротко мигает кольцом
        }
    }

    // ---------- магазин, реклама, покупки ----------

    private fun openShop() {
        if (screen == Screen.SHOP) return
        shopFrom = screen
        go(Screen.SHOP)
    }

    /** Назад из магазина: туда, откуда пришли; игра при этом ждёт на паузе. */
    private fun closeShop() {
        go(shopFrom)
        if (shopFrom == Screen.PLAY && game?.state == State.PLAY) paused = true
    }

    /** Видео за награду: `onReward` вызывается, только если видео досмотрели. */
    private fun watchRewarded(onReward: () -> Unit) {
        if (busy || !ads.rewardedReady) return
        busy = true
        ads.showRewarded { earned ->
            post {
                busy = false; lastAdMs = now(); ads.load()
                if (earned) onReward() else notify(tr("Видео не досмотрено: награды нет", "Video not finished: no reward"))
            }
        }
    }

    /** Нажатие на строку магазина: 0 — видео за монеты, дальше товары. */
    private fun shopItem(i: Int) {
        if (i == 0) {
            if (progress.freeAdsLeft() <= 0) { notify(tr("На сегодня видео закончились", "No more videos today")); return }
            if (!ads.rewardedReady) { notify(tr("Видео пока недоступно", "No video right now")); return }
            watchRewarded { val c = progress.claimFreeAd(); notify(tr("+$c монет", "+$c coins")) }
            return
        }
        val o = Shop.offers.getOrNull(i - 1) ?: return
        if (progress.owned(o.sku)) { notify(tr("Уже куплено", "Already owned")); return }
        if (billing.price(o.sku) == null) { notify(tr("Покупки сейчас недоступны", "Purchases are unavailable right now")); return }
        if (busy) return
        busy = true
        billing.buy(o.sku) { ok ->
            post {
                busy = false
                if (ok && progress.applyPurchase(o.sku)) notify(tr("Спасибо! Покупка получена", "Thank you! Purchase received"))
                else if (!ok) notify(tr("Покупка не завершена", "Purchase not completed"))
            }
        }
    }

    private fun restorePurchases() {
        if (busy) return
        busy = true
        billing.restore { skus ->
            post {
                busy = false
                val n = progress.restoreOwned(skus)
                notify(if (n > 0) tr("Восстановлено покупок: $n", "Purchases restored: $n") else tr("Нечего восстанавливать", "Nothing to restore"))
            }
        }
    }

    // ---------- рисование ----------

    override fun onDraw(cv: Canvas) {
        val p = painter ?: return
        p.background(cv, ui.clock)
        cv.save(); cv.translate(ox, oy); cv.scale(s, s)
        when (screen) {
            Screen.SPLASH -> screens?.splash(cv, splashT)
            Screen.SETTINGS -> screens?.settings(cv, ui)
            Screen.MENU -> screens?.menu(cv, ui)
            Screen.LEVELS -> screens?.levels(cv, ui)
            Screen.HELP -> screens?.help(cv, ui)
            Screen.SHOP -> screens?.shop(cv, ui, billing, ads.rewardedReady, progress.freeAdsLeft())
            Screen.PLAY -> game?.let { g ->
                renderer?.draw(cv, g, ui)
                if (g.state != State.PLAY) { screens?.end(cv, g, ui, reward, current == LEVEL_COUNT, stars, prizeText, ads.rewardedReady, doubled); renderer?.confetti(cv, g) }
                if (paused) screens?.pause(cv, ui)
                if (infoOpen) screens?.info(cv, ui)
            }
        }
        val sa = simAds
        if (sa != null && sa.pending != null) screens?.fakeAd(cv, ui, simAdT, sa.interstitial)
        cv.restore()
        if (fade > 0f) {
            fadePaint.color = Theme.alpha(Theme.fade, (fade * fade * 255).toInt())
            cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
    }
}
