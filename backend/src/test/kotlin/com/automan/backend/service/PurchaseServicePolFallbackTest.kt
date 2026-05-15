package com.automan.backend.service

import com.automan.backend.model.BookingMapping
import com.automan.backend.model.Purchase
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.repository.PurchaseRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.TestPropertySource

@DataJpaTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:purchase_service_pol_fallback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
class PurchaseServicePolFallbackTest {

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var bookingMappingRepository: BookingMappingRepository

    private lateinit var purchaseService: PurchaseService

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
        bookingMappingRepository.deleteAll()
        purchaseService = PurchaseService(purchaseRepository, bookingMappingRepository, PdfService())
    }

    @Test
    fun `booking POL filters include legacy purchases whose pol was blank after migration`() {
        bookingMappingRepository.save(
            BookingMapping(
                country = "STOCK_LOCATION_POL",
                stockLocation = "GLOBAL KAWASAKI",
                pols = "YOKOHAMA"
            )
        )
        purchaseRepository.save(
            purchase(
                chassis = "LEGACY-001",
                country = "PAKISTAN",
                stockLocation = "GLOBAL KAWASAKI",
                pol = null,
                shipped = false
            )
        )
        purchaseRepository.save(
            purchase(
                chassis = "LEGACY-SHIPPED",
                country = "PAKISTAN",
                stockLocation = "GLOBAL KAWASAKI",
                pol = null,
                shipped = true
            )
        )
        purchaseRepository.save(
            purchase(
                chassis = "CURRENT-001",
                country = "PAKISTAN",
                stockLocation = "GLOBAL KAWASAKI",
                pol = "HAKATA",
                shipped = false
            )
        )

        assertThat(purchaseService.getPolByCountry("PAKISTAN"))
            .containsExactly("HAKATA", "YOKOHAMA")
        assertThat(purchaseService.getFilteredChassis("PAKISTAN", "YOKOHAMA"))
            .containsExactly("LEGACY-001")
        assertThat(purchaseService.getFilteredPurchasesByCountryAndPol("PAKISTAN", "YOKOHAMA").map { it.chassis })
            .containsExactly("LEGACY-001")
        assertThat(purchaseService.getFilteredChassis("PAKISTAN", "HAKATA"))
            .containsExactly("CURRENT-001")
    }

    @Test
    fun `legacy purchases with multi POL stock locations can be selected by any mapped POL`() {
        bookingMappingRepository.save(
            BookingMapping(
                country = "STOCK_LOCATION_POL",
                stockLocation = "KLC",
                pols = "OSAKA,SENBOKU,KOBE"
            )
        )
        purchaseRepository.save(
            purchase(
                chassis = "LEGACY-KLC",
                country = "UAE",
                stockLocation = "KLC",
                pol = "",
                shipped = false
            )
        )

        assertThat(purchaseService.getPolByCountry("UAE"))
            .containsExactly("KOBE", "OSAKA", "SENBOKU")
        assertThat(purchaseService.getFilteredChassis("UAE", "SENBOKU"))
            .containsExactly("LEGACY-KLC")
    }

    private fun purchase(
        chassis: String,
        country: String,
        stockLocation: String,
        pol: String?,
        shipped: Boolean
    ): Purchase {
        return Purchase(
            chassis = chassis,
            country = country,
            stockLocation = stockLocation,
            pol = pol,
            carName = "Test Car",
            shipped = shipped
        )
    }
}
