package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "rixo_prices")
data class RixoPrice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "auction_house", nullable = false)
    @com.fasterxml.jackson.annotation.JsonAlias("auctionName")
    val auctionHouse: String,
    
    @Column(name = "shipment_size")
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
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        // createdAt is set in the constructor with default value
    }
}
