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
    
    @Column(name = "stock_location", nullable = false)
    val stockLocation: String,
    
    @Column(name = "rixo_company", nullable = false)
    val rixoCompany: String,
    
    @Column(name = "venue_id")
    val venueId: String? = null,

    @Column(name = "pol")
    val pol: String? = null,
    
    @Column(name = "created_at")
    @JsonIgnore
    val createdAt: LocalDateTime = LocalDateTime.now()
)
