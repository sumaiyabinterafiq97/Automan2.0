package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Locale

@Service
class PurchaseWorkflowService(
    private val purchaseRepository: PurchaseRepository,
) {
    companion object {
        val WORKFLOW_RIXO_CONFIRMED_OR_LATER: Set<WorkflowStatus> = setOf(
            WorkflowStatus.RIXO_CONFIRMED,
            WorkflowStatus.BOOKING_REQUESTED,
            WorkflowStatus.INVOICE_CONFIRMED,
        )

        val WORKFLOW_BOOKING_OR_LATER: Set<WorkflowStatus> = setOf(
            WorkflowStatus.BOOKING_REQUESTED,
            WorkflowStatus.INVOICE_CONFIRMED,
        )

        /** JPQL: Rixo-confirmed eligibility for car booking pool. */
        const val JPQL_RIXO_CONFIRMED_ELIGIBILITY =
            "p.workflowStatus IN (com.automan.backend.model.WorkflowStatus.RIXO_CONFIRMED, " +
                "com.automan.backend.model.WorkflowStatus.BOOKING_REQUESTED, " +
                "com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED)"

        /** Native SQL: Rixo-confirmed eligibility for car booking pool. */
        const val SQL_RIXO_CONFIRMED_ELIGIBILITY =
            "p.workflow_status IN ('RIXO_CONFIRMED', 'BOOKING_REQUESTED', 'INVOICE_CONFIRMED')"

        const val JPQL_BOOKING_NOT_REQUESTED =
            "(p.workflowStatus IS NULL OR p.workflowStatus NOT IN (" +
                "com.automan.backend.model.WorkflowStatus.BOOKING_REQUESTED, " +
                "com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED))"

        const val SQL_BOOKING_NOT_REQUESTED =
            "(p.workflow_status IS NULL OR p.workflow_status NOT IN ('BOOKING_REQUESTED', 'INVOICE_CONFIRMED'))"

        /** JPQL: exclude LOCAL domestic sales from the car booking pool. */
        const val JPQL_NOT_LOCAL = "p.local = false"

        /** Native SQL: exclude LOCAL domestic sales (`local` is reserved in MySQL). */
        const val SQL_NOT_LOCAL = "p.`local` = 0"

        const val JPQL_INVOICE_NOT_CONFIRMED =
            "(p.workflowStatus IS NULL OR p.workflowStatus <> com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED)"

        fun isRixoFlagTrue(raw: String?): Boolean {
            val v = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return v == "TRUE" || v == "1"
        }

        fun rixoFlagTrue(): String = "TRUE"

        fun rixoFlagFalse(): String = "0"

        /** Dual-read during transition; after V54 relies on [Purchase.workflowStatus]. */
        fun isRixoConfirmedForBooking(p: Purchase): Boolean {
            if (isRixoFlagTrue(p.rixoConfirmed)) return true
            return p.workflowStatus in WORKFLOW_RIXO_CONFIRMED_OR_LATER
        }

        fun isBookingRequested(p: Purchase): Boolean {
            if (p.bookingRequested) return true
            return p.workflowStatus in WORKFLOW_BOOKING_OR_LATER
        }

        fun isInvoiceConfirmed(p: Purchase): Boolean {
            if (p.invoiceConfirmed == true) return true
            return p.workflowStatus == WorkflowStatus.INVOICE_CONFIRMED
        }
    }

    fun computeStatus(p: Purchase): WorkflowStatus {
        if (p.invoiceConfirmed == true) return WorkflowStatus.INVOICE_CONFIRMED
        if (p.bookingRequested) return WorkflowStatus.BOOKING_REQUESTED
        if (isRixoFlagTrue(p.rixoConfirmed)) return WorkflowStatus.RIXO_CONFIRMED
        if (isRixoFlagTrue(p.rixoRequested)) return WorkflowStatus.RIXO_REQUESTED
        return p.workflowStatus ?: WorkflowStatus.PURCHASED
    }

    @Transactional(readOnly = true)
    fun applyForRead(purchase: Purchase): Purchase {
        val status = purchase.workflowStatus ?: WorkflowStatus.PURCHASED
        return purchase.copy(
            rixoRequested = when (status) {
                WorkflowStatus.RIXO_REQUESTED,
                WorkflowStatus.RIXO_CONFIRMED,
                WorkflowStatus.BOOKING_REQUESTED,
                WorkflowStatus.INVOICE_CONFIRMED,
                -> rixoFlagTrue()
                else -> rixoFlagFalse()
            },
            rixoConfirmed = when (status) {
                WorkflowStatus.RIXO_CONFIRMED,
                WorkflowStatus.BOOKING_REQUESTED,
                WorkflowStatus.INVOICE_CONFIRMED,
                -> rixoFlagTrue()
                else -> rixoFlagFalse()
            },
            bookingRequested = status in WORKFLOW_BOOKING_OR_LATER,
            invoiceConfirmed = status == WorkflowStatus.INVOICE_CONFIRMED,
        )
    }

    /** Persists canonical workflow_status from incoming transient flags (V54 column drop). */
    fun applyWorkflowWrite(purchase: Purchase): Purchase {
        val next = computeStatus(purchase)
        val now = LocalDateTime.now()
        return purchase.copy(
            workflowStatus = next,
            workflowStatusUpdatedAt = if (purchase.workflowStatus != next) now else purchase.workflowStatusUpdatedAt,
            updatedAt = now,
        )
    }

    @Transactional
    fun recomputeByPurchaseId(id: Long) {
        val p = purchaseRepository.findById(id).orElse(null) ?: return
        applyIfChanged(p)
    }

    @Transactional
    fun recomputeByPurchaseIds(ids: Iterable<Long>) {
        for (id in ids.toSet()) {
            recomputeByPurchaseId(id)
        }
    }

    @Transactional
    fun setWorkflowStatus(purchase: Purchase, status: WorkflowStatus): Purchase {
        val now = LocalDateTime.now()
        return purchaseRepository.save(
            purchase.copy(
                workflowStatus = status,
                workflowStatusUpdatedAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun applyIfChanged(p: Purchase) {
        if (p.id == null) return
        val next = computeStatus(p)
        if (p.workflowStatus == next) return
        val now = LocalDateTime.now()
        purchaseRepository.save(
            p.copy(
                workflowStatus = next,
                workflowStatusUpdatedAt = now,
                updatedAt = now,
            ),
        )
    }
}
