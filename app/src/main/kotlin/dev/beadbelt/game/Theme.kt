package dev.beadbelt.game

import android.graphics.Typeface

/** Цвета и шрифты в одном месте. */
object Theme {
    val WHITE = 0xFFFFFFFF.toInt()
    val BLACK = 0xFF000000.toInt()
    val INK = 0xFF0B1B3A.toInt()
    val GOLD = 0xFFFFC21F.toInt()
    val GOLD_DEEP = 0xFFB87800.toInt()
    val GREEN = 0xFF2FD060.toInt()
    val RED = 0xFFFF5A6E.toInt()

    // фон: небо → глубокий синий
    val BG_TOP = 0xFF4AA8FF.toInt()
    val BG_MID = 0xFF2C58E0.toInt()
    val BG_BOTTOM = 0xFF162585.toInt()

    val fontBlack: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val fontMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(s: Int) = ((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * u).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Четыре тона каждого цвета шарика: блик, основной, тёмный, глубокий. */
    val pal: Array<IntArray> = arrayOf(
        longArrayOf(0xFFFF9EA6, 0xFFFF2D4A, 0xFFB5122B),   // красный
        longArrayOf(0xFF9CC4FF, 0xFF2F6BFF, 0xFF1239A8),   // синий
        longArrayOf(0xFFA6F5BB, 0xFF22C55E, 0xFF0B7A33),   // зелёный
        longArrayOf(0xFFFFF3A0, 0xFFFFD21F, 0xFFC08F00),   // жёлтый
        longArrayOf(0xFFE8B8FF, 0xFFB04BFF, 0xFF6A18A8),   // фиолетовый
        longArrayOf(0xFFA2F2FF, 0xFF14C8E8, 0xFF0A7C94),   // бирюзовый
        longArrayOf(0xFFFFCF9E, 0xFFFF8A1F, 0xFFB04F00),   // оранжевый
        longArrayOf(0xFFFFBCE0, 0xFFFF5FAE, 0xFFA8286A),   // розовый
    ).map { r ->
        val l = r[0].toInt(); val m = r[1].toInt(); val d = r[2].toInt()
        intArrayOf(l, m, d, mix(d, BLACK, 0.5f))
    }.toTypedArray()
}
