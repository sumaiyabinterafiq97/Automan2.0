package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.model.EventType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TransactionServiceValidationTest {

    private fun baseRequest(
        eventType: EventType,
        paymentReceived: Double? = null,
        transactionPrice: Double? = null,
        eventDescription: String? = null,
    ) = CreateTransactionRequest(
        clientId = 1L,
        eventDate = "2026-05-20",
        eventType = eventType,
        eventDescription = eventDescription,
        paymentReceived = paymentReceived,
        transactionPrice = transactionPrice,
    )

    @Test
    fun `payment received requires positive amount and no debit`() {
        assertDoesNotThrow {
            TransactionService.validateManualTransaction(
                baseRequest(EventType.PAYMENT_RECEIVED, paymentReceived = 1_000_000.0),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionService.validateManualTransaction(
                baseRequest(EventType.PAYMENT_RECEIVED, paymentReceived = 0.0),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionService.validateManualTransaction(
                baseRequest(
                    EventType.PAYMENT_RECEIVED,
                    paymentReceived = 100.0,
                    transactionPrice = 50.0,
                ),
            )
        }
    }

    @Test
    fun `adjustment requires description and single non-zero amount`() {
        assertDoesNotThrow {
            TransactionService.validateManualTransaction(
                baseRequest(
                    EventType.ADJUSTMENT,
                    paymentReceived = -50_000.0,
                    eventDescription = "Refund partial TT",
                ),
            )
        }
        assertDoesNotThrow {
            TransactionService.validateManualTransaction(
                baseRequest(
                    EventType.ADJUSTMENT,
                    transactionPrice = 25_000.0,
                    eventDescription = "Bank fee",
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionService.validateManualTransaction(
                baseRequest(EventType.ADJUSTMENT, paymentReceived = 100.0, eventDescription = null),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionService.validateManualTransaction(
                baseRequest(
                    EventType.ADJUSTMENT,
                    paymentReceived = 100.0,
                    transactionPrice = 50.0,
                    eventDescription = "Both sides",
                ),
            )
        }
    }

    @Test
    fun `invoice issued cannot be posted manually`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            TransactionService.validateManualTransaction(
                baseRequest(
                    EventType.INVOICE_ISSUED,
                    transactionPrice = 850_000.0,
                    eventDescription = "INV-1",
                ),
            )
        }
        assertEquals(true, ex.message?.contains("automatically") == true)
    }

    @Test
    fun `invoice reversal cannot be posted manually`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            TransactionService.validateManualTransaction(
                baseRequest(
                    EventType.INVOICE_REVERSAL,
                    paymentReceived = 850_000.0,
                    eventDescription = "Reversal",
                ),
            )
        }
        assertEquals(true, ex.message?.contains("automatically") == true)
    }
}
