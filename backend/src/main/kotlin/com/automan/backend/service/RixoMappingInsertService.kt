package com.automan.backend.service

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Runs rixo_prices INSERT in its own transaction (REQUIRES_NEW).
 * When add-mapping retries (with vs without auction_house), the first attempt
 * must not mark the caller's transaction as rollback-only (which causes
 * "Transaction silently rolled back" on AWS when schema differs).
 */
@Service
class RixoMappingInsertService(
    private val entityManager: EntityManager
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertWithAuctionHouse(
        auctionHouse: String,
        shipmentSize: String?,
        stockLocation: String,
        rixoCompany: String,
        rixoPrice: String?,
        venueId: String?,
        pol: String? = null
    ): Long {
        val resolvedPol = pol?.takeIf { it.isNotBlank() } ?: polFromStockLocation(stockLocation)
        val params = mapOf(
            "auctionName" to auctionHouse,
            "auctionHouse" to auctionHouse,
            "typeOfVehicle" to (shipmentSize?.takeIf { it.isNotBlank() }),
            "stockLocation" to stockLocation,
            "rixoCompany" to rixoCompany,
            "venueId" to (venueId?.takeIf { it.isNotBlank() }),
            "rixoPrice" to (rixoPrice?.takeIf { it.isNotBlank() }),
            "pol" to (resolvedPol?.takeIf { it.isNotBlank() })
        )
        val sql = """
            INSERT INTO rixo_prices (auction_name, auction_house, type_of_vehicle, stock_location, rixo_company, venue_id, rixo_price, pol, created_at)
            VALUES (:auctionName, :auctionHouse, :typeOfVehicle, :stockLocation, :rixoCompany, :venueId, :rixoPrice, :pol, CURRENT_TIMESTAMP)
        """
        val q = entityManager.createNativeQuery(sql)
        params.forEach { (k, v) -> q.setParameter(k, v) }
        q.executeUpdate()
        val idResult = entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").singleResult
        return (idResult as? Number)?.toLong() ?: throw IllegalStateException("Failed to get inserted ID")
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertWithoutAuctionHouse(
        auctionHouse: String,
        shipmentSize: String?,
        stockLocation: String,
        rixoCompany: String,
        rixoPrice: String?,
        venueId: String?,
        pol: String? = null
    ): Long {
        val resolvedPol = pol?.takeIf { it.isNotBlank() } ?: polFromStockLocation(stockLocation)
        val params = mapOf(
            "auctionName" to auctionHouse,
            "typeOfVehicle" to (shipmentSize?.takeIf { it.isNotBlank() }),
            "stockLocation" to stockLocation,
            "rixoCompany" to rixoCompany,
            "venueId" to (venueId?.takeIf { it.isNotBlank() }),
            "rixoPrice" to (rixoPrice?.takeIf { it.isNotBlank() }),
            "pol" to (resolvedPol?.takeIf { it.isNotBlank() })
        )
        val sql = """
            INSERT INTO rixo_prices (auction_name, type_of_vehicle, stock_location, rixo_company, venue_id, rixo_price, pol, created_at)
            VALUES (:auctionName, :typeOfVehicle, :stockLocation, :rixoCompany, :venueId, :rixoPrice, :pol, CURRENT_TIMESTAMP)
        """
        val q = entityManager.createNativeQuery(sql)
        params.forEach { (k, v) -> q.setParameter(k, v) }
        q.executeUpdate()
        val idResult = entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").singleResult
        return (idResult as? Number)?.toLong() ?: throw IllegalStateException("Failed to get inserted ID")
    }

    /** POL from stock_location mapping (GLOBAL KAWASAKI->YOKOHAMA, AQUA LOGISTICS->YOKOHAMA, GLOBAL NAGOYA->NAGOYA, FLASHRISE->NAGOYA, KLC->OSAKA, GLOBAL HAKATA->HAKATA, BARAKI PARKING->---, LOCAL->---). */
    private fun polFromStockLocation(stockLocation: String): String? {
        val s = stockLocation.trim()
        return when {
            s.startsWith("GLOBAL KAWASAKI", ignoreCase = true) -> "YOKOHAMA"
            s.equals("AQUA LOGISTICS", ignoreCase = true) -> "YOKOHAMA"
            s.startsWith("GLOBAL NAGOYA", ignoreCase = true) -> "NAGOYA"
            s.equals("FLASHRISE", ignoreCase = true) -> "NAGOYA"
            s.equals("KLC", ignoreCase = true) -> "OSAKA"
            s.startsWith("GLOBAL HAKATA", ignoreCase = true) -> "HAKATA"
            s.equals("BARAKI PARKING", ignoreCase = true) -> "---"
            s.equals("LOCAL", ignoreCase = true) -> "---"
            else -> null
        }
    }
}
