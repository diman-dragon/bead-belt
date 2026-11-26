package dev.beadbelt.game

import android.content.Context
import android.content.res.Configuration

/** Прогресс новой версии. Отдельное хранилище намеренно начинает игру с нуля и не читает старые сохранения. */
class Progress(context: Context) {
    companion object {
        const val HINT_PRICE = 30
        const val UNDO_PRICE = 20
        const val SHUFFLE_PRICE = 35
        const val CLEAR_PRICE = 40
        const val REVIVE_PRICE = 50

        /** Цена помощника в монетах, когда запас закончился. */
        fun price(aid: Int) = when (aid) { AID_UNDO -> UNDO_PRICE; AID_SHUFFLE -> SHUFFLE_PRICE; AID_CLEAR -> CLEAR_PRICE; else -> HINT_PRICE }

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

    private val prefs = context.getSharedPreferences("bead_v5_aids", Context.MODE_PRIVATE)

    var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, LEVEL_COUNT); private set
    var coins = prefs.getInt("coins", 0); private set
    /** Запас помощников по `AID_*`. */
    private val aids = IntArray(AID_COUNT) { prefs.getInt("aid$it", 0) }
    var adsRemoved = prefs.getBoolean("noads", false); private set
    var starterBought = prefs.getBoolean("starter", false); private set
    private var adDay = prefs.getLong("adDay", -1L)
    private var adCount = prefs.getInt("adCount", 0)
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
    private var lessons = prefs.getInt("lessons", 0)
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

    fun have(aid: Int) = aids[aid.coerceIn(0, AID_COUNT - 1)]

    fun canAfford(aid: Int) = have(aid) > 0 || coins >= price(aid)

    /** Списывает помощника (из запаса, а если пуст, то за монеты). */
    fun spend(aid: Int) {
        if (aids[aid] > 0) aids[aid]-- else coins -= price(aid)
        save()
    }

    /** Выдаёт приз: монеты и помощников. */
    fun grant(p: Prize) {
        coins += p.coins
        for (i in 0 until AID_COUNT) aids[i] += p.aids[i]
        save()
    }

    fun addCoins(n: Int) { coins += n; save() }

    fun canRevive() = coins >= REVIVE_PRICE
    fun spendRevive() { coins -= REVIVE_PRICE; save() }

    // ---------- уроки ----------

    /** Урок пройден: его механика открыта, приз получен. */
    fun lessonDone(id: Int) = lessons and (1 shl id) != 0

    /** Урок пройден (победа на учебном уровне): отмечаем и выдаём приз. Повторно приз не выдаётся. */
    fun completeLesson(id: Int) {
        if (lessonDone(id)) return
        lessons = lessons or (1 shl id)
        grant(Lessons.all[id].prize)
    }

    // ---------- реклама и покупки ----------

    /** Сколько бесплатных видео осталось сегодня. */
    fun freeAdsLeft(today: Long = dayNumber()) = AdPolicy.freeAdsLeft(today, adDay, adCount)

    /** Бесплатные монеты за просмотренное видео. Возвращает монеты или 0, если лимит дня исчерпан. */
    fun claimFreeAd(today: Long = dayNumber()): Int {
        if (freeAdsLeft(today) <= 0) return 0
        if (adDay != today) { adDay = today; adCount = 0 }
        adCount++
        coins += Shop.FREE_COINS
        save()
        return Shop.FREE_COINS
    }

    /** Товар, который покупается один раз, уже куплен. */
    fun owned(sku: String) = when (sku) { SKU_NOADS -> adsRemoved; SKU_STARTER -> starterBought; else -> false }

    /** Оплаченная покупка: выдаёт содержимое. Разовые товары повторно не выдаются. */
    fun applyPurchase(sku: String): Boolean {
        val o = Shop.find(sku) ?: return false
        if (o.oneTime && owned(sku)) return false
        when (sku) { SKU_NOADS -> adsRemoved = true; SKU_STARTER -> starterBought = true }
        grant(o.prize)
        return true
    }

    /** «Восстановить покупки»: отмечает разовые товары купленными, не выдавая содержимое второй раз. Возвращает, сколько восстановлено. */
    fun restoreOwned(skus: List<String>): Int {
        var n = 0
        if (SKU_NOADS in skus && !adsRemoved) { adsRemoved = true; n++ }
        if (SKU_STARTER in skus && !starterBought) { starterBought = true; n++ }
        if (n > 0) save()
        return n
    }

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
        val e = prefs.edit()
        for (i in 0 until AID_COUNT) e.putInt("aid$i", aids[i])
        e.putInt("unlocked", unlocked).putInt("coins", coins)
            .putBoolean("noads", adsRemoved).putBoolean("starter", starterBought).putLong("adDay", adDay).putInt("adCount", adCount)
            .putInt("lessons", lessons).putString("stars", sb.toString()).putLong("giftDay", giftDay).putInt("giftStreak", giftStreak).apply()
    }
}
