package com.automan.backend.controller

import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.service.EventService
import com.automan.backend.service.ClientService
import com.automan.backend.service.AsyncImportService
import com.automan.backend.service.PerformanceMonitoringService
import com.automan.backend.dto.TransactionRequest
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/events")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class EventController(
    private val eventService: EventService,
    private val clientService: ClientService,
    private val asyncImportService: AsyncImportService,
    private val performanceMonitoringService: PerformanceMonitoringService
) {
    
    @GetMapping("/test")
    fun testEndpoint(): ResponseEntity<String> {
        return ResponseEntity.ok("Test endpoint working!")
    }
    
    @PostMapping("/test-create")
    fun testCreateEvent(@RequestBody eventData: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "message" to "Test endpoint working!",
            "receivedData" to eventData
        ))
    }
    
    @PostMapping("/simple")
    fun simpleTransaction(): ResponseEntity<String> {
        return ResponseEntity.ok("Simple transaction endpoint working!")
    }
    
    @PostMapping("/test")
    fun testEndpoint(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        Logger.debug("Test endpoint called with: $request")
        return ResponseEntity.ok(mapOf("message" to "Test endpoint working", "received" to request))
    }
    
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<String> {
        return ResponseEntity.ok("EventController is working!")
    }
    
    @GetMapping
    fun getAllEvents(): ResponseEntity<List<Event>> {
        val events = eventService.getAllEvents()
        return ResponseEntity.ok(events)
    }
    
    @GetMapping("/ping", produces = ["application/json"])
    fun pingEndpoint(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf("message" to "Ping endpoint working", "timestamp" to System.currentTimeMillis()))
    }
    
    @GetMapping("/system/backup")
    fun createDataBackup(): ResponseEntity<Map<String, Any>> {
        return try {
            val backup = eventService.createDataBackup()
            ResponseEntity.ok(backup)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    @GetMapping("/system/audit-trail")
    fun getAuditTrail(@RequestParam(required = false) clientId: Long?): ResponseEntity<List<Map<String, Any>>> {
        return try {
            val auditTrail = eventService.getAuditTrail(clientId)
            ResponseEntity.ok(auditTrail)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(listOf(mapOf("error" to (e.message ?: "Unknown error"))))
        }
    }
    
    
    
    // Performance Monitoring Endpoints
    @GetMapping("/system/performance/metrics", produces = ["application/json"])
    fun getPerformanceMetrics(): ResponseEntity<Map<String, Any>> {
        return try {
            val metrics = performanceMonitoringService.getAllOperationMetrics()
            ResponseEntity.ok(metrics)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    @GetMapping("/system/performance/summary")
    fun getPerformanceSummary(): ResponseEntity<Map<String, Any>> {
        return try {
            val summary = performanceMonitoringService.getPerformanceSummary()
            ResponseEntity.ok(summary)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    @GetMapping("/system/performance/operation/{operationName}")
    fun getOperationMetrics(@PathVariable operationName: String): ResponseEntity<Map<String, Any>> {
        return try {
            val metrics = performanceMonitoringService.getOperationMetrics(operationName)
            if (metrics != null) {
                ResponseEntity.ok(metrics)
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    @PostMapping("/system/performance/reset")
    fun resetPerformanceMetrics(): ResponseEntity<Map<String, Any>> {
        return try {
            performanceMonitoringService.resetMetrics()
            ResponseEntity.ok(mapOf("message" to "Performance metrics reset successfully"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    // GET endpoint for individual events - temporarily disabled to test specific endpoints
    // @GetMapping("/{id}")
    // fun getEventById(@PathVariable id: Long): ResponseEntity<Event> {
    //     val event = eventService.getEventById(id)
    //     return if (event != null) {
    //         ResponseEntity.ok(event)
    //     } else {
    //         ResponseEntity.notFound().build()
    //     }
    // }
    
    @GetMapping("/client/{clientId}")
    fun getEventsByClientId(@PathVariable clientId: Long): ResponseEntity<List<Event>> {
        val events = eventService.getEventsByClientId(clientId)
        return ResponseEntity.ok(events)
    }
    
    @GetMapping("/client/{clientId}/date-range")
    fun getEventsByClientIdAndDateRange(
        @PathVariable clientId: Long,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<Event>> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        val events = eventService.getEventsByClientIdAndDateRange(clientId, start, end)
        return ResponseEntity.ok(events)
    }
    
    // Removed type-based endpoints
    
    @PostMapping
    fun createEvent(@RequestBody eventData: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            // Extract clientId directly from the payload
            val clientId = eventData["clientId"] as? Long ?: throw IllegalArgumentException("Client ID is required")
            
            // Verify client exists
            val client = clientService.getClientById(clientId)
                ?: throw IllegalArgumentException("Client not found: $clientId")
            
            Logger.debug("Client found: ${client.clientName}, Currency: ${client.currency}, Status: ${client.status}")
            
            // Create Event object with just clientId
            val event = Event(
                clientId = clientId,
                eventDate = java.time.LocalDate.parse(eventData["eventDate"] as String),
                eventType = com.automan.backend.model.EventType.valueOf(eventData["eventType"] as? String ?: "OTHER"),
                eventDescription = eventData["eventDescription"] as? String,
                quantity = (eventData["quantity"] as? Number)?.toInt(),
                billNumber = eventData["billNumber"] as? String,
                invoiceNumber = eventData["invoiceNumber"] as? String,
                transactionPrice = (eventData["transactionPrice"] as? Number)?.toDouble(),
                paymentReceived = (eventData["paymentReceived"] as? Number)?.toDouble(),
                runningBalance = (eventData["runningBalance"] as? Number)?.toDouble() ?: 0.0
            )
            
            val createdEvent = eventService.createEvent(event)
            ResponseEntity.ok(mapOf(
                "id" to (createdEvent.id ?: 0L),
                "message" to "Transaction created successfully",
                "clientId" to clientId,
                "eventDate" to createdEvent.eventDate.toString(),
                "eventDescription" to (createdEvent.eventDescription ?: ""),
                "runningBalance" to createdEvent.runningBalance
            ))
        } catch (e: Exception) {
            Logger.error("Exception in createEvent: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
    
    
    
    @GetMapping("/client/{clientId}/payments/total")
    fun getTotalPaymentsByClientId(@PathVariable clientId: Long): ResponseEntity<Map<String, Double>> {
        val total = eventService.getTotalPaymentsByClientId(clientId)
        return ResponseEntity.ok(mapOf("totalPayments" to total))
    }
    
    @GetMapping("/client/{clientId}/transactions/total")
    fun getTotalTransactionPricesByClientId(@PathVariable clientId: Long): ResponseEntity<Map<String, Double>> {
        val total = eventService.getTotalTransactionPricesByClientId(clientId)
        return ResponseEntity.ok(mapOf("totalTransactions" to total))
    }
    
    @GetMapping("/date-range")
    fun getEventsByDateRange(
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<Event>> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        val events = eventService.getEventsByDateRange(start, end)
        return ResponseEntity.ok(events)
    }
    
    @GetMapping("/description")
    fun getEventsByDescription(@RequestParam description: String): ResponseEntity<List<Event>> {
        val events = eventService.getEventsByDescription(description)
        return ResponseEntity.ok(events)
    }
    
    @GetMapping("/bill-number/{billNumber}")
    fun getEventsByBillNumber(@PathVariable billNumber: String): ResponseEntity<List<Event>> {
        val events = eventService.getEventsByBillNumber(billNumber)
        return ResponseEntity.ok(events)
    }
    
    @GetMapping("/client/{clientId}/count")
    fun getEventCountByClientId(@PathVariable clientId: Long): ResponseEntity<Map<String, Long>> {
        val count = eventService.getEventCountByClientId(clientId)
        return ResponseEntity.ok(mapOf("count" to count))
    }
    
    @GetMapping("/balance-range")
    fun getEventsByBalanceRange(
        @RequestParam minBalance: Double,
        @RequestParam maxBalance: Double
    ): ResponseEntity<List<Event>> {
        val events = eventService.getEventsByBalanceRange(minBalance, maxBalance)
        return ResponseEntity.ok(events)
    }
    
    
    
    
    @GetMapping("/export/{clientId}")
    fun exportClientTransactions(
        @PathVariable clientId: Long,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<String> {
        return try {
            val start = startDate?.let { LocalDate.parse(it) }
            val end = endDate?.let { LocalDate.parse(it) }
            val csvData = eventService.exportClientTransactions(clientId, start, end)
            
            ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=client_${clientId}_transactions.csv")
                .body(csvData)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("Error exporting data: ${e.message}")
        }
    }
    
    @GetMapping("/export/all-clients")
    fun exportAllClientsData(): ResponseEntity<String> {
        return try {
            val csvData = eventService.exportAllClientsData()
            
            ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=all_clients_data.csv")
                .body(csvData)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body("Error exporting data: ${e.message}")
        }
    }
}
