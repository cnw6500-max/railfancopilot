package com.railfancopilot.app.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_status")
private val PREF_IS_PRO = booleanPreferencesKey("is_pro")

const val PRO_PRODUCT_ID = "railfan_copilot_pro"

class ProRepository(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private val dataStore = context.proDataStore

    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        // Load cached pro status immediately so UI doesn't flash locked state on restart
        scope.launch {
            dataStore.data.first()[PREF_IS_PRO]?.let { _isProUser.value = it }
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
        _isProUser.value = pro
        dataStore.edit { it[PREF_IS_PRO] = pro }
    }
}
