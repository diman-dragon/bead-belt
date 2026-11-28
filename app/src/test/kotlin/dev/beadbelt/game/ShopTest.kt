package dev.beadbelt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopTest {
    @Test
    fun offersAreWellFormed() {
        assertEquals("id товаров уникальны", Shop.offers.size, Shop.offers.map { it.sku }.toSet().size)
        for (o in Shop.offers) {
            val p = o.prize
            if (o.sku == SKU_NOADS) assertTrue(o.oneTime) else assertTrue("${o.sku}: пустой товар", p.total > 0)
            assertEquals(o, Shop.find(o.sku))
        }
        assertEquals(null, Shop.find("nope"))
        assertEquals(setOf(SKU_STARTER, SKU_NOADS), Shop.offers.filter { it.oneTime }.map { it.sku }.toSet())
    }

    /** Чем больше пакет монет, тем выгоднее монета: цены в Play Console — 0.99 / 1.99 / 3.99. */
    @Test
    fun biggerCoinPacksAreBetterValue() {
        val coins = Shop.offers.filter { it.icon == ICON_COIN }.map { it.prize.coins }
        assertEquals(coins.sorted(), coins)
        val price = listOf(0.99, 1.99, 3.99)
        val per = coins.indices.map { coins[it] / price[it] }
        assertTrue(per.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun interstitialRules() {
        val now = 1_000_000L; val old = now - AdPolicy.MIN_GAP_MS
        assertTrue(AdPolicy.interstitialDue(21, 3, old, now, false))
        assertTrue("после покупки рекламы нет", !AdPolicy.interstitialDue(21, 9, old, now, true))
        assertTrue("рано: обучение", !AdPolicy.interstitialDue(AdPolicy.FROM_LEVEL - 1, 9, old, now, false))
        assertTrue("не каждую победу", !AdPolicy.interstitialDue(21, AdPolicy.EVERY_WINS - 1, old, now, false))
        assertTrue("не чаще интервала", !AdPolicy.interstitialDue(21, 9, now - 1000, now, false))
        for (ls in Lessons.all.filter { it.at >= AdPolicy.FROM_LEVEL }) assertTrue("на учебном уровне ${ls.at}", !AdPolicy.interstitialDue(ls.at, 9, old, now, false))
    }

    @Test
    fun freeVideoLimitResetsEveryDay() {
        assertEquals(Shop.FREE_ADS_PER_DAY, AdPolicy.freeAdsLeft(10, 9, 5))
        assertEquals(2, AdPolicy.freeAdsLeft(10, 10, Shop.FREE_ADS_PER_DAY - 2))
        assertEquals(0, AdPolicy.freeAdsLeft(10, 10, 99))
    }

    @Test
    fun providersBehave() {
        assertTrue(!NoAds.rewardedReady && !NoAds.interstitialReady)
        var r = true; NoAds.showRewarded { r = it }; assertTrue("без SDK награды нет", !r)
        var closed = false; NoAds.showInterstitial { closed = true }; assertTrue(closed)
        assertEquals(null, NoBilling.price(SKU_NOADS))
        var ok = true; NoBilling.buy(SKU_NOADS) { ok = it }; assertTrue(!ok)

        val ads = SimAds(); var got: Boolean? = null
        ads.showRewarded { got = it }
        assertTrue(ads.pending != null && !ads.interstitial)
        ads.finish(true); assertEquals(true, got); assertTrue(ads.pending == null)
        var gone = false; ads.showInterstitial { gone = true }
        assertTrue(ads.interstitial); ads.finish(true); assertTrue(gone)

        val b = SimBilling()
        assertTrue(Shop.skus.all { b.price(it) != null })
        b.buy(SKU_NOADS) {}; b.buy(SKU_COINS_S) {}
        var owned = emptyList<String>(); b.restore { owned = it }
        assertEquals(listOf(SKU_NOADS), owned)
    }
}
