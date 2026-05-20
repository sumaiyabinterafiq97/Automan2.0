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
        fun isRixoFlagTrue(raw: String?): Boolean {
            val v = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return v == "TRUE" || v == "1"
        }
    }

    fun computeStatus(p: Purchase): WorkflowStatus {
        if (p.invoiceConfirmed == true) return WorkflowStatus.INVOICE_CONFIRMED
        if (p.bookingRequested) return WorkflowStatus.BOOKING_REQUESTED
        if (isRixoFlagTrue(p.rixoConfirmed)) return WorkflowStatus.RIXO_CONFIRMED
        if (isRixoFlagTrue(p.rixoRequested)) return WorkflowStatus.RIXO_REQUESTED
        return WorkflowStatus.PURCHASED
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
