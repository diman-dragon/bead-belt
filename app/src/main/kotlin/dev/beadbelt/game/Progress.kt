package dev.beadbelt.game

import android.content.Context
import android.content.res.Configuration

// виды платных помощников
const val AID_ROCKET = 0
const val AID_RETURN = 1
const val AID_HINT = 2

// разовые советы: каждый показывается один раз за всю игру
const val TIP_TAP = 0
const val TIP_ROCKET = 1
const val TIP_ARROWS = 2
const val TIP_LIFT = 3
const val TIP_HINT = 4
const val TIP_FULL = 5

/** Прогресс новой версии. Отдельное хранилище намеренно начинает игру с нуля и не читает старые сохранения. */
class Progress(context: Context) {
    companion object {
        const val ROCKET_PRICE = 60
        const val RETURN_PRICE = 40
        const val HINT_PRICE = 30
        const val REVIVE_PRICE = 50

        fun price(aid: Int) = when (aid) { AID_ROCKET -> ROCKET_PRICE; AID_RETURN -> RETURN_PRICE; else -> HINT_PRICE }

        /** Звёзды за прохождение: 3 — без помощи, 2 — до двух раз, 1 — больше. */
        fun starsFor(usage: Int) = when { usage <= 0 -> 3; usage <= 2 -> 2; else -> 1 }

        /** Номер дня по местному времени (для ежедневного подарка). */
        fun dayNumber(nowMs: Long = System.currentTimeMillis(), tz: java.util.TimeZone = java.util.TimeZone.getDefault()): Long =
            Math.floorDiv(nowMs + tz.getOffset(nowMs), 86_400_000L)

        /** Награда за день серии: 20, 30, … 80 монет. */
        fun giftCoins(streak: Int) = 20 + 10 * (streak.coerceIn(1, 7) - 1)

        fun systemIsDark(context: Context) =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private val prefs = context.getSharedPreferences("bead_v2_fresh_start", Context.MODE_PRIVATE)

    var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, LEVEL_COUNT); private set
    var coins = prefs.getInt("coins", 100); private set
    var rockets = prefs.getInt("rockets", 2); private set
    var returns = prefs.getInt("returns", 2); private set
    var hints = prefs.getInt("hints", 3); private set
    var sound = prefs.getBoolean("sound", true)
        set(v) { field = v; prefs.edit().putBoolean("sound", v).apply() }
    /** Язык: 0 — как в системе, 1 — русский, 2 — английский. */
    var langMode = prefs.getInt("lang", Lang.AUTO).coerceIn(Lang.AUTO, Lang.EN)
        set(v) { field = v; prefs.edit().putInt("lang", v).apply() }
    /** Тема: пока игрок ничего не выбрал, повторяет тему системы. */
    var darkTheme = prefs.getBoolean("dark", systemIsDark(context))
        set(v) { field = v; prefs.edit().putBoolean("dark", v).apply() }

    private val stars = IntArray(LEVEL_COUNT + 1).also { arr ->
        val s = prefs.getString("stars", "") ?: ""
        for (i in s.indices) if (i < LEVEL_COUNT) arr[i + 1] = (s[i] - '0').coerceIn(0, 3)
    }
    private var tips = prefs.getInt("tips", 0)
    private var giftDay = prefs.getLong("giftDay", -1L)
    var giftStreak = prefs.getInt("giftStreak", 0); private set

    /** Звёзды уровня (0 — не пройден). */
    fun starsOf(level: Int) = if (level in 1..LEVEL_COUNT) stars[level] else 0
    val totalStars: Int get() { var t = 0; for (i in 1..LEVEL_COUNT) t += stars[i]; return t }

    fun rewardFor(level: Int, stars: Int = 1) = 20 + level / 10 * 5 + (stars - 1) * 5

    /** Победа на уровне `level`: монеты, звёзды и открытие следующего. Возвращает награду. */
    fun win(level: Int, usage: Int): Int {
        val st = starsFor(usage)
        val r = rewardFor(level, st)
        coins += r
        if (st > stars[level]) stars[level] = st
        if (level == unlocked && unlocked < LEVEL_COUNT) unlocked++
        save()
        return r
    }

    // ---------- помощники ----------

    fun have(aid: Int) = when (aid) { AID_ROCKET -> rockets; AID_RETURN -> returns; else -> hints }

    fun canAfford(aid: Int) = have(aid) > 0 || coins >= price(aid)

    /** Списывает помощника (бесплатный, если есть, иначе за монеты). */
    fun spend(aid: Int) {
        when (aid) {
            AID_ROCKET -> if (rockets > 0) rockets-- else coins -= ROCKET_PRICE
            AID_RETURN -> if (returns > 0) returns-- else coins -= RETURN_PRICE
            else -> if (hints > 0) hints-- else coins -= HINT_PRICE
        }
        save()
    }

    fun canRevive() = coins >= REVIVE_PRICE
    fun spendRevive() { coins -= REVIVE_PRICE; save() }

    // ---------- разовые советы ----------

    fun tipSeen(id: Int) = tips and (1 shl id) != 0
    fun markTip(id: Int) { tips = tips or (1 shl id); prefs.edit().putInt("tips", tips).apply() }

    // ---------- ежедневный подарок ----------

    fun giftReady(today: Long = dayNumber()) = giftDay != today

    /** Какой по счёту день серии получит игрок, если заберёт подарок сегодня. */
    fun nextStreak(today: Long = dayNumber()) = if (giftDay == today - 1) (giftStreak % 7) + 1 else 1

    /** Забирает подарок. Возвращает монеты или 0, если сегодня уже забирал. */
    fun claimGift(today: Long = dayNumber()): Int {
        if (!giftReady(today)) return 0
        giftStreak = nextStreak(today)
        giftDay = today
        val c = giftCoins(giftStreak)
        coins += c
        save()
        return c
    }

    private fun save() {
        val sb = StringBuilder(LEVEL_COUNT)
        for (i in 1..LEVEL_COUNT) sb.append(('0' + stars[i]))
        prefs.edit().putInt("unlocked", unlocked).putInt("coins", coins)
            .putInt("rockets", rockets).putInt("returns", returns).putInt("hints", hints)
            .putString("stars", sb.toString()).putLong("giftDay", giftDay).putInt("giftStreak", giftStreak).apply()
    }
}
