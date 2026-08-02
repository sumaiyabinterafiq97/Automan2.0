package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "booking_mappings")
data class BookingMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "country", nullable = false)
    val country: String,

    @Column(name = "consignee_name")
    val consigneeName: String? = null,

    @Lob
    @Column(name = "consignee_address")
    val consigneeAddress: String? = null,

    @Lob
    @Column(name = "pod")
    val pod: String? = null,

    @Column(name = "final_destination", columnDefinition = "TEXT")
    val finalDestination: String? = null,

    @Column(name = "notify_party", columnDefinition = "TEXT")
    val notifyParty: String? = null,

    @Column(name = "in_transit_clause", columnDefinition = "TEXT")
    val inTransitClause: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
