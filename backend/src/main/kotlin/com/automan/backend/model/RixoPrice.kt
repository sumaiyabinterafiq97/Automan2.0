package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonIgnore

@Entity
@Table(name = "rixo_prices")
data class RixoPrice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "auction_name", nullable = false)
    @com.fasterxml.jackson.annotation.JsonAlias("auctionName")
    val auctionHouse: String,
    
    @Column(name = "type_of_vehicle")
    @com.fasterxml.jackson.annotation.JsonAlias("typeOfVehicle")
    val shipmentSize: String? = null,
    
    @Column(name = "stock_location", nullable = false)
    val stockLocation: String,
    
    @Column(name = "rixo_company", nullable = false)
    val rixoCompany: String,
    
    @Column(name = "venue_id")
    val venueId: String? = null,
    
    @Column(name = "rixo_price")
    val rixoPrice: String? = null,
    
    @Column(name = "created_at")
    @JsonIgnore
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        // createdAt is set in the constructor with default value
        // auction_house needs to be set from auctionHouse
        // Since this is a data class, we'll handle this in the service layer
    }
    
    companion object {
        fun create(
            auctionHouse: String,
            shipmentSize: String? = null,
            stockLocation: String,
            rixoCompany: String,
            rixoPrice: String? = null,
            venueId: String? = null
        ): RixoPrice {
            return RixoPrice(
                auctionHouse = auctionHouse,
                // auctionHouseDb is a generated column, automatically set from auction_name
                shipmentSize = shipmentSize,
                stockLocation = stockLocation,
                rixoCompany = rixoCompany,
                rixoPrice = rixoPrice,
                venueId = venueId
            )
        }
    }
}
