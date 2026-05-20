package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.TransactionResponse
import com.automan.backend.model.EventType
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionService(
    private val eventService: EventService,
    private val clientService: ClientService,
) {

    companion object {
        /**
         * Validates manual ledger posts (Option A: positive balance = prepaid credit).
         * Invoices post via [EventService.postInvoiceIssuedLedger] only.
         */
        fun validateManualTransaction(request: CreateTransactionRequest) {
            when (request.eventType) {
                EventType.INVOICE_ISSUED, EventType.INVOICE_REVERSAL ->
                    throw IllegalArgumentException(
                        "${request.eventType} entries are created automatically from Invoice; use Payment or Adjustment.",
                    )
                EventType.OTHER ->
                    throw IllegalArgumentException(
                        "Manual entries must use PAYMENT_RECEIVED or ADJUSTMENT.",
                    )
                EventType.SHIPMENT ->
                    throw IllegalArgumentException(
                        "SHIPMENT entries are legacy; use Payment or Adjustment.",
                    )
                EventType.PAYMENT_RECEIVED -> validatePaymentReceived(request)
                EventType.ADJUSTMENT -> validateAdjustment(request)
            }
        }

        private fun validatePaymentReceived(request: CreateTransactionRequest) {
            val payment = request.paymentReceived ?: 0.0
            val charge = request.transactionPrice ?: 0.0
            if (payment <= 0.0) {
                throw IllegalArgumentException("Payment received must be greater than zero.")
            }
            if (charge != 0.0) {
                throw IllegalArgumentException("Payment entries cannot include a debit/charge amount.")
            }
        }

        private fun validateAdjustment(request: CreateTransactionRequest) {
            val desc = request.eventDescription?.trim().orEmpty()
            if (desc.isEmpty()) {
                throw IllegalArgumentException("Adjustment reason/description is required.")
            }
            val payment = request.paymentReceived ?: 0.0
            val charge = request.transactionPrice ?: 0.0
            if (payment == 0.0 && charge == 0.0) {
                throw IllegalArgumentException("Adjustment amount cannot be zero.")
            }
            if (payment != 0.0 && charge != 0.0) {
                throw IllegalArgumentException("Adjustment must use either credit or debit, not both.")
            }
        }

        fun parseManualEventType(transactionData: Map<String, Any>): EventType {
            val raw = transactionData["eventType"] as? String
                ?: throw IllegalArgumentException("eventType is required (PAYMENT_RECEIVED or ADJUSTMENT)")
            return try {
                EventType.valueOf(raw.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid eventType: $raw")
            }
        }
    }

    @Transactional
    fun createTransaction(request: CreateTransactionRequest): TransactionResponse {
        Logger.debug("TransactionService.createTransaction — client ${request.clientId}, type ${request.eventType}")
        return try {
            clientService.getClientById(request.clientId)
                ?: throw IllegalArgumentException("Client not found: ${request.clientId}")

            validateManualTransaction(request)

            val saved = eventService.createEventFromDto(request.toCreateEventRequest())
            TransactionResponse(
                success = true,
                transactionId = saved.id,
                message = "Transaction created successfully",
                runningBalance = saved.runningBalance,
            )
        } catch (e: IllegalArgumentException) {
            Logger.warn("Manual transaction rejected: ${e.message}")
            TransactionResponse(
                success = false,
                message = e.message ?: "Invalid request",
            )
        } catch (e: Exception) {
            Logger.error("Exception in TransactionService.createTransaction: ${e.message}", e)
            TransactionResponse(
                success = false,
                message = "Failed to create transaction: ${e.message}",
            )
        }
    }
}
