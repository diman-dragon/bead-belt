package dev.beadbelt.game

// идентификаторы нажимаемых элементов
const val BTN_NONE = 0
const val BTN_PLAY = 1
const val BTN_LEVELS = 2
const val BTN_SOUND = 3
const val BTN_BACK = 4
const val BTN_RESTART = 5
const val BTN_ROCKET = 6
const val BTN_RETURN = 7
const val BTN_NEXT = 8
const val BTN_MENU = 9
const val BTN_RESUME = 10
const val BTN_HINT = 11
const val BTN_REVIVE = 12
const val BTN_GIFT = 13
const val BTN_HELP = 14
const val BTN_SETTINGS = 15
const val BTN_INFO = 16         // значок «i» рядом с подсказкой
const val BTN_INFO_OK = 17      // закрыть окно с пояснением
const val BTN_LANG = 20         // BTN_LANG + 0 авто, 1 русский, 2 английский
const val BTN_THEME = 30        // BTN_THEME + 0 тёмная, 1 светлая
const val BTN_SOUND_ON = 40
const val BTN_SOUND_OFF = 41
const val BTN_LEVEL = 1000      // BTN_LEVEL + номер уровня

/** Раскладка экранов в логических координатах 360x640 — общая для рисования и для нажатий. */
object Layout {
    // меню
    val MENU_PLAY = floatArrayOf(50f, 340f, 310f, 408f)
    val MENU_LEVELS = floatArrayOf(50f, 424f, 310f, 476f)
    const val MENU_SOUND_X = 180f
    const val MENU_SOUND_Y = 530f
    const val MENU_GIFT_X = 108f
    const val MENU_HELP_X = 252f
    const val MENU_ROUND_R = 25f
    const val MENU_GEAR_X = 38f
    const val MENU_GEAR_Y = 40f

    // настройки: ряды плашек
    val SET_CARD = floatArrayOf(20f, 86f, 340f, 420f)
    val LANG_CHIPS = Array(3) { floatArrayOf(36f + it * 99f, 146f, 126f + it * 99f, 190f) }
    val THEME_CHIPS = Array(2) { floatArrayOf(36f + it * 148f, 254f, 176f + it * 148f, 298f) }
    val SOUND_CHIPS = Array(2) { floatArrayOf(36f + it * 148f, 362f, 176f + it * 148f, 406f) }

    // окно «что это за подсказка»
    val INFO_CARD = floatArrayOf(24f, 130f, 336f, 504f)
    val INFO_OK = floatArrayOf(80f, 446f, 280f, 490f)
    /** Окно подсказки в игре (всплывающая карточка под шапкой): рамка, текст в одну-две строки, значок «i» справа. */
    const val HINT_X0 = 12f
    const val HINT_X1 = 348f
    const val HINT_TOP = 64f
    const val HINT_H1 = 32f
    const val HINT_H2 = 42f
    const val HINT_SIZE = 13f
    const val HINT_TEXT_X = 62f
    const val HINT_TEXT_W = 274f
    const val HINT_INFO_X = 328f

    // кнопки HUD
    const val HUD_L_X = 38f
    const val HUD_R_X = 322f
    const val HUD_Y = 40f
    const val HUD_R = 22f
    const val BOOST_R = 26f
    // нижний ряд: ракета, подсказка, монеты, возврат
    const val HINT_BX = 112f
    const val COIN_BX = 204f

    // список уровней: сетка 5 в ряд с прокруткой
    const val LV_COLS = 5
    const val LV_X0 = 16f
    const val LV_W = 56f
    const val LV_H = 60f
    const val LV_GAP_X = 12f
    const val LV_PITCH = 72f
    const val LV_TOP = 92f
    const val LV_BOTTOM = 606f
    val LV_ROWS = (LEVEL_COUNT + LV_COLS - 1) / LV_COLS
    val LV_CONTENT = LV_ROWS * LV_PITCH

    // итоговая карточка
    val END_CARD = floatArrayOf(36f, 150f, 324f, 490f)
    val END_NEXT = floatArrayOf(60f, 374f, 300f, 428f)
    val END_MENU = floatArrayOf(60f, 440f, 300f, 478f)
    val END_REVIVE = floatArrayOf(60f, 316f, 300f, 364f)

    // пауза
    val PAUSE_BTN = floatArrayOf(60f, 300f, 300f, 356f)

    fun inRect(r: FloatArray, x: Float, y: Float) = x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]
    fun inCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean { val dx = x - cx; val dy = y - cy; return dx * dx + dy * dy <= r * r }

    fun levelX(i: Int) = LV_X0 + (i % LV_COLS) * (LV_W + LV_GAP_X)
    fun levelY(i: Int, scroll: Float) = LV_TOP + (i / LV_COLS) * LV_PITCH - scroll
    fun maxScroll() = (LV_CONTENT - (LV_BOTTOM - LV_TOP) + 12f).coerceAtLeast(0f)
}

/** Состояние интерфейса, общее для ввода и рисования. */
class Ui {
    var pressId = BTN_NONE
    var press = 0f
    var clock = 0f
    var scroll = 0f
    var endT = 0f
    /** Колонка, на которую указывает подсказка (-1 — нет), и сколько секунд ещё показывать. */
    var hintCol = -1
    var hintT = 0f
    /** Сообщение в меню (подарок и т. п.) и время его появления. */
    var menuMsg = ""
    var menuMsgAt = -100f
    /** Время (по ui.clock), когда открыто меню: от него идут анимации появления. */
    var menuAt = 0f
    fun pr(id: Int) = if (pressId == id) press else 0f
}
