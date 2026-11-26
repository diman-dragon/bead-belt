# Монетизация

В игре только два способа заработка: **добровольная реклама за награду** и **покупки «вкусняшек»**.
Никаких баннеров, принудительных окон посреди уровня, платных уровней и случайных наборов (лутбоксов).

## Что и когда видит игрок

| Где | Что | Правила |
|---|---|---|
| Магазин, первая строка | видео за **+40 монет** | до 5 раз в день (`Shop.FREE_ADS_PER_DAY`) |
| Карточка поражения | **«Ещё шанс» за видео** (рядом с вариантом за 50 монет) | только если на ленте есть что вернуть |
| Карточка победы | **«×2 видео»**: награда за уровень удваивается | не на учебных уровнях, один раз за уровень |
| После победы, на «Дальше» | **реклама между уровнями** | не раньше 15-го уровня, каждая 3-я победа, не чаще раза в 90 с (считая и видео), никогда на учебных уровнях; **отключается покупкой** |
| Нажатие на помощника без запаса и монет | открывается магазин | |

Магазин открывается из меню (кнопка с монетой), по нажатию на монеты в игре и в списке уровней, а также при нехватке монет на помощника.
Правила рекламы записаны в `AdPolicy` (`Shop.kt`) и покрыты `ShopTest`.

## Товары (создать в Play Console → Монетизация → Товары)

| Id | Тип | Содержимое | Ориентир цены |
|---|---|---|---|
| `bead_starter_pack` | разовый (не расходуется) | 300 монет, подсказки ×4, отмены ×4, перемешать ×3, очистка ×3 | $1.99 |
| `bead_no_ads` | разовый | отключает рекламу между уровнями (видео за награду остаётся по желанию) | $2.99 |
| `bead_coins_300` | расходуемый | 300 монет | $0.99 |
| `bead_coins_900` | расходуемый | 900 монет | $1.99 |
| `bead_coins_2200` | расходуемый | 2200 монет | $3.99 |
| `bead_hints_12` | расходуемый | подсказки ×12 | $0.99 |
| `bead_undo_10` | расходуемый | отмены хода ×10 | $0.99 |
| `bead_shuffle_8` | расходуемый | перемешать ×8 | $0.99 |
| `bead_clear_8` | расходуемый | очистка ленты ×8 | $0.99 |

Id нельзя менять после публикации. Пакеты монет устроены так, что чем больше пакет, тем выгоднее монета (`ShopTest.biggerCoinPacksAreBetterValue`).
Цены в игре берутся из Play (`Billing.price`), поэтому ориентиры выше можно менять в консоли без обновления приложения.
Цены помощников за монеты: подсказка 30, отмена 20, перемешать 35, очистка 40 (`Progress`).

## Как это устроено в коде

Игра общается с рекламой и оплатой только через два интерфейса из `Monetization.kt`:

```kotlin
interface Ads { rewardedReady; interstitialReady; load(); showRewarded(done: (Boolean)->Unit); showInterstitial(done: ()->Unit) }
interface Billing { price(sku): String?; buy(sku, done: (Boolean)->Unit); restore(done: (List<String>)->Unit) }
```

`Monetization.ads(debuggable)` и `Monetization.billing(debuggable)` выбирают реализацию:

- **отладочная сборка**: `SimAds` (игра рисует заглушку «Тестовая реклама» на 3 секунды, потом награда считается полученной) и `SimBilling` (оплата проходит сразу, цены условные). Позволяет пройти все сценарии без SDK, денег не приносит.
- **релизная сборка без SDK** (так сейчас): `NoAds` и `NoBilling`. Кнопки видео скрыты, покупки в магазине показывают «…» и не работают, игра полностью играбельна.

**Чтобы начать зарабатывать, нужно подключить два настоящих SDK**: код ниже — заготовки, они не собирались и не проверялись
на устройстве (для этого нужны ваши аккаунты AdMob и Play, и я не мог их использовать), поэтому проверьте их по актуальной документации.

### 1. Реклама (Google AdMob)

1. Завести приложение и два рекламных блока (rewarded и interstitial) в AdMob, получить App ID и id блоков. Для проверки сначала используйте тестовые блоки Google.
2. `app/build.gradle.kts`: `implementation("com.google.android.gms:play-services-ads:<актуальная версия>")`.
3. `AndroidManifest.xml`: `<uses-permission android:name="android.permission.INTERNET"/>`, `<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>` и внутри `<application>`:
   `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-XXXX~YYYY"/>`.
4. Согласие на рекламу (GDPR/UMP): до первой загрузки рекламы запросить `ConsentInformation` и показать форму в EEA/Великобритании (User Messaging Platform). Это требование Google, без него реклама в Европе не показывается.
5. Реализация (заготовка):

```kotlin
class AdMobAds(private val activity: android.app.Activity) : Ads {
    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null
    override val rewardedReady get() = rewarded != null
    override val interstitialReady get() = interstitial != null

    override fun load() {
        if (rewarded == null) RewardedAd.load(activity, REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null }
        })
        if (interstitial == null) InterstitialAd.load(activity, INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
        })
    }

    override fun showRewarded(done: (Boolean) -> Unit) {
        val ad = rewarded ?: return done(false)
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = done(earned)
            override fun onAdFailedToShowFullScreenContent(e: AdError) = done(false)
        }
        ad.show(activity) { earned = true }
    }

    override fun showInterstitial(done: () -> Unit) {
        val ad = interstitial ?: return done()
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = done()
            override fun onAdFailedToShowFullScreenContent(e: AdError) = done()
        }
        ad.show(activity)
    }
}
```

   Инициализация `MobileAds.initialize(context)` после получения согласия. Игра сама вызывает `load()` после каждого показа и переносит колбэки в UI-поток.

### 2. Покупки (Google Play Billing)

1. Создать товары из таблицы выше, пометить `bead_starter_pack` и `bead_no_ads` как непотребляемые (в Billing это обычные «one-time products»), остальные игра «погашает» сама.
2. `implementation("com.android.billingclient:billing-ktx:<актуальная версия>")`.
3. Реализация (заготовка): `BillingClient.newBuilder(context).setListener(...).enablePendingPurchases(...)`, `startConnection`, `queryProductDetailsAsync` для всех `Shop.skus` (цены кладутся в словарь, из которого читает `price(sku)`), `launchBillingFlow` в `buy`.
   В `PurchasesUpdatedListener` для расходуемых товаров после успеха вызвать `consumeAsync`, для разовых `acknowledgePurchase` (иначе Play вернёт деньги через 3 дня), и только потом `done(true)`.
   `restore` вызывает `queryPurchasesAsync(ONE_TIME)` и отдаёт id купленных разовых товаров.
4. Заменить в `Monetization` правые части на `AdMobAds(activity)` и вашу реализацию `Billing` (понадобится передать `Activity` вместо `debuggable`).
5. Проверка: License testers в Play Console, тестовые покупки; для рекламы тестовые блоки Google.

Прогресс игрока (монеты, помощники, флаги «без рекламы» и «стартовый набор», дневной лимит видео) хранится локально в `Progress`.
`applyPurchase` выдаёт содержимое и не даёт повторно выдать разовый товар, `restoreOwned` только возвращает флаги.

## Чек-лист перед публикацией

- [ ] Подключены настоящие SDK, тестовые id заменены на боевые.
- [ ] Форма согласия (UMP) показывается в EEA/Великобритании; ссылка на политику конфиденциальности ([PRIVACY.md](PRIVACY.md)) опубликована.
- [ ] Play Console: «Реклама: да», «Покупки в приложении: да», раздел «Безопасность данных» заполнен под AdMob (рекламный идентификатор, IP, диагностика) и Play Billing.
- [ ] Целевая аудитория не включает детей до 13 лет (для детского контента действуют отдельные правила, рекламный SDK надо настраивать иначе).
- [ ] Есть файл `app-ads.txt` на сайте разработчика (рекомендует AdMob).
- [ ] Проверено на устройстве: видео награждает только после просмотра, покупка не выдаётся дважды, «Восстановить покупки» работает, реклама не появляется на учебных уровнях и после покупки «Без рекламы».
