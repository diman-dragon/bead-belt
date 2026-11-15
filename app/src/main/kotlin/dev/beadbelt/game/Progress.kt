package dev.beadbelt.game

import android.content.Context

/** Всё, что сохраняется между запусками: уровни, монеты, бустеры, звук. */
class Progress(context: Context) {
    companion object {
        const val ROCKET_PRICE = 60
        const val RETURN_PRICE = 40
    }

    private val prefs = context.getSharedPreferences("bead", Context.MODE_PRIVATE)

    var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, LEVEL_COUNT); private set
    var coins = prefs.getInt("coins", 100); private set
    var rockets = prefs.getInt("rockets", 2); private set
    var returns = prefs.getInt("returns", 2); private set
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

    /** Списывает бустер (бесплатный, если есть, иначе за монеты). */
    fun spend(rocket: Boolean) {
        if (rocket) { if (rockets > 0) rockets-- else coins -= ROCKET_PRICE }
        else { if (returns > 0) returns-- else coins -= RETURN_PRICE }
        save()
    }

    private fun save() {
        prefs.edit().putInt("unlocked", unlocked).putInt("coins", coins)
            .putInt("rockets", rockets).putInt("returns", returns).apply()
    }
}
