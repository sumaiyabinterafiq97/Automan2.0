package com.automan.backend.service

import com.automan.backend.model.Purchase
import org.springframework.stereotype.Service

/**
 * When [Purchase.local] is true, strips export/shipping fields before persistence adapters run.
 * Rixo Information (company, stock location, price) and Rixo workflow flags are kept:
 * LOCAL still allows yard/Rixo cost, and Rixo Save must be able to mark requested.
 */
@Service
class LocalPurchaseSanitizer {
    fun apply(purchase: Purchase): Purchase {
        if (!purchase.local) return purchase
        return purchase.copy(
            pol = null,
            pod = null,
            country = null,
            bookingId = null,
            bookingRequested = false,
            shipmentDate = null,
            blNo = null,
            vessel = null,
            shipmentCharges = null,
            freight = null,
            inspectionFee = null,
        )
    }
}
