package com.automan.backend.service

import com.automan.backend.repository.EventRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.model.Event
import com.automan.backend.model.Client
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Service
class AsyncImportService(
    private val eventRepository: EventRepository,
    private val clientRepository: ClientRepository
) {
    
    // Progress tracking for async operations
    private val importProgress = ConcurrentHashMap<String, ImportProgress>()
    
    data class ImportProgress(
        val clientId: Long,
        val totalRows: Int,
        val processedRows: AtomicInteger = AtomicInteger(0),
        val importedCount: AtomicInteger = AtomicInteger(0),
        val errorCount: AtomicInteger = AtomicInteger(0),
        val startTime: Long = System.currentTimeMillis(),
        var status: String = "PROCESSING"
    )
    
    @Async
    fun importEventsAsync(clientId: Long, csvData: List<Map<String, String>>): CompletableFuture<Map<String, Any>> {
        val progressId = "import_${clientId}_${System.currentTimeMillis()}"
        val progress = ImportProgress(
            clientId = clientId,
            totalRows = csvData.size
        )
        importProgress[progressId] = progress
        
        return try {
            val result = processImportAsync(clientId, csvData, progress)
            progress.status = "COMPLETED"
            CompletableFuture.completedFuture(result)
        } catch (e: Exception) {
            progress.status = "FAILED"
            CompletableFuture.completedFuture(mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error"),
                "progressId" to progressId
            ))
        }
    }
    
    @Transactional
    private fun processImportAsync(clientId: Long, csvData: List<Map<String, String>>, progress: ImportProgress): Map<String, Any> {
        val client = clientRepository.findById(clientId).orElse(null)
            ?: throw IllegalArgumentException("Client with ID $clientId not found")
        
        val importedEvents = mutableListOf<Event>()
        val errors = mutableListOf<String>()
        var runningBalance = client.currentBalance
        
        // Sort events by date
        val sortedEvents = csvData.sortedBy { row ->
            try {
                val dateStr = row["DATE"] ?: ""
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                try {
                    LocalDate.parse(row["DATE"] ?: "", DateTimeFormatter.ofPattern("yyyy-M-d"))
                } catch (e2: Exception) {
                    LocalDate.now() // Default to current date if parsing fails
                }
            }
        }
        
        for ((index, row) in sortedEvents.withIndex()) {
            try {
                progress.processedRows.incrementAndGet()
                
                val event = parseEventFromCsvRow(clientId, row, runningBalance)
                if (event != null) {
                    val savedEvent = eventRepository.save(event)
                    importedEvents.add(savedEvent)
                    progress.importedCount.incrementAndGet()
                    
                    // Update running balance for next event
                    runningBalance = runningBalance + (event.paymentReceived ?: 0.0) - (event.transactionPrice ?: 0.0)
                }
            } catch (e: Exception) {
                progress.errorCount.incrementAndGet()
                errors.add("Row ${index + 1}: ${e.message}")
            }
        }
        
        // Update client's current balance
        if (importedEvents.isNotEmpty()) {
            val updatedClient = client.copy(currentBalance = runningBalance)
            clientRepository.save(updatedClient)
        }
        
        return mapOf(
            "success" to true,
            "imported" to importedEvents.size,
            "errors" to errors.size,
            "errorMessages" to errors,
            "finalBalance" to runningBalance,
            "processingTime" to (System.currentTimeMillis() - progress.startTime),
            "progressId" to "import_${clientId}_${progress.startTime}"
        )
    }
    
    private fun parseEventFromCsvRow(clientId: Long, row: Map<String, String>, currentBalance: Double): Event? {
        val dateStr = row["DATE"] ?: return null
        val eventDescription = row["Event"] ?: return null
        
        // Parse date
        val eventDate = try {
            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-M-d"))
            } catch (e2: Exception) {
                throw IllegalArgumentException("Invalid date format: $dateStr")
            }
        }
        
        // Determine event type
        // eventType removed
        
        // Parse amounts (remove currency symbols and commas)
        val transactionPrice = parseAmount(row["T.S PRICE"])
        val paymentReceived = parseAmount(row["PAYMENT RECEIVED"])
        val runningBalance = parseAmount(row["T. BALANCE"]) ?: currentBalance
        
        // Parse quantity
        val quantity = row["QUANTITY"]?.let { qty ->
            try {
                qty.replace(" UNITS", "").replace(" UNITS", "").toIntOrNull()
            } catch (e: Exception) {
                null
            }
        }
        
        return Event(
            clientId = clientId,
            eventDate = eventDate,
            eventDescription = eventDescription,
            quantity = quantity,
            billNumber = row["BILL. NO"]?.takeIf { it.isNotBlank() },
            invoiceNumber = null,
            transactionPrice = transactionPrice,
            paymentReceived = paymentReceived,
            runningBalance = runningBalance
        )
    }
    
    private fun parseAmount(amountStr: String?): Double? {
        if (amountStr.isNullOrBlank()) return null
        
        return try {
            amountStr
                .replace("¥", "")
                .replace("$", "")
                .replace(",", "")
                .replace("-", "")
                .trim()
                .toDouble()
        } catch (e: Exception) {
            null
        }
    }
    
    fun getImportProgress(progressId: String): Map<String, Any>? {
        val progress = importProgress[progressId] ?: return null
        
        val elapsedTime = System.currentTimeMillis() - progress.startTime
        val progressPercentage = if (progress.totalRows > 0) {
            (progress.processedRows.get() * 100.0 / progress.totalRows).toInt()
        } else 0
        
        return mapOf<String, Any>(
            "progressId" to progressId,
            "clientId" to progress.clientId,
            "status" to progress.status,
            "totalRows" to progress.totalRows,
            "processedRows" to progress.processedRows.get(),
            "importedCount" to progress.importedCount.get(),
            "errorCount" to progress.errorCount.get(),
            "progressPercentage" to progressPercentage,
            "elapsedTime" to elapsedTime,
            "estimatedTimeRemaining" to if (progress.processedRows.get() > 0) {
                (elapsedTime * (progress.totalRows - progress.processedRows.get()) / progress.processedRows.get()).toLong()
            } else 0L
        )
    }
    
    fun clearImportProgress(progressId: String) {
        importProgress.remove(progressId)
    }
}
