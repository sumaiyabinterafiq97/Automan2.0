package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseIdDateProjection
import com.automan.backend.util.PurchaseDateParseUtils
import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.Locale

/**
 * One bounded JPQL query returning a superset of purchase id+date rows that may fall in an inclusive
 * [from]..[to] range after [PurchaseDateParseUtils.parseToLocalDate]. Final range equality stays in Kotlin.
 */
@Component
class PurchaseDateRangeCandidateQuery(
    private val entityManager: EntityManager,
) {
    fun findIdDateCandidates(from: LocalDate, to: LocalDate): List<PurchaseIdDateProjection> {
        val months = PurchaseDateParseUtils.yearMonthsInInclusiveRange(from, to)
        if (months.isEmpty()) return emptyList()

        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(Tuple::class.java)
        val root = cq.from(Purchase::class.java)
        val datePath = root.get<String>("date")
        val trimmed = cb.trim(datePath)
        val notBlank = cb.and(
            cb.isNotNull(datePath),
            cb.notEqual(trimmed, ""),
        )

        val fromIso = from.toString()
        val toIso = to.toString()
        val isoInRange = cb.and(
            cb.equal(cb.length(trimmed), 10),
            cb.greaterThanOrEqualTo(trimmed, fromIso),
            cb.lessThanOrEqualTo(trimmed, toIso),
        )

        val monthPredicates = months.map { ym ->
            val pattern = PurchaseDateParseUtils.sqlLabelMonthYearLikePattern(ym).lowercase(Locale.ROOT)
            cb.like(cb.lower(trimmed), pattern)
        }

        cq.multiselect(root.get<Long>("id"), datePath)
        cq.where(cb.and(notBlank, cb.or(isoInRange, *monthPredicates.toTypedArray())))

        return entityManager.createQuery(cq).resultList.map { tuple ->
            PurchaseIdDateProjectionRow(
                id = tuple.get(0, Long::class.java),
                date = tuple.get(1, String::class.java),
            )
        }
    }

    private data class PurchaseIdDateProjectionRow(
        private val id: Long?,
        private val date: String?,
    ) : PurchaseIdDateProjection {
        override fun getId(): Long? = id
        override fun getDate(): String? = date
    }
}
