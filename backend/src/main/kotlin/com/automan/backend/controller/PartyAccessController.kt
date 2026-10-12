package com.automan.backend.controller

import com.automan.backend.security.AuthContext
import com.automan.backend.security.AuthPolicy
import com.automan.backend.service.PartyAccessService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/admin/party-access")
class PartyAccessController(
    private val partyAccessService: PartyAccessService,
) {
    data class CreateConsigneeRequest(val legalName: String?)
    data class ConsigneeStatusRequest(val status: String?)
    data class AssignConsigneeRequest(val consigneeId: Long?)
    data class ActivateLinkRequest(val userId: Long?, val partyType: String?, val partyId: Long?)

    @GetMapping("/clients")
    fun clients(@RequestParam(required = false) q: String?) = partyAccessService.searchClients(q)

    @GetMapping("/clients/{clientId}/assignments")
    fun assignments(@PathVariable clientId: Long) = partyAccessService.assignmentHistory(clientId)

    @PutMapping("/clients/{clientId}/consignee")
    fun assign(
        @PathVariable clientId: Long,
        @RequestBody body: AssignConsigneeRequest,
        request: HttpServletRequest,
    ) = partyAccessService.assignConsignee(clientId, body.consigneeId, adminId(request))

    @GetMapping("/consignees")
    fun consignees(@RequestParam(required = false) q: String?) = partyAccessService.searchConsignees(q)

    @PostMapping("/consignees")
    fun createConsignee(@RequestBody body: CreateConsigneeRequest) =
        partyAccessService.createConsignee(body.legalName.orEmpty())

    @PutMapping("/consignees/{consigneeId}/status")
    fun consigneeStatus(@PathVariable consigneeId: Long, @RequestBody body: ConsigneeStatusRequest) =
        partyAccessService.updateConsigneeStatus(consigneeId, body.status.orEmpty())

    @GetMapping("/users")
    fun users(@RequestParam(required = false) q: String?) = partyAccessService.listExternalUsers(q)

    @GetMapping("/users/{userId}/links")
    fun links(@PathVariable userId: Long) = partyAccessService.linkHistory(userId)

    @PostMapping("/links")
    fun activate(@RequestBody body: ActivateLinkRequest, request: HttpServletRequest): Any {
        val userId = body.userId ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a user.")
        val partyId = body.partyId ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a party by its id.")
        val partyType = body.partyType ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a party type.")
        return partyAccessService.activateLink(userId, partyType, partyId, adminId(request))
    }

    @PostMapping("/links/{linkId}/revoke")
    fun revoke(@PathVariable linkId: Long) = partyAccessService.revokeLink(linkId)

    private fun adminId(request: HttpServletRequest): Long =
        AuthContext.current(request)?.userId
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthPolicy.ANONYMOUS_MESSAGE)
}
