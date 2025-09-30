package com.automan.backend.controller

import com.automan.backend.model.Client
import com.automan.backend.model.ClientStatus
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.service.ClientService
import com.automan.backend.repository.EventRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping(value = ["/clients", "/api/clients"]) // support both base-path and double /api clients
@CrossOrigin(origins = [
    "http://localhost:8080",
    "http://localhost:8081",
    "http://localhost:8083",
    "http://localhost:8084",
    "http://localhost:8085",
    "http://localhost:8089",
    "http://localhost:8090"
])
class ClientController(
    private val clientService: ClientService,
    private val eventRepository: EventRepository
) {
    
    @GetMapping
    fun getAllClients(): ResponseEntity<List<Client>> {
        val clients = clientService.getAllClients()
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/search")
    fun searchClients(@RequestParam query: String): ResponseEntity<List<Client>> {
        val clients = clientService.searchClients(query)
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/{id}")
    fun getClientById(@PathVariable id: Long): ResponseEntity<Client> {
        val client = clientService.getClientById(id)
        return if (client != null) {
            ResponseEntity.ok(client)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @GetMapping("/number/{clientNumber}")
    fun getClientByNumber(@PathVariable clientNumber: String): ResponseEntity<Client> {
        val client = clientService.getClientByClientNumber(clientNumber)
        return if (client != null) {
            ResponseEntity.ok(client)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @PostMapping
    fun createClient(@RequestBody client: Client): ResponseEntity<Client> {
        try {
            val createdClient = clientService.createClient(client)
            return ResponseEntity.ok(createdClient)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
    }
    
    @PutMapping("/{id}")
    fun updateClient(@PathVariable id: Long, @RequestBody updateData: Map<String, Any>): ResponseEntity<Client> {
        val updatedClient = clientService.updateClient(id, updateData)
        return if (updatedClient != null) {
            ResponseEntity.ok(updatedClient)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @DeleteMapping("/{id}")
    fun deleteClient(@PathVariable id: Long): ResponseEntity<Void> {
        val deleted = clientService.deleteClient(id)
        return if (deleted) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @GetMapping("/status/{status}")
    fun getClientsByStatus(@PathVariable status: String): ResponseEntity<List<Client>> {
        try {
            val clientStatus = ClientStatus.valueOf(status.uppercase())
            val clients = clientService.getClientsByStatus(clientStatus)
            return ResponseEntity.ok(clients)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
    }
    
    @GetMapping("/debt")
    fun getClientsWithDebt(): ResponseEntity<List<Client>> {
        val clients = clientService.getClientsWithDebt()
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/credit")
    fun getClientsWithCredit(): ResponseEntity<List<Client>> {
        val clients = clientService.getClientsWithCredit()
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/alerts")
    fun getClientAlerts(): ResponseEntity<List<Client>> {
        val clients = clientService.getClientAlerts()
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/near-limit")
    fun getClientsNearCreditLimit(): ResponseEntity<List<Client>> {
        val clients = clientService.getClientsNearCreditLimit()
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/balance-range")
    fun getClientsByBalanceRange(
        @RequestParam minBalance: Double,
        @RequestParam maxBalance: Double
    ): ResponseEntity<List<Client>> {
        val clients = clientService.getClientsByBalanceRange(minBalance, maxBalance)
        return ResponseEntity.ok(clients)
    }
    
    @GetMapping("/{id}/balance")
    fun getClientBalance(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        val balance = clientService.getClientBalance(id)
        return if (balance != null) {
            ResponseEntity.ok(mapOf("balance" to balance))
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @GetMapping("/{id}/near-limit")
    fun isClientNearCreditLimit(@PathVariable id: Long): ResponseEntity<Map<String, Boolean>> {
        val isNearLimit = clientService.isClientNearCreditLimit(id)
        return ResponseEntity.ok(mapOf("nearLimit" to isNearLimit))
    }
    
    @PutMapping("/{id}/balance")
    fun updateClientBalance(
        @PathVariable id: Long,
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Client> {
        val newBalance = (request["balance"] as? Number)?.toDouble()
        if (newBalance == null) {
            return ResponseEntity.badRequest().build()
        }
        
        val updatedClient = clientService.updateClientBalance(id, newBalance)
        return if (updatedClient != null) {
            ResponseEntity.ok(updatedClient)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/import")
    fun importClients(@RequestBody importRequest: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            val clients = importRequest["clients"] as List<Map<String, Any>>
            val updateExisting = importRequest["updateExisting"] as Boolean? ?: false
            val result = clientService.importClients(clients, updateExisting)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    @GetMapping("/debug/test")
    fun testEndpoint(): ResponseEntity<String> {
        return ResponseEntity.ok("Test endpoint is working!")
    }
    
    @PostMapping("/add-transaction")
    fun createTransaction(
        @RequestBody transactionData: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        return try {
            // Extract clientId from request body
            val clientId = (transactionData["clientId"] as? Number)?.toLong() 
                ?: throw IllegalArgumentException("Client ID is required")
            
            println("DEBUG: Creating transaction for client $clientId")
            println("DEBUG: Transaction data: $transactionData")
            
            // Verify client exists
            val client = clientService.getClientById(clientId)
                ?: throw IllegalArgumentException("Client not found: $clientId")
            
            println("DEBUG: Client found: ${client.clientName}")
            
            // Calculate running balance based on client's current balance
            val currentBalance = client.currentBalance
            val transactionPrice = (transactionData["transactionPrice"] as? Number)?.toDouble() ?: 0.0
            val paymentReceived = (transactionData["paymentReceived"] as? Number)?.toDouble() ?: 0.0
            val newBalance = currentBalance + paymentReceived - transactionPrice
            
            println("DEBUG: Current balance: $currentBalance, New balance: $newBalance")
            
            // Create Event object
            val event = Event(
                clientId = clientId,
                eventDate = LocalDate.parse(transactionData["eventDate"] as String),
                eventType = EventType.OTHER,
                eventDescription = transactionData["eventDescription"] as? String,
                quantity = (transactionData["quantity"] as? Number)?.toInt(),
                billNumber = transactionData["billNumber"] as? String,
                transactionPrice = transactionPrice,
                paymentReceived = paymentReceived,
                runningBalance = newBalance
            )
            
            // Save event directly using EventRepository
            val savedEvent = eventRepository.save(event)
            println("DEBUG: Event saved with ID: ${savedEvent.id}")
            
            // Update client balance
            clientService.updateClientBalance(clientId, newBalance)
            println("DEBUG: Client balance updated to: $newBalance")
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "transactionId" to (savedEvent.id ?: 0L),
                "message" to "Transaction created successfully",
                "runningBalance" to newBalance
            ))
        } catch (e: Exception) {
            println("ERROR: Exception in createTransaction: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
}
