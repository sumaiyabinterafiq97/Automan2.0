package com.automan.backend.service

import com.automan.backend.model.Purchase
import org.springframework.stereotype.Service

/**
 * When [Purchase.local] is true, strips export/shipping fields before persistence adapters run.
 * Rixo Information (company, stock location, price), Rixo workflow flags, and country are kept:
 * LOCAL still allows yard/Rixo cost, and Vehicle Summary can show the stored country.
 */
@Service
class LocalPurchaseSanitizer {
    fun apply(purchase: Purchase): Purchase {
        if (!purchase.local) return purchase
        return purchase.copy(
            pol = null,
            pod = null,
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
