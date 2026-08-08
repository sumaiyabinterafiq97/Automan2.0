package com.automan.backend.controller

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.model.Client
import com.automan.backend.model.ClientStatus
import com.automan.backend.service.ClientService
import com.automan.backend.service.ClientTransactionsReportService
import com.automan.backend.service.InvoiceHistoryService
import com.automan.backend.service.PdfService
import com.automan.backend.service.TransactionService
import com.automan.backend.util.Logger
import com.automan.backend.util.PdfFilenameUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
    private val transactionService: TransactionService,
    private val invoiceHistoryService: InvoiceHistoryService,
    private val clientTransactionsReportService: ClientTransactionsReportService,
    private val pdfService: PdfService,
) {
    
    @GetMapping
    fun getAllClients(): ResponseEntity<List<Client>> {
        val clients = clientService.getAllClients()
        return ResponseEntity.ok(clients)
    }

    @GetMapping("/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(clientService.listPage(page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/page-search")
    fun searchPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(clientService.searchPage(q, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }
    
    @GetMapping("/resolve-ledger")
    fun resolveLedgerClient(
        @RequestParam name: String,
        @RequestParam(required = false) purchaseIds: List<Long>?,
        @RequestParam(required = false) invoiceNumber: String?,
        @RequestParam(required = false) invoiceAmount: Double?,
    ): ResponseEntity<Map<String, Any?>> {
        val result = invoiceHistoryService.previewLedgerClient(
            clientName = name,
            purchaseIds = purchaseIds.orEmpty(),
            invoiceNumber = invoiceNumber,
            invoiceAmount = invoiceAmount,
        )
        return ResponseEntity.ok(result)
    }

    @GetMapping("/credit-check")
    fun creditCheck(
        @RequestParam clientId: Long,
        @RequestParam invoiceNumber: String,
        @RequestParam invoiceAmount: Double,
    ): ResponseEntity<Map<String, Any?>> {
        val assessment = clientService.assessCreditForInvoiceCharge(clientId, invoiceNumber, invoiceAmount)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(assessment.toResponseMap())
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
    fun updateClient(@PathVariable id: Long, @RequestBody updateData: Map<String, Any?>): ResponseEntity<Client> {
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
    
    @GetMapping("/{id}/statement-pdf")
    fun clientStatementPdf(
        @PathVariable id: Long,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
    ): ResponseEntity<ByteArray> {
        return try {
            val statement = clientTransactionsReportService.buildClientStatement(id, startDate, endDate)
            val pdf = pdfService.generateClientStatementPdf(statement)
            val filename = PdfFilenameUtils.build("ClientStatement", statement.clientName)
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    PdfFilenameUtils.contentDisposition(filename),
                )
                .body(pdf)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
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
            val request = CreateTransactionRequest(
                clientId = (transactionData["clientId"] as? Number)?.toLong()
                    ?: throw IllegalArgumentException("Client ID is required"),
                eventDate = transactionData["eventDate"] as String,
                eventType = TransactionService.parseManualEventType(transactionData),
                eventDescription = transactionData["eventDescription"] as? String,
                quantity = (transactionData["quantity"] as? Number)?.toInt(),
                billNumber = transactionData["billNumber"] as? String,
                transactionPrice = (transactionData["transactionPrice"] as? Number)?.toDouble(),
                paymentReceived = (transactionData["paymentReceived"] as? Number)?.toDouble(),
            )
            val response = transactionService.createTransaction(request)
            if (response.success) {
                ResponseEntity.ok(mapOf<String, Any>(
                    "success" to true,
                    "transactionId" to (response.transactionId ?: 0L),
                    "message" to response.message,
                    "runningBalance" to (response.runningBalance ?: 0.0)
                ))
            } else {
                ResponseEntity.status(500).body(mapOf<String, Any>(
                    "success" to false,
                    "error" to response.message
                ))
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf<String, Any>(
                "success" to false,
                "error" to (e.message ?: "Invalid request")
            ))
        } catch (e: Exception) {
            Logger.error("Exception in createTransaction: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf<String, Any>(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
}
