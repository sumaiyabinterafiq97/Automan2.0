package com.automan.backend.dto

import java.math.BigDecimal

data class ShippingSchedulePdfRequest(
    val bookingNo: String,
    val vesselName: String,
    val pol: String, // Port of Loading
    val pod: String, // Port of Discharge
    val shippingDate: String,
    val consigneeName: String,
    val consigneeAddress: String,
    /** Consignee country selected on booking form — used with POD to pick the right `booking_mappings` row when names repeat. */
    val consigneeCountry: String? = null,
    val chassisNumbers: List<String>,
    val calculationMode: String? = null, // "C&F" or "FOB" - determines TRADE TERMS
    /**
     * Optional yen totals from the FOB/C&F calculator (per chassis). When present for a chassis,
     * the PDF price column uses this value instead of recomputing from DB / shipping_history.
     */
    val frontendTotalYenByChassis: Map<String, BigDecimal>? = null,
    val carrier: String? = null,
    val cyCutDate: String? = null,
    val eta: String? = null,
    val finalDestination: String? = null,
    val notifyParty: String? = null,
    val inTransitClause: String? = null,
)
