package com.automan.backend.service

import com.automan.backend.model.RixoHistory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * Narrows Rixo history rows that might share a chassis token with [tokens].
 * Callers must still apply exact delimiter-token overlap rules (see [RixoHistoryService.parseChassisTokens]).
 */
@Component
class RixoHistoryOverlapCandidateQuery(
    private val entityManager: EntityManager,
) {
    fun findCandidates(tokens: Collection<String>): List<RixoHistory> {
        val distinct = tokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return emptyList()
        return distinct.chunked(MAX_OR_CHUNK).flatMap { chunk -> findCandidatesChunk(chunk) }
    }

    private fun findCandidatesChunk(tokens: List<String>): List<RixoHistory> {
        if (tokens.isEmpty()) return emptyList()
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(RixoHistory::class.java)
        val root = cq.from(RixoHistory::class.java)
        val chassisLower = cb.lower(root.get<String>("chassis"))
        val orPredicates = tokens.map { token ->
            val escaped = escapeLikeContainsPattern(token)
            cb.like(chassisLower, cb.literal("%$escaped%"), '\\')
        }
        cq.select(root).where(cb.or(*orPredicates.toTypedArray()))
        return entityManager.createQuery(cq).resultList
    }

    private fun escapeLikeContainsPattern(token: String): String =
        token.lowercase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    companion object {
        const val MAX_OR_CHUNK = 25
    }
}
