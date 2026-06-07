package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.OpeningBalanceImportRequest
import com.automan.backend.dto.OpeningBalanceImportResult
import com.automan.backend.dto.OpeningBalanceImportRowDto
import com.automan.backend.dto.TransactionResponse
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class TransactionService(
    private val eventService: EventService,
    private val clientService: ClientService,
    private val clientRepository: ClientRepository,
    private val eventRepository: EventRepository,
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
                        "Manual entries must use PAYMENT_RECEIVED, ADJUSTMENT, or OPENING_BALANCE.",
                    )
                EventType.SHIPMENT ->
                    throw IllegalArgumentException(
                        "SHIPMENT entries are legacy; use Payment or Adjustment.",
                    )
                EventType.PAYMENT_RECEIVED -> validatePaymentReceived(request)
                EventType.ADJUSTMENT -> validateAdjustment(request)
                EventType.OPENING_BALANCE -> validateOpeningBalance(request, existingOpeningCount = null)
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

        private fun validateOpeningBalance(request: CreateTransactionRequest, existingOpeningCount: Long?) {
            if (existingOpeningCount != null && existingOpeningCount > 0) {
                throw IllegalArgumentException("This client already has an opening balance entry.")
            }
            val payment = request.paymentReceived ?: 0.0
            val charge = request.transactionPrice ?: 0.0
            if (payment == 0.0 && charge == 0.0) {
                throw IllegalArgumentException("Opening balance amount cannot be zero.")
            }
            if (payment != 0.0 && charge != 0.0) {
                throw IllegalArgumentException("Opening balance must be either credit or debit, not both.")
            }
        }

        fun parseManualEventType(transactionData: Map<String, Any>): EventType {
            val raw = transactionData["eventType"] as? String
                ?: throw IllegalArgumentException(
                    "eventType is required (PAYMENT_RECEIVED, ADJUSTMENT, or OPENING_BALANCE)",
                )
            return try {
                EventType.valueOf(raw.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid eventType: $raw")
            }
        }

        /** Option A: positive signed amount = prepaid credit; negative = amount owed. */
        fun signedAmountToLedger(signedAmount: Double): Pair<Double?, Double?> {
            return when {
                signedAmount > 0.0 -> Pair(signedAmount, null)
                signedAmount < 0.0 -> Pair(null, kotlin.math.abs(signedAmount))
                else -> Pair(null, null)
            }
        }
    }

    @Transactional
    fun createTransaction(request: CreateTransactionRequest): TransactionResponse {
        Logger.debug("TransactionService.createTransaction — client ${request.clientId}, type ${request.eventType}")
        return try {
            clientService.getClientById(request.clientId)
                ?: throw IllegalArgumentException("Client not found: ${request.clientId}")

            if (request.eventType == EventType.OPENING_BALANCE) {
                val count = eventRepository.countByClientIdAndEventType(
                    request.clientId,
                    EventType.OPENING_BALANCE,
                )
                validateOpeningBalance(request, count)
            } else {
                validateManualTransaction(request)
            }

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

    @Transactional
    fun updateManualTransaction(eventId: Long, updateData: Map<String, Any>): TransactionResponse {
        return try {
            val existing = eventService.getEventById(eventId)
                ?: throw IllegalArgumentException("Ledger entry not found: $eventId")
            if (!EventService.isManualEditableEventType(existing.eventType)) {
                throw IllegalArgumentException("This ledger entry cannot be edited.")
            }

            validateManualTransaction(toValidationRequest(existing, updateData))

            val updated = eventService.updateManualEvent(eventId, updateData)
                ?: throw IllegalArgumentException("Ledger entry not found: $eventId")

            TransactionResponse(
                success = true,
                transactionId = updated.id,
                message = "Ledger entry updated",
                runningBalance = updated.runningBalance,
            )
        } catch (e: IllegalArgumentException) {
            TransactionResponse(success = false, message = e.message ?: "Invalid request")
        } catch (e: Exception) {
            TransactionResponse(success = false, message = "Failed to update: ${e.message}")
        }
    }

    @Transactional
    fun deleteManualTransaction(eventId: Long): TransactionResponse {
        return try {
            val existing = eventService.getEventById(eventId)
                ?: throw IllegalArgumentException("Ledger entry not found: $eventId")
            if (!EventService.isManualEditableEventType(existing.eventType)) {
                throw IllegalArgumentException("This ledger entry cannot be deleted.")
            }
            val deleted = eventService.deleteManualEvent(eventId)
            if (!deleted) {
                throw IllegalArgumentException("Ledger entry not found: $eventId")
            }
            val client = clientService.getClientById(existing.clientId)
            TransactionResponse(
                success = true,
                message = "Ledger entry deleted",
                runningBalance = client?.currentBalance,
            )
        } catch (e: IllegalArgumentException) {
            TransactionResponse(success = false, message = e.message ?: "Invalid request")
        } catch (e: Exception) {
            TransactionResponse(success = false, message = "Failed to delete: ${e.message}")
        }
    }

    private fun toValidationRequest(existing: Event, updateData: Map<String, Any>): CreateTransactionRequest {
        return CreateTransactionRequest(
            clientId = existing.clientId,
            eventDate = (updateData["eventDate"] as? String) ?: existing.eventDate.toString(),
            eventType = existing.eventType,
            eventDescription = if (updateData.containsKey("eventDescription")) {
                updateData["eventDescription"] as? String
            } else {
                existing.eventDescription
            },
            billNumber = if (updateData.containsKey("billNumber")) {
                updateData["billNumber"] as? String
            } else {
                existing.billNumber
            },
            transactionPrice = if (updateData.containsKey("transactionPrice")) {
                (updateData["transactionPrice"] as? Number)?.toDouble()
            } else {
                existing.transactionPrice
            },
            paymentReceived = if (updateData.containsKey("paymentReceived")) {
                (updateData["paymentReceived"] as? Number)?.toDouble()
            } else {
                existing.paymentReceived
            },
        )
    }

    @Transactional
    fun importOpeningBalances(request: OpeningBalanceImportRequest): OpeningBalanceImportResult {
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0

        for ((index, row) in request.rows.withIndex()) {
            val line = index + 1
            val clientNumber = row.clientNumber.trim()
            if (clientNumber.isEmpty()) {
                errors.add("Line $line: client number is required")
                skipped++
                continue
            }
            val client = clientRepository.findByClientNumber(clientNumber)
            if (client == null) {
                errors.add("Line $line: client not found: $clientNumber")
                skipped++
                continue
            }
            val clientId = client.id!!
            if (eventRepository.countByClientIdAndEventType(clientId, EventType.OPENING_BALANCE) > 0) {
                errors.add("Line $line: $clientNumber already has an opening balance — skipped")
                skipped++
                continue
            }
            val eventDate = try {
                LocalDate.parse(row.eventDate.trim())
            } catch (_: DateTimeParseException) {
                errors.add("Line $line: invalid date ${row.eventDate}")
                skipped++
                continue
            }
            if (row.amount == 0.0) {
                errors.add("Line $line: amount cannot be zero")
                skipped++
                continue
            }
            val (payment, charge) = signedAmountToLedger(row.amount)
            val note = row.note?.trim().orEmpty()
            val description = if (note.isNotEmpty()) {
                note
            } else {
                "Opening balance as of $eventDate"
            }

            val createReq = CreateTransactionRequest(
                clientId = clientId,
                eventDate = eventDate.toString(),
                eventType = EventType.OPENING_BALANCE,
                eventDescription = description,
                paymentReceived = payment,
                transactionPrice = charge,
            )
            val result = createTransaction(createReq)
            if (result.success) {
                imported++
            } else {
                errors.add("Line $line: ${result.message}")
                skipped++
            }
        }

        return OpeningBalanceImportResult(
            imported = imported,
            skipped = skipped,
            errors = errors,
        )
    }
}
