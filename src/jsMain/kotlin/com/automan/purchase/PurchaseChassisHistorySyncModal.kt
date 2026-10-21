package com.automan.purchase

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import kotlin.js.JSON

private val chassisHistorySyncScope = MainScope()

private const val CHASSIS_HISTORY_PREVIEW_LOADING_ID = "purchaseChassisHistorySyncLoading"

private const val PURCHASE_BULK_SUMMARY_OVERLAY_STYLE =
    "position:fixed;inset:0;background:rgba(15,23,42,0.45);z-index:12000;display:flex;align-items:center;justify-content:center;padding:24px;"

/**
 * Omit workflow/status keys from the PUT unless the user changed them in the confirm modal.
 */
fun stripUnchangedWorkflowFieldsFromEditPayload(purchaseData: dynamic, auditFields: Array<String>?) {
    if (auditFields == null) return
    val audit = auditFields.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun keep(field: String, aliases: List<String> = listOf(field)): Boolean =
        aliases.any { it in audit }
    if (!keep("rixoRequested")) js("delete purchaseData.rixoRequested")
    if (!keep("rixoConfirmed")) js("delete purchaseData.rixoConfirmed")
    if (!keep("bookingRequested")) js("delete purchaseData.bookingRequested")
    if (!keep("invoiceConfirmed", listOf("invoiceConfirmed", "sold"))) {
        js("delete purchaseData.invoiceConfirmed")
    }
    if (!keep("sold", listOf("sold", "invoiceConfirmed"))) js("delete purchaseData.sold")
}

fun finalizeEditPurchasePayloadForSubmit(purchaseData: dynamic, auditFields: Array<String>?): dynamic {
    val payload = JSON.parse<dynamic>(JSON.stringify(purchaseData))
    stripUnchangedWorkflowFieldsFromEditPayload(payload, auditFields)
    return payload
}

/**
 * When chassis changes on Edit Purchase, preview linked history rows and ask whether to sync.
 */
fun runEditPurchaseSaveWithChassisHistorySync(
    purchaseId: Long,
    oldChassis: String,
    newChassis: String,
    purchaseData: dynamic,
    auditChangedFields: Array<String>?,
    onSubmit: (dynamic) -> Unit,
    onCancel: () -> Unit = {},
) {
    val oldNorm = oldChassis.trim()
    val newNorm = newChassis.trim()
    val chassisInAudit = auditChangedFields?.contains("chassis") == true
    if (!chassisInAudit ||
        oldNorm.isEmpty() || newNorm.isEmpty() ||
        oldNorm.equals(newNorm, ignoreCase = true)
    ) {
        onSubmit(finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields))
        return
    }

    showChassisHistoryPreviewLoading()
    chassisHistorySyncScope.launch {
        try {
            val previewBody = js("{}")
            previewBody.newChassis = newNorm
            when (val result = ApiClient.post<dynamic>(
                "purchases/$purchaseId/chassis-history-sync/preview",
                previewBody,
            )) {
                is ApiResult.Success -> {
                    hideChassisHistoryPreviewLoading()
                    val preview = result.data
                    if (preview == null) {
                        onSubmit(finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields))
                        return@launch
                    }
                    val blockers = previewBlockers(preview)
                    val hasUpdates = previewHasUpdates(preview)
                    if (!hasUpdates && blockers.isEmpty()) {
                        onSubmit(finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields))
                        return@launch
                    }
                    showPurchaseChassisHistorySyncModal(
                        preview = preview,
                        onYes = {
                            val payload = finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields)
                            payload.syncHistoryChassis = true
                            onSubmit(payload)
                        },
                        onNo = {
                            val payload = finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields)
                            payload.syncHistoryChassis = false
                            onSubmit(payload)
                        },
                        onCancel = onCancel,
                    )
                }
                is ApiResult.Error -> {
                    hideChassisHistoryPreviewLoading()
                    onSubmit(finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields))
                }
            }
        } catch (_: dynamic) {
            hideChassisHistoryPreviewLoading()
            onSubmit(finalizeEditPurchasePayloadForSubmit(purchaseData, auditChangedFields))
        }
    }
}

private fun showChassisHistoryPreviewLoading() {
    hideChassisHistoryPreviewLoading()
    val overlay = document.createElement("div") as HTMLElement
    overlay.id = CHASSIS_HISTORY_PREVIEW_LOADING_ID
    overlay.style.cssText =
        "position:fixed;inset:0;background:rgba(15,23,42,0.35);z-index:11999;display:flex;align-items:center;justify-content:center;padding:24px;"
    overlay.innerHTML = """
        <div style="background:#fff;border-radius:12px;padding:20px 28px;box-shadow:0 20px 40px rgba(0,0,0,0.18);font-size:14px;color:#334155;font-weight:500;">
            Checking linked history…
        </div>
    """
    document.body?.appendChild(overlay)
}

private fun hideChassisHistoryPreviewLoading() {
    document.getElementById(CHASSIS_HISTORY_PREVIEW_LOADING_ID)?.remove()
}

private fun previewBlockers(preview: dynamic): List<String> {
    val raw = preview.blockers
    if (raw == null || raw == js("undefined")) return emptyList()
    val len = (raw.length as? Int) ?: (raw.length as? Double)?.toInt() ?: return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until len) {
        val s = raw[i]?.toString()?.trim().orEmpty()
        if (s.isNotEmpty()) out.add(s)
    }
    return out
}

private fun previewHasUpdates(preview: dynamic): Boolean =
    sectionCount(preview, "rixo") + sectionCount(preview, "shipping") + sectionCount(preview, "invoice") > 0

private fun sectionCount(preview: dynamic, key: String): Int {
        val raw = preview[key]
        if (raw == null || raw == js("undefined")) return 0
        val len = (raw.length as? Int) ?: (raw.length as? Double)?.toInt() ?: 0
        return len
}

private fun showPurchaseChassisHistorySyncModal(
    preview: dynamic,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit,
) {
    document.getElementById("purchaseChassisHistorySyncModal")?.remove()
    val blockers = previewBlockers(preview)
    val canApply = preview.canApply == true ||
        preview.canApply?.toString()?.equals("true", ignoreCase = true) == true
    val skipped = preview.skippedReason?.toString()?.trim().orEmpty()
    val blockerHtml = if (blockers.isEmpty()) {
        ""
    } else {
        """<div class="purchase-bulk-summary-error">${blockers.joinToString("<br>") { escapeHtml(it) }}</div>"""
    }
    val skippedHtml = if (skipped.isEmpty()) {
        ""
    } else {
        """<p class="purchase-bulk-note">${escapeHtml(skipped)}</p>"""
    }
    val overlay = document.createElement("div") as HTMLElement
    overlay.id = "purchaseChassisHistorySyncModal"
    overlay.className = "purchase-bulk-summary-overlay"
    overlay.style.cssText = PURCHASE_BULK_SUMMARY_OVERLAY_STYLE
    overlay.innerHTML = """
        <div class="purchase-bulk-summary" role="dialog" aria-modal="true" aria-labelledby="purchaseChassisSyncTitle">
            <h2 id="purchaseChassisSyncTitle">Update history chassis?</h2>
            <p style="margin:0 0 12px;color:#334155;font-size:14px;">
                This purchase chassis was changed. You can update the same chassis text in history records below.
            </p>
            $blockerHtml
            $skippedHtml
            ${previewSectionHtml("Rixo History", preview.rixo, ::formatRixoSyncRow)}
            ${previewSectionHtml("Shipping History", preview.shipping, ::formatShippingSyncRow)}
            ${previewSectionHtml("Invoice History", preview.invoice, ::formatInvoiceSyncRow)}
            <div class="purchase-bulk-summary-actions">
                <button type="button" id="purchaseChassisSyncCancel">Cancel</button>
                <button type="button" id="purchaseChassisSyncNo">No</button>
                <button type="button" id="purchaseChassisSyncYes"${if (!canApply) """ disabled aria-disabled="true"""" else ""}>Yes, update history</button>
            </div>
        </div>
    """
    document.body?.appendChild(overlay)
    fun close() {
        overlay.remove()
    }
    document.getElementById("purchaseChassisSyncCancel")?.addEventListener("click", {
        close()
        onCancel()
    })
    document.getElementById("purchaseChassisSyncNo")?.addEventListener("click", {
        close()
        onNo()
    })
    document.getElementById("purchaseChassisSyncYes")?.addEventListener("click", {
        if (!canApply) return@addEventListener
        close()
        onYes()
    })
}

private fun previewSectionHtml(
    title: String,
    items: dynamic,
    rowFormatter: (dynamic) -> String,
): String {
    val len = when (val l = items?.length) {
        is Int -> l
        is Double -> l.toInt()
        else -> 0
    }
    if (len <= 0) {
        return """<h3 style="margin:16px 0 6px;font-size:15px;color:#0f172a;">${escapeHtml(title)}</h3><p class="purchase-bulk-note">None</p>"""
    }
    val rows = (0 until len).joinToString("") { i ->
        val item = items[i]
        """<tr>${rowFormatter(item)}</tr>"""
    }
    return """
        <h3 style="margin:16px 0 6px;font-size:15px;color:#0f172a;">${escapeHtml(title)}</h3>
        <div class="purchase-bulk-summary-table-wrap">
            <table><tbody>$rows</tbody></table>
        </div>
    """
}

private fun formatRixoSyncRow(item: dynamic): String {
    val id = item.historyId?.toString() ?: "-"
    val date = item.buyingDate?.toString()?.trim().orEmpty().ifEmpty { "-" }
    val company = item.rixoCompany?.toString()?.trim().orEmpty().ifEmpty { "-" }
    val old = item.oldChassis?.toString()?.trim().orEmpty()
    val new = item.newChassis?.toString()?.trim().orEmpty()
    return """
        <td>${escapeHtml(id)}</td>
        <td>${escapeHtml(date)}</td>
        <td>${escapeHtml(company)}</td>
        <td>${escapeHtml("$old → $new")}</td>
    """
}

private fun formatShippingSyncRow(item: dynamic): String {
    val id = item.historyId?.toString() ?: "-"
    val client = item.clientName?.toString()?.trim().orEmpty().ifEmpty { "-" }
    val vessel = item.vessel?.toString()?.trim().orEmpty().ifEmpty { "-" }
    val old = item.oldChassis?.toString()?.trim().orEmpty()
    val new = item.newChassis?.toString()?.trim().orEmpty()
    return """
        <td>${escapeHtml(id)}</td>
        <td>${escapeHtml(client)}</td>
        <td>${escapeHtml(vessel)}</td>
        <td>${escapeHtml("$old → $new")}</td>
    """
}

private fun formatInvoiceSyncRow(item: dynamic): String {
    val inv = item.invoiceNumber?.toString()?.trim().orEmpty().ifEmpty { "-" }
    val lineId = item.lineId?.toString() ?: "-"
    val old = item.oldChassis?.toString()?.trim().orEmpty()
    val new = item.newChassis?.toString()?.trim().orEmpty()
    return """
        <td>${escapeHtml(inv)}</td>
        <td>${escapeHtml(lineId)}</td>
        <td>${escapeHtml("$old → $new")}</td>
    """
}
