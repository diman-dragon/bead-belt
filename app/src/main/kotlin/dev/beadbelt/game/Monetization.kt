package dev.beadbelt.game

/**
 * Реклама. Настоящая реализация (AdMob и т. п.) подключается здесь: см. docs/MONETIZATION.md.
 * Все колбэки реализация вызывает на любом потоке, игра сама переносит их на UI-поток.
 */
interface Ads {
    /** Видео за награду загружено и готово. */
    val rewardedReady: Boolean
    /** Полноэкранная реклама между уровнями готова. */
    val interstitialReady: Boolean
    /** Начать загрузку (вызывать можно часто, лишнее игнорируется). */
    fun load()
    /** Показать видео; `done(true)` — досмотрено, награду выдавать, `done(false)` — закрыто раньше или ошибка. */
    fun showRewarded(done: (Boolean) -> Unit)
    /** Показать рекламу между уровнями; `done` вызывается, когда её закрыли (или не удалось показать). */
    fun showInterstitial(done: () -> Unit)
}

/** Покупки. Настоящая реализация — Google Play Billing (см. docs/MONETIZATION.md). */
interface Billing {
    /** Цена товара в валюте пользователя («$0.99») или null, пока она неизвестна или магазин недоступен. */
    fun price(sku: String): String?
    /** Купить `sku`; `done(true)` — оплата прошла (расходуемые товары реализация сама «погашает»). */
    fun buy(sku: String, done: (Boolean) -> Unit)
    /** Список уже купленных непогашаемых товаров (отключение рекламы, стартовый набор) — для «Восстановить покупки». */
    fun restore(done: (List<String>) -> Unit)
}

/** Рекламы нет (SDK не подключён): кнопки видео скрываются, игра остаётся полностью играбельной. */
object NoAds : Ads {
    override val rewardedReady = false
    override val interstitialReady = false
    override fun load() {}
    override fun showRewarded(done: (Boolean) -> Unit) = done(false)
    override fun showInterstitial(done: () -> Unit) = done()
}

/** Покупок нет (SDK не подключён). */
object NoBilling : Billing {
    override fun price(sku: String): String? = null
    override fun buy(sku: String, done: (Boolean) -> Unit) = done(false)
    override fun restore(done: (List<String>) -> Unit) = done(emptyList())
}

/**
 * Тестовая реклама для отладочных сборок: игра рисует заглушку на 3 секунды (`pending` — ждёт закрытия),
 * после чего награда считается полученной. Деньги не приносит, в релиз не попадает.
 */
class SimAds : Ads {
    override val rewardedReady = true
    override val interstitialReady = true
    var pending: ((Boolean) -> Unit)? = null; private set
    var interstitial = false; private set
    override fun load() {}
    override fun showRewarded(done: (Boolean) -> Unit) { interstitial = false; pending = done }
    override fun showInterstitial(done: () -> Unit) { interstitial = true; pending = { done() } }
    /** Заглушку закрыли. */
    fun finish(rewarded: Boolean) { val p = pending; pending = null; p?.invoke(rewarded) }
}

/** Тестовые покупки для отладочных сборок: оплата проходит сразу, цены условные. */
class SimBilling : Billing {
    private val owned = HashSet<String>()
    override fun price(sku: String): String? = when (sku) {
        SKU_COINS_S -> "\$0.99"; SKU_COINS_M -> "\$1.99"; SKU_COINS_L -> "\$3.99"
        SKU_HINTS -> "\$0.99"; SKU_UNDOS -> "\$0.99"; SKU_SHUFFLES -> "\$0.99"; SKU_CLEARS -> "\$0.99"
        SKU_STARTER -> "\$1.99"; SKU_NOADS -> "\$2.99"
        else -> null
    }
    override fun buy(sku: String, done: (Boolean) -> Unit) {
        if (Shop.find(sku)?.oneTime == true) owned.add(sku)
        done(true)
    }
    override fun restore(done: (List<String>) -> Unit) = done(owned.toList())
}

/** Какие реализации использует игра. Для релиза с настоящими SDK заменить правые части (см. docs/MONETIZATION.md). */
object Monetization {
    fun ads(debuggable: Boolean): Ads = if (debuggable) SimAds() else NoAds
    fun billing(debuggable: Boolean): Billing = if (debuggable) SimBilling() else NoBilling
}
