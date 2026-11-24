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
private const val SPLASH_LEN = 3.1f     // сколько секунд длится заставка
private const val VERSION_FALLBACK = "0.9.2"

/**
 * Вид игры: цикл кадров, ввод и переходы между экранами.
 * Рисование вынесено в [GameRenderer] и [Screens], тяжёлая графика запекается в [Assets].
 */
class GameView(context: Context) : View(context) {
    private enum class Screen { SPLASH, MENU, LEVELS, PLAY, HELP, SETTINGS }

    private val progress = Progress(context).also { Lang.set(it.langMode); Theme.dark = it.darkTheme }
    private val sound = Sound(context).also { it.enabled = progress.sound }
    private val ui = Ui()
    private val version = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: VERSION_FALLBACK } catch (e: Exception) { VERSION_FALLBACK }
    private val touchSlop = 12f * resources.displayMetrics.density
    private val fadePaint = Paint()

    private var screen = Screen.SPLASH
    private var splashT = 0f
    private var hintInfo = ""          // пояснение к текущей подсказке (окно по значку «i»)
    private var infoOpen = false
    private var viewW = 0
    private var viewH = 0
    private var game: Game? = null
    private var current = progress.unlocked
    private var reward = 0
    private var paused = false
    private var fade = 0f
    private var hint = ""
    private var hintAt = 0f
    private var stars = 0
    private var hintsUsed = 0          // подсказки, взятые на этом уровне (влияют на звёзды)
    private var idle = 0f              // сколько секунд игрок ничего не нажимал
    private var idleTipShown = false
    private var fullTipShown = false

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
        val g = game
        if (screen == Screen.PLAY && g != null && !paused && !infoOpen) {
            g.update(dt)
            if (g.state != State.PLAY) ui.endT += dt else tutorialTick(g, dt)
        }
    }

    /** Подсказки по ходу игры: указатель гаснет сам; если игрок завис или лента почти полна — мягкий совет. */
    private fun tutorialTick(g: Game, dt: Float) {
        if (ui.hintT > 0f) { ui.hintT -= dt; if (ui.hintT <= 0f) ui.hintCol = -1 }
        idle += dt
        val quiet = hint.isEmpty() || ui.clock - hintAt > 6f
        val ls = lesson
        if (ls != null) { lessonTick(g, ls, quiet); return }        // на учебном уровне остальные советы молчат
        if (current <= 2 && idle > 3.5f && ui.hintCol < 0 && Hints.calm(g)) {   // самые первые уровни: сразу показываем пальцем
            val a = Hints.advise(g, 5_000)
            if (a.kind == Advice.TAP) point(a.col)
            idle = 0f
        } else if (!idleTipShown && idle > 14f && quiet && progress.lessonDone(LS_BULB)) {
            idleTipShown = true; say(tr("Не знаешь, что нажать? Лампочка покажет ход", "Not sure what to tap? The bulb shows a move"), bulbInfo())
        }
        if (!fullTipShown && quiet && g.n - g.occupied <= 1) {
            fullTipShown = true
            if (progress.lessonDone(LS_RETURN)) say(tr("Лента почти полна! Поможет лампочка или «Вернуть»", "The belt is almost full! Try the bulb or Return"), fullInfo())
            else say(tr("Лента почти полна! Сыпь только то, что нужно коробкам", "The belt is almost full! Pour only what the boxes need"), "")
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

    /** Урок усвоен: убираем подсветку и хвалим (silent — когда игра уже сказала своё). */
    private fun lessonPassed(silent: Boolean = false) {
        val ls = lesson ?: return
        if (lessonOk) return
        lessonOk = true; ui.focusAid = -1; ui.hintCol = -1
        if (!silent) say(LessonText.praise(ls))
    }

    /** Игрок нажал на лоток: `launched` — сколько лотков высыпалось этим нажатием. */
    private fun lessonTap(launched: Int) {
        val ls = lesson ?: return
        if (lessonOk) return
        if (ls.id == LS_BASICS || (ls.id == LS_LINKS && launched >= 2)) lessonPassed()
    }

    /** Пока урок не усвоен: напоминает задание и показывает пальцем, что нажать. */
    private fun lessonTick(g: Game, ls: Lesson, quiet: Boolean) {
        if (ls.id == LS_LIFT && g.elevatorIsOpen) lessonPassed()
        if (ls.id == LS_LINKS && !lessonOk && linkLeader(g) < 0) lessonPassed(silent = true)   // связи уже нет: нажать нечего
        if (!lessonOk && quiet) say(LessonText.task(ls))
        if (ui.hintCol >= 0 || !Hints.calm(g)) return
        when (ls.id) {
            LS_BASICS -> if (idle > (if (lessonOk) 3.5f else 1.2f)) {
                val a = Hints.advise(g, 5_000)
                if (a.kind == Advice.TAP) point(a.col)
                idle = 0f
            }
            LS_LINKS -> if (!lessonOk && idle > 0.8f) { val c = linkLeader(g); if (c >= 0) point(c); idle = 0f }
        }
    }

    private fun say(msg: String, info: String = "") { hint = msg; hintInfo = info; hintAt = ui.clock }

    private fun hintAlphaNow() = if (hint.isEmpty()) 0f else (8f - (ui.clock - hintAt)).coerceIn(0f, 1f)

    // пояснения, которые открываются по значку «i» рядом с подсказкой
    private fun bulbInfo() = tr(
        "Лампочка просчитывает партию и показывает, какой лоток нажать. Подсказки бесплатны, пока они есть, потом по ${Progress.HINT_PRICE} монет. Подсказки и бустеры влияют на звёзды: без помощи их три.",
        "The bulb analyzes the position and points to the tray to tap. Hints are free while you have them, then ${Progress.HINT_PRICE} coins each. Hints and boosters affect stars: with no help you get three.")
    private fun fullInfo() = tr(
        "Конвейер вмещает ограниченное число шариков. Если он забьётся и ни одна коробка не подойдёт, уровень проигран. «Вернуть» снимает до 8 шариков с ленты и возвращает их в лотки.",
        "The belt holds a limited number of beads. If it jams and no box matches, the level is lost. Return takes up to 8 beads off the belt and puts them back into trays.")

    private fun openInfo() {
        if (hint.isEmpty() || hintInfo.isEmpty()) return
        screens?.prepareInfo(hint, hintInfo)
        infoOpen = true
    }

    private fun point(col: Int) { ui.hintCol = col; ui.hintT = 4.5f }

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
        if (infoOpen) { infoOpen = false; hintAt = ui.clock; return true }
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
        hint = ""; hintInfo = ""; infoOpen = false; hintAt = ui.clock; hintsUsed = 0; idle = 0f; idleTipShown = false; fullTipShown = false
        stars = 0; ui.hintCol = -1; ui.hintT = 0f
        // урок: новая механика появляется на своём уровне (карточка, тренировка, приз)
        lesson = Lessons.atLevel(current)?.takeIf { !progress.lessonDone(it.id) }
        lessonOk = false; freeUsed = 0; prizeText = ""
        ui.freeAid = lesson?.aid ?: -1; ui.focusAid = ui.freeAid
        game = Game(lv) { ev, arg ->
            sound.event(ev, arg)
            if (ev == EV_WIN || ev == EV_LOSE) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (ev == EV_WIN) {
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

    private fun hit(x: Float, y: Float): Int {
        when (screen) {
            Screen.SPLASH -> return BTN_NONE
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
                else -> BTN_NONE
            }
            Screen.HELP -> return if (Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f)) BTN_BACK else BTN_NONE
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
                if (infoOpen) return if (Layout.inRect(Layout.INFO_OK, x, y) || !Layout.inRect(Layout.INFO_CARD, x, y)) BTN_INFO_OK else BTN_BLOCK
                if (paused) return if (Layout.inRect(Layout.PAUSE_BTN, x, y)) BTN_RESUME else BTN_BLOCK
                if (g.state != State.PLAY && ui.endT > 0.4f) {
                    if (ui.endT < 0.9f) return BTN_BLOCK
                    return when {
                        g.state == State.LOST && progress.canRevive() && Layout.inRect(Layout.END_REVIVE, x, y) -> BTN_REVIVE
                        Layout.inRect(Layout.END_NEXT, x, y) -> BTN_NEXT
                        Layout.inRect(Layout.END_MENU, x, y) -> BTN_MENU
                        else -> BTN_BLOCK
                    }
                }
                return when {
                    hintInfo.isNotEmpty() && hintAlphaNow() > 0.3f && renderer?.hintHit(x, y) == true -> BTN_INFO
                    Layout.inCircle(x, y, Layout.HUD_R_X, Layout.HUD_Y, Layout.HUD_R + 6f) -> BTN_RESTART
                    Layout.inCircle(x, y, Layout.HUD_L_X, Layout.HUD_Y, Layout.HUD_R + 6f) -> BTN_SOUND
                    Layout.inCircle(x, y, ROCKET_X, BOOST_Y, Layout.BOOST_R + 6f) && boosterOn(AID_ROCKET) -> BTN_ROCKET
                    Layout.inCircle(x, y, Layout.HINT_BX, BOOST_Y, Layout.BOOST_R + 6f) && boosterOn(AID_HINT) -> BTN_HINT
                    Layout.inCircle(x, y, RETURN_X, BOOST_Y, Layout.BOOST_R + 6f) && boosterOn(AID_RETURN) -> BTN_RETURN
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
        if (id in BTN_LANG..BTN_LANG + 2) { progress.langMode = id - BTN_LANG; Lang.set(progress.langMode); return }
        if (id == BTN_THEME || id == BTN_THEME + 1) {
            val dark = id == BTN_THEME
            if (dark != Theme.dark) { progress.darkTheme = dark; Theme.dark = dark; build(); fade = 1f }
            return
        }
        val g = game
        when (id) {
            BTN_PLAY -> startLevel(progress.unlocked)
            BTN_LEVELS -> go(Screen.LEVELS)
            BTN_SOUND -> { progress.sound = !progress.sound; sound.enabled = progress.sound }
            BTN_BACK, BTN_MENU -> go(Screen.MENU)
            BTN_RESTART -> startLevel(current)
            BTN_RESUME -> { paused = false; lastNs = 0L; smoothDt = 0f }
            BTN_ROCKET -> if (g != null) useBooster(AID_ROCKET, g)
            BTN_RETURN -> if (g != null) useBooster(AID_RETURN, g)
            BTN_HINT -> if (g != null) useHint(g)
            BTN_REVIVE -> if (g != null && g.state == State.LOST && progress.canRevive() && g.revive()) {
                progress.spendRevive(); ui.endT = 0f; idle = 0f
            }
            BTN_HELP -> go(Screen.HELP)
            BTN_SETTINGS -> go(Screen.SETTINGS)
            BTN_INFO -> openInfo()
            BTN_INFO_OK -> { infoOpen = false; hintAt = ui.clock }
            BTN_SOUND_ON -> { progress.sound = true; sound.enabled = true }
            BTN_SOUND_OFF -> { progress.sound = false; sound.enabled = false }
            BTN_GIFT -> {
                val c = progress.claimGift()
                ui.menuMsg = if (c > 0) tr("Подарок: +$c монет · день ${progress.giftStreak} подряд", "Gift: +$c coins · day ${progress.giftStreak} in a row") else tr("Следующий подарок будет завтра", "Next gift is tomorrow")
                ui.menuMsgAt = ui.clock
            }
            BTN_NEXT -> if (g != null) startLevel(if (g.state == State.WON && current < LEVEL_COUNT) current + 1 else current)
        }
    }

    private fun useBooster(aid: Int, g: Game) {
        val free = ui.freeAid == aid                 // на учебном уровне помощник бесплатный и не портит звёзды
        if (g.state != State.PLAY || !(free || progress.canAfford(aid))) return
        if (if (aid == AID_ROCKET) g.useRocket() else g.returnMarbles()) {
            idle = 0f
            if (free) { freeUsed++; lessonPassed() } else progress.spend(aid)
        }
    }

    /**
     * Умная подсказка: игра переводится в модель и ищется выигрышная последовательность от *текущего* положения.
     * Платим только за ход, который реально показали; «подожди» и «тупик» бесплатны.
     */
    private fun useHint(g: Game) {
        if (g.state != State.PLAY) return
        val free = ui.freeAid == AID_HINT
        if (!free && !progress.canAfford(AID_HINT)) {
            say(tr("Не хватает монет на подсказку (${Progress.HINT_PRICE})", "Not enough coins for a hint (${Progress.HINT_PRICE})"),
                tr("Подсказка стоит ${Progress.HINT_PRICE} монет, когда бесплатные закончились. Монеты дают за пройденные уровни и ежедневный подарок в меню.",
                    "A hint costs ${Progress.HINT_PRICE} coins once the free ones run out. Coins come from finished levels and the daily gift in the menu."))
            return
        }
        val a = Hints.advise(g, 30_000)
        when (a.kind) {
            Advice.TAP, Advice.UNSURE -> {
                if (free) lessonPassed(silent = true) else { progress.spend(AID_HINT); hintsUsed++ }
                idle = 0f
                point(a.col)
                if (a.kind == Advice.TAP) say(tr("Нажми сюда: это ведёт к победе", "Tap here: this leads to a win"),
                    tr("Лампочка нашла ходы, которые приводят к победе из текущего положения. Выделенный лоток — первый в этой цепочке.",
                        "The bulb found a winning sequence from the current position. The highlighted tray is its first step."))
                else say(tr("Точно просчитать не вышло, но ход разумный", "Could not calculate it fully, but this move is sensible"),
                    tr("Положение слишком сложное, чтобы перебрать все варианты. Лампочка показала ход, после которого на ленте остаётся меньше всего шариков.",
                        "The position is too complex to check every option. The bulb picked the move that leaves the fewest beads on the belt."))
            }
            Advice.WAIT -> say(tr("Подожди: шарики ещё едут", "Wait: the beads are still moving"),
                tr("Пока шарики падают и едут по ленте, неизвестно, куда они попадут, поэтому совет был бы неточным. Это бесплатно.",
                    "While beads are falling and riding the belt, it is unclear where they end up, so any advice would be inaccurate. This one is free."))
            Advice.STUCK -> say(tr("Похоже, тупик. Поможет «Вернуть» или заново", "Looks like a dead end. Try Return or restart"),
                tr("Из этого положения выиграть уже нельзя. «Вернуть» освободит ленту, а кнопка со стрелкой в углу начнёт уровень сначала. Подсказка бесплатная.",
                    "This position can no longer be won. Return frees the belt, or restart the level with the button in the corner. This hint is free."))
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
            Screen.PLAY -> game?.let { g ->
                renderer?.draw(cv, g, ui, hint, ui.clock - hintAt, hintInfo.isNotEmpty())
                if (g.state != State.PLAY) { screens?.end(cv, g, ui, reward, current == LEVEL_COUNT, stars, prizeText); renderer?.confetti(cv, g) }
                if (paused) screens?.pause(cv, ui)
                if (infoOpen) screens?.info(cv, ui)
            }
        }
        cv.restore()
        if (fade > 0f) {
            fadePaint.color = Theme.alpha(Theme.fade, (fade * fade * 255).toInt())
            cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
    }
}
