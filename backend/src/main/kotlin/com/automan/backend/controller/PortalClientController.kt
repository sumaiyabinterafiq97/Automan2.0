package com.automan.backend.controller

import com.automan.backend.portal.PortalAccess
import com.automan.backend.portal.PortalLedgerService
import com.automan.backend.portal.PortalQueryService
import com.automan.backend.portal.PortalScope
import com.automan.backend.security.AuthContext
import com.automan.backend.security.AuthPolicy
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
@RequestMapping("/portal/client")
class PortalClientController(
    private val access: PortalAccess,
    private val portal: PortalQueryService,
    private val ledger: PortalLedgerService,
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

    @GetMapping("/cars/list")
    fun purchaseList(request: HttpServletRequest) = portal.clientPurchaseList(scope(request))

    @GetMapping("/cars/{purchaseId}")
    fun purchase(
        request: HttpServletRequest,
        @PathVariable purchaseId: Long,
    ) = portal.clientPurchase(scope(request), purchaseId)

    @GetMapping("/cars/{purchaseId}/history")
    fun purchaseHistory(
        request: HttpServletRequest,
        @PathVariable purchaseId: Long,
    ) = portal.clientPurchaseHistory(scope(request), purchaseId)

    @GetMapping("/shipments")
    fun shipments(request: HttpServletRequest) = portal.clientShipments(scope(request))

    @GetMapping("/invoices")
    fun invoices(request: HttpServletRequest) = portal.invoices(scope(request))

    @GetMapping("/transactions")
    fun transactions(request: HttpServletRequest) = portal.transactions(scope(request))

    @GetMapping("/ledger")
    fun clientLedger(request: HttpServletRequest) = ledger.ledger(scope(request))

    @PutMapping("/credit-limit")
    fun creditLimit(
        request: HttpServletRequest,
        @RequestBody body: Map<String, Any?>,
    ): Any {
        if (body.keys != setOf("creditLimit")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only credit limit can be changed.")
        }
        return ledger.updateCreditLimit(scope(request), body["creditLimit"])
    }

    @GetMapping("/ledger/export")
    fun ledgerExport(request: HttpServletRequest) = ledger.exportCsv(scope(request))

    @GetMapping("/ledger/statement-pdf")
    fun ledgerStatement(
        request: HttpServletRequest,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
    ) = ledger.statementPdf(scope(request), startDate, endDate)

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
