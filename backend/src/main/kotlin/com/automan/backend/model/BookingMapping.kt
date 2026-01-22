package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "booking_mappings")
data class BookingMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "country", nullable = false)
    val country: String,

    @Column(name = "client_name")
    val clientName: String? = null,

    @Column(name = "pod")
    val pod: String? = null,

    @Column(name = "stock_location")
    val stockLocation: String? = null,

    @Column(name = "pols")
    val pols: String? = null, // comma-separated list

    @Column(name = "consignee_name")
    val consigneeName: String? = null,

    @Lob
    @Column(name = "consignee_address")
    val consigneeAddress: String? = null,
    
    @Lob
    @Column(name = "notes")
    val notes: String? = null,
)


