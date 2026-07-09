package com.automan.backend.dto

import com.automan.backend.model.RixoMapping
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

/** API shape for Supplier Map and purchase supplier autofill (rixo prices endpoints). */
data class SupplierMapRowDto(
    val id: Long,
    @JsonProperty("auctionHouse")
    @JsonAlias("auctionName")
    val auctionHouse: String,
    val stockLocation: String,
    val rixoCompany: String,
    val venueId: String? = null,
    val pol: String? = null,
    val supportedVehicleType: String? = null,
    val rixoPrice: String? = null,
) {
    companion object {
        fun from(mapping: RixoMapping): SupplierMapRowDto = SupplierMapRowDto(
            id = mapping.id ?: 0L,
            auctionHouse = mapping.auctionName.orEmpty(),
            stockLocation = mapping.stockLocation,
            rixoCompany = mapping.rixoCompany,
            venueId = mapping.venueId,
            pol = mapping.pol,
            supportedVehicleType = mapping.supportedVehicleType,
            rixoPrice = mapping.rixoPrice,
        )

        fun toSupplierMapMap(dto: SupplierMapRowDto): Map<String, Any> = mapOf(
            "id" to dto.id,
            "auctionHouse" to dto.auctionHouse,
            "shipmentSize" to (dto.supportedVehicleType ?: ""),
            "stockLocation" to dto.stockLocation,
            "rixoCompany" to dto.rixoCompany,
            "venueId" to (dto.venueId ?: ""),
            "pol" to (dto.pol ?: ""),
            "supportedVehicleType" to (dto.supportedVehicleType ?: ""),
            "rixoPrice" to (dto.rixoPrice ?: ""),
        )
    }
}
