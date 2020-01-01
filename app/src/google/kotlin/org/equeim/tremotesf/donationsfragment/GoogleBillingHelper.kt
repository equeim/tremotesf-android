package org.equeim.tremotesf.donationsfragment

import android.app.Activity
import android.content.Context

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.SkuDetailsResponseListener

import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.utils.LiveEvent
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.NonNullMutableLiveData


class GoogleBillingHelper(context: Context) :
        IGoogleBillingHelper,
        BillingClientStateListener,
        SkuDetailsResponseListener,
        PurchasesUpdatedListener,
        ConsumeResponseListener,
        Logger {
    companion object {
        private val skuIds = listOf("tremotesf.1",
                                    "tremotesf.2",
                                    "tremotesf.3",
                                    "tremotesf.5",
                                    "tremotesf.10")

        private val debugSkuIds = listOf("android.test.purchased",
                                         "android.test.canceled",
                                         "android.test.refunded",
                                         "android.test.item_unavailable")

        private fun createPurchaseError(result: BillingResult): IGoogleBillingHelper.PurchaseError {
            return when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> IGoogleBillingHelper.PurchaseError.None
                BillingClient.BillingResponseCode.USER_CANCELED -> IGoogleBillingHelper.PurchaseError.Cancelled
                else -> IGoogleBillingHelper.PurchaseError.Error
            }
        }

        private fun resultToString(result: BillingResult): String {
            return "BillingResult(responseCode=${result.responseCode}, debugMessage=${result.debugMessage})"
        }
    }

    override val isSetUp = NonNullMutableLiveData(false)
    override val purchasesUpdatedEvent = LiveEvent<IGoogleBillingHelper.PurchaseError>()
    override var skus: List<IGoogleBillingHelper.SkuData> = emptyList()

    private val billingClient = BillingClient.newBuilder(context).enablePendingPurchases().setListener(this).build()
    private var skuDetails: List<SkuDetails>? = null

    init {
        info("init")
        billingClient.startConnection(this)
    }

    override fun launchBillingFlow(skuIndex: Int, activity: Activity): IGoogleBillingHelper.PurchaseError {
        val skuDetails = this.skuDetails
        if (!billingClient.isReady || skuDetails == null) return IGoogleBillingHelper.PurchaseError.Error

        if (skuIndex !in skuDetails.indices) throw IllegalArgumentException()

        val result = billingClient.launchBillingFlow(activity,
                                                     BillingFlowParams.newBuilder()
                                                             .setSkuDetails(skuDetails[skuIndex])
                                                             .build())
        debug("launchBillingFlow result=${resultToString(result)}")
        return createPurchaseError(result)
    }

    override fun endConnection() {
        info("endConnection")
        billingClient.endConnection()
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        info("onBillingSetupFinished result=${resultToString(result)}")
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(if (BuildConfig.DEBUG) debugSkuIds else skuIds)
            params.setType(BillingClient.SkuType.INAPP)
            billingClient.querySkuDetailsAsync(params.build(), this)
        }
    }

    override fun onBillingServiceDisconnected() {
        info("onBillingServiceDisconnected")
        skuDetails = null
        skus = emptyList()
        isSetUp.value = false
        billingClient.startConnection(this)
    }

    override fun onSkuDetailsResponse(result: BillingResult, skuDetails: MutableList<SkuDetails>?) {
        if (BuildConfig.DEBUG) {
            debug("onSkuDetailsResponse result=${resultToString(result)} skuDetails=$skuDetails")
        } else {
            debug("onSkuDetailsResponse result=${resultToString(result)}")
        }
        if (result.responseCode == BillingClient.BillingResponseCode.OK && skuDetails != null) {
            val sorted = skuDetails.sortedBy(SkuDetails::getPriceAmountMicros)
            this.skuDetails = sorted
            skus = sorted.map { IGoogleBillingHelper.SkuData(it.sku, it.price) }
            isSetUp.value = true

            for (purchase in billingClient.queryPurchases(BillingClient.SkuType.INAPP).purchasesList) {
                if (!purchase.isAcknowledged) {
                    consumePurchase(purchase)
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        debug("onPurchasesUpdated result=${resultToString(result)} purchases=$purchases")
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::consumePurchase)
        } else {
            purchasesUpdatedEvent.emit(createPurchaseError(result))
        }
    }

    override fun onConsumeResponse(result: BillingResult, purchaseToken: String) {
        debug("onConsumeResponse result=${resultToString(result)} purchaseToken=$purchaseToken")
        purchasesUpdatedEvent.emit(createPurchaseError(result))
    }

    private fun consumePurchase(purchase: Purchase) {
        debug("consumePurchase purchase=$purchase")
        val params = ConsumeParams.newBuilder()
                .setDeveloperPayload(purchase.developerPayload)
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        billingClient.consumeAsync(params, this)
    }

    override fun debug(msg: String, tr: Throwable?) {
        if (BuildConfig.DEBUG) {
            super.debug(msg, tr)
        }
    }
}