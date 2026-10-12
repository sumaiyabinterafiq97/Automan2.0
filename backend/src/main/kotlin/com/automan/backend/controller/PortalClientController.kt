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
@RequestMapping("/portal/client")
class PortalClientController(
    private val access: PortalAccess,
    private val portal: PortalQueryService,
) {
    @GetMapping("/me")
    fun me(request: HttpServletRequest) = scope(request).me

    @GetMapping("/dashboard")
    fun dashboard(request: HttpServletRequest) = portal.dashboard(scope(request))

    @GetMapping("/cars")
    fun cars(
        request: HttpServletRequest,
        @RequestParam(required = false) q: String?,
    ) = portal.cars(scope(request), q, null)

    @GetMapping("/shipments")
    fun shipments(request: HttpServletRequest) = portal.clientShipments(scope(request))

    @GetMapping("/invoices")
    fun invoices(request: HttpServletRequest) = portal.invoices(scope(request))

    @GetMapping("/transactions")
    fun transactions(request: HttpServletRequest) = portal.transactions(scope(request))

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
