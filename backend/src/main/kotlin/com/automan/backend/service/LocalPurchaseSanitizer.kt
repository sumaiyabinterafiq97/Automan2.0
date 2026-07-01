package com.automan.backend.service

import com.automan.backend.model.Purchase
import org.springframework.stereotype.Service

/**
 * When [Purchase.local] is true, strips export/shipping fields before persistence adapters run.
 * UI may still display prior values until save; canonical stores are cleared on write.
 */
@Service
class LocalPurchaseSanitizer {
    fun apply(purchase: Purchase): Purchase {
        if (!purchase.local) return purchase
        return purchase.copy(
            rixoCompany = null,
            stockLocation = null,
            pol = null,
            pod = null,
            country = null,
            bookingId = null,
            rixoRequested = null,
            rixoConfirmed = null,
            bookingRequested = false,
            shipmentDate = null,
            blNo = null,
            vessel = null,
            shipmentCharges = null,
            freight = null,
            inspectionFee = null,
            rixoPrice = null,
        )
    }
}
