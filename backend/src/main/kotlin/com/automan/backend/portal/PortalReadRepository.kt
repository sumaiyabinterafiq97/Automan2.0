package com.automan.backend.portal

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Portal reads select only the columns the DTOs need.
 * Purchases are scoped by client_id. An active consignee also sees purchases whose consignee text
 * matches that consignee's legal name. That match does not change client assignments.
 * Shipping and invoice rows are reached through that purchase's chassis
 * only when the trimmed chassis belongs to exactly one purchase. A blank or shared chassis is omitted.
 * shipping_history.booking_id is a string and purchases.booking_id is a number, so those columns are not joined.
 * client_name and consignee text on shipping and invoice rows are not selected and are not authorization keys.
 */
@Repository
class PortalReadRepository(
    private val em: EntityManager,
) {
    data class ClientIdentity(val clientNumber: String, val clientName: String)
    data class ConsigneeIdentity(val legalName: String, val status: String)

    data class CarRow(
        val purchaseId: Long,
        val chassis: String,
        val carName: String?,
        val year: String?,
        val stockLocation: String?,
        val country: String?,
        val clientId: Long?,
        val bookingId: Long?,
        val pol: String?,
        val pod: String?,
        val shipped: Boolean,
    )

    data class ShipRow(
        val id: Long,
        val bookingId: String?,
        val vessel: String?,
        val pol: String?,
        val pod: String?,
        val shipmentDate: LocalDate?,
        val cyCutDate: LocalDate?,
        val eta: LocalDate?,
        val chassis: String,
        val clientId: Long?,
    )

    data class InvoiceRow(
        val invoiceId: Long,
        val invoiceNumber: String,
        val createdAt: LocalDateTime?,
        val vessel: String?,
        val priceType: String?,
        val chassis: String,
    )

    data class TransactionRow(
        val date: LocalDate,
        val type: String,
    )

    data class DashboardCounts(
        val purchased: Long,
        val shipped: Long,
        val invoiced: Long,
    )

    fun clientIdentity(clientId: Long): ClientIdentity? {
        val rows = query(
            "SELECT c.clientNumber, c.clientName FROM Client c WHERE c.id = :id",
            mapOf("id" to clientId),
        )
        val cells = rows.firstOrNull()?.let(::cells) ?: return null
        return ClientIdentity(cells[0] as String, cells[1] as String)
    }

    fun consigneeIdentity(consigneeId: Long): ConsigneeIdentity? {
        val rows = query(
            "SELECT g.legalName, g.status FROM Consignee g WHERE g.id = :id",
            mapOf("id" to consigneeId),
        )
        val cells = rows.firstOrNull()?.let(::cells) ?: return null
        return ConsigneeIdentity(cells[0] as String, cells[1] as String)
    }

    fun clientIdsForConsignee(consigneeId: Long): List<Long> =
        query(
            "SELECT c.id FROM Client c WHERE c.consigneeId = :id ORDER BY c.id",
            mapOf("id" to consigneeId),
        ).map { (it as Number).toLong() }

    fun clientNames(clientIds: Collection<Long>): Map<Long, String> {
        if (clientIds.isEmpty()) return emptyMap()
        return query(
            "SELECT c.id, c.clientName FROM Client c WHERE c.id IN :ids",
            mapOf("ids" to clientIds),
        ).associate { row ->
            val cells = cells(row)
            (cells[0] as Number).toLong() to cells[1] as String
        }
    }

    data class PurchaseScope(
        val clientIds: List<Long> = emptyList(),
        val consigneeName: String = "",
    ) {
        fun isOpen(): Boolean = clientIds.isNotEmpty() || consigneeName.isNotBlank()
    }

    fun purchaseInScope(purchaseId: Long, scope: PurchaseScope): Boolean {
        if (!scope.isOpen()) return false
        val (predicate, params) = purchasePredicate(scope)
        val count = em.createQuery(
            "SELECT COUNT(p.id) FROM Purchase p WHERE p.id = :purchaseId AND ($predicate)",
        )
        params.forEach { (name, value) -> count.setParameter(name, value) }
        count.setParameter("purchaseId", purchaseId)
        return (count.singleResult as Number).toLong() > 0
    }

    fun dashboard(scope: PurchaseScope): DashboardCounts {
        if (!scope.isOpen()) return DashboardCounts(0, 0, 0)
        val (predicate, params) = purchasePredicate(scope)
        val created = em.createQuery(
            """
            SELECT COUNT(p.id),
                   SUM(CASE WHEN $UNAMBIGUOUS_CHASSIS AND (SELECT COUNT(s.id) FROM ShippingHistory s WHERE TRIM(s.chassis) = TRIM(p.chassis)) > 0 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN $UNAMBIGUOUS_CHASSIS AND (SELECT COUNT(l.id) FROM InvoiceHistoryLine l WHERE TRIM(l.chassis) = TRIM(p.chassis)) > 0 THEN 1 ELSE 0 END)
            FROM Purchase p
            WHERE $predicate
            """.trimIndent(),
        )
        params.forEach { (name, value) -> created.setParameter(name, value) }
        val cells = cells(created.singleResult)
        return DashboardCounts(number(cells[0]), number(cells[1]), number(cells[2]))
    }

    fun cars(scope: PurchaseScope, chassisContains: String): List<CarRow> {
        if (!scope.isOpen()) return emptyList()
        val (predicate, params) = purchasePredicate(scope)
        return query(
            """
            SELECT p.id, p.chassis, p.carName, p.manufactureYear, p.stockLocation, p.country,
                   p.clientId, p.bookingId, p.pol, p.pod,
                   (SELECT COUNT(s.id) FROM ShippingHistory s WHERE $UNAMBIGUOUS_CHASSIS AND TRIM(s.chassis) = TRIM(p.chassis))
            FROM Purchase p
            WHERE $predicate
              AND (:chassis = '' OR LOWER(p.chassis) LIKE LOWER(CONCAT('%', :chassis, '%')))
            ORDER BY p.chassis ASC, p.id ASC
            """.trimIndent(),
            params + ("chassis" to chassisContains),
        ).map { row ->
            val cells = cells(row)
            CarRow(
                purchaseId = (cells[0] as Number).toLong(),
                chassis = cells[1] as String,
                carName = cells[2] as String?,
                year = cells[3] as String?,
                stockLocation = cells[4] as String?,
                country = cells[5] as String?,
                clientId = (cells[6] as Number?)?.toLong(),
                bookingId = (cells[7] as Number?)?.toLong(),
                pol = cells[8] as String?,
                pod = cells[9] as String?,
                shipped = number(cells[10]) > 0,
            )
        }
    }

    fun shipments(scope: PurchaseScope): List<ShipRow> {
        if (!scope.isOpen()) return emptyList()
        val (predicate, params) = purchasePredicate(scope)
        return query(
            """
            SELECT s.id, s.bookingId, s.vessel, s.pol, s.pod, s.shipmentDate, s.cyCutDate, s.eta, s.chassis, p.clientId
            FROM ShippingHistory s, Purchase p
            WHERE $predicate
              AND $UNAMBIGUOUS_CHASSIS
              AND TRIM(p.chassis) = TRIM(s.chassis)
            ORDER BY s.id ASC
            """.trimIndent(),
            params,
        ).map { row ->
            val cells = cells(row)
            ShipRow(
                id = (cells[0] as Number).toLong(),
                bookingId = cells[1] as String?,
                vessel = cells[2] as String?,
                pol = cells[3] as String?,
                pod = cells[4] as String?,
                shipmentDate = cells[5] as LocalDate?,
                cyCutDate = cells[6] as LocalDate?,
                eta = cells[7] as LocalDate?,
                chassis = cells[8] as String,
                clientId = (cells[9] as Number?)?.toLong(),
            )
        }
    }

    fun invoices(scope: PurchaseScope): List<InvoiceRow> {
        if (!scope.isOpen()) return emptyList()
        val (predicate, params) = purchasePredicate(scope)
        return query(
            """
            SELECT h.id, h.invoiceNumber, h.createdAt, h.vessel, h.priceType, l.chassis
            FROM InvoiceHistory h, InvoiceHistoryLine l, Purchase p
            WHERE l.invoiceHistoryId = h.id
              AND $predicate
              AND $UNAMBIGUOUS_CHASSIS
              AND TRIM(p.chassis) = TRIM(l.chassis)
            ORDER BY h.createdAt DESC, h.id DESC, l.sortOrder ASC
            """.trimIndent(),
            params,
        ).map { row ->
            val cells = cells(row)
            InvoiceRow(
                invoiceId = (cells[0] as Number).toLong(),
                invoiceNumber = cells[1] as String,
                createdAt = cells[2] as LocalDateTime?,
                vessel = cells[3] as String?,
                priceType = cells[4] as String?,
                chassis = cells[5] as String,
            )
        }
    }

    fun transactions(clientIds: Collection<Long>): List<TransactionRow> {
        if (clientIds.isEmpty()) return emptyList()
        return query(
            """
            SELECT e.eventDate, e.eventType
            FROM Event e
            WHERE e.clientId IN :ids
            ORDER BY e.eventDate DESC, e.id DESC
            """.trimIndent(),
            mapOf("ids" to clientIds),
        ).map { row ->
            val cells = cells(row)
            TransactionRow(
                date = cells[0] as LocalDate,
                type = cells[1].toString(),
            )
        }
    }

    private companion object {
        /**
         * purchases.chassis is indexed and not unique. shipping_history and invoice_history_line have no purchase id.
         * Associating those rows is safe only when one purchase owns the trimmed chassis.
         */
        const val UNAMBIGUOUS_CHASSIS =
            "TRIM(p.chassis) <> '' AND (SELECT COUNT(other.id) FROM Purchase other WHERE TRIM(other.chassis) = TRIM(p.chassis)) = 1"
    }

    private fun purchasePredicate(scope: PurchaseScope): Pair<String, Map<String, Any>> {
        val clauses = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        if (scope.clientIds.isNotEmpty()) {
            clauses += "p.clientId IN :ids"
            params["ids"] = scope.clientIds
        }
        val name = scope.consigneeName.trim()
        if (name.isNotEmpty()) {
            clauses += "LOWER(TRIM(COALESCE(p.consignee, ''))) = LOWER(:consigneeName)"
            params["consigneeName"] = name
        }
        if (clauses.isEmpty()) return "1 = 0" to emptyMap()
        return "(${clauses.joinToString(" OR ")})" to params
    }

    private fun query(jpql: String, params: Map<String, Any>): List<*> {
        val created = em.createQuery(jpql)
        params.forEach { (name, value) -> created.setParameter(name, value) }
        return created.resultList
    }

    private fun cells(row: Any?): List<Any?> = when (row) {
        is Array<*> -> row.toList()
        is List<*> -> row
        else -> listOf(row)
    }

    private fun number(value: Any?): Long = (value as? Number)?.toLong() ?: 0L
}
