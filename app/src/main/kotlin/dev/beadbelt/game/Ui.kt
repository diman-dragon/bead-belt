package dev.beadbelt.game

// идентификаторы нажимаемых элементов
const val BTN_NONE = 0
const val BTN_PLAY = 1
const val BTN_LEVELS = 2
const val BTN_SOUND = 3
const val BTN_BACK = 4
const val BTN_RESTART = 5
const val BTN_UNDO = 6          // отменить последний ход
const val BTN_CLEAR = 7         // очистить ленту
const val BTN_NEXT = 8
const val BTN_MENU = 9
const val BTN_RESUME = 10
const val BTN_HINT = 11
const val BTN_REVIVE = 12
const val BTN_GIFT = 13
const val BTN_HELP = 14
const val BTN_SETTINGS = 15
const val BTN_INFO_OK = 17      // закрыть карточку урока
const val BTN_SHUFFLE = 18      // перемешать лотки
const val BTN_SHOP = 19         // открыть магазин
const val BTN_LANG = 20         // BTN_LANG + 0 авто, 1 русский, 2 английский
const val BTN_RESTORE = 23      // «Восстановить покупки»
const val BTN_AD_CLOSE = 24     // закрыть тестовую заглушку рекламы
const val BTN_AD_REVIVE = 25    // ещё шанс за видео
const val BTN_AD_DOUBLE = 26    // удвоить награду за видео
const val BTN_THEME = 30        // BTN_THEME + 0 тёмная, 1 светлая
const val BTN_SOUND_ON = 40
const val BTN_SOUND_OFF = 41
const val BTN_SHOP_ITEM = 100   // BTN_SHOP_ITEM + номер строки магазина
const val BTN_LEVEL = 1000      // BTN_LEVEL + номер уровня

/** Раскладка экранов в логических координатах 360x640 — общая для рисования и для нажатий. */
object Layout {
    // меню
    val MENU_PLAY = floatArrayOf(50f, 340f, 310f, 408f)
    val MENU_LEVELS = floatArrayOf(50f, 424f, 310f, 476f)
    const val MENU_SOUND_X = 147f
    const val MENU_SOUND_Y = 530f
    const val MENU_GIFT_X = 81f
    const val MENU_HELP_X = 213f
    const val MENU_SHOP_X = 279f
    val MENU_COIN = floatArrayOf(224f, 14f, 344f, 58f)
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
    // кнопки шапки на экранах вне игры
    const val HUD_L_X = 38f
    const val HUD_R_X = 322f
    const val HUD_Y = 40f
    const val HUD_R = 22f
    val BAR_COIN = floatArrayOf(236f, 20f, 356f, 60f)

    // игра: шапка прижата к самому верху; под названием уровня — монеты (нажатие открывает магазин)
    const val PLAY_Y = 24f
    /** Слева в игре два кружка: назад в меню и звук. */
    const val PLAY_BACK_X = 30f
    const val PLAY_SOUND_X = 76f
    const val PLAY_R = 20f
    const val PLAY_COIN_Y = 57f
    val PLAY_COIN = floatArrayOf(124f, 39f, 236f, 75f)

    // нижний ряд: четыре помощника (подсказка, отменить, перемешать, очистить ленту)
    const val BOOST_R = 26f
    const val AID_X0 = 54f
    const val AID_DX = 84f
    fun aidX(aid: Int) = AID_X0 + AID_DX * aid

    // магазин: список строк (первая — видео за монеты, дальше товары)
    const val SHOP_TOP = 84f
    const val SHOP_PITCH = 56f
    const val SHOP_H = 52f
    val SHOP_ROWS = Array(1 + Shop.offers.size) { floatArrayOf(14f, SHOP_TOP + SHOP_PITCH * it, 346f, SHOP_TOP + SHOP_PITCH * it + SHOP_H) }
    val SHOP_RESTORE = floatArrayOf(90f, 592f, 270f, 624f)

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
    /** Если «ещё шанс» доступен и за монеты, и за видео, кнопка делится пополам. */
    val END_REVIVE_L = floatArrayOf(60f, 316f, 176f, 364f)
    val END_REVIVE_R = floatArrayOf(184f, 316f, 300f, 364f)
    val END_DOUBLE = floatArrayOf(236f, 322f, 320f, 358f)
    val AD_CLOSE = floatArrayOf(80f, 440f, 280f, 490f)

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
    /** Помощник, который на несколько секунд подсвечивается кольцом (-1 — нет): мягкая подсказка без текста. */
    var nudgeAid = -1
    var nudgeT = 0f
    /** Помощник, которым можно пользоваться бесплатно на учебном уровне (-1 — нет), и помощник, на который указывает урок. */
    var freeAid = -1
    var focusAid = -1
    /** Сообщение в меню (подарок и т. п.) и время его появления. */
    var menuMsg = ""
    var menuMsgAt = -100f
    /** Время (по ui.clock), когда открыто меню: от него идут анимации появления. */
    var menuAt = 0f
    fun pr(id: Int) = if (pressId == id) press else 0f
}
