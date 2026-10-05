package com.automan.backend.repository

import com.automan.backend.model.RixoEmailMap
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RixoEmailMapRepository : JpaRepository<RixoEmailMap, Long> {

    @Query(
        """
        SELECT r FROM RixoEmailMap r
        WHERE LOWER(TRIM(r.rixoCompany)) = LOWER(TRIM(:company))
          AND LOWER(TRIM(r.email)) = LOWER(TRIM(:email))
        """,
    )
    fun findByCompanyAndEmailIgnoreCase(
        @Param("company") company: String,
        @Param("email") email: String,
    ): RixoEmailMap?

    @Query(
        """
        SELECT r.email FROM RixoEmailMap r
        WHERE LOWER(TRIM(r.rixoCompany)) = LOWER(TRIM(:company))
        ORDER BY r.email ASC
        """,
    )
    fun findEmailsByCompanyIgnoreCase(@Param("company") company: String): List<String>
}
