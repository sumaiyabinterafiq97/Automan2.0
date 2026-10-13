package com.automan.backend.portal

import com.automan.backend.model.UserRole
import com.automan.backend.repository.UserPartyLinkRepository
import com.automan.backend.security.AuthPrincipal
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Loads the caller's single active party link and the client ids that link may see.
 *
 * CLIENT scope is that link's clients.id.
 * CONSIGNEE scope is clients whose consignee_id is the linked consignee, and only when that consignee is ACTIVE.
 * Pending, revoked, missing, or more than one active link fails closed. Names and free-text consignee fields are not used.
 */
@Service
class PortalAccess(
    private val links: UserPartyLinkRepository,
    private val reads: PortalReadRepository,
) {
    @Transactional(readOnly = true)
    fun resolve(principal: AuthPrincipal): PortalScope {
        val partyType = when (principal.role) {
            UserRole.CLIENT -> CLIENT
            UserRole.CONSIGNEE -> CONSIGNEE
            else -> return closed()
        }
        val active = links.findByUserIdAndPartyTypeAndStatus(principal.userId, partyType, ACTIVE)
        if (active.size != 1) {
            if (active.size > 1) {
                Logger.warn("Portal access failed closed because user ${principal.userId} has more than one active $partyType link")
            }
            return closed()
        }
        val link = active.single()
        return if (partyType == CLIENT) resolveClient(link.clientId, link.partyId) else resolveConsignee(link.consigneeId, link.partyId)
    }

    private fun resolveClient(clientId: Long?, partyId: Long): PortalScope {
        if (clientId == null || clientId != partyId) return closed()
        val identity = reads.clientIdentity(clientId) ?: return closed()
        return PortalScope(
            active = true,
            clientIds = listOf(clientId),
            me = PortalMeDto(
                linked = true,
                active = true,
                clientNumber = identity.clientNumber,
                clientName = identity.clientName,
            ),
        )
    }

    private fun resolveConsignee(consigneeId: Long?, partyId: Long): PortalScope {
        if (consigneeId == null || consigneeId != partyId) return closed()
        val identity = reads.consigneeIdentity(consigneeId) ?: return closed()
        if (identity.status != ACTIVE) {
            return PortalScope(
                active = false,
                clientIds = emptyList(),
                me = PortalMeDto(linked = true, active = false, legalName = identity.legalName),
            )
        }
        return PortalScope(
            active = true,
            clientIds = reads.clientIdsForConsignee(consigneeId).distinct().sorted(),
            me = PortalMeDto(linked = true, active = true, legalName = identity.legalName),
            consigneeLegalName = identity.legalName,
        )
    }

    private fun closed() = PortalScope(
        active = false,
        clientIds = emptyList(),
        me = PortalMeDto(linked = false, active = false),
    )

    companion object {
        const val CLIENT = "CLIENT"
        const val CONSIGNEE = "CONSIGNEE"
        const val ACTIVE = "ACTIVE"
    }
}
