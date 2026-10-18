package com.automan.backend.controller

import com.automan.backend.portal.PortalAccess
import com.automan.backend.portal.PortalQueryService
import com.automan.backend.portal.PortalScope
import com.automan.backend.security.AuthContext
import com.automan.backend.security.AuthPolicy
import jakarta.servlet.http.HttpServletRequest
import com.automan.backend.util.PdfFilenameUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
        @RequestParam(required = false) booking: String?,
    ) = portal.cars(scope(request), q, false, booking?.equals("none", ignoreCase = true) == true)

    @GetMapping("/purchases")
    fun purchases(request: HttpServletRequest) = portal.consigneePurchaseList(scope(request))

    @GetMapping("/purchases/{purchaseId}")
    fun purchase(
        request: HttpServletRequest,
        @PathVariable purchaseId: Long,
    ) = portal.clientPurchase(scope(request), purchaseId)

    @GetMapping("/invoices")
    fun invoices(request: HttpServletRequest) = portal.invoices(scope(request))

    @GetMapping("/cars/shipped")
    fun shipped(
        request: HttpServletRequest,
        @RequestParam(required = false) q: String?,
    ) = portal.consigneeShipped(scope(request), q)

    @GetMapping("/clients")
    fun clients(request: HttpServletRequest) = portal.consigneeClientMap(scope(request))

    @GetMapping("/shipments/history")
    fun history(request: HttpServletRequest) = portal.consigneeHistory(scope(request))

    @GetMapping("/shipping-history")
    fun shippingHistory(request: HttpServletRequest) = portal.consigneeShippingHistory(scope(request))

    @GetMapping("/shipping-history/pdf")
    fun shippingHistoryPdf(
        request: HttpServletRequest,
        @RequestParam bookingId: String,
    ): ResponseEntity<ByteArray> {
        val pdfBytes = portal.consigneeShippingPdf(scope(request), bookingId)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.set(
            HttpHeaders.CONTENT_DISPOSITION,
            PdfFilenameUtils.contentDisposition(
                PdfFilenameUtils.build("ShippingSchedule", bookingId.trim().ifBlank { "unknown" }, null),
            ),
        )
        headers.contentLength = pdfBytes.size.toLong()
        return ResponseEntity.ok().headers(headers).body(pdfBytes)
    }

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
