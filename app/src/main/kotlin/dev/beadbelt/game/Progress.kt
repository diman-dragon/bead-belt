package dev.beadbelt.game

import android.content.Context

/** Всё, что сохраняется между запусками: уровни, монеты, бустеры, звук. */
class Progress(context: Context) {
    companion object {
        const val ROCKET_PRICE = 60
        const val RETURN_PRICE = 40
        const val HINT_PRICE = 20
        const val UNDO_PRICE = 30
    }

    private val prefs = context.getSharedPreferences("bead", Context.MODE_PRIVATE)

    var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, LEVEL_COUNT); private set
    var coins = prefs.getInt("coins", 100); private set
    var rockets = prefs.getInt("rockets", 2); private set
    var returns = prefs.getInt("returns", 2); private set
    var hints = prefs.getInt("hints", 3); private set
    var undos = prefs.getInt("undos", 3); private set
    var sound = prefs.getBoolean("sound", true)
        set(v) { field = v; prefs.edit().putBoolean("sound", v).apply() }

    fun rewardFor(level: Int) = 20 + level / 10 * 5

    /** Победа на уровне `level`: монеты и открытие следующего. Возвращает награду. */
    fun win(level: Int): Int {
        val r = rewardFor(level)
        coins += r
        if (level == unlocked && unlocked < LEVEL_COUNT) unlocked++
        save()
        return r
    }

    fun canAfford(rocket: Boolean) = (if (rocket) rockets else returns) > 0 || coins >= (if (rocket) ROCKET_PRICE else RETURN_PRICE)

    fun canAfford(type: String): Boolean = when (type) {
        "rocket" -> rockets > 0 || coins >= ROCKET_PRICE
        "return" -> returns > 0 || coins >= RETURN_PRICE
        "undo" -> undos > 0 || coins >= UNDO_PRICE
        "hint" -> hints > 0 || coins >= HINT_PRICE
        else -> false
    }

    /** Списывает бустер (бесплатный, если есть, иначе за монеты). */
    fun spend(rocket: Boolean) {
        if (rocket) { if (rockets > 0) rockets-- else coins -= ROCKET_PRICE }
        else { if (returns > 0) returns-- else coins -= RETURN_PRICE }
        save()
    }

    fun spend(type: String) {
        when (type) {
            "rocket" -> if (rockets > 0) rockets-- else coins -= ROCKET_PRICE
            "return" -> if (returns > 0) returns-- else coins -= RETURN_PRICE
            "undo" -> if (undos > 0) undos-- else coins -= UNDO_PRICE
            "hint" -> if (hints > 0) hints-- else coins -= HINT_PRICE
        }
        save()
    }

    private fun save() {
        prefs.edit().putInt("unlocked", unlocked).putInt("coins", coins)
            .putInt("rockets", rockets).putInt("returns", returns)
            .putInt("hints", hints).putInt("undos", undos).apply()
    }
}
