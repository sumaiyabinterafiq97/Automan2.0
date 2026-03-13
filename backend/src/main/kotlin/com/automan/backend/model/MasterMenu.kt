package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "master_menu")
data class MasterMenu(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "field_name", nullable = false, unique = true)
    val fieldName: String,

    @Column(name = "field_values")
    val fieldValues: String? = null,
)

