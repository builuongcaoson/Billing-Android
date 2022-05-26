package com.sola.billing.version5x

import android.content.Context
import com.sola.billing.version5x.listener.BillingListener
import com.sola.billing.version5x.repo.BillingRepository
import com.sola.billing.version5x.repo.BillingRepositoryImpl

class LsBilling constructor(
    context: Context,
    nonConsumableKeys: List<String> = emptyList(),
    consumableKeys: List<String> = emptyList(),
    subscriptionKeys: List<String> = emptyList(),
    billingListener: BillingListener,
    enableLogging: Boolean = false
) {

    private val billingRepo: BillingRepository

    init {
        val contextLocal = context.applicationContext ?: context
        billingRepo = BillingRepositoryImpl(contextLocal, nonConsumableKeys, consumableKeys, subscriptionKeys, billingListener)
        billingRepo.enableDebugLogging(enableLogging)
    }

    fun disconnect(){
        billingRepo.disconnect()
    }

    fun enableLogging(enableLogging: Boolean){
        billingRepo.enableDebugLogging(enableLogging)
    }

}