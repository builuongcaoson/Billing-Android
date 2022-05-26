package com.sola.billing.version5x.model

import com.android.billingclient.api.ProductDetails

class DataWrappers {

    data class ProductDetail(
        val title: String,
        val description: String,
        val name: String,
        val productType: String,
        val productId: String,
        val oneTimePurchaseOfferDetails: ProductDetails.OneTimePurchaseOfferDetails? = null,
        val subscriptionOfferDetails: List<ProductDetails.SubscriptionOfferDetails>? = null,
        val offerTokens: String?,
        val offerTags: String?,
        val sumPriceAmountMicros: Long?,
        val formattedPrices: String?,
        val priceCurrencyCodes: String?,
        val productDetails: ProductDetails
    )

}