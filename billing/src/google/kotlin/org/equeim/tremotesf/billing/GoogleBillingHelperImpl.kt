package org.equeim.tremotesf.billing

import android.app.Activity
import android.content.Context

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.querySkuDetails

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.utils.MutableEventFlow

import timber.log.Timber

@Suppress("FunctionName")
fun GoogleBillingHelper(context: Context, coroutineScope: CoroutineScope): GoogleBillingHelper =
    GoogleBillingHelperImpl(context, coroutineScope)

private class GoogleBillingHelperImpl(
    context: Context,
    private val coroutineScope: CoroutineScope
) :
    GoogleBillingHelper,
    BillingClientStateListener,
    PurchasesUpdatedListener {
    companion object {
        private val skuIds = listOf(
            "tremotesf.1",
            "tremotesf.2",
            "tremotesf.3",
            "tremotesf.5",
            "tremotesf.10"
        )

        private val debugSkuIds = listOf(
            "android.test.purchased",
            "android.test.canceled",
            "android.test.refunded",
            "android.test.item_unavailable"
        )

        private fun createPurchaseError(result: BillingResult): GoogleBillingHelper.PurchaseError {
            return when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> GoogleBillingHelper.PurchaseError.None
                BillingClient.BillingResponseCode.USER_CANCELED -> GoogleBillingHelper.PurchaseError.Cancelled
                else -> GoogleBillingHelper.PurchaseError.Error
            }
        }

        private fun resultToString(result: BillingResult): String {
            return "BillingResult(responseCode=${result.responseCode}, debugMessage=${result.debugMessage})"
        }
    }

    override val isSetUp = MutableStateFlow(false)
    override val purchasesUpdatedEvent = MutableEventFlow<GoogleBillingHelper.PurchaseError>()
    override var skus: List<GoogleBillingHelper.SkuData> = emptyList()

    private val billingClient =
        BillingClient.newBuilder(context).enablePendingPurchases().setListener(this).build()
    private var skuDetails: List<SkuDetails>? = null

    init {
        Timber.i("init")
        billingClient.startConnection(this)
    }

    override fun launchBillingFlow(
        skuIndex: Int,
        activity: Activity
    ): GoogleBillingHelper.PurchaseError {
        debug("launchBillingFlow")
        val skuDetails = this.skuDetails
        if (!billingClient.isReady || skuDetails == null) return GoogleBillingHelper.PurchaseError.Error

        if (skuIndex !in skuDetails.indices) throw IllegalArgumentException()

        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails[skuIndex])
                .build()
        )
        debug("launchBillingFlow result=${resultToString(result)}")
        return createPurchaseError(result)
    }

    override fun endConnection() {
        Timber.i("endConnection")
        billingClient.endConnection()
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        Timber.i("onBillingSetupFinished result=${resultToString(result)}")
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            val consume = billingClient
                .queryPurchases(BillingClient.SkuType.INAPP)
                .purchasesList
                ?.filterNot(Purchase::isAcknowledged)
            if (consume?.isNotEmpty() == true) {
                coroutineScope.launch(Dispatchers.IO) {
                    consumePurchases(consume, false)
                }
            }

            coroutineScope.launch(Dispatchers.IO) {
                querySkuDetails()
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        Timber.i("onBillingServiceDisconnected")
        skuDetails = null
        skus = emptyList()
        isSetUp.value = false
        billingClient.startConnection(this)
    }

    private suspend fun querySkuDetails() {
        debug("querySkuDetails")
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(if (BuildConfig.DEBUG) debugSkuIds else skuIds)
            .setType(BillingClient.SkuType.INAPP)
            .build()
        val (result, skuDetails) = billingClient.querySkuDetails(params)

        if (BuildConfig.DEBUG) {
            debug("querySkuDetails result=${resultToString(result)} skuDetails=$skuDetails")
        } else {
            debug("querySkuDetails result=${resultToString(result)}")
        }
        if (result.responseCode == BillingClient.BillingResponseCode.OK && skuDetails != null) {
            val sorted = skuDetails.sortedBy(SkuDetails::getPriceAmountMicros)
            this.skuDetails = sorted
            skus = sorted.map { GoogleBillingHelper.SkuData(it.sku, it.price) }
            isSetUp.value = true
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        debug("onPurchasesUpdated result=${resultToString(result)} purchases=$purchases")
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases?.isNotEmpty() == true) {
                coroutineScope.launch(Dispatchers.IO) {
                    consumePurchases(purchases, true)
                }
            }
        } else {
            purchasesUpdatedEvent.tryEmit(createPurchaseError(result))
        }
    }

    private suspend fun consumePurchases(purchases: List<Purchase>, emitError: Boolean) {
        debug("consumePurchase purchases=$purchases")
        for (purchase in purchases) {
            val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val (result, purchaseToken) = billingClient.consumePurchase(params)
            debug("consumePurchases result=${resultToString(result)} purchaseToken=$purchaseToken")
            if (emitError) {
                purchasesUpdatedEvent.emit(createPurchaseError(result))
            }
        }
    }

    private fun debug(msg: String, tr: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Timber.d(tr, msg)
        }
    }
}
