package com.automan.backend.service

import com.automan.backend.config.AppConstants
import com.automan.backend.dto.ClientCreditLimitAssessment
import com.automan.backend.dto.ClientLedgerPreview
import com.automan.backend.dto.ClientNameLedgerResolution
import com.automan.backend.dto.ClientPageResponse
import com.automan.backend.dto.CreditLimitStatus
import com.automan.backend.model.Client
import com.automan.backend.model.ClientStatus
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.util.Logger
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ClientService(
    private val clientRepository: ClientRepository,
    private val eventRepository: EventRepository
) {
    
    fun getAllClients(): List<Client> {
        return clientRepository.findAll()
    }

    /** Paginated browse for Client Accounts UI (no search text). Prefer this over [getAllClients] for lists. */
    @Transactional(readOnly = true)
    fun listPage(
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): ClientPageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, resolveClientListSort(sortField, sortOrder))
        val pg = clientRepository.findAll(pageable)
        return ClientPageResponse(
            content = pg.content,
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    /** Paginated search for Client Accounts UI (client number / name). */
    @Transactional(readOnly = true)
    fun searchPage(
        rawQuery: String,
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): ClientPageResponse {
        val q = sanitizeClientSearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, resolveClientListSort(sortField, sortOrder))
        val pg = clientRepository.searchClientsPage(q, pageable)
        return ClientPageResponse(
            content = pg.content,
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    private fun resolveClientListSort(sortField: String?, sortOrder: String?): Sort {
        val dir = if (sortOrder?.trim().equals("desc", ignoreCase = true) == true) {
            Sort.Direction.DESC
        } else {
            Sort.Direction.ASC
        }
        val prop = when (sortField?.trim()?.lowercase()) {
            null, "", "clientname", "client_name", "name" -> "clientName"
            "clientnumber", "client_number", "number" -> "clientNumber"
            "currentbalance", "current_balance", "balance" -> "currentBalance"
            "creditlimit", "credit_limit" -> "creditLimit"
            "id" -> "id"
            else -> "clientName"
        }
        return Sort.by(dir, prop)
    }

    private fun sanitizeClientSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)
    
    fun getClientById(id: Long): Client? {
        return clientRepository.findById(id).orElse(null)
    }
    
    fun getClientByClientNumber(clientNumber: String): Client? {
        return clientRepository.findByClientNumber(clientNumber)
    }
    
    /**
     * Preview whether an invoice client name can post to the ledger (no DB insert).
     * Unknown names are resolvable — a client row is created on invoice save (Phase 2b).
     */
    fun previewClientNameForLedger(
        clientName: String,
        purchaseClientIds: List<Long> = emptyList(),
    ): ClientLedgerPreview {
        val distinctPurchaseIds = purchaseClientIds.distinct()
        if (distinctPurchaseIds.size > 1) {
            return ClientLedgerPreview(
                ledgerResolvable = false,
                warning = "Purchases reference multiple clients; ledger entry will not be posted. Link purchases to one client.",
            )
        }

        val name = clientName.trim()
        if (name.isNotEmpty()) {
            val matches = clientRepository.findByClientNameIgnoreCase(name)
            return when {
                matches.size > 1 -> ClientLedgerPreview(
                    ledgerResolvable = false,
                    warning = "Multiple clients named \"$name\"; fix duplicates in Client Management before posting the ledger.",
                )
                matches.size == 1 -> ClientLedgerPreview(
                    clientId = matches.first().id,
                    ledgerResolvable = true,
                )
                else -> ClientLedgerPreview(
                    ledgerResolvable = true,
                    willCreateClient = true,
                )
            }
        }

        if (distinctPurchaseIds.size == 1) {
            return ClientLedgerPreview(
                clientId = distinctPurchaseIds.first(),
                ledgerResolvable = true,
            )
        }
        return ClientLedgerPreview(
            ledgerResolvable = false,
            warning = "Invoice has no client name; ledger entry will not be posted.",
        )
    }

    /**
     * Resolves invoice buyer name to a client id, creating a new [Client] when none exists (exact name, case-insensitive).
     */
    @Transactional
    fun resolveClientNameForLedger(clientName: String): ClientNameLedgerResolution {
        val name = clientName.trim()
        if (name.isEmpty()) {
            return ClientNameLedgerResolution.Skipped("Invoice has no client name; ledger entry was not posted.")
        }
        val matches = clientRepository.findByClientNameIgnoreCase(name)
        return when {
            matches.size > 1 -> ClientNameLedgerResolution.Skipped(
                "Multiple clients named \"$name\"; ledger entry was not posted. Fix duplicates in Client Management.",
            )
            matches.size == 1 -> {
                val id = matches.first().id
                    ?: return ClientNameLedgerResolution.Skipped("Client \"$name\" has no id; ledger entry was not posted.")
                ClientNameLedgerResolution.Ok(id, created = false)
            }
            else -> {
                val saved = createClient(
                    Client(
                        clientNumber = generateNextClientNumber(),
                        clientName = name,
                        currentBalance = 0.0,
                        creditLimit = null,
                        alertThreshold = null,
                        currency = "JPY",
                        status = ClientStatus.ACTIVE,
                    ),
                )
                Logger.log("Auto-created client ${saved.clientNumber} ($name) from invoice")
                val id = saved.id
                    ?: return ClientNameLedgerResolution.Skipped("Failed to create client \"$name\"; ledger entry was not posted.")
                ClientNameLedgerResolution.Ok(id, created = true)
            }
        }
    }

    /** Next unique client number (CL0001, CL0002, …). */
    fun generateNextClientNumber(): String {
        var n = clientRepository.count() + 1
        var candidate = "CL${n.toString().padStart(4, '0')}"
        while (clientRepository.existsByClientNumber(candidate)) {
            n++
            candidate = "CL${n.toString().padStart(4, '0')}"
        }
        return candidate
    }

    @Transactional
    fun createClient(client: Client): Client {
        // Check if client number already exists
        if (clientRepository.existsByClientNumber(client.clientNumber)) {
            throw IllegalArgumentException("Client with number ${client.clientNumber} already exists")
        }
        
        return clientRepository.save(client)
    }
    
    @Transactional
    fun updateClient(id: Long, updateData: Map<String, Any?>): Client? {
        val existingClient = clientRepository.findById(id).orElse(null)
        if (existingClient == null) {
            return null
        }
        
        fun anyToDoubleOrNull(value: Any?): Double? = when (value) {
            null -> null
            is Number -> value.toDouble()
            is String -> value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            else -> null
        }

        // Create updated client with new data
        val updatedClient = existingClient.copy(
            clientName = updateData["clientName"] as? String ?: existingClient.clientName,
            currentBalance = anyToDoubleOrNull(updateData["currentBalance"]) ?: existingClient.currentBalance,
            creditLimit = if (updateData.containsKey("creditLimit")) {
                anyToDoubleOrNull(updateData["creditLimit"])
            } else {
                existingClient.creditLimit
            },
            alertThreshold = if (updateData.containsKey("alertThreshold")) {
                anyToDoubleOrNull(updateData["alertThreshold"])
            } else {
                existingClient.alertThreshold
            },
            currency = updateData["currency"] as? String ?: existingClient.currency,
            status = if (updateData["status"] != null) {
                ClientStatus.valueOf(updateData["status"] as String)
            } else existingClient.status,
            updatedAt = LocalDateTime.now()
        )
        
        return clientRepository.save(updatedClient)
    }
    
    @Transactional
    fun deleteClient(id: Long): Boolean {
        return if (clientRepository.existsById(id)) {
            clientRepository.deleteById(id)
            true
        } else {
            false
        }
    }
    
    fun searchClients(query: String): List<Client> {
        return clientRepository.searchClients(query)
    }
    
    fun getClientsByStatus(status: ClientStatus): List<Client> {
        return clientRepository.findByStatus(status)
    }
    
    fun getClientsWithDebt(): List<Client> {
        return clientRepository.findClientsWithDebt()
    }
    
    fun getClientsWithCredit(): List<Client> {
        return clientRepository.findClientsWithCredit()
    }
    
    fun getClientsNearCreditLimit(): List<Client> {
        return clientRepository.findClientsNearCreditLimit()
    }
    
    fun getClientsByBalanceRange(minBalance: Double, maxBalance: Double): List<Client> {
        return clientRepository.findClientsByBalanceRange(minBalance, maxBalance)
    }
    
    @Transactional
    fun updateClientBalance(clientId: Long, newBalance: Double): Client? {
        val client = clientRepository.findById(clientId).orElse(null)
        if (client == null) {
            return null
        }
        
        val updatedClient = client.copy(
            currentBalance = newBalance,
            updatedAt = LocalDateTime.now()
        )
        
        return clientRepository.save(updatedClient)
    }
    
    fun getClientBalance(clientId: Long): Double? {
        val client = clientRepository.findById(clientId).orElse(null)
        return client?.currentBalance
    }
    
    fun isClientNearCreditLimit(clientId: Long): Boolean {
        val client = clientRepository.findById(clientId).orElse(null)
        if (client == null || client.creditLimit == null || client.alertThreshold == null) {
            return false
        }
        
        return client.currentBalance <= client.alertThreshold
    }
    
    fun getClientAlerts(): List<Client> {
        val alerts = linkedSetOf<Client>()
        alerts.addAll(clientRepository.findClientsOverCreditLimit())
        alerts.addAll(clientRepository.findClientsNearCreditLimit())
        alerts.addAll(
            clientRepository.findClientsWithDebt().filter { client ->
                val limit = client.creditLimit
                limit == null || limit <= 0.0
            },
        )
        return alerts.toList()
    }

    /** Net open invoice charge on ledger for one invoice number. */
    fun openInvoiceLedgerCharge(clientId: Long, invoiceNumber: String): Double {
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

    /**
     * Option A: [projectedBalance] = currentBalance − (newCharge − openChargeOnInvoice).
     */
    fun projectedBalanceAfterInvoiceCharge(
        clientId: Long,
        invoiceNumber: String,
        newCharge: Double,
    ): Double? {
        val client = clientRepository.findById(clientId).orElse(null) ?: return null
        val open = openInvoiceLedgerCharge(clientId, invoiceNumber.trim())
        return client.currentBalance - (newCharge - open)
    }

    fun assessCreditForInvoiceCharge(
        clientId: Long,
        invoiceNumber: String,
        invoiceCharge: Double,
    ): ClientCreditLimitAssessment? {
        if (invoiceCharge <= 0.0) return null
        val client = clientRepository.findById(clientId).orElse(null) ?: return null
        val limit = client.creditLimit
        val projected = projectedBalanceAfterInvoiceCharge(clientId, invoiceNumber, invoiceCharge)
            ?: return null
        val availableAfter = if (limit != null) limit + projected else null

        if (limit == null || limit <= 0.0) {
            return ClientCreditLimitAssessment(
                status = CreditLimitStatus.NO_LIMIT,
                clientId = clientId,
                clientName = client.clientName,
                currentBalance = client.currentBalance,
                projectedBalance = projected,
                creditLimit = limit,
                invoiceCharge = invoiceCharge,
                availableCreditAfter = availableAfter,
                message = null,
                blocked = false,
            )
        }

        val nearLine = -limit * AppConstants.CREDIT_LIMIT_NEAR_FRACTION
        val overLine = -limit

        val status = when {
            projected < overLine -> CreditLimitStatus.OVER_LIMIT
            projected <= nearLine -> CreditLimitStatus.NEAR_LIMIT
            else -> CreditLimitStatus.OK
        }

        val owedAfter = kotlin.math.abs(projected.coerceAtMost(0.0))
        val limitInt = limit.toLong()
        val message = when (status) {
            CreditLimitStatus.OVER_LIMIT -> {
                val blockNote = if (AppConstants.BLOCK_INVOICE_WHEN_OVER_CREDIT_LIMIT) {
                    " Invoice was not saved."
                } else {
                    " Invoice was saved; finance should review."
                }
                "Client \"${client.clientName}\" would exceed credit limit (¥$limitInt): " +
                    "owed after invoice ≈ ¥${owedAfter.toLong()}.$blockNote"
            }
            CreditLimitStatus.NEAR_LIMIT -> {
                "Client \"${client.clientName}\" is near credit limit (¥$limitInt): " +
                    "≈ ${((owedAfter / limit) * 100).toInt()}% of limit used after this invoice."
            }
            else -> null
        }

        val blocked = status == CreditLimitStatus.OVER_LIMIT &&
            AppConstants.BLOCK_INVOICE_WHEN_OVER_CREDIT_LIMIT

        return ClientCreditLimitAssessment(
            status = status,
            clientId = clientId,
            clientName = client.clientName,
            currentBalance = client.currentBalance,
            projectedBalance = projected,
            creditLimit = limit,
            invoiceCharge = invoiceCharge,
            availableCreditAfter = availableAfter,
            message = message,
            blocked = blocked,
        )
    }

    /**
     * Throws [IllegalArgumentException] when Phase 3 block is enabled and invoice would exceed limit.
     */
    fun enforceInvoiceCreditLimit(clientId: Long, invoiceNumber: String, invoiceCharge: Double) {
        val assessment = assessCreditForInvoiceCharge(clientId, invoiceNumber, invoiceCharge) ?: return
        if (assessment.blocked) {
            throw IllegalArgumentException(assessment.message ?: "Invoice exceeds client credit limit.")
        }
    }

    @Transactional
    fun importClients(clients: List<Map<String, Any>>, updateExisting: Boolean): Map<String, Any> {
        var imported = 0
        var updated = 0
        var errors = 0

        for (clientData in clients) {
            try {
                val clientNumber = clientData["clientNumber"] as? String
                val clientName = clientData["clientName"] as? String
                
                if (clientNumber.isNullOrBlank() || clientName.isNullOrBlank()) {
                    errors++
                    continue
                }

                val existingClient = clientRepository.findByClientNumber(clientNumber)
                
                if (existingClient != null) {
                    if (updateExisting) {
                        val updatedClient = existingClient.copy(
                            clientName = clientName,
                            currentBalance = (clientData["currentBalance"] as? Number)?.toDouble() ?: existingClient.currentBalance,
                            creditLimit = (clientData["creditLimit"] as? Number)?.toDouble(),
                            alertThreshold = (clientData["alertThreshold"] as? Number)?.toDouble(),
                            currency = clientData["currency"] as? String ?: existingClient.currency,
                            status = ClientStatus.valueOf((clientData["status"] as? String) ?: "ACTIVE"),
                            updatedAt = LocalDateTime.now()
                        )
                        clientRepository.save(updatedClient)
                        updated++
                    } else {
                        errors++
                    }
                } else {
                    val newClient = Client(
                        clientNumber = clientNumber,
                        clientName = clientName,
                        currentBalance = (clientData["currentBalance"] as? Number)?.toDouble() ?: 0.0,
                        creditLimit = (clientData["creditLimit"] as? Number)?.toDouble(),
                        alertThreshold = (clientData["alertThreshold"] as? Number)?.toDouble(),
                        currency = clientData["currency"] as? String ?: "JPY",
                        status = ClientStatus.valueOf((clientData["status"] as? String) ?: "ACTIVE")
                    )
                    clientRepository.save(newClient)
                    imported++
                }
            } catch (e: Exception) {
                errors++
            }
        }

        return mapOf(
            "imported" to imported,
            "updated" to updated,
            "errors" to errors,
            "total" to clients.size
        )
    }
}
