package com.sola.billing.version5x.repo

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.sola.billing.version5x.listener.BillingListener
import com.sola.billing.version5x.model.DataWrappers
import com.sola.billing.version5x.model.Response

private var isEnableLogging = false

class BillingRepositoryImpl constructor(
    private val context: Context,
    private val nonConsumableKeys: List<String> = emptyList(),
    private val consumableKeys: List<String> = emptyList(),
    private val subscriptionKeys: List<String> = emptyList(),
    private val billingListener: BillingListener? = null
) : BillingRepository() {

    companion object {
        const val TAG = "BillingVersion5x"
    }

    private val nonConsumableProductDetails = arrayListOf<Pair<String, DataWrappers.ProductDetail>>()
    private val consumableProductDetails = arrayListOf<Pair<String, DataWrappers.ProductDetail>>()
    private val subscriptionProductDetails = arrayListOf<Pair<String, DataWrappers.ProductDetail>>()

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context).setListener { billingResult, purchaseList ->
            billingListener?.onPurchasesUpdate(billingResult, purchaseList)

            if (billingResult.isOk() && !purchaseList.isNullOrEmpty()){
                handlePurchase(purchaseList)
            }
        }.enablePendingPurchases().build()
    }

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                log("Billing service disconnected")

                billingListener?.disconnected()
            }

            override fun onBillingSetupFinished(p0: BillingResult) {
                log("Billing setup finish with debug message: ${p0.debugMessage} --- code: ${p0.responseCode}")
                if (p0.isOk()) {
                    billingListener?.connected()

                    nonConsumableKeys.takeIf { it.isNotEmpty() }
                        ?.queryProductDetails(BillingClient.ProductType.INAPP) { response ->
                            billingListener?.updateNonConsumablePrices(response)
                        }
                        ?: run { billingListener?.updateNonConsumablePrices(Response.error(message = "No data!")) }

                    consumableKeys.takeIf { it.isNotEmpty() }
                        ?.queryProductDetails(BillingClient.ProductType.INAPP) { response ->
                            billingListener?.updateConsumablePrices(response)
                        }
                        ?: run { billingListener?.updateConsumablePrices(Response.error(message = "No data!")) }

                    subscriptionKeys.takeIf { it.isNotEmpty() }
                        ?.queryProductDetails(BillingClient.ProductType.SUBS) { response ->
                            billingListener?.updateSubscriptionPrices(response)
                        }
                        ?: run { billingListener?.updateSubscriptionPrices(Response.error(message = "No data!")) }

                    queryPurchased()

                } else billingListener?.failed()
            }
        })
    }

    /**
     * @see queryPurchased - Get a list of purchased products and handing purchased products
     */

    private fun queryPurchased() {
        val queryPurchaseHistory = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(queryPurchaseHistory) { billingResult, purchasedList ->
            if (billingResult.isOk() && !purchasedList.isNullOrEmpty()) {
                purchasedList.forEach {
                    it.products.forEach { productId ->
                        when {
                            nonConsumableKeys.contains(productId) -> {
                                handlePurchased(it)
                            }
                            consumableKeys.contains(productId) -> {
                                handlePurchased(it)
                            }
                            subscriptionKeys.contains(productId) -> {
                                handlePurchased(it)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @see handlePurchase - Handling recently purchased products
     */

    private fun handlePurchase(purchaseList: List<Purchase>) {
        purchaseList.forEach {
            it.products.forEach { productId ->
                when {
                    nonConsumableKeys.contains(productId) -> {
                        handlePurchased(it)
                    }
                    consumableKeys.contains(productId) -> {
                        handlePurchased(it)
                    }
                    subscriptionKeys.contains(productId) -> {
                        handlePurchased(it)
                    }
                }
            }
        }
    }

    /**
     * @see handlePurchased - Handling purchased products
     */

    private fun handlePurchased(purchase: Purchase) {
        handleConsumableProduct(purchase)

        handleNonConsumableProduct(purchase)
    }

    /**
     * @see handleConsumableProduct - Confirm purchases with consumer products
     */

    private fun handleConsumableProduct(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.isOk()) {
                billingListener?.onPurchasesUpdated(purchase)
            }
        }
    }

    /**
     * @see handleNonConsumableProduct - Confirm purchases with non-consumable products
     */

    private fun handleNonConsumableProduct(purchase: Purchase) {
        when {
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged -> {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.isOk()) {
                        billingListener?.onPurchasesUpdated(purchase)
                    }
                }
            }
        }
    }

    /**
     * @see disconnect - Disconnect with billing google service
     */

    override fun disconnect() {
        billingClient.endConnection()
    }

    /**
     * @see buy - Buy with an existing product id
     */

    override fun buy(
        activity: Activity,
        productId: String,
        @BillingClient.ProductType type: String
    ) {
        if (!productId.isSkuReady(type)) {
            log("Google billing service is not ready yet.")
            return
        }

        launchBillingFlow(activity, productId, type)
    }

    /**
     * @see launchBillingFlow - Handling the purchasing process
     */

    private fun launchBillingFlow(
        activity: Activity,
        productId: String,
        @BillingClient.ProductType type: String
    ) {
        val product = when (type) {
            BillingClient.ProductType.INAPP -> {
                nonConsumableProductDetails.find { it.first == productId }
                    ?: consumableProductDetails.find { it.first == productId }
            }
            else -> {
                subscriptionProductDetails.find { it.first == productId }
            }
        } ?: run {
            log("Not found product to buy.")
            return
        }
        val offerProduct = product.second.offerTokens ?: run {
            log("Not found offer product to buy.")
            return
        }

        val params = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(product.second.productDetails)
            .setOfferToken(offerProduct)

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params.build()))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun enableDebugLogging(enable: Boolean) {
        isEnableLogging = enable
    }

    private fun List<String>.queryProductDetails(
        @BillingClient.ProductType type: String,
        body: (Response<List<Pair<String, DataWrappers.ProductDetail>>>) -> Unit
    ) {
        body(Response.loading())

        if (!billingClient.isReady) {
            log("Google billing service is not ready yet.")
            body(Response.error(message = "Google billing service is not ready yet."))
            return
        }

        val productDetails = map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(type)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder().setProductList(productDetails)

        billingClient.queryProductDetailsAsync(params.build()) { billingResult, productDetailsList ->
            if (billingResult.isOk() && !productDetailsList.isNullOrEmpty()) {
                val map = productDetailsList.associateBy { it.productId }

                map.mapNotNull { entry ->
                    entry.value.let {
                        entry.key to DataWrappers.ProductDetail(
                            title = it.title,
                            description = it.description,
                            name = it.name,
                            productType = it.productType,
                            productId = it.productId,
                            oneTimePurchaseOfferDetails = it.oneTimePurchaseOfferDetails,
                            subscriptionOfferDetails = it.subscriptionOfferDetails,
                            offerTokens = it.subscriptionOfferDetails?.joinToString { sub -> sub.offerToken },
                            offerTags = it.subscriptionOfferDetails?.joinToString { sub -> sub.offerTags.joinToString { tags -> tags } },
                            sumPriceAmountMicros = it.subscriptionOfferDetails?.sumOf { sub -> sub.pricingPhases.pricingPhaseList.sumOf { price -> price.priceAmountMicros } },
                            formattedPrices = it.subscriptionOfferDetails?.joinToString { sub -> sub.pricingPhases.pricingPhaseList.joinToString { price -> price.formattedPrice } },
                            priceCurrencyCodes = it.subscriptionOfferDetails?.joinToString { sub -> sub.pricingPhases.pricingPhaseList.joinToString { price -> price.priceCurrencyCode } },
                            productDetails = it
                        )
                    }
                }.let {
                    body(Response.success(it))
                }
            } else body(Response.error(message = "Google billing service is not ready yet."))
        }
    }

    private fun String.isSkuReady(@BillingClient.ProductType type: String): Boolean {
        return when (type) {
            BillingClient.ProductType.INAPP -> nonConsumableProductDetails.find { it.first == this } != null || consumableProductDetails.find { it.first == this } != null
            else -> subscriptionProductDetails.find { it.first == this } != null
        }
    }

}

private fun BillingResult.isOk(): Boolean {
    return this.responseCode == OK
}

private fun log(message: String) {
    if (isEnableLogging) {
        Log.d(BillingRepositoryImpl.TAG, message)
    }
}
