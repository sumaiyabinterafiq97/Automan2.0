package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.TransactionResponse
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.EventRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class TransactionService(
    private val eventRepository: EventRepository,
    private val clientService: ClientService
) {
    
    @Transactional
    fun createTransaction(request: CreateTransactionRequest): TransactionResponse {
        Logger.debug("TransactionService.createTransaction called - Client ID: ${request.clientId}")
        
        return try {
            // Fetch client from database
            val client = clientService.getClientById(request.clientId)
                ?: throw IllegalArgumentException("Client not found: ${request.clientId}")
            Logger.debug("Client found: ${client.clientName}")
            
            // Calculate running balance
            val currentBalance = client.currentBalance
            val newBalance = currentBalance + (request.paymentReceived ?: 0.0) - (request.transactionPrice ?: 0.0)
            Logger.debug("Current balance: $currentBalance, New balance: $newBalance")
            
            // Create Event object
            val event = Event(
                clientId = client.id!!,
                eventDate = LocalDate.parse(request.eventDate),
                eventType = EventType.OTHER,
                eventDescription = request.eventDescription,
                quantity = request.quantity,
                billNumber = request.billNumber,
                transactionPrice = request.transactionPrice,
                paymentReceived = request.paymentReceived,
                runningBalance = newBalance
            )
            
            // Save the event
            val savedEvent = eventRepository.save(event)
            Logger.debug("Event saved with ID: ${savedEvent.id}")
            
            // Update client balance
            clientService.updateClientBalance(request.clientId, newBalance)
            Logger.debug("Client balance updated to: $newBalance")
            
            TransactionResponse(
                success = true,
                transactionId = savedEvent.id,
                message = "Transaction created successfully",
                runningBalance = newBalance
            )
            
        } catch (e: Exception) {
            Logger.error("Exception in TransactionService.createTransaction: ${e.message}", e)
            TransactionResponse(
                success = false,
                message = "Failed to create transaction: ${e.message}"
            )
        }
    }
}
