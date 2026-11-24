package dev.beadbelt.game

// виды платных помощников
const val AID_ROCKET = 0
const val AID_RETURN = 1
const val AID_HINT = 2

// уроки: номер = индекс в [Lessons.all]
const val LS_BASICS = 0
const val LS_BULB = 1
const val LS_ROCKET = 2
const val LS_RETURN = 3
const val LS_LINKS = 4
const val LS_LIFT = 5

/** Приз за пройденный урок. */
class Prize(val coins: Int = 0, val rockets: Int = 0, val returns: Int = 0, val hints: Int = 0)

/**
 * Урок: новая механика появляется на уровне `level`. Перед уровнем — карточка с объяснением, сам уровень —
 * тренировка (нужный элемент подсвечен, помощник на нём бесплатный), после победы игрок получает `prize`.
 * `aid` — помощник, который открывает урок (-1, если это не помощник).
 */
class Lesson(val id: Int, val level: Int, val aid: Int, val prize: Prize)

object Lessons {
    val all = listOf(
        Lesson(LS_BASICS, 1, -1, Prize(coins = 30)),
        Lesson(LS_BULB, 4, AID_HINT, Prize(hints = 3)),
        Lesson(LS_ROCKET, 10, AID_ROCKET, Prize(rockets = 2)),
        Lesson(LS_RETURN, 16, AID_RETURN, Prize(returns = 2)),
        Lesson(LS_LINKS, 41, -1, Prize(coins = 60, hints = 1)),
        Lesson(LS_LIFT, 60, -1, Prize(rockets = 1, returns = 1)),
    )

    /** На учебных уровнях лента просторнее: игрок осваивает новое, а не борется за место. */
    const val BELT_BONUS = 3

    /** Урок, который начинается на этом уровне. */
    fun atLevel(level: Int): Lesson? = all.firstOrNull { it.level == level }

    /** Урок, открывающий помощника. */
    fun forAid(aid: Int): Lesson = when (aid) { AID_ROCKET -> all[LS_ROCKET]; AID_RETURN -> all[LS_RETURN]; else -> all[LS_BULB] }
}
