package com.sola.billing.version5x.listener

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.sola.billing.version5x.model.DataWrappers
import com.sola.billing.version5x.model.Response

interface BillingListener {

    fun onPurchasesUpdated(purchase: Purchase)

    fun onPurchasesUpdate(billingResult: BillingResult, purchaseResults: List<Purchase>?)

    fun disconnected()

    fun connected()

    fun failed()

    fun updateNonConsumablePrices(response: Response<List<Pair<String, DataWrappers.ProductDetail>>>)

    fun updateConsumablePrices(response: Response<List<Pair<String, DataWrappers.ProductDetail>>>)

    fun updateSubscriptionPrices(response: Response<List<Pair<String, DataWrappers.ProductDetail>>>)

}