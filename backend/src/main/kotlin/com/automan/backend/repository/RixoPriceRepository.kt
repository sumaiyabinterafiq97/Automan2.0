package com.automan.backend.repository

import com.automan.backend.model.RixoPrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface RixoPriceRepository : JpaRepository<RixoPrice, Long> {
    
    fun findByAuctionHouse(auctionHouse: String): List<RixoPrice>
    
    fun findByStockLocation(stockLocation: String): List<RixoPrice>
    
    fun findByRixoCompany(rixoCompany: String): List<RixoPrice>
    
    @Query("SELECT DISTINCT r.auctionHouse FROM RixoPrice r ORDER BY r.auctionHouse")
    fun findDistinctAuctionNames(): List<String>
    
    @Query("SELECT DISTINCT r.stockLocation FROM RixoPrice r ORDER BY r.stockLocation")
    fun findDistinctStockLocations(): List<String>
    
    @Query("SELECT DISTINCT r.rixoCompany FROM RixoPrice r ORDER BY r.rixoCompany")
    fun findDistinctRixoCompanies(): List<String>
    
    @Query("SELECT DISTINCT r.rixoPrice FROM RixoPrice r WHERE r.rixoPrice IS NOT NULL ORDER BY r.rixoPrice")
    fun findDistinctRixoPrices(): List<String>
    
    @Modifying
    @Transactional
    @Query("UPDATE rixo_prices SET auction_house = :auctionHouse WHERE id = :id", nativeQuery = true)
    fun updateAuctionHouse(id: Long, auctionHouse: String)
}
