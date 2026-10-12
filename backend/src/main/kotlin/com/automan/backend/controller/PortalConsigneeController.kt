package com.automan.backend.controller

import com.automan.backend.portal.PortalAccess
import com.automan.backend.portal.PortalQueryService
import com.automan.backend.portal.PortalScope
import com.automan.backend.security.AuthContext
import com.automan.backend.security.AuthPolicy
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/portal/consignee")
class PortalConsigneeController(
    private val access: PortalAccess,
    private val portal: PortalQueryService,
) {
    @GetMapping("/me")
    fun me(request: HttpServletRequest) = scope(request).me

    @GetMapping("/dashboard")
    fun dashboard(request: HttpServletRequest) = portal.consigneeDashboard(scope(request))

    @GetMapping("/shipments/ongoing")
    fun ongoing(request: HttpServletRequest) = portal.consigneeOngoing(scope(request))

    @GetMapping("/cars/not-shipped")
    fun notShipped(
        request: HttpServletRequest,
        @RequestParam(required = false) q: String?,
    ) = portal.cars(scope(request), q, false)

    @GetMapping("/cars/shipped")
    fun shipped(
        request: HttpServletRequest,
        @RequestParam(required = false) q: String?,
    ) = portal.cars(scope(request), q, true)

    @GetMapping("/shipments/history")
    fun history(request: HttpServletRequest) = portal.consigneeHistory(scope(request))

    @GetMapping("/cars/{purchaseId}/media/{mediaId}/url")
    fun mediaUrl(
        request: HttpServletRequest,
        @PathVariable purchaseId: Long,
        @PathVariable mediaId: Long,
    ) = portal.mediaUrl(scope(request), purchaseId, mediaId)

    private fun scope(request: HttpServletRequest): PortalScope {
        val principal = AuthContext.current(request)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, AuthPolicy.ANONYMOUS_MESSAGE)
        return access.resolve(principal)
    }
}
