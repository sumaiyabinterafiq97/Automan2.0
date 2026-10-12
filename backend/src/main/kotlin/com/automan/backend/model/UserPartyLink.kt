package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * One portal account's access to one party.
 * party_id is clients.id when party_type is CLIENT, and consignees.id when it is CONSIGNEE.
 * The matching client_id or consignee_id column is the real foreign key.
 * active_user_type is a MySQL generated column and is not mapped here.
 */
@Entity
@Table(name = "user_party_links")
data class UserPartyLink(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "party_type", nullable = false, length = 16)
    val partyType: String,

    @Column(name = "party_id", nullable = false)
    val partyId: Long,

    @Column(name = "client_id")
    val clientId: Long? = null,

    @Column(name = "consignee_id")
    val consigneeId: Long? = null,

    @Column(name = "status", nullable = false, length = 16)
    val status: String,

    @Column(name = "linked_by_user_id")
    val linkedByUserId: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "revoked_at")
    val revokedAt: LocalDateTime? = null,
)
