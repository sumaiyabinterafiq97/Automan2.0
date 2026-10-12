package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "client_consignee_assignments")
data class ClientConsigneeAssignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "client_id", nullable = false)
    val clientId: Long,

    @Column(name = "previous_consignee_id")
    val previousConsigneeId: Long? = null,

    @Column(name = "consignee_id")
    val consigneeId: Long? = null,

    @Column(name = "changed_by_user_id", nullable = false)
    val changedByUserId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
