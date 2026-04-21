package com.automan.backend.repository

import com.automan.backend.model.MasterMenu
import org.springframework.data.jpa.repository.JpaRepository

interface MasterMenuRepository : JpaRepository<MasterMenu, Long> {
    fun findByFieldNameIgnoreCase(fieldName: String): MasterMenu?
    fun existsByFieldNameIgnoreCase(fieldName: String): Boolean

    fun deleteByFieldNameIgnoreCase(fieldName: String): Long
}

