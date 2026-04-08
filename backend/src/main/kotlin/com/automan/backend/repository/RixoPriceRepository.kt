package com.automan.backend.repository

import com.automan.backend.model.RixoPrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}
