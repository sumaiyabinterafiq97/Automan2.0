package com.automan.backend.repository

import com.automan.backend.model.ShippingChargeMap
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShippingChargeMapRepository : JpaRepository<ShippingChargeMap, Long> {

    fun findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(stockLocation: String): List<ShippingChargeMap>

    fun findByStockLocationIgnoreCaseAndCarsPerContainer(
        stockLocation: String,
        carsPerContainer: Int,
    ): ShippingChargeMap?

    @Query(
        value = """
            SELECT * FROM shipping_charge_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
              OR CAST(s.cars_per_container AS CHAR) LIKE CONCAT('%', :q, '%')
              OR CAST(s.shipping_price_per_car AS CHAR) LIKE CONCAT('%', :q, '%')
        """,
        countQuery = """
            SELECT COUNT(*) FROM shipping_charge_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
              OR CAST(s.cars_per_container AS CHAR) LIKE CONCAT('%', :q, '%')
              OR CAST(s.shipping_price_per_car AS CHAR) LIKE CONCAT('%', :q, '%')
        """,
        nativeQuery = true,
    )
    fun searchAllFields(@Param("q") q: String, pageable: Pageable): Page<ShippingChargeMap>

    @Query(
        value = """
            SELECT * FROM shipping_charge_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        countQuery = """
            SELECT COUNT(*) FROM shipping_charge_map s WHERE
              LOWER(s.stock_location) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
        nativeQuery = true,
    )
    fun searchStockLocationContains(@Param("q") q: String, pageable: Pageable): Page<ShippingChargeMap>

    @Query(
        value = """
            SELECT * FROM shipping_charge_map s WHERE
              CAST(s.cars_per_container AS CHAR) LIKE CONCAT('%', :q, '%')
        """,
        countQuery = """
            SELECT COUNT(*) FROM shipping_charge_map s WHERE
              CAST(s.cars_per_container AS CHAR) LIKE CONCAT('%', :q, '%')
        """,
        nativeQuery = true,
    )
    fun searchCarsPerContainerContains(@Param("q") q: String, pageable: Pageable): Page<ShippingChargeMap>

    @Query(
        value = """
            SELECT * FROM shipping_charge_map s WHERE
              CAST(s.shipping_price_per_car AS CHAR) LIKE CONCAT('%', :q, '%')
        """,
        countQuery = """
            SELECT COUNT(*) FROM shipping_charge_map s WHERE
              CAST(s.shipping_price_per_car AS CHAR) LIKE CONCAT('%', :q, '%')
        """,
        nativeQuery = true,
    )
    fun searchPriceContains(@Param("q") q: String, pageable: Pageable): Page<ShippingChargeMap>

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ShippingChargeMap e WHERE LOWER(e.stockLocation) = LOWER(:loc)")
    fun deleteByStockLocationIgnoreCase(@Param("loc") loc: String): Int
}
