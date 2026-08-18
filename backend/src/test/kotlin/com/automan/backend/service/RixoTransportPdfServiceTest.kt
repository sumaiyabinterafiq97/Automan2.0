package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RixoTransportPdfServiceTest {

    @Autowired
    private lateinit var purchaseService: PurchaseService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @SpyBean
    private lateinit var pdfService: PdfService

    @Test
    fun `generateRixoTransportPdf hydrates venueId from extended attributes`() {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = "PDF-VENUE-1",
                brand = "TOYOTA",
                carName = "Test Car",
                country = "Japan",
                price = "1000",
                totalPrice = "1000",
                extendedAttributesJson = """{"venueId":"1564","numberCut":"品川500あ1234"}""",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )

        val capturedPurchases = mutableListOf<List<Purchase>>()
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedPurchases.add(invocation.getArgument(0) as List<Purchase>)
            ByteArray(0)
        }.`when`(pdfService).generateRixoTransportPdf(
            ArgumentMatchers.anyList(),
            ArgumentMatchers.anyMap(),
            ArgumentMatchers.anyMap(),
        )

        purchaseService.generateRixoTransportPdf(
            listOf(saved.id!!),
            mapOf("recipient" to "SHAHBAZ", "buyingDate" to "2026-06-29"),
        )

        assertTrue(capturedPurchases.isNotEmpty())
        val purchase = capturedPurchases.single().single()
        assertEquals("1564", purchase.venueId)
        assertEquals("品川500あ1234", purchase.numberCut)
    }
}
