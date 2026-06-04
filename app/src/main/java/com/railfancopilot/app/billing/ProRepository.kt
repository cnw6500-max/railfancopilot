package com.railfancopilot.app.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.ceil

private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_status")
private val PREF_IS_PRO       = booleanPreferencesKey("is_pro")
private val PREF_TRIAL_START  = longPreferencesKey("trial_start_ms")

private const val TRIAL_DURATION_MS = 7L * 24 * 60 * 60 * 1000L

const val PRO_PRODUCT_ID = "railfan_copilot_pro"

class ProRepository(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private val dataStore = context.proDataStore

    private val _isPurchased  = MutableStateFlow(false)
    private val _trialStartMs = MutableStateFlow(Long.MAX_VALUE)

    /** True only when the user has paid — used for purchase-specific UI. */
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

    // Ticks every minute so trial state stays current during a long session.
    private val ticker: Flow<Unit> = flow {
        while (true) { emit(Unit); delay(60_000L) }
    }.shareIn(scope, SharingStarted.Eagerly)

    /** True while the 7-day trial window is open. */
    val isInTrial: StateFlow<Boolean> = combine(_trialStartMs, ticker) { start, _ ->
        start != Long.MAX_VALUE && System.currentTimeMillis() - start < TRIAL_DURATION_MS
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Remaining full days in the trial (rounded up), 0 when expired or not started. */
    val trialDaysLeft: StateFlow<Int> = combine(_trialStartMs, ticker) { start, _ ->
        if (start == Long.MAX_VALUE) return@combine 0
        val remaining = (TRIAL_DURATION_MS - (System.currentTimeMillis() - start)).coerceAtLeast(0L)
        ceil(remaining.toDouble() / (24 * 60 * 60 * 1000.0)).toInt()
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** Pro features are unlocked while purchased or within the trial window. */
    val isProUser: StateFlow<Boolean> = combine(_isPurchased, isInTrial) { purchased, trial ->
        purchased || trial
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        scope.launch {
            val prefs = dataStore.data.first()

            // Restore cached purchase status immediately — avoids locked UI flash on restart
            _isPurchased.value = prefs[PREF_IS_PRO] ?: false

            // Seed trial on first launch; restore on subsequent launches
            val savedStart = prefs[PREF_TRIAL_START]
            if (savedStart == null) {
                val now = System.currentTimeMillis()
                dataStore.edit { it[PREF_TRIAL_START] = now }
                _trialStartMs.value = now
            } else {
                _trialStartMs.value = savedStart
            }
        }
        connectBilling()
    }

    private fun connectBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { queryExistingPurchases() }
                }
            }
            override fun onBillingServiceDisconnected() {
                // Will reconnect on next purchase/restore attempt
            }
        })
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { purchases.forEach { handlePurchase(it) } }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params)
        }
        setPro(true)
    }

    private suspend fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        val hasPro = result.purchasesList.any { purchase ->
            purchase.products.contains(PRO_PRODUCT_ID) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        setPro(hasPro)
    }

    fun purchasePro(activity: Activity) {
        scope.launch {
            if (!billingClient.isReady) {
                connectBilling()
                return@launch
            }
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRO_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val queryResult = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            )
            val productDetails = queryResult.productDetailsList?.firstOrNull() ?: return@launch
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            connectBilling()
            return
        }
        scope.launch { queryExistingPurchases() }
    }

    private suspend fun setPro(pro: Boolean) {
        _isPurchased.value = pro
        dataStore.edit { it[PREF_IS_PRO] = pro }
    }
}
