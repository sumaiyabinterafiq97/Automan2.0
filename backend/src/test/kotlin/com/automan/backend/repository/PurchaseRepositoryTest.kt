package com.automan.backend.repository

import com.automan.backend.model.Purchase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.TestPropertySource

@DataJpaTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:purchase_repository_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    ]
)
class PurchaseRepositoryTest {

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `booking filters fall back to stock location when pol is missing`() {
        purchaseRepository.save(
            purchase(
                chassis = "LEGACY-001",
                country = "Japan",
                stockLocation = "GLOBAL KAWASAKI",
                pol = null,
                shipped = false
            )
        )
        purchaseRepository.save(
            purchase(
                chassis = "LEGACY-SHIPPED",
                country = "Japan",
                stockLocation = "GLOBAL KAWASAKI",
                pol = null,
                shipped = true
            )
        )
        purchaseRepository.save(
            purchase(
                chassis = "CURRENT-001",
                country = "Japan",
                stockLocation = "GLOBAL KAWASAKI",
                pol = "YOKOHAMA",
                shipped = false
            )
        )

        assertThat(purchaseRepository.findDistinctPolByCountry("Japan"))
            .containsExactly("GLOBAL KAWASAKI", "YOKOHAMA")
        assertThat(purchaseRepository.findFilteredChassis("Japan", "GLOBAL KAWASAKI"))
            .containsExactly("LEGACY-001")
        assertThat(purchaseRepository.findFilteredPurchasesByCountryAndPol("Japan", "GLOBAL KAWASAKI").map { it.chassis })
            .containsExactly("LEGACY-001")
        assertThat(purchaseRepository.findUnshippedChassisByPolPort("GLOBAL KAWASAKI"))
            .containsExactly("LEGACY-001")
    }

    @Test
    fun `explicit pol takes precedence over stock location fallback`() {
        purchaseRepository.save(
            purchase(
                chassis = "CURRENT-001",
                country = "Japan",
                stockLocation = "GLOBAL KAWASAKI",
                pol = "YOKOHAMA",
                shipped = false
            )
        )

        assertThat(purchaseRepository.findFilteredChassis("Japan", "GLOBAL KAWASAKI"))
            .isEmpty()
        assertThat(purchaseRepository.findFilteredChassis("Japan", "YOKOHAMA"))
            .containsExactly("CURRENT-001")
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
