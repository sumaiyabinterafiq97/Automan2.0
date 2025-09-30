package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.model.ClientStatus
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
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
    
    fun getClientById(id: Long): Client? {
        return clientRepository.findById(id).orElse(null)
    }
    
    fun getClientByClientNumber(clientNumber: String): Client? {
        return clientRepository.findByClientNumber(clientNumber)
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
    fun updateClient(id: Long, updateData: Map<String, Any>): Client? {
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
            address = updateData["address"] as? String ?: existingClient.address,
            phone = updateData["phone"] as? String ?: existingClient.phone,
            currentBalance = anyToDoubleOrNull(updateData["currentBalance"]) ?: existingClient.currentBalance,
            creditLimit = anyToDoubleOrNull(updateData["creditLimit"]) ?: existingClient.creditLimit,
            alertThreshold = anyToDoubleOrNull(updateData["alertThreshold"]) ?: existingClient.alertThreshold,
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
        val alerts = mutableListOf<Client>()
        
        // Add clients with debt
        alerts.addAll(clientRepository.findClientsWithDebt())
        
        // Add clients near credit limit
        alerts.addAll(clientRepository.findClientsNearCreditLimit())
        
        return alerts.distinctBy { it.id }
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
                            address = clientData["address"] as? String,
                            phone = clientData["phone"] as? String,
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
                        address = clientData["address"] as? String,
                        phone = clientData["phone"] as? String,
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
