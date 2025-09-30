package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.TransactionResponse
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.EventRepository
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
        println("DEBUG: TransactionService.createTransaction called")
        println("DEBUG: Client ID: ${request.clientId}")
        println("DEBUG: Event Description: ${request.eventDescription}")
        
        return try {
            // Fetch client from database
            val client = clientService.getClientById(request.clientId)
                ?: throw IllegalArgumentException("Client not found: ${request.clientId}")
            println("DEBUG: Client found: ${client.clientName}")
            
            // Calculate running balance
            val currentBalance = client.currentBalance
            val newBalance = currentBalance + (request.paymentReceived ?: 0.0) - (request.transactionPrice ?: 0.0)
            println("DEBUG: Current balance: $currentBalance, New balance: $newBalance")
            
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
            println("DEBUG: Event saved with ID: ${savedEvent.id}")
            
            // Update client balance
            clientService.updateClientBalance(request.clientId, newBalance)
            println("DEBUG: Client balance updated to: $newBalance")
            
            TransactionResponse(
                success = true,
                transactionId = savedEvent.id,
                message = "Transaction created successfully",
                runningBalance = newBalance
            )
            
        } catch (e: Exception) {
            println("ERROR: Exception in TransactionService.createTransaction: ${e.message}")
            e.printStackTrace()
            TransactionResponse(
                success = false,
                message = "Failed to create transaction: ${e.message}"
            )
        }
    }
}
