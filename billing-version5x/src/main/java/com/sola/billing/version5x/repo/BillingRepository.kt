package com.sola.billing.version5x.repo

import android.app.Activity
import com.android.billingclient.api.BillingClient

abstract class BillingRepository {

    abstract fun disconnect()

    abstract fun buy(activity: Activity, productId: String, @BillingClient.ProductType type: String)

    abstract fun enableDebugLogging(enable: Boolean)

}
