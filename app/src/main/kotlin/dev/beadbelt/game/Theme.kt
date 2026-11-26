package dev.beadbelt.game

import android.graphics.Typeface

/**
 * Цвета и шрифты в одном месте. Две темы: тёмная (чёрный фон, экономит батарею на OLED) и светлая.
 * Цвета, зависящие от темы, — свойства-геттеры: при смене темы Assets и Painter пересоздаются.
 */
object Theme {
    /** true — тёмная тема, false — светлая. */
    var dark = true

    val WHITE = 0xFFFFFFFF.toInt()
    val BLACK = 0xFF000000.toInt()
    val INK = 0xFF0B1B3A.toInt()
    val GOLD = 0xFFFFC21F.toInt()
    val GOLD_DEEP = 0xFFB87800.toInt()
    val GREEN = 0xFF2FD060.toInt()
    val RED = 0xFFFF5A6E.toInt()

    // ---------- фон ----------
    val bgTop: Int get() = if (dark) 0xFF0B1020.toInt() else 0xFFF3F8FF.toInt()
    val bgMid: Int get() = if (dark) 0xFF000000.toInt() else 0xFFC4D9FF.toInt()
    val bgBottom: Int get() = if (dark) 0xFF000000.toInt() else 0xFF97B6F0.toInt()
    /** Свет сверху и его сила. */
    val bgGlow: Int get() = if (dark) Theme.alpha(0xFF3A5BD9.toInt(), 46) else Theme.alpha(WHITE, 130)
    val bokehScale: Float get() = if (dark) 0.5f else 1f
    /** Виньетка по краям (в тёмной теме не нужна: фон и так чёрный). */
    val vignette: Int get() = if (dark) 0 else Theme.alpha(0xFF3A5AA8.toInt(), 70)
    /** Цвет затемнения при переходах между экранами. */
    val fade: Int get() = if (dark) BLACK else 0xFFB4CCF7.toInt()

    // ---------- текст на фоне ----------
    val text: Int get() = if (dark) WHITE else INK
    val textDim: Int get() = if (dark) Theme.alpha(WHITE, 130) else Theme.alpha(INK, 150)
    /** Золотой текст на фоне (читается на чёрном и на светлом). */
    val accentText: Int get() = if (dark) GOLD else 0xFF9A6200.toInt()
    /** Цвет знака DataLaw: буква светлая на тёмном и тёмная на светлом. */
    val logoBody: Int get() = if (dark) 0xFFF1F1F4.toInt() else 0xFF121216.toInt()

    // ---------- элементы интерфейса ----------
    val pillBg: Int get() = if (dark) Theme.alpha(0xFF1A1D27.toInt(), 215) else Theme.alpha(0xFF14265E.toInt(), 225)
    val roundFace: Int get() = if (dark) 0xE0262A36.toInt() else 0xE0224A9E.toInt()
    val roundFaceOff: Int get() = if (dark) 0xB0161922.toInt() else 0xB0172A62.toInt()
    val btn2Face: Int get() = if (dark) 0xFF343A4A.toInt() else 0xFF4470E4.toInt()
    val btn2Lip: Int get() = if (dark) 0xFF14161D.toInt() else 0xFF1B2F86.toInt()
    val chipOff: Int get() = if (dark) 0xFF262A35.toInt() else 0xFF2E4488.toInt()
    val chipOffLip: Int get() = if (dark) 0xFF0F1117.toInt() else 0xFF14214F.toInt()
    val track: Int get() = if (dark) Theme.alpha(0xFF2A2F3C.toInt(), 200) else Theme.alpha(0xFF0A1850.toInt(), 160)
    val overlay: Int get() = if (dark) 0xFF000000.toInt() else 0xFF050B24.toInt()
    val cardFace: Int get() = if (dark) 0xFF15171E.toInt() else 0xFF1C2E6E.toInt()
    val cardEdge: Int get() = if (dark) 0xFF424963.toInt() else 0xFF5873BC.toInt()
    val lockedFace: Int get() = if (dark) Theme.alpha(0xFF2A2F3D.toInt(), 210) else Theme.alpha(0xFF0F2060.toInt(), 150)
    val lockedLip: Int get() = if (dark) Theme.alpha(0xFF0E1016.toInt(), 230) else Theme.alpha(0xFF08123C.toInt(), 170)
    val lockedText: Int get() = Theme.alpha(WHITE, if (dark) 90 else 150)
    /** Рамка и стрелка подсказки над лотком. */
    val pointer: Int get() = if (dark) WHITE else INK

    // ---------- игровое поле: панель ленты и воронка ----------
    val panelTop: Int get() = if (dark) 0xFF232837.toInt() else 0xFF25396C.toInt()
    val panelBottom: Int get() = if (dark) 0xFF0C0E14.toInt() else 0xFF111C42.toInt()
    val panelEdgeTop: Int get() = if (dark) 0xFF6F7896.toInt() else 0xFF7C98DC.toInt()
    val panelEdgeBottom: Int get() = if (dark) 0xFF2A2F42.toInt() else 0xFF2B3E78.toInt()
    val trough: Int get() = if (dark) 0xFF04060B.toInt() else 0xFF060C22.toInt()
    val troughRim: Int get() = if (dark) 0xFF58617F.toInt() else 0xFF5A70AC.toInt()
    val troughInner: Int get() = if (dark) 0xFF10131C.toInt() else 0xFF16224A.toInt()
    val beltFace: Int get() = if (dark) 0xFF1D2232.toInt() else 0xFF24346A.toInt()
    val slotHole: Int get() = if (dark) 0xFF04060A.toInt() else 0xFF080F2C.toInt()
    val pocketHole: Int get() = if (dark) 0xFF03050A.toInt() else 0xFF060C26.toInt()
    /** Стекло воронки: в светлой теме подкрашено синим, иначе белое стекло исчезает на светлом фоне. */
    val glassTop: Int get() = if (dark) Theme.alpha(WHITE, 44) else Theme.alpha(0xFF2C58E0.toInt(), 54)
    val glassMid: Int get() = if (dark) Theme.alpha(WHITE, 14) else Theme.alpha(0xFF2C58E0.toInt(), 24)

    val fontBlack: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val fontMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** Светлый ли цвет: тень рисуем только под светлым текстом. */
    fun isLight(c: Int): Boolean = ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 > 140

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(s: Int) = ((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * u).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Четыре тона каждого цвета шарика: блик, основной, тёмный, глубокий. Цвета разнесены по тону И по светлоте
     * (тёмно-синий против светло-бирюзового, тёмно-красный против янтарного и розового), чтобы не сливались;
     * на коробках и лотках к цвету добавлен ещё и значок-фигурка (см. Assets.glyph).
     */
    /** Нейтральная палитра «скрытого» лотка: серо-синий, цвет не угадать. Формат тот же, что у [pal]. */
    val hiddenPal: IntArray = intArrayOf(0xFFD6DCEE.toInt(), 0xFF7C89B4.toInt(), 0xFF3F4A74.toInt(), mix(0xFF3F4A74.toInt(), BLACK, 0.5f))

    val pal: Array<IntArray> = arrayOf(
        longArrayOf(0xFFFF8A94, 0xFFE5192E, 0xFF8F0C1C),   // красный (тёмный, насыщенный)
        longArrayOf(0xFF8FB4FF, 0xFF2B52F0, 0xFF0E1F94),   // синий (глубокий)
        longArrayOf(0xFFB2F58C, 0xFF2DBE3C, 0xFF0B6A22),   // зелёный (травяной)
        longArrayOf(0xFFFFF7A8, 0xFFFFDD00, 0xFFC09000),   // жёлтый (лимонный)
        longArrayOf(0xFFD7B0FF, 0xFF8B3FE0, 0xFF4A1594),   // фиолетовый
        longArrayOf(0xFFB0FBFF, 0xFF1FD0D8, 0xFF0A8189),   // бирюзовый (светлый)
        longArrayOf(0xFFFFD2A0, 0xFFFF8C00, 0xFFA84700),   // оранжевый (янтарный)
        longArrayOf(0xFFFFC4E6, 0xFFFF6FC0, 0xFFB02A78),   // розовый (светлый)
    ).map { r ->
        val l = r[0].toInt(); val m = r[1].toInt(); val d = r[2].toInt()
        intArrayOf(l, m, d, mix(d, BLACK, 0.5f))
    }.toTypedArray()
}
