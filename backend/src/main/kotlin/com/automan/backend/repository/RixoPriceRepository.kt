package com.automan.backend.repository

import com.automan.backend.model.RixoPrice
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RixoPriceRepository : JpaRepository<RixoPrice, Long> {
    
    fun findByAuctionHouse(auctionHouse: String): List<RixoPrice>

    fun findFirstByAuctionHouseIgnoreCase(auctionHouse: String): RixoPrice?
    
    fun findByStockLocation(stockLocation: String): List<RixoPrice>
    
    fun findByRixoCompany(rixoCompany: String): List<RixoPrice>
    
    @Query("SELECT DISTINCT r.auctionHouse FROM RixoPrice r ORDER BY r.auctionHouse")
    fun findDistinctAuctionNames(): List<String>
    
    @Query("SELECT DISTINCT r.stockLocation FROM RixoPrice r ORDER BY r.stockLocation")
    fun findDistinctStockLocations(): List<String>
    
    @Query("SELECT DISTINCT r.rixoCompany FROM RixoPrice r ORDER BY r.rixoCompany")
    fun findDistinctRixoCompanies(): List<String>

    @Query(
        value = """
            SELECT r FROM RixoPrice r WHERE
            LOWER(r.auctionHouse) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(r) FROM RixoPrice r WHERE
            LOWER(r.auctionHouse) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%'))
            """
    )
    fun searchSupplierMapAllFields(@Param("q") q: String, pageable: Pageable): Page<RixoPrice>

    @Query(
        value = """SELECT r FROM RixoPrice r WHERE LOWER(r.auctionHouse) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(r) FROM RixoPrice r WHERE LOWER(r.auctionHouse) LIKE LOWER(CONCAT('%', :q, '%'))"""
    )
    fun searchSupplierMapAuctionHouseContains(@Param("q") q: String, pageable: Pageable): Page<RixoPrice>

    @Query(
        value = """SELECT r FROM RixoPrice r WHERE LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(r) FROM RixoPrice r WHERE LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%'))"""
    )
    fun searchSupplierMapStockLocationContains(@Param("q") q: String, pageable: Pageable): Page<RixoPrice>

    @Query(
        value = """SELECT r FROM RixoPrice r WHERE LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(r) FROM RixoPrice r WHERE LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%'))"""
    )
    fun searchSupplierMapRixoCompanyContains(@Param("q") q: String, pageable: Pageable): Page<RixoPrice>
}
