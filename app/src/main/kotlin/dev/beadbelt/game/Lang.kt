package dev.beadbelt.game

import java.util.Locale

/** Язык интерфейса: «Авто» берёт язык системы (русский, иначе английский). */
object Lang {
    const val AUTO = 0
    const val RU = 1
    const val EN = 2

    var mode = AUTO; private set
    var ru = true; private set
    /** Растёт при каждой смене языка: кэши готовых строк сверяют с ним свою версию. */
    var version = 0; private set

    fun set(m: Int) {
        mode = m.coerceIn(AUTO, EN)
        ru = when (mode) {
            RU -> true
            EN -> false
            else -> Locale.getDefault().language == "ru"
        }
        version++
    }
}

/** Строка на текущем языке. Обе строки — константы, поэтому в кадре ничего не создаётся. */
fun tr(ru: String, en: String): String = if (Lang.ru) ru else en
