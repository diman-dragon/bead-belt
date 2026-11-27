package dev.beadbelt.game

// четыре помощника внизу экрана (порядок = порядок кнопок слева направо)
const val AID_HINT = 0       // подсказка: показывает ход
const val AID_UNDO = 1       // отменить последний ход
const val AID_SHUFFLE = 2    // перемешать лотки
const val AID_CLEAR = 3      // очистить ленту: шарики с ленты возвращаются в лотки
const val AID_COUNT = 4

// уроки: номер = индекс в [Lessons.all]
const val LS_BASICS = 0
const val LS_BULB = 1
const val LS_UNDO = 2
const val LS_CLEAR = 3
const val LS_SHUFFLE = 4
const val LS_HIDDEN = 5
const val LS_LINKS = 6
const val LS_LIFT = 7
const val LS_MIXED = 8      // двухцветные лотки
const val LS_CHAIN = 9      // лоток в цепи
const val LS_MYSTERY = 10   // скрытые коробки

/** Приз: монеты и штуки помощников (`aids[AID_*]`). */
class Prize(val coins: Int = 0, hint: Int = 0, undo: Int = 0, shuffle: Int = 0, clear: Int = 0) {
    val aids = intArrayOf(hint, undo, shuffle, clear)
    val total: Int get() = coins + aids.sum()
}

/**
 * Урок: новая механика появляется на уровне `level`. Перед уровнем — карточка с объяснением, сам уровень —
 * тренировка (нужный элемент подсвечен, помощник на нём бесплатный), после победы игрок получает `prize`.
 * `aid` — помощник, который открывает урок (-1, если это не помощник).
 */
class Lesson(val id: Int, val level: Int, val aid: Int, val prize: Prize)

object Lessons {
    /** Одиннадцать уроков: помощники и первые правила поля в первых 50 уровнях, новые усложнения (два цвета, цепь, скрытые коробки) на 56, 76 и 100-м. */
    val all = listOf(
        Lesson(LS_BASICS, 1, -1, Prize(coins = 30)),
        Lesson(LS_BULB, 4, AID_HINT, Prize(hint = 3)),
        Lesson(LS_UNDO, 8, AID_UNDO, Prize(undo = 3)),
        Lesson(LS_CLEAR, 12, AID_CLEAR, Prize(clear = 2)),
        Lesson(LS_SHUFFLE, 16, AID_SHUFFLE, Prize(shuffle = 2)),
        Lesson(LS_HIDDEN, 20, -1, Prize(coins = 50, undo = 1)),
        Lesson(LS_LINKS, 26, -1, Prize(coins = 60, shuffle = 1)),
        Lesson(LS_LIFT, 34, -1, Prize(coins = 60, clear = 1)),
        Lesson(LS_MIXED, 56, -1, Prize(coins = 80, hint = 1)),
        Lesson(LS_CHAIN, 76, -1, Prize(coins = 80, undo = 1)),
        Lesson(LS_MYSTERY, 100, -1, Prize(coins = 100, shuffle = 1)),
    )

    /** На учебных уровнях лента просторнее: игрок осваивает новое, а не борется за место. */
    const val BELT_BONUS = 3

    /** Урок, который начинается на этом уровне. */
    fun atLevel(level: Int): Lesson? = all.firstOrNull { it.level == level }

    /** Урок, открывающий помощника. */
    fun forAid(aid: Int): Lesson = all.first { it.aid == aid }
}
