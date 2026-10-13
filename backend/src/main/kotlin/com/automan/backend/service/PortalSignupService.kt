package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.model.Consignee
import com.automan.backend.model.User
import com.automan.backend.model.UserPartyLink
import com.automan.backend.model.UserRole
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.ConsigneeRepository
import com.automan.backend.repository.UserPartyLinkRepository
import com.automan.backend.security.AuthSessionService
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Opens a client or consignee portal account when the signup name matches an existing record.
 * Client names come from purchases.client_name. Consignee names come from booking_mappings.consignee_name.
 * A purchase that already points at a different client is left unchanged.
 */
@Service
class PortalSignupService(
    private val em: EntityManager,
    private val clientRepository: ClientRepository,
    private val consigneeRepository: ConsigneeRepository,
    private val linkRepository: UserPartyLinkRepository,
) {
    @Transactional
    fun linkMatchedParty(user: User, partyName: String, role: UserRole) {
        val name = partyName.trim()
        if (name.isEmpty()) {
            throw IllegalArgumentException("Enter the name exactly as it appears on the existing record.")
        }
        val userId = user.id ?: throw IllegalStateException("Cannot link an unsaved user")
        when (role) {
            UserRole.CLIENT -> linkClient(userId, name)
            UserRole.CONSIGNEE -> linkConsignee(userId, name)
            else -> throw IllegalArgumentException("Public signup can only request the Viewer role.")
        }
    }

    private fun linkClient(userId: Long, name: String) {
        if (count("SELECT COUNT(p.id) FROM Purchase p WHERE LOWER(TRIM(COALESCE(p.clientName, ''))) = LOWER(:name)", name) == 0L) {
            throw IllegalArgumentException("No client with that name was found.")
        }
        val matches = clientsNamed(name)
        if (matches.size > 1) {
            throw IllegalArgumentException("More than one client uses that name. Ask an administrator to link this account.")
        }
        val client = matches.singleOrNull() ?: clientRepository.save(
            Client(
                clientNumber = newClientNumber(),
                clientName = name,
            ),
        )
        val clientId = client.id ?: throw IllegalStateException("Client was not saved")
        em.flush()
        em.createQuery(
            """
            UPDATE Purchase p
            SET p.clientId = :clientId
            WHERE p.clientId IS NULL
              AND LOWER(TRIM(COALESCE(p.clientName, ''))) = LOWER(:name)
            """.trimIndent(),
        )
            .setParameter("clientId", clientId)
            .setParameter("name", name)
            .executeUpdate()
        saveLink(userId, "CLIENT", clientId, clientId, null)
    }

    private fun linkConsignee(userId: Long, name: String) {
        if (count(
                "SELECT COUNT(b.id) FROM BookingMapping b WHERE LOWER(TRIM(COALESCE(b.consigneeName, ''))) = LOWER(:name)",
                name,
            ) == 0L
        ) {
            throw IllegalArgumentException("No consignee with that name was found.")
        }
        val matches = consigneesNamed(name)
        if (matches.size > 1) {
            throw IllegalArgumentException("More than one consignee uses that name. Ask an administrator to link this account.")
        }
        val consignee = matches.singleOrNull() ?: consigneeRepository.save(
            Consignee(legalName = name, status = "ACTIVE", createdAt = AuthSessionService.utcNow()),
        )
        val consigneeId = consignee.id ?: throw IllegalStateException("Consignee was not saved")
        saveLink(userId, "CONSIGNEE", consigneeId, null, consigneeId)
    }

    private fun saveLink(userId: Long, partyType: String, partyId: Long, clientId: Long?, consigneeId: Long?) {
        linkRepository.save(
            UserPartyLink(
                userId = userId,
                partyType = partyType,
                partyId = partyId,
                clientId = clientId,
                consigneeId = consigneeId,
                status = "ACTIVE",
                linkedByUserId = null,
                createdAt = AuthSessionService.utcNow(),
            ),
        )
    }

    private fun clientsNamed(name: String): List<Client> =
        em.createQuery(
            "SELECT c FROM Client c WHERE LOWER(TRIM(c.clientName)) = LOWER(:name)",
            Client::class.java,
        ).setParameter("name", name).resultList

    private fun consigneesNamed(name: String): List<Consignee> =
        em.createQuery(
            "SELECT c FROM Consignee c WHERE LOWER(TRIM(c.legalName)) = LOWER(:name)",
            Consignee::class.java,
        ).setParameter("name", name).resultList

    private fun count(jpql: String, name: String): Long =
        (em.createQuery(jpql).setParameter("name", name).singleResult as Number).toLong()

    private fun newClientNumber(): String {
        repeat(5) {
            val candidate = "P" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
            if (!clientRepository.existsByClientNumber(candidate)) return candidate
        }
        throw IllegalStateException("Could not assign a client number.")
    }
}
