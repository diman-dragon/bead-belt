package dev.beadbelt.game

// идентификаторы товаров (те же id заводятся в Play Console; менять нельзя после публикации)
const val SKU_STARTER = "bead_starter_pack"
const val SKU_NOADS = "bead_no_ads"
const val SKU_COINS_S = "bead_coins_300"
const val SKU_COINS_M = "bead_coins_900"
const val SKU_COINS_L = "bead_coins_2200"
const val SKU_HINTS = "bead_hints_12"
const val SKU_UNDOS = "bead_undo_10"
const val SKU_SHUFFLES = "bead_shuffle_8"
const val SKU_CLEARS = "bead_clear_8"

// значки строк магазина
const val ICON_COIN = 0
const val ICON_HINT = 1
const val ICON_UNDO = 2
const val ICON_SHUFFLE = 3
const val ICON_CLEAR = 4
const val ICON_GIFT = 5
const val ICON_NOADS = 6

/** Товар за деньги. `oneTime` — покупается один раз (не расходуется): стартовый набор и отключение рекламы. */
class Offer(val sku: String, val prize: Prize, val oneTime: Boolean, val icon: Int)

/**
 * Монетизация в игре исключительно двух видов: добровольная реклама за награду и покупки «вкусняшек».
 * Ничто не продаётся и не показывается во время прохождения уровня без действия игрока.
 */
object Shop {
    val offers = listOf(
        Offer(SKU_STARTER, Prize(coins = 300, hint = 4, undo = 4, shuffle = 3, clear = 3), true, ICON_GIFT),
        Offer(SKU_NOADS, Prize(), true, ICON_NOADS),
        Offer(SKU_COINS_S, Prize(coins = 300), false, ICON_COIN),
        Offer(SKU_COINS_M, Prize(coins = 900), false, ICON_COIN),
        Offer(SKU_COINS_L, Prize(coins = 2200), false, ICON_COIN),
        Offer(SKU_HINTS, Prize(hint = 12), false, ICON_HINT),
        Offer(SKU_UNDOS, Prize(undo = 10), false, ICON_UNDO),
        Offer(SKU_SHUFFLES, Prize(shuffle = 8), false, ICON_SHUFFLE),
        Offer(SKU_CLEARS, Prize(clear = 8), false, ICON_CLEAR),
    )

    /** Бесплатные монеты за просмотр видео в магазине и сколько раз в день. */
    const val FREE_COINS = 40
    const val FREE_ADS_PER_DAY = 5

    fun find(sku: String): Offer? = offers.firstOrNull { it.sku == sku }
    val skus: List<String> get() = offers.map { it.sku }
}

/** Когда можно показывать рекламу. Принудительная реклама редкая и отключается покупкой; видео за награду всегда по желанию. */
object AdPolicy {
    /** Реклама между уровнями не раньше этого уровня (первые уровни — обучение). */
    const val FROM_LEVEL = 15
    /** Не чаще, чем раз в столько побед и не чаще, чем раз в столько миллисекунд (любой рекламы, в том числе за награду). */
    const val EVERY_WINS = 3
    const val MIN_GAP_MS = 90_000L

    /** Показывать ли рекламу после победы на уровне `level` (когда игрок нажал «Дальше»). Не показывается на учебных уровнях. */
    fun interstitialDue(level: Int, winsSinceAd: Int, lastAdMs: Long, nowMs: Long, adFree: Boolean): Boolean =
        !adFree && level >= FROM_LEVEL && Lessons.atLevel(level) == null &&
            winsSinceAd >= EVERY_WINS && nowMs - lastAdMs >= MIN_GAP_MS

    /** Сколько бесплатных видео осталось сегодня. */
    fun freeAdsLeft(today: Long, storedDay: Long, used: Int): Int =
        if (storedDay != today) Shop.FREE_ADS_PER_DAY else (Shop.FREE_ADS_PER_DAY - used).coerceAtLeast(0)
}
