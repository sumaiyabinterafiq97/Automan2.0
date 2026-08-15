package com.automan.backend.repository

import com.automan.backend.model.StockLocationMap
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockLocationMapRepository : JpaRepository<StockLocationMap, Long> {

    fun findByStockLocationIgnoreCase(stockLocation: String): StockLocationMap?

    @Query(
        value = """
            SELECT * FROM stock_location_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(IFNULL(s.pol, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(IFNULL(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        countQuery = """
            SELECT COUNT(*) FROM stock_location_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(IFNULL(s.pol, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(IFNULL(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        nativeQuery = true,
    )
    fun searchAllFields(@Param("q") q: String, pageable: Pageable): Page<StockLocationMap>

    @Query(
        value = """
            SELECT * FROM stock_location_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        countQuery = """
            SELECT COUNT(*) FROM stock_location_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        nativeQuery = true,
    )
    fun searchStockLocationContains(@Param("q") q: String, pageable: Pageable): Page<StockLocationMap>

    @Query(
        value = """
            SELECT * FROM stock_location_map s WHERE
              LOWER(IFNULL(s.pol, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        countQuery = """
            SELECT COUNT(*) FROM stock_location_map s WHERE
              LOWER(IFNULL(s.pol, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        nativeQuery = true,
    )
    fun searchPolContains(@Param("q") q: String, pageable: Pageable): Page<StockLocationMap>

    @Query(
        value = """
            SELECT * FROM stock_location_map s WHERE
              LOWER(IFNULL(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        countQuery = """
            SELECT COUNT(*) FROM stock_location_map s WHERE
              LOWER(IFNULL(s.address, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        nativeQuery = true,
    )
    fun searchAddressContains(@Param("q") q: String, pageable: Pageable): Page<StockLocationMap>
}
