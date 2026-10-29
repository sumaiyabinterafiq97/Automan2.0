package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CarSearchServiceCountTest {

    @Autowired
    private lateinit var carSearchService: CarSearchService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun clearPurchases() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun countUnshippedCars_excludesRowsWithBookingId() {
        savePurchase(chassis = "U-1", country = "Japan", stockLocation = "Tokyo", bookingId = null)
        savePurchase(chassis = "U-2", country = "Japan", stockLocation = "Tokyo", bookingId = null)
        savePurchase(chassis = "B-1", country = "Japan", stockLocation = "Tokyo", bookingId = 99L)

        assertEquals(2L, carSearchService.getUnshippedCarsCount())
        assertEquals(2L, purchaseRepository.countUnshippedCars())
    }

    @Test
    fun countUnshippedCarsByCountry_matchesExactCountryAndNullBookingOnly() {
        savePurchase(chassis = "JP-1", country = "Japan", stockLocation = "Tokyo", bookingId = null)
        savePurchase(chassis = "JP-2", country = "Japan", stockLocation = "Osaka", bookingId = null)
        savePurchase(chassis = "KR-1", country = "Korea", stockLocation = "Busan", bookingId = null)
        savePurchase(chassis = "JP-SHIPPED", country = "Japan", stockLocation = "Tokyo", bookingId = 1L)

        assertEquals(2L, carSearchService.getUnshippedCarsCountByCountry("Japan"))
        assertEquals(0L, carSearchService.getUnshippedCarsCountByCountry("China"))
    }

    @Test
    fun countUnshippedCarsByPolPort_matchesExactStockLocationAndNullBookingOnly() {
        savePurchase(chassis = "T-1", country = "Japan", stockLocation = "Tokyo", bookingId = null)
        savePurchase(chassis = "T-2", country = "Korea", stockLocation = "Tokyo", bookingId = null)
        savePurchase(chassis = "O-1", country = "Japan", stockLocation = "Osaka", bookingId = null)
        savePurchase(chassis = "T-BOOKED", country = "Japan", stockLocation = "Tokyo", bookingId = 5L)

        assertEquals(2L, carSearchService.getUnshippedCarsCountByPolPort("Tokyo"))
        assertEquals(0L, carSearchService.getUnshippedCarsCountByPolPort("Yokohama"))
    }

    @Test
    fun countByBookingId_countsOnlyMatchingBooking() {
        savePurchase(chassis = "B1-1", country = "Japan", stockLocation = "Tokyo", bookingId = 10L)
        savePurchase(chassis = "B1-2", country = "Japan", stockLocation = "Tokyo", bookingId = 10L)
        savePurchase(chassis = "B2-1", country = "Japan", stockLocation = "Tokyo", bookingId = 20L)
        savePurchase(chassis = "NONE", country = "Japan", stockLocation = "Tokyo", bookingId = null)

        assertEquals(2L, carSearchService.getBookingCarCount(10L))
        assertEquals(1L, carSearchService.getBookingCarCount(20L))
        assertEquals(0L, carSearchService.getBookingCarCount(999L))
    }

    @Test
    fun countMethods_returnZeroOnEmptyDatabase() {
        assertEquals(0L, carSearchService.getUnshippedCarsCount())
        assertEquals(0L, carSearchService.getUnshippedCarsCountByCountry("Japan"))
        assertEquals(0L, carSearchService.getUnshippedCarsCountByPolPort("Tokyo"))
        assertEquals(0L, carSearchService.getBookingCarCount(1L))
    }

    @Test
    fun serviceCountMethods_useRepositoryCountQueries_notListLoads() {
        val repo = mock(PurchaseRepository::class.java)
        val service = CarSearchService(repo)
        `when`(repo.countUnshippedCars()).thenReturn(3L)
        `when`(repo.countUnshippedCarsByCountry("Japan")).thenReturn(2L)
        `when`(repo.countUnshippedCarsByPolPort("Tokyo")).thenReturn(1L)
        `when`(repo.countByBookingId(7L)).thenReturn(4L)

        assertEquals(3L, service.getUnshippedCarsCount())
        assertEquals(2L, service.getUnshippedCarsCountByCountry("Japan"))
        assertEquals(1L, service.getUnshippedCarsCountByPolPort("Tokyo"))
        assertEquals(4L, service.getBookingCarCount(7L))

        verify(repo).countUnshippedCars()
        verify(repo).countUnshippedCarsByCountry("Japan")
        verify(repo).countUnshippedCarsByPolPort("Tokyo")
        verify(repo).countByBookingId(7L)
        verify(repo, never()).findUnshippedCars()
        verify(repo, never()).findUnshippedCarsByCountry("Japan")
        verify(repo, never()).findUnshippedCarsByPolPort("Tokyo")
        verify(repo, never()).findByBookingId(7L)
    }

    private fun savePurchase(
        chassis: String,
        country: String,
        stockLocation: String?,
        bookingId: Long?,
    ): Purchase =
        purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Test Car",
                brand = "Toyota",
                country = country,
                stockLocation = stockLocation,
                price = "1",
                auctionFee = "0",
                recycleFee = "0",
                roadTax = "0",
                totalPrice = "1",
                paymentDate = LocalDate.now().toString(),
                rixoRequested = "0",
                rixoConfirmed = "0",
                rixoPrice = "0",
                shipmentDate = LocalDate.now().toString(),
                vessel = "V1",
                shipmentCharges = "0",
                freight = "0",
                storageCharges = "0",
                miscCharges = "0",
                inspectionFee = "0",
                commission = "0",
                repairCompany = "R",
                repairCharges = "0",
                notes = "",
                numberCut = "0",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                bookingId = bookingId,
            ),
        )
}
