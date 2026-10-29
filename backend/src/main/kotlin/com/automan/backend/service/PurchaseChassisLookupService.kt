package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.ChassisKeyNormalizer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

/**
 * Indexed chassis equality/prefix lookups and batched token resolution.
 */
@Service
class PurchaseChassisLookupService(
    private val purchaseRepository: PurchaseRepository,
    private val chassisBatchQuerySupport: ChassisBatchQuerySupport,
) {
    @Transactional(readOnly = true)
    fun findByExactToken(token: String): List<Purchase> {
        val key = ChassisKeyNormalizer.normalize(token)
        if (key.isEmpty()) return emptyList()
        return purchaseRepository.findByChassisKey(key)
    }

    @Transactional(readOnly = true)
    fun findByExactTokens(tokens: Collection<String>): List<Purchase> {
        val keys = tokens
            .map { ChassisKeyNormalizer.normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        if (keys.isEmpty()) return emptyList()
        return keys.chunked(MAX_IN_CLAUSE).flatMap { purchaseRepository.findByChassisKeyIn(it) }
    }

    /** Uppercase trimmed chassis → purchases (Rixo history enrichment index). */
    fun indexByUpperChassis(purchases: Collection<Purchase>): Map<String, MutableList<Purchase>> {
        val map = linkedMapOf<String, MutableList<Purchase>>()
        for (p in purchases) {
            val key = ChassisKeyNormalizer.normalizeUpperDisplayKey(p.chassis)
            if (key.isEmpty()) continue
            val list = map.getOrPut(key) { mutableListOf() }
            if (list.none { it.id == p.id }) list.add(p)
        }
        return map
    }

    @Transactional(readOnly = true)
    fun buildEnrichmentIndex(tokens: Collection<String>): Map<String, MutableList<Purchase>> =
        indexByUpperChassis(findByExactTokens(tokens))

    @Transactional(readOnly = true)
    fun findByCodePrefix(prefix: String): List<Purchase> {
        val normalized = escapeLikePrefix(prefix)
        if (normalized.isEmpty()) return emptyList()
        return purchaseRepository.findByChassisKeyPrefix(normalized)
    }

    /**
     * Shipping-history recreate: exact chassis first. A token with no hyphen also matches `TOKEN-…` (lowest id).
     */
    @Transactional(readOnly = true)
    fun resolveShippingChassisTokens(tokens: List<String>): List<Purchase> {
        val seen = LinkedHashMap<Long, Purchase>()
        val cleaned = tokens.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }
        if (cleaned.isEmpty()) return emptyList()

        val exactHits = findByExactTokens(cleaned)
        val hitsByKey = exactHits.groupBy { ChassisKeyNormalizer.normalize(it.chassis) }

        val codeOnlyMisses = cleaned.filter { token ->
            val key = ChassisKeyNormalizer.normalize(token)
            hitsByKey[key].orEmpty().isEmpty() && !token.contains('-')
        }
        val prefixKeys = codeOnlyMisses.map { escapeLikePrefix(it) }.filter { it.isNotEmpty() }.distinct()
        val prefixPool = if (prefixKeys.isEmpty()) {
            emptyList()
        } else {
            chassisBatchQuerySupport.findPurchasesByCodePrefixes(prefixKeys)
        }
        val prefixHitsByKey = prefixPool.groupBy { purchase ->
            val key = purchase.chassisKey
            val dash = key.indexOf('-')
            if (dash <= 0) key else key.substring(0, dash)
        }

        for (token in cleaned) {
            val key = ChassisKeyNormalizer.normalize(token)
            val exact = hitsByKey[key].orEmpty()
            val hits = if (exact.isNotEmpty()) {
                listOf(exact.minBy { it.id ?: Long.MAX_VALUE })
            } else if (!token.contains('-')) {
                val escaped = escapeLikePrefix(token)
                prefixHitsByKey[escaped].orEmpty().sortedBy { it.id ?: Long.MAX_VALUE }.take(1)
            } else {
                emptyList()
            }
            for (purchase in hits) {
                val id = purchase.id ?: continue
                if (id !in seen) seen[id] = purchase
            }
        }
        return seen.values.toList()
    }

    fun escapeLikePrefix(token: String): String =
        ChassisKeyNormalizer.normalize(token)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    companion object {
        const val MAX_IN_CLAUSE = 500
    }
}

fun Purchase.withNormalizedChassisKey(): Purchase {
    val key = ChassisKeyNormalizer.normalize(chassis)
    return if (chassisKey == key) this else copy(chassisKey = key)
}
