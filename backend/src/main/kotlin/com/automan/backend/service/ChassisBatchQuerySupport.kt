package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.model.Purchase
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/**
 * Batched chassis prefix queries (JPQL/Criteria) that are awkward to express as static @Query methods.
 */
@Component
class ChassisBatchQuerySupport(
    private val entityManager: EntityManager,
) {
    /** All purchases whose chassis_key matches `prefix-…` for any normalized [prefixes] (lowest id per prefix resolved by caller). */
    fun findPurchasesByCodePrefixes(prefixes: Collection<String>): List<Purchase> {
        val distinct = prefixes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return emptyList()
        return distinct.chunked(MAX_PREFIX_OR_CHUNK).flatMap { chunk -> findPurchasesByCodePrefixesChunk(chunk) }
    }

    private fun findPurchasesByCodePrefixesChunk(prefixes: List<String>): List<Purchase> {
        if (prefixes.isEmpty()) return emptyList()
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(Purchase::class.java)
        val root = cq.from(Purchase::class.java)
        val chassisKey = root.get<String>("chassisKey")
        val orPredicates = prefixes.map { prefix ->
            cb.like(chassisKey, cb.literal("$prefix-%"), '\\')
        }
        cq.select(root).where(cb.or(*orPredicates.toTypedArray())).orderBy(cb.asc(root.get<Long>("id")))
        return entityManager.createQuery(cq).resultList
    }

    /**
     * Mapping rows that are a prefix of any full chassis in [fullChassisList]
     * (same rule as [com.automan.backend.repository.CarBrandMappingRepository.findBestPrefixMatchForChassis]).
     */
    fun findMappingPrefixCandidatesForFullChassis(fullChassisList: Collection<String>): List<CarBrandMapping> {
        val distinct = fullChassisList.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return emptyList()
        return distinct.chunked(MAX_PREFIX_OR_CHUNK).flatMap { chunk ->
            findMappingPrefixCandidatesChunk(chunk)
        }
    }

    private fun findMappingPrefixCandidatesChunk(fullChassisList: List<String>): List<CarBrandMapping> {
        if (fullChassisList.isEmpty()) return emptyList()
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(CarBrandMapping::class.java)
        val root = cq.from(CarBrandMapping::class.java)
        val mappingChassis = root.get<String>("chassis")
        val notBlank = cb.and(
            cb.isNotNull(mappingChassis),
            cb.notEqual(mappingChassis, ""),
        )
        val orPredicates = fullChassisList.map { full ->
            cb.like(cb.literal(full), cb.concat(mappingChassis, cb.literal("%")))
        }
        cq.select(root).where(notBlank, cb.or(*orPredicates.toTypedArray()))
        return entityManager.createQuery(cq).resultList
    }

    companion object {
        const val MAX_PREFIX_OR_CHUNK = 25
    }
}
