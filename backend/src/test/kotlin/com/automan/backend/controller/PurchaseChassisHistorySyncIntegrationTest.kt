package com.automan.backend.controller

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.ShippingHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.service.PurchaseWorkflowService
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseChassisHistorySyncIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var rixoHistoryRepository: RixoHistoryRepository
    @Autowired private lateinit var shippingHistoryRepository: ShippingHistoryRepository
    @Autowired private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Autowired private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisNumbers = mutableListOf<String>()
    private val historyIds = mutableListOf<Long>()
    private val shippingIds = mutableListOf<Long>()
    private val invoiceIds = mutableListOf<Long>()
    private val purchaseIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            invoiceIds.distinct().forEach { id ->
                invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(id)
                    .forEach { invoiceHistoryLineRepository.delete(it) }
                if (invoiceHistoryRepository.existsById(id)) invoiceHistoryRepository.deleteById(id)
            }
            shippingIds.distinct().forEach { id ->
                if (shippingHistoryRepository.existsById(id)) shippingHistoryRepository.deleteById(id)
            }
            historyIds.distinct().forEach { id ->
                if (rixoHistoryRepository.existsById(id)) rixoHistoryRepository.deleteById(id)
            }
            purchaseIds.distinct().forEach { id ->
                if (purchaseRepository.existsById(id)) purchaseRepository.deleteById(id)
            }
            chassisNumbers.distinct().forEach { chassis ->
                purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        historyIds.clear()
        shippingIds.clear()
        invoiceIds.clear()
        purchaseIds.clear()
        chassisNumbers.clear()
    }

    @Test
    fun previewAndSyncUpdatesHistoryWhenFlagTrue() {
        val old = "SYNC-${UUID.randomUUID().toString().take(8)}"
        val new = "SYNC-${UUID.randomUUID().toString().take(8)}"
        chassisNumbers.add(old)
        chassisNumbers.add(new)

        val purchase = purchaseRepository.save(
            Purchase(chassis = old, date = "2026-10-16", rixoCompany = "KLC", carName = "Test"),
        )
        purchaseIds.add(purchase.id!!)

        val rixo = rixoHistoryRepository.save(
            RixoHistory(
                buyingDate = LocalDate.of(2026, 10, 16),
                rixoCompany = "KLC",
                chassis = old,
            ),
        )
        historyIds.add(rixo.id!!)

        val shipping = shippingHistoryRepository.save(ShippingHistory(chassis = old, clientName = "C"))
        shippingIds.add(shipping.id!!)

        val invoice = invoiceHistoryRepository.save(InvoiceHistory(invoiceNumber = "INV-${UUID.randomUUID().toString().take(8)}"))
        invoiceIds.add(invoice.id!!)
        val line = invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(invoiceHistoryId = invoice.id!!, chassis = old, sortOrder = 0),
        )

        mockMvc.perform(
            post("/purchases/${purchase.id}/chassis-history-sync/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"newChassis":"$new"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rixo.length()").value(1))
            .andExpect(jsonPath("$.shipping.length()").value(1))
            .andExpect(jsonPath("$.invoice.length()").value(1))

        mockMvc.perform(
            put("/purchases/${purchase.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"$new","syncHistoryChassis":true}"""),
        )
            .andExpect(status().isOk)

        val rixoAfter = rixoHistoryRepository.findById(rixo.id!!).orElseThrow()
        assertEquals(new, rixoAfter.chassis)
        val shipAfter = shippingHistoryRepository.findById(shipping.id!!).orElseThrow()
        assertEquals(new, shipAfter.chassis)
        val lineAfter = invoiceHistoryLineRepository.findById(line.id!!).orElseThrow()
        assertEquals(new, lineAfter.chassis)
        val purchaseAfter = purchaseRepository.findById(purchase.id!!).orElseThrow()
        assertEquals(new, purchaseAfter.chassis)
    }

    @Test
    fun syncFalseLeavesHistoryUnchanged() {
        val old = "NOSYNC-${UUID.randomUUID().toString().take(8)}"
        val new = "NOSYNC-${UUID.randomUUID().toString().take(8)}"
        chassisNumbers.add(old)
        chassisNumbers.add(new)

        val purchase = purchaseRepository.save(Purchase(chassis = old, date = "2026-10-16", rixoCompany = "KLC"))
        purchaseIds.add(purchase.id!!)
        val rixo = rixoHistoryRepository.save(
            RixoHistory(buyingDate = LocalDate.of(2026, 10, 16), rixoCompany = "KLC", chassis = old),
        )
        historyIds.add(rixo.id!!)

        mockMvc.perform(
            put("/purchases/${purchase.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"$new","syncHistoryChassis":false}"""),
        )
            .andExpect(status().isOk)

        assertEquals(old, rixoHistoryRepository.findById(rixo.id!!).orElseThrow().chassis)
        assertEquals(new, purchaseRepository.findById(purchase.id!!).orElseThrow().chassis)
    }

    @Test
    fun chassisSyncWithAuditPreservesBookingWorkflowStatus() {
        val old = "WF-${UUID.randomUUID().toString().take(8)}"
        val new = "WF-${UUID.randomUUID().toString().take(8)}"
        chassisNumbers.add(old)
        chassisNumbers.add(new)

        val purchase = purchaseRepository.save(
            Purchase(
                chassis = old,
                date = "2026-10-16",
                rixoCompany = "KLC",
                carName = "Workflow",
                workflowStatus = WorkflowStatus.BOOKING_REQUESTED,
                bookingRequested = true,
                rixoConfirmed = PurchaseWorkflowService.rixoFlagTrue(),
            ),
        )
        purchaseIds.add(purchase.id!!)

        val rixo = rixoHistoryRepository.save(
            RixoHistory(
                buyingDate = LocalDate.of(2026, 10, 16),
                rixoCompany = "KLC",
                chassis = old,
            ),
        )
        historyIds.add(rixo.id!!)

        mockMvc.perform(
            put("/purchases/${purchase.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"chassis":"$new","venueId":"22431","syncHistoryChassis":true,"auditChangedFields":["chassis","venueId"]}""",
                ),
        )
            .andExpect(status().isOk)

        val after = purchaseRepository.findById(purchase.id!!).orElseThrow()
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, after.workflowStatus)
        assertTrue(PurchaseWorkflowService.isRixoConfirmedForBooking(after))
        assertTrue(PurchaseWorkflowService.isBookingRequested(after))
        assertEquals(new, rixoHistoryRepository.findById(rixo.id!!).orElseThrow().chassis)
    }
}
