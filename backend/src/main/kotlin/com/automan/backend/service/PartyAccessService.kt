package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.model.ClientConsigneeAssignment
import com.automan.backend.model.Consignee
import com.automan.backend.model.User
import com.automan.backend.model.UserPartyLink
import com.automan.backend.model.UserRole
import com.automan.backend.repository.ClientAssignmentRepository
import com.automan.backend.repository.ClientConsigneeAssignmentRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.ConsigneeRepository
import com.automan.backend.repository.UserPartyLinkRepository
import com.automan.backend.repository.UserRepository
import com.automan.backend.security.AuthSessionService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset
import java.time.LocalDateTime

@Service
class PartyAccessService(
    private val clientRepository: ClientRepository,
    private val clientAssignmentRepository: ClientAssignmentRepository,
    private val assignmentRepository: ClientConsigneeAssignmentRepository,
    private val consigneeRepository: ConsigneeRepository,
    private val userRepository: UserRepository,
    private val linkRepository: UserPartyLinkRepository,
    private val authSessionService: AuthSessionService,
) {
    @Transactional(readOnly = true)
    fun searchClients(query: String?): List<ClientAssignmentView> {
        val clients = matchingClients(query)
        val consigneeIds = clients.mapNotNull { it.consigneeId }.toSet()
        val consignees = if (consigneeIds.isEmpty()) emptyMap() else consigneeRepository.findAllById(consigneeIds).associateBy { it.id }
        return clients.map { client ->
            val consignee = client.consigneeId?.let { consignees[it] }
            client.toView(consignee)
        }
    }

    @Transactional(readOnly = true)
    fun searchConsignees(query: String?): List<ConsigneeView> = matchingConsignees(query).map { it.toView() }

    @Transactional
    fun createConsignee(legalName: String): ConsigneeView {
        val name = legalName.trim()
        if (name.isEmpty() || name.length > 255) {
            reject(HttpStatus.BAD_REQUEST, "Enter the consignee legal name.")
        }
        return consigneeRepository.save(
            Consignee(legalName = name, status = ACTIVE, createdAt = utcNow()),
        ).toView()
    }

    @Transactional
    fun updateConsigneeStatus(consigneeId: Long, status: String): ConsigneeView {
        val next = status.trim().uppercase()
        if (next != ACTIVE && next != INACTIVE) {
            reject(HttpStatus.BAD_REQUEST, "Consignee status must be ACTIVE or INACTIVE.")
        }
        val consignee = consigneeRepository.findById(consigneeId).orElse(null)
            ?: reject(HttpStatus.NOT_FOUND, "No consignee exists with that id.")
        if (next == INACTIVE && linkRepository.findByPartyTypeAndPartyIdAndStatus(CONSIGNEE, consigneeId, LINK_ACTIVE).isNotEmpty()) {
            reject(HttpStatus.CONFLICT, "Revoke active consignee portal links before marking this consignee inactive.")
        }
        if (consignee.status == next) return consignee.toView()
        return consigneeRepository.save(consignee.copy(status = next)).toView()
    }

    @Transactional
    fun assignConsignee(clientId: Long, consigneeId: Long?, adminUserId: Long): ClientAssignmentView {
        val client = clientAssignmentRepository.findForUpdate(clientId)
            ?: reject(HttpStatus.NOT_FOUND, "No client exists with that id.")
        val next = if (consigneeId == null) {
            null
        } else {
            val consignee = consigneeRepository.findById(consigneeId).orElse(null)
                ?: reject(HttpStatus.NOT_FOUND, "No consignee exists with that id.")
            if (consignee.status != ACTIVE) {
                reject(HttpStatus.BAD_REQUEST, "Choose an active consignee. Inactive consignees cannot be assigned.")
            }
            consignee
        }
        if (client.consigneeId == next?.id) {
            return client.toView(next)
        }
        if (next == null && linkRepository.findByPartyTypeAndPartyIdAndStatus(CLIENT, clientId, LINK_ACTIVE).isNotEmpty()) {
            reject(
                HttpStatus.CONFLICT,
                "Revoke active client portal links before clearing this consignee assignment.",
            )
        }
        val previousId = client.consigneeId
        val saved = clientRepository.save(client.copy(consigneeId = next?.id, updatedAt = utcNow()))
        assignmentRepository.save(
            ClientConsigneeAssignment(
                clientId = clientId,
                previousConsigneeId = previousId,
                consigneeId = next?.id,
                changedByUserId = adminUserId,
                createdAt = utcNow(),
            ),
        )
        val affected = listOfNotNull(previousId, next?.id).flatMap { id ->
            linkRepository.findByPartyTypeAndPartyIdAndStatus(CONSIGNEE, id, LINK_ACTIVE).map { it.userId }
        }
        authSessionService.revokeAllForUsersJoined(affected)
        return saved.toView(next)
    }

    @Transactional(readOnly = true)
    fun assignmentHistory(clientId: Long): List<AssignmentHistoryView> {
        if (!clientRepository.existsById(clientId)) {
            reject(HttpStatus.NOT_FOUND, "No client exists with that id.")
        }
        return assignmentRepository.findByClientIdOrderByIdDesc(clientId).map {
            AssignmentHistoryView(
                id = it.id!!,
                clientId = it.clientId,
                previousConsigneeId = it.previousConsigneeId,
                consigneeId = it.consigneeId,
                changedByUserId = it.changedByUserId,
                createdAt = it.createdAt.toString(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun listExternalUsers(query: String?): List<ExternalUserView> {
        val users = userRepository.findByRoleIn(listOf(UserRole.CLIENT, UserRole.CONSIGNEE))
            .filter { matchesUser(it, query) }
            .sortedBy { it.email }
            .take(100)
        return users.map { user ->
            val active = linkRepository.findByUserIdAndPartyTypeAndStatus(user.id!!, user.role.name, LINK_ACTIVE).firstOrNull()
            ExternalUserView(
                userId = user.id!!,
                name = user.name,
                email = user.email,
                role = user.role.name,
                activeLink = active?.let { toLinkView(it) },
            )
        }
    }

    @Transactional(readOnly = true)
    fun linkHistory(userId: Long): List<LinkView> {
        val user = userRepository.findById(userId).orElse(null)
            ?: reject(HttpStatus.NOT_FOUND, "No user exists with that id.")
        if (user.role != UserRole.CLIENT && user.role != UserRole.CONSIGNEE) {
            reject(HttpStatus.BAD_REQUEST, "Link history is only available for client and consignee accounts.")
        }
        return linkRepository.findByUserIdOrderByIdDesc(userId).map { toLinkView(it) }
    }

    @Transactional
    fun activateLink(userId: Long, partyType: String, partyId: Long, adminUserId: Long): LinkView {
        val requestedType = partyType.trim().uppercase()
        if (requestedType != CLIENT && requestedType != CONSIGNEE) {
            reject(HttpStatus.BAD_REQUEST, "Party type must be CLIENT or CONSIGNEE.")
        }
        val user = userRepository.findForUpdate(userId)
            ?: reject(HttpStatus.NOT_FOUND, "No user exists with that id.")
        if (user.role != UserRole.CLIENT && user.role != UserRole.CONSIGNEE) {
            reject(HttpStatus.BAD_REQUEST, "Only a client or consignee account can receive a portal link.")
        }
        if (user.role.name != requestedType) {
            reject(
                HttpStatus.BAD_REQUEST,
                if (user.role == UserRole.CLIENT) {
                    "A client account can only be linked to a client."
                } else {
                    "A consignee account can only be linked to a consignee."
                },
            )
        }
        if (linkRepository.findByUserIdAndPartyTypeAndStatus(userId, requestedType, LINK_ACTIVE).isNotEmpty()) {
            reject(HttpStatus.CONFLICT, "This user already has an active link. Revoke it before creating another.")
        }
        val (clientId, consigneeId) = resolveParty(requestedType, partyId)
        val saved = try {
            linkRepository.saveAndFlush(
                UserPartyLink(
                    userId = userId,
                    partyType = requestedType,
                    partyId = partyId,
                    clientId = clientId,
                    consigneeId = consigneeId,
                    status = LINK_ACTIVE,
                    linkedByUserId = adminUserId,
                    createdAt = utcNow(),
                    revokedAt = null,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            reject(HttpStatus.CONFLICT, "This user already has an active link. Revoke it before creating another.")
        }
        authSessionService.revokeAllForUsersJoined(listOf(userId))
        return toLinkView(saved)
    }

    @Transactional
    fun revokeLink(linkId: Long): LinkView {
        val link = linkRepository.findById(linkId).orElse(null)
            ?: reject(HttpStatus.NOT_FOUND, "No link exists with that id.")
        if (link.status != LINK_ACTIVE) {
            reject(HttpStatus.CONFLICT, "Only an active link can be revoked.")
        }
        val saved = linkRepository.save(
            link.copy(status = LINK_REVOKED, revokedAt = utcNow()),
        )
        authSessionService.revokeAllForUsersJoined(listOf(link.userId))
        return toLinkView(saved)
    }

    private fun resolveParty(partyType: String, partyId: Long): Pair<Long?, Long?> {
        if (partyType == CLIENT) {
            val client = clientRepository.findById(partyId).orElse(null)
                ?: reject(HttpStatus.NOT_FOUND, "No client exists with that id.")
            val consigneeId = client.consigneeId
                ?: reject(HttpStatus.BAD_REQUEST, "Assign an active consignee to this client before linking a portal user.")
            val consignee = consigneeRepository.findById(consigneeId).orElse(null)
            if (consignee == null || consignee.status != ACTIVE) {
                reject(HttpStatus.BAD_REQUEST, "Assign an active consignee to this client before linking a portal user.")
            }
            return partyId to null
        }
        val consignee = consigneeRepository.findById(partyId).orElse(null)
            ?: reject(HttpStatus.NOT_FOUND, "No consignee exists with that id.")
        if (consignee.status != ACTIVE) {
            reject(HttpStatus.BAD_REQUEST, "Inactive consignees cannot receive a new portal link.")
        }
        return null to partyId
    }

    private fun matchingClients(query: String?): List<Client> {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return clientRepository.findAll().take(100)
        val byId = q.toLongOrNull()?.let { clientRepository.findById(it).orElse(null) }
        val found = linkedSetOf<Client>()
        if (byId != null) found.add(byId)
        found.addAll(clientRepository.searchClients(q))
        return found.take(100)
    }

    private fun matchingConsignees(query: String?): List<Consignee> {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return consigneeRepository.findAll().take(100)
        val found = linkedSetOf<Consignee>()
        q.toLongOrNull()?.let { consigneeRepository.findById(it).orElse(null) }?.let { found.add(it) }
        found.addAll(consigneeRepository.findByLegalNameContainingIgnoreCase(q))
        return found.take(100)
    }

    private fun matchesUser(user: User, query: String?): Boolean {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return true
        if (q.toLongOrNull() == user.id) return true
        return user.name.contains(q, ignoreCase = true) || user.email.contains(q, ignoreCase = true)
    }

    private fun toLinkView(link: UserPartyLink): LinkView {
        val partyName = when (link.partyType) {
            CLIENT -> clientRepository.findById(link.partyId).orElse(null)?.let { "${it.clientNumber} ${it.clientName}" }
            CONSIGNEE -> consigneeRepository.findById(link.partyId).orElse(null)?.legalName
            else -> null
        }
        return LinkView(
            id = link.id!!,
            userId = link.userId,
            partyType = link.partyType,
            partyId = link.partyId,
            partyName = partyName,
            status = link.status,
            linkedByUserId = link.linkedByUserId,
            createdAt = link.createdAt.toString(),
            revokedAt = link.revokedAt?.toString(),
        )
    }

    private fun Client.toView(consignee: Consignee?) = ClientAssignmentView(
        clientId = id!!,
        clientNumber = clientNumber,
        clientName = clientName,
        consigneeId = consigneeId,
        consigneeLegalName = consignee?.legalName,
        consigneeStatus = consignee?.status,
    )

    private fun Consignee.toView() = ConsigneeView(id = id!!, legalName = legalName, status = status)

    private fun utcNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    private fun reject(status: HttpStatus, message: String): Nothing {
        throw ResponseStatusException(status, message)
    }

    companion object {
        const val CLIENT = "CLIENT"
        const val CONSIGNEE = "CONSIGNEE"
        const val ACTIVE = "ACTIVE"
        const val INACTIVE = "INACTIVE"
        const val LINK_ACTIVE = "ACTIVE"
        const val LINK_REVOKED = "REVOKED"
    }
}

data class ClientAssignmentView(
    val clientId: Long,
    val clientNumber: String,
    val clientName: String,
    val consigneeId: Long?,
    val consigneeLegalName: String?,
    val consigneeStatus: String?,
)

data class ConsigneeView(
    val id: Long,
    val legalName: String,
    val status: String,
)

data class AssignmentHistoryView(
    val id: Long,
    val clientId: Long,
    val previousConsigneeId: Long?,
    val consigneeId: Long?,
    val changedByUserId: Long,
    val createdAt: String,
)

data class ExternalUserView(
    val userId: Long,
    val name: String,
    val email: String,
    val role: String,
    val activeLink: LinkView?,
)

data class LinkView(
    val id: Long,
    val userId: Long,
    val partyType: String,
    val partyId: Long,
    val partyName: String?,
    val status: String,
    val linkedByUserId: Long?,
    val createdAt: String,
    val revokedAt: String?,
)
