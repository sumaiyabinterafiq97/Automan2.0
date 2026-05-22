package com.automan.backend.service

import com.automan.backend.dto.CreateEventRequest
import com.automan.backend.dto.InvoiceLedgerResult
import com.automan.backend.model.Event
import com.automan.backend.model.Client
import com.automan.backend.model.EventType
import com.automan.backend.repository.EventRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val clientRepository: ClientRepository
) {
    
    fun getAllEvents(): List<Event> {
        return eventRepository.findAll()
    }
    
    fun getEventById(id: Long): Event? {
        return eventRepository.findById(id).orElse(null)
    }
    
    fun getEventsByClientId(clientId: Long): List<Event> {
        return eventRepository.findByClientIdOrderByEventDateDesc(clientId)
    }
    
    fun getEventsByClientIdAndDateRange(clientId: Long, startDate: LocalDate, endDate: LocalDate): List<Event> {
        return eventRepository.findByClientIdAndEventDateBetweenOrderByEventDateDesc(clientId, startDate, endDate)
    }
    
    // Removed eventType APIs
    
    @Transactional
    fun createEvent(event: Event): Event {
        Logger.debug("createEvent called for client ID: ${event.clientId}")
        return createEventFromDto(
            CreateEventRequest(
                clientId = event.clientId,
                eventDate = event.eventDate,
                eventType = event.eventType,
                eventDescription = event.eventDescription,
                quantity = event.quantity,
                billNumber = event.billNumber,
                invoiceNumber = event.invoiceNumber,
                transactionPrice = event.transactionPrice,
                paymentReceived = event.paymentReceived,
            ),
        )
    }

    @Transactional
    fun createEventFromDto(req: com.automan.backend.dto.CreateEventRequest): Event {
        Logger.debug("createEventFromDto called for client ID: ${req.clientId}")
        try {
            val client = clientRepository.findById(req.clientId).orElseThrow { IllegalArgumentException("Client not found: ${req.clientId}") }
            Logger.debug("Client found: ${client.clientName}")
            val clientId = client.id!!
            // Determine previous balance using aggregates to avoid non-unique-result
            Logger.debug("Calculating aggregates...")
            val totalPayments = eventRepository.calculateTotalPaymentsByClientId(clientId) ?: 0.0
            Logger.debug("Total payments: $totalPayments")
            val totalShipments = eventRepository.calculateTotalTransactionPricesByClientId(clientId) ?: 0.0
            Logger.debug("Total shipments: $totalShipments")
            val previousBalance = totalPayments - totalShipments
            Logger.debug("Previous balance: $previousBalance")

            val newBalance = previousBalance + (req.paymentReceived ?: 0.0) - (req.transactionPrice ?: 0.0)
            Logger.debug("New balance: $newBalance")

            val event = Event(
                clientId = client.id!!,
                eventDate = req.eventDate,
                eventType = req.eventType,
                eventDescription = req.eventDescription,
                quantity = req.quantity,
                billNumber = req.billNumber,
                invoiceNumber = req.invoiceNumber,
                transactionPrice = req.transactionPrice,
                paymentReceived = req.paymentReceived,
                runningBalance = newBalance,
            )

            Logger.debug("Saving event...")
            val saved = eventRepository.save(event)
            Logger.debug("Event saved with ID: ${saved.id}")
            Logger.debug("Updating client balance...")
            updateClientBalance(client.id!!, newBalance)
            Logger.debug("Client balance updated successfully")
            return saved
        } catch (e: Exception) {
            Logger.error("Exception in createEventFromDto: ${e.message}", e)
            throw e
        }
    }

    /**
     * Posts one INVOICE_ISSUED ledger line when a customer invoice is confirmed.
     * Skips when the latest invoice ledger state already reflects [transactionPriceTotal].
     */
    @Transactional
    fun postInvoiceIssuedLedger(
        clientId: Long,
        invoiceNumber: String,
        eventDate: LocalDate,
        transactionPriceTotal: Double,
        lineCount: Int,
        vessel: String?,
    ): Event? {
        val inv = invoiceNumber.trim()
        if (inv.isEmpty()) return null
        if (transactionPriceTotal <= 0.0) {
            Logger.warn("Ledger: skip invoice $inv — zero or negative total ($transactionPriceTotal)")
            return null
        }
        if (isInvoiceLedgerBalancedTo(clientId, inv, transactionPriceTotal)) {
            Logger.debug("Ledger: invoice $inv already at target amount $transactionPriceTotal for client $clientId")
            return findLatestInvoiceIssued(clientId, inv)
        }
        val desc = buildString {
            append("Invoice ").append(inv)
            if (!vessel.isNullOrBlank()) {
                append(" · ").append(vessel.trim())
            }
        }
        return createEventFromDto(
            CreateEventRequest(
                clientId = clientId,
                eventDate = eventDate,
                eventDescription = desc,
                quantity = lineCount,
                billNumber = inv,
                transactionPrice = transactionPriceTotal,
                paymentReceived = null,
                eventType = EventType.INVOICE_ISSUED,
                invoiceNumber = inv,
            ),
        )
    }

    /**
     * Credits back an active [INVOICE_ISSUED] amount (Option A). Idempotent while an issued amount remains open.
     */
    @Transactional
    fun reverseActiveInvoiceLedger(
        clientId: Long,
        invoiceNumber: String,
        eventDate: LocalDate,
        vessel: String?,
    ): Event? {
        val inv = invoiceNumber.trim()
        if (inv.isEmpty()) return null
        val openAmount = openInvoiceLedgerCharge(clientId, inv)
        if (openAmount <= 0.0) {
            Logger.debug("Ledger: no open invoice charge to reverse for $inv (client $clientId)")
            return null
        }
        val desc = buildString {
            append("Reversal · Invoice ").append(inv)
            if (!vessel.isNullOrBlank()) {
                append(" · ").append(vessel.trim())
            }
        }
        return createEventFromDto(
            CreateEventRequest(
                clientId = clientId,
                eventDate = eventDate,
                eventDescription = desc,
                quantity = null,
                billNumber = inv,
                transactionPrice = null,
                paymentReceived = openAmount,
                eventType = EventType.INVOICE_REVERSAL,
                invoiceNumber = inv,
            ),
        )
    }

    /** Net invoice charge still on the ledger: issued debits minus reversal credits. */
    internal fun openInvoiceLedgerCharge(clientId: Long, invoiceNumber: String): Double {
        val inv = invoiceNumber.trim()
        if (inv.isEmpty()) return 0.0
        val events = eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(clientId, inv)
        var issued = 0.0
        var reversed = 0.0
        for (e in events) {
            when (e.eventType) {
                EventType.INVOICE_ISSUED -> issued += e.transactionPrice ?: 0.0
                EventType.INVOICE_REVERSAL -> reversed += e.paymentReceived ?: 0.0
                else -> { }
            }
        }
        return (issued - reversed).coerceAtLeast(0.0)
    }

    private fun clientIdsWithOpenInvoiceLedger(invoiceNumber: String): List<Long> {
        val inv = invoiceNumber.trim()
        if (inv.isEmpty()) return emptyList()
        return eventRepository.findByInvoiceNumberOrderByIdDesc(inv)
            .map { it.clientId }
            .distinct()
            .filter { clientId -> openInvoiceLedgerCharge(clientId, inv) > 0.0 }
    }

    @Transactional
    fun reverseOpenInvoiceLedgersForInvoice(invoiceNumber: String, eventDate: LocalDate, vessel: String?): Int {
        var reversed = 0
        for (clientId in clientIdsWithOpenInvoiceLedger(invoiceNumber)) {
            if (reverseActiveInvoiceLedger(clientId, invoiceNumber, eventDate, vessel) != null) {
                reversed += 1
            }
        }
        return reversed
    }

    private fun reverseOpenInvoiceLedgersForOtherClients(
        clientId: Long,
        invoiceNumber: String,
        eventDate: LocalDate,
        vessel: String?,
    ): Int {
        var reversed = 0
        for (openClientId in clientIdsWithOpenInvoiceLedger(invoiceNumber)) {
            if (openClientId == clientId) continue
            if (reverseActiveInvoiceLedger(openClientId, invoiceNumber, eventDate, vessel) != null) {
                reversed += 1
            }
        }
        return reversed
    }

    private fun isInvoiceLedgerBalancedTo(clientId: Long, invoiceNumber: String, targetCharge: Double): Boolean {
        val open = openInvoiceLedgerCharge(clientId, invoiceNumber)
        return kotlin.math.abs(open - targetCharge) < 0.01
    }

    private fun findLatestInvoiceIssued(clientId: Long, invoiceNumber: String): Event? =
        eventRepository.findFirstByClientIdAndInvoiceNumberAndEventTypeOrderByIdDesc(
            clientId, invoiceNumber.trim(), EventType.INVOICE_ISSUED,
        ).orElse(null)

    /**
     * Re-sync ledger for invoice save/confirm: no-op when already at [transactionPriceTotal];
     * otherwise reverse open charge and post the new total.
     */
    @Transactional
    fun syncInvoiceLedger(
        clientId: Long,
        invoiceNumber: String,
        eventDate: LocalDate,
        transactionPriceTotal: Double,
        lineCount: Int,
        vessel: String?,
    ): InvoiceLedgerResult {
        val inv = invoiceNumber.trim()
        val otherClientReversals = reverseOpenInvoiceLedgersForOtherClients(clientId, inv, eventDate, vessel)
        if (transactionPriceTotal <= 0.0) {
            val reversed = reverseActiveInvoiceLedger(clientId, inv, eventDate, vessel)
            return InvoiceLedgerResult(
                reversed = reversed != null || otherClientReversals > 0,
                clientId = clientId,
                warning = "Invoice total is zero; any open ledger charge was reversed.",
            )
        }
        if (isInvoiceLedgerBalancedTo(clientId, inv, transactionPriceTotal)) {
            return InvoiceLedgerResult(reversed = otherClientReversals > 0, clientId = clientId)
        }
        val reversed = if (openInvoiceLedgerCharge(clientId, inv) > 0.0) {
            reverseActiveInvoiceLedger(clientId, inv, eventDate, vessel)
        } else {
            null
        }
        val posted = postInvoiceIssuedLedger(
            clientId = clientId,
            invoiceNumber = inv,
            eventDate = eventDate,
            transactionPriceTotal = transactionPriceTotal,
            lineCount = lineCount,
            vessel = vessel,
        )
        return InvoiceLedgerResult(
            posted = posted != null,
            reversed = reversed != null || otherClientReversals > 0,
            clientId = clientId,
        )
    }
    
    @Transactional
    fun updateEvent(id: Long, updateData: Map<String, Any>): Event? {
        val existingEvent = eventRepository.findById(id).orElse(null)
        if (existingEvent == null) {
            return null
        }
        
        // Create updated event with new data
        val updatedEvent = existingEvent.copy(
            eventDate = if (updateData["eventDate"] != null) {
                LocalDate.parse(updateData["eventDate"] as String)
            } else existingEvent.eventDate,
            // eventType removed
            eventDescription = updateData["eventDescription"] as? String ?: existingEvent.eventDescription,
            quantity = (updateData["quantity"] as? Number)?.toInt() ?: existingEvent.quantity,
            billNumber = updateData["billNumber"] as? String ?: existingEvent.billNumber,
            invoiceNumber = updateData["invoiceNumber"] as? String ?: existingEvent.invoiceNumber,
            transactionPrice = (updateData["transactionPrice"] as? Number)?.toDouble() ?: existingEvent.transactionPrice,
            paymentReceived = (updateData["paymentReceived"] as? Number)?.toDouble() ?: existingEvent.paymentReceived
        )
        
        // Recalculate running balance for this event and all subsequent events
        recalculateBalancesFromEvent(existingEvent.clientId, existingEvent.eventDate)
        
        return eventRepository.save(updatedEvent)
    }
    
    @Transactional
    fun deleteEvent(id: Long): Boolean {
        val event = eventRepository.findById(id).orElse(null)
        if (event == null) {
            return false
        }
        
        val clientId = event.clientId
        val eventDate = event.eventDate
        
        eventRepository.deleteById(id)
        
        // Recalculate balances for all events after the deleted one
        recalculateBalancesFromEvent(clientId, eventDate)
        
        return true
    }
    
    fun getTotalPaymentsByClientId(clientId: Long): Double {
        return eventRepository.calculateTotalPaymentsByClientId(clientId)
    }
    
    fun getTotalTransactionPricesByClientId(clientId: Long): Double {
        return eventRepository.calculateTotalTransactionPricesByClientId(clientId)
    }
    
    fun getEventsByDateRange(startDate: LocalDate, endDate: LocalDate): List<Event> {
        return eventRepository.findByEventDateBetweenOrderByEventDateDesc(startDate, endDate)
    }
    
    fun getEventsByDescription(description: String): List<Event> {
        return eventRepository.findByEventDescriptionContainingIgnoreCase(description)
    }
    
    fun getEventsByBillNumber(billNumber: String): List<Event> {
        return eventRepository.findByBillNumber(billNumber)
    }
    
    fun getEventCountByClientId(clientId: Long): Long {
        return eventRepository.countByClientId(clientId)
    }
    
    fun getEventsByBalanceRange(minBalance: Double, maxBalance: Double): List<Event> {
        return eventRepository.findEventsByBalanceRange(minBalance, maxBalance)
    }
    
    @Transactional
    private fun updateClientBalance(clientId: Long, newBalance: Double) {
        val client = clientRepository.findById(clientId).orElse(null)
        if (client != null) {
            val updatedClient = client.copy(currentBalance = newBalance)
            clientRepository.save(updatedClient)
        }
    }
    
    @Transactional
    private fun recalculateBalancesFromEvent(clientId: Long, fromDate: LocalDate) {
        val events = eventRepository.findByClientIdOrderByEventDateDesc(clientId)
            .filter { it.eventDate >= fromDate }
            .sortedBy { it.eventDate }
        
        var runningBalance = 0.0
        
        // Find the balance before the fromDate
        val previousEvents = eventRepository.findByClientIdOrderByEventDateDesc(clientId)
            .filter { it.eventDate < fromDate }
            .maxByOrNull { it.eventDate }
        
        if (previousEvents != null) {
            runningBalance = previousEvents.runningBalance
        }
        
        // Recalculate balances for all events from the fromDate onwards
        for (event in events) {
            val newBalance = runningBalance + (event.paymentReceived ?: 0.0) - (event.transactionPrice ?: 0.0)
            
            val updatedEvent = event.copy(runningBalance = newBalance)
            eventRepository.save(updatedEvent)
            runningBalance = newBalance
        }
        
        // Update client's current balance
        updateClientBalance(clientId, runningBalance)
    }
    
    @Transactional
    fun importEvents(clientId: Long, csvData: List<Map<String, String>>): Map<String, Any> {
        val client = clientRepository.findById(clientId).orElse(null)
            ?: throw IllegalArgumentException("Client with ID $clientId not found")
        
        val importedEvents = mutableListOf<Event>()
        val errors = mutableListOf<String>()
        var runningBalance = client.currentBalance
        
        // Sort events by date to ensure proper running balance calculation
        val sortedEvents = csvData
            .filter { row -> 
                row.containsKey("DATE") && row["DATE"]?.isNotBlank() == true &&
                row.containsKey("Event") && row["Event"]?.isNotBlank() == true
            }
            .sortedBy { row ->
                try {
                    LocalDate.parse(row["DATE"]!!, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } catch (e: Exception) {
                    LocalDate.parse(row["DATE"]!!, DateTimeFormatter.ofPattern("yyyy-M-d"))
                }
            }
        
        for ((index, row) in sortedEvents.withIndex()) {
            try {
                val event = parseEventFromCsvRow(clientId, row, runningBalance)
                if (event != null) {
                    val savedEvent = eventRepository.save(event)
                    importedEvents.add(savedEvent)
                    
                    // Update running balance for next event
                    runningBalance = runningBalance + (event.paymentReceived ?: 0.0) - (event.transactionPrice ?: 0.0)
                }
            } catch (e: Exception) {
                errors.add("Row ${index + 1}: ${e.message}")
            }
        }
        
        // Update client's current balance
        if (importedEvents.isNotEmpty()) {
            updateClientBalance(clientId, runningBalance)
        }
        
        return mapOf(
            "imported" to importedEvents.size,
            "errors" to errors.size,
            "errorMessages" to errors,
            "finalBalance" to runningBalance
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
        // eventType removed; we infer semantics for balance from amounts only
        
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
            eventDate = eventDate,
            eventDescription = eventDescription,
            quantity = quantity,
            billNumber = row["BILL. NO"]?.takeIf { it.isNotBlank() },
            invoiceNumber = null,
            transactionPrice = transactionPrice,
            paymentReceived = paymentReceived,
            runningBalance = runningBalance,
            clientId = clientId
        )
    }
    
    private fun parseAmount(amountStr: String?): Double? {
        if (amountStr.isNullOrBlank()) return null
        
        return try {
            amountStr
                .replace("¥", "")
                .replace("$", "")
                .replace(",", "")
                .trim()
                .let { raw ->
                    // Keep leading minus if present; strip any spaces and currency artifacts already removed
                    // Also handle parentheses negatives like (123) if they appear
                    val cleaned = raw.replace(Regex("[^0-9.-]"), "")
                    cleaned.toDouble()
                }
        } catch (e: Exception) {
            null
        }
    }
    
    @Transactional
    fun bulkImportEvents(importData: Map<String, List<Map<String, String>>>): Map<String, Any> {
        val results = mutableMapOf<String, Map<String, Any>>()
        val totalImported = mutableListOf<Event>()
        val totalErrors = mutableListOf<String>()
        
        for ((clientIdStr, csvData) in importData) {
            try {
                val clientId = clientIdStr.toLong()
                val result = importEvents(clientId, csvData)
                results[clientIdStr] = result
                
                val imported = result["imported"] as Int
                val errors = result["errors"] as Int
                
                if (imported > 0) {
                    totalImported.addAll(getEventsByClientId(clientId))
                }
                
                if (errors > 0) {
                    val errorMessages = result["errorMessages"] as List<String>
                    totalErrors.addAll(errorMessages.map { "Client $clientId: $it" })
                }
            } catch (e: Exception) {
                totalErrors.add("Client $clientIdStr: ${e.message}")
                results[clientIdStr] = mapOf(
                    "imported" to 0,
                    "errors" to 1,
                    "errorMessages" to listOf(e.message ?: "Unknown error")
                )
            }
        }
        
        return mapOf(
            "clientsProcessed" to importData.size,
            "totalImported" to totalImported.size,
            "totalErrors" to totalErrors.size,
            "clientResults" to results,
            "allErrors" to totalErrors
        )
    }
    
    fun validateBulkImportData(importData: Map<String, List<Map<String, String>>>): Map<String, Any> {
        val validationResults = mutableMapOf<String, Map<String, Any>>()
        val totalIssues = mutableListOf<String>()
        
        for ((clientIdStr, csvData) in importData) {
            val clientIssues = mutableListOf<String>()
            
            // Validate client exists
            try {
                val clientId = clientIdStr.toLong()
                val client = clientRepository.findById(clientId).orElse(null)
                if (client == null) {
                    clientIssues.add("Client ID $clientId not found")
                }
            } catch (e: Exception) {
                clientIssues.add("Invalid client ID: $clientIdStr")
            }
            
            // Validate CSV data structure
            if (csvData.isEmpty()) {
                clientIssues.add("No transaction data provided")
            } else {
                // Check for required columns
                val firstRow = csvData.firstOrNull()
                if (firstRow == null) {
                    clientIssues.add("CSV data is empty")
                } else {
                    val requiredColumns = listOf("DATE", "Event", "T. BALANCE")
                    val missingColumns = requiredColumns.filter { !firstRow.containsKey(it) }
                
                    if (missingColumns.isNotEmpty()) {
                        clientIssues.add("Missing required columns: ${missingColumns.joinToString(", ")}")
                    }
                
                    // Validate each row
                    csvData.forEachIndexed { index, row ->
                        val rowIssues = mutableListOf<String>()
                    
                    // Check date format
                    val dateStr = row["DATE"]
                    if (dateStr.isNullOrBlank()) {
                        rowIssues.add("Missing date")
                    } else {
                        try {
                            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        } catch (e: Exception) {
                            try {
                                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-M-d"))
                            } catch (e2: Exception) {
                                rowIssues.add("Invalid date format: $dateStr")
                            }
                        }
                    }
                    
                    // Check event description
                    if (row["Event"].isNullOrBlank()) {
                        rowIssues.add("Missing event description")
                    }
                    
                    // Check balance format
                    val balanceStr = row["T. BALANCE"]
                    if (!balanceStr.isNullOrBlank()) {
                        try {
                            parseAmount(balanceStr)
                        } catch (e: Exception) {
                            rowIssues.add("Invalid balance format: $balanceStr")
                        }
                    }
                    
                        if (rowIssues.isNotEmpty()) {
                            clientIssues.add("Row ${index + 1}: ${rowIssues.joinToString(", ")}")
                        }
                    }
                }
            }
            
            validationResults[clientIdStr] = mapOf(
                "valid" to clientIssues.isEmpty(),
                "issues" to clientIssues.size,
                "issueMessages" to clientIssues
            )
            
            totalIssues.addAll(clientIssues.map { "Client $clientIdStr: $it" })
        }
        
        return mapOf(
            "clientsValidated" to importData.size,
            "totalIssues" to totalIssues.size,
            "validationResults" to validationResults,
            "allIssues" to totalIssues
        )
    }
    
    fun exportClientTransactions(clientId: Long, startDate: LocalDate? = null, endDate: LocalDate? = null): String {
        val client = clientRepository.findById(clientId).orElse(null)
            ?: throw IllegalArgumentException("Client with ID $clientId not found")
        
        val events = if (startDate != null && endDate != null) {
            getEventsByClientIdAndDateRange(clientId, startDate, endDate)
        } else {
            getEventsByClientId(clientId)
        }
        
        val csvHeader = "DATE,Event,QUANTITY,BILL. NO,T.S PRICE,PAYMENT RECEIVED,T. BALANCE\n"
        val csvRows = events.sortedBy { it.eventDate }.joinToString("\n") { event ->
            val date = event.eventDate.toString()
            val eventDesc = event.eventDescription ?: ""
            val quantity = event.quantity?.let { "${it} UNITS" } ?: ""
            val billNo = event.billNumber ?: ""
            val tPrice = event.transactionPrice?.let { "¥${it.toInt()}" } ?: ""
            val payment = event.paymentReceived?.let { "¥${it.toInt()}" } ?: ""
            val balance = "¥${event.runningBalance.toInt()}"
            
            "$date,$eventDesc,$quantity,$billNo,$tPrice,$payment,$balance"
        }
        
        return csvHeader + csvRows
    }
    
    fun exportAllClientsData(): String {
        val clients = clientRepository.findAll()
        val csvHeader = "CLIENT_ID,CLIENT_NUMBER,CLIENT_NAME,CURRENT_BALANCE,CREDIT_LIMIT,ALERT_THRESHOLD,CURRENCY,STATUS,CREATED_AT\n"
        val csvRows = clients.joinToString("\n") { client ->
            "${client.id},${client.clientNumber},${client.clientName},${client.currentBalance},${client.creditLimit ?: ""},${client.alertThreshold ?: ""},${client.currency},${client.status},${client.createdAt}"
        }
        
        return csvHeader + csvRows
    }
    
    fun createDataBackup(): Map<String, Any> {
        val clients = clientRepository.findAll()
        val allEvents = eventRepository.findAll()
        
        val clientData = clients.map { client ->
            mapOf(
                "id" to client.id,
                "clientNumber" to client.clientNumber,
                "clientName" to client.clientName,
                "currentBalance" to client.currentBalance,
                "creditLimit" to client.creditLimit,
                "alertThreshold" to client.alertThreshold,
                "currency" to client.currency,
                "status" to client.status,
                "createdAt" to client.createdAt.toString(),
                "updatedAt" to client.updatedAt.toString()
            )
        }
        
        val eventData = allEvents.map { event ->
            mapOf(
                "id" to event.id,
                "clientId" to event.clientId,
                "eventDate" to event.eventDate.toString(),
                "eventDescription" to event.eventDescription,
                "quantity" to event.quantity,
                "billNumber" to event.billNumber,
                "transactionPrice" to event.transactionPrice,
                "paymentReceived" to event.paymentReceived,
                "runningBalance" to event.runningBalance,
                "createdAt" to event.createdAt.toString()
            )
        }
        
        return mapOf(
            "backupDate" to LocalDate.now().toString(),
            "clients" to clientData,
            "events" to eventData,
            "totalClients" to clients.size,
            "totalEvents" to allEvents.size
        )
    }
    
    fun getAuditTrail(clientId: Long? = null): List<Map<String, Any>> {
        val events = if (clientId != null) {
            getEventsByClientId(clientId)
        } else {
            getAllEvents()
        }
        
        return events.sortedByDescending { it.createdAt }.map { event ->
            mapOf(
                "eventId" to (event.id ?: 0L),
                "clientId" to event.clientId,
                "clientName" to "Client ${event.clientId}", // Simplified since we don't have client object
                "eventDate" to event.eventDate.toString(),
                "eventDescription" to (event.eventDescription ?: ""),
                "transactionPrice" to (event.transactionPrice ?: 0.0),
                "paymentReceived" to (event.paymentReceived ?: 0.0),
                "runningBalance" to event.runningBalance,
                "createdAt" to event.createdAt.toString()
            )
        }
    }
}
