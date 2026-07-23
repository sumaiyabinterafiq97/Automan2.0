package com.automan.backend.repository

import com.automan.backend.model.RixoHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RixoHistoryRepository : JpaRepository<RixoHistory, Long> {

    @Query(
        value = (
            "SELECT h FROM RixoHistory h WHERE " +
                "LOWER(COALESCE(h.rixoCompany,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.chassis,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.message,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(CAST(h.buyingDate AS string),'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
        countQuery = (
            "SELECT count(h) FROM RixoHistory h WHERE " +
                "LOWER(COALESCE(h.rixoCompany,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.chassis,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.message,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(CAST(h.buyingDate AS string),'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
    )
    fun searchKeyFields(@Param("q") q: String, pageable: Pageable): Page<RixoHistory>
}
