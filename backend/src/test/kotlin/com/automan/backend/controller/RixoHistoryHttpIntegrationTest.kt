package com.automan.backend.controller

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.ShippingHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * HTTP wiring for confirm-selected and remove-selected.
 * Chassis confirm rules themselves are covered by [com.automan.backend.service.RixoHistoryChassisConfirmTest].
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RixoHistoryHttpIntegrationTest {

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
            chassisNumbers.distinct().forEach { chassis ->
                purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        historyIds.clear()
        chassisNumbers.clear()
        shippingIds.clear()
        invoiceIds.clear()
    }

    @Test
    fun confirmSelectedMarksOnlyChassisOnTheChosenRow() {
        val selected = chassis()
        val other = chassis()
        val selectedPurchase = purchaseRepository.save(
            Purchase(chassis = selected, carName = "Selected", workflowStatus = WorkflowStatus.PURCHASED),
        )
        val otherPurchase = purchaseRepository.save(
            Purchase(chassis = other, carName = "Other", workflowStatus = WorkflowStatus.PURCHASED),
        )
        val row = rixoHistoryRepository.save(RixoHistory(rixoCompany = "LOGICO", chassis = selected))
        historyIds.add(row.id!!)

        mockMvc.perform(
            post("/rixo-history/confirm-selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyIds":[${row.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedRows").value(1))
            .andExpect(jsonPath("$.updatedPurchases").value(1))

        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(selectedPurchase.id!!).orElseThrow().workflowStatus)
        assertEquals(WorkflowStatus.PURCHASED, purchaseRepository.findById(otherPurchase.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun confirmChassisRequiresHistoryIdAndToken() {
        mockMvc.perform(
            post("/rixo-history/confirm-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassisToken":"AAA"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("historyId is required"))

        mockMvc.perform(
            post("/rixo-history/confirm-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyId":1}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("chassisToken is required"))
    }

    @Test
    fun removeSelectedDeletesTheHistoryRow() {
        val row = rixoHistoryRepository.save(RixoHistory(rixoCompany = "LOGICO", chassis = chassis()))
        historyIds.add(row.id!!)

        mockMvc.perform(
            post("/rixo-history/remove-selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyIds":[${row.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedRows").value(1))

        assertFalse(rixoHistoryRepository.existsById(row.id!!))
    }

    @Test
    fun moveChassisLeavesOldCompanyAndJoinsTheNewCompanyForTheSameDate() {
        val buyingDate = LocalDate.of(2026, 10, 1)
        val moved = chassis()
        val stayed = chassis()
        val alreadyThere = chassis()
        val movedPurchase = purchaseRepository.save(
            Purchase(chassis = moved, carName = "Moved", workflowStatus = WorkflowStatus.RIXO_CONFIRMED),
        )
        val stayedPurchase = purchaseRepository.save(
            Purchase(chassis = stayed, carName = "Stayed", workflowStatus = WorkflowStatus.RIXO_CONFIRMED),
        )
        val source = rixoHistoryRepository.save(
            RixoHistory(
                buyingDate = buyingDate,
                rixoCompany = "KLC",
                message = "old request",
                chassis = "$moved;$stayed",
            ),
        )
        val destination = rixoHistoryRepository.save(
            RixoHistory(
                buyingDate = buyingDate,
                rixoCompany = "STYLISH AUTO",
                message = "keep me",
                chassis = alreadyThere,
            ),
        )
        historyIds.add(source.id!!)
        historyIds.add(destination.id!!)
        val shipping = shippingHistoryRepository.save(ShippingHistory(chassis = moved))
        shippingIds.add(shipping.id!!)
        val invoice = invoiceHistoryRepository.save(InvoiceHistory(invoiceNumber = "INV-${UUID.randomUUID().toString().take(8)}"))
        invoiceIds.add(invoice.id!!)
        invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(invoiceHistoryId = invoice.id!!, chassis = moved, sortOrder = 0),
        )

        mockMvc.perform(
            post("/rixo-history/move-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyId":${source.id},"chassisToken":"$moved","rixoCompany":"stylish auto"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedRow").value(false))
            .andExpect(jsonPath("$.destinationHistoryId").value(destination.id))

        val sourceAfter = rixoHistoryRepository.findById(source.id!!).orElseThrow()
        assertEquals(stayed, sourceAfter.chassis)
        assertEquals("old request", sourceAfter.message)
        val destAfter = rixoHistoryRepository.findById(destination.id!!).orElseThrow()
        assertEquals("$alreadyThere;$moved", destAfter.chassis)
        assertEquals("keep me", destAfter.message)
        assertEquals("STYLISH AUTO", destAfter.rixoCompany)
        assertEquals(WorkflowStatus.RIXO_REQUESTED, purchaseRepository.findById(movedPurchase.id!!).orElseThrow().workflowStatus)
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(stayedPurchase.id!!).orElseThrow().workflowStatus)
        assertTrue(shippingHistoryRepository.existsById(shipping.id!!))
        assertTrue(invoiceHistoryRepository.existsById(invoice.id!!))
        assertEquals(1, invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(invoice.id!!).size)
    }

    @Test
    fun moveChassisCreatesARowAndDeletesTheEmptySource() {
        val buyingDate = LocalDate.of(2026, 10, 1)
        val moved = chassis()
        purchaseRepository.save(
            Purchase(chassis = moved, carName = "Only", workflowStatus = WorkflowStatus.RIXO_CONFIRMED),
        )
        val source = rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "KLC", chassis = moved),
        )
        historyIds.add(source.id!!)

        val result = mockMvc.perform(
            post("/rixo-history/move-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyId":${source.id},"chassisToken":"$moved","rixoCompany":"STYLISH AUTO"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedRow").value(true))
            .andReturn()

        assertFalse(rixoHistoryRepository.existsById(source.id!!))
        val createdId = com.jayway.jsonpath.JsonPath.parse(result.response.contentAsString)
            .read<Number>("$.destinationHistoryId")
            .toLong()
        historyIds.add(createdId)
        val created = rixoHistoryRepository.findById(createdId).orElseThrow()
        assertEquals(moved, created.chassis)
        assertEquals("STYLISH AUTO", created.rixoCompany)
        assertEquals(buyingDate, created.buyingDate)
        assertTrue(created.message.isNullOrBlank())
        assertEquals(WorkflowStatus.RIXO_REQUESTED, purchaseRepository.findByChassis(moved).single().workflowStatus)
    }

    @Test
    fun moveChassisRefusesABookingRequestedCar() {
        val moved = chassis()
        val purchase = purchaseRepository.save(
            Purchase(chassis = moved, carName = "Booked", workflowStatus = WorkflowStatus.BOOKING_REQUESTED),
        )
        val source = rixoHistoryRepository.save(RixoHistory(rixoCompany = "KLC", chassis = moved))
        historyIds.add(source.id!!)
        val shipping = shippingHistoryRepository.save(ShippingHistory(chassis = moved))
        shippingIds.add(shipping.id!!)

        mockMvc.perform(
            post("/rixo-history/move-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyId":${source.id},"chassisToken":"$moved","rixoCompany":"STYLISH AUTO"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Cannot remove: this car is already booking requested."))

        assertEquals(moved, rixoHistoryRepository.findById(source.id!!).orElseThrow().chassis)
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
        assertTrue(shippingHistoryRepository.existsById(shipping.id!!))
        assertNotNull(shipping.id)
    }

    @Test
    fun removeChassisRequiresHistoryId() {
        mockMvc.perform(
            post("/rixo-history/remove-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassisToken":"AAA"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("historyId is required"))
    }

    private fun chassis(): String {
        val value = "P0RH-${UUID.randomUUID().toString().take(8)}"
        chassisNumbers.add(value)
        return value
    }
}
