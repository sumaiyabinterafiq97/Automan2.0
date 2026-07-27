package com.automan.backend.service

import com.automan.backend.dto.ConsigneeMapPageResponse
import com.automan.backend.model.BookingMapping
import com.automan.backend.repository.BookingMappingRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class BookingMappingSaveResult(
    val mapping: BookingMapping,
    val mergedIntoExisting: Boolean = false,
)

@Service
class BookingMappingService(
    private val repo: BookingMappingRepository,
) {
    @Transactional(readOnly = true)
    fun getDistinctConsigneeNames(): List<String> {
        return repo.findDistinctConsigneeNames()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @Transactional(readOnly = true)
    fun getDistinctNotifyParties(): List<String> {
        return repo.findDistinctNotifyParties()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    @Transactional
    fun add(payload: BookingMapping): BookingMappingSaveResult {
        val name = payload.consigneeName?.trim().orEmpty()
        if (name.isBlank()) {
            throw IllegalArgumentException("Consignee name is required")
        }
        val existing = repo.findFirstByConsigneeNameIgnoreCaseOrderByIdAsc(name)
        if (existing != null) {
            val merged = mergeIncomingIntoExisting(existing, payload)
            return BookingMappingSaveResult(merged, mergedIntoExisting = true)
        }
        val toSave = payload.copy(
            id = 0,
            country = ensureCountryColumn(payload.country),
            consigneeName = name,
            notifyParty = payload.notifyParty?.trim()?.takeIf { it.isNotEmpty() },
            inTransitClause = payload.inTransitClause?.trim()?.takeIf { it.isNotEmpty() },
        )
        return BookingMappingSaveResult(repo.save(toSave), mergedIntoExisting = false)
    }

    @Transactional
    fun update(id: Long, payload: BookingMapping): BookingMappingSaveResult {
        val current = repo.findById(id).orElse(null)
            ?: throw NoSuchElementException("Booking mapping not found")
        val name = payload.consigneeName?.trim().orEmpty()
        if (name.isBlank()) {
            throw IllegalArgumentException("Consignee name is required")
        }
        val other = repo.findFirstByConsigneeNameIgnoreCaseOrderByIdAsc(name)
        if (other != null && other.id != current.id) {
            val merged = mergeIncomingIntoExisting(other, payload)
            repo.deleteById(current.id)
            return BookingMappingSaveResult(merged, mergedIntoExisting = true)
        }
        val saved = repo.save(
            payload.copy(
                id = id,
                country = ensureCountryColumn(payload.country),
                consigneeName = name,
                notifyParty = payload.notifyParty?.trim()?.takeIf { it.isNotEmpty() },
                inTransitClause = payload.inTransitClause?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = current.createdAt,
            ),
        )
        return BookingMappingSaveResult(saved, mergedIntoExisting = false)
    }

    private fun mergeIncomingIntoExisting(target: BookingMapping, incoming: BookingMapping): BookingMapping {
        val mergedCountry = mergeTokenFields(
            target.country,
            incoming.country,
            TokenSplit.COMMA_SEMICOLON_NEWLINE,
        )
        val mergedPod = mergeTokenFields(target.pod, incoming.pod, TokenSplit.COMMA_SEMICOLON_NEWLINE)
        val mergedAddress = mergeTokenFields(
            target.consigneeAddress,
            incoming.consigneeAddress,
            TokenSplit.SEMICOLON_NEWLINE_ONLY,
        )
        val mergedNotify = mergeTokenFields(
            target.notifyParty,
            incoming.notifyParty,
            TokenSplit.SEMICOLON_ONLY,
        )
        val mergedInTransit = mergeTokenFields(
            target.inTransitClause,
            incoming.inTransitClause,
            TokenSplit.SEMICOLON_ONLY,
        )
        return repo.save(
            target.copy(
                country = ensureCountryColumn(mergedCountry ?: target.country),
                consigneeName = target.consigneeName,
                consigneeAddress = mergedAddress ?: target.consigneeAddress,
                pod = mergedPod ?: target.pod,
                notifyParty = mergedNotify ?: target.notifyParty,
                inTransitClause = mergedInTransit ?: target.inTransitClause,
                createdAt = target.createdAt,
            ),
        )
    }

    private fun ensureCountryColumn(country: String?): String {
        val c = country?.trim().orEmpty()
        return if (c.isEmpty()) "-" else c
    }

    private enum class TokenSplit {
        COMMA_SEMICOLON_NEWLINE,
        SEMICOLON_NEWLINE_ONLY,
        /** Notify / In-Transit: preserve commas and newlines inside a single value. */
        SEMICOLON_ONLY,
    }

    private fun mergeTokenFields(
        existing: String?,
        incoming: String?,
        split: TokenSplit,
    ): String? {
        val a = tokenize(existing, split)
        val b = tokenize(incoming, split)
        val ordered = mutableListOf<String>()
        val seenKeys = HashSet<String>()
        for (t in a + b) {
            val trimmed = t.trim()
            if (trimmed.isEmpty()) continue
            val key = trimmed.uppercase()
            if (seenKeys.add(key)) {
                ordered.add(trimmed)
            }
        }
        if (ordered.isEmpty()) return null
        return ordered.joinToString(";")
    }

    private fun tokenize(s: String?, split: TokenSplit): List<String> {
        if (s.isNullOrBlank()) return emptyList()
        val regex = when (split) {
            TokenSplit.COMMA_SEMICOLON_NEWLINE -> Regex("""[,;\n]+""")
            TokenSplit.SEMICOLON_NEWLINE_ONLY -> Regex("""[;\n]+""")
            TokenSplit.SEMICOLON_ONLY -> Regex(""";+""")
        }
        return regex.split(s).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Paginated browse for Consignee Map UI (no search text). Prefer this over findAll for UI.
     */
    @Transactional(readOnly = true)
    fun listConsigneeMapPage(page: Int, rawSize: Int): ConsigneeMapPageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "id"))
        val pg = repo.findAll(pageable)
        return ConsigneeMapPageResponse(
            content = pg.content,
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    /**
     * Paginated search for Consignee Map UI.
     * [field]: `all`, `consigneeName`, `country`.
     */
    @Transactional(readOnly = true)
    fun searchConsigneeMapPage(rawQuery: String, rawField: String, page: Int, rawSize: Int): ConsigneeMapPageResponse {
        val q = sanitizeConsigneeMapSearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val field = rawField.trim().lowercase().ifEmpty { "all" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "id"))
        val pg = when (field) {
            "consigneename", "consignee_name" ->
                repo.searchConsigneeMapConsigneeNameContains(q, pageable)
            "country" ->
                repo.searchConsigneeMapCountryContains(q, pageable)
            "all" -> repo.searchConsigneeMapAllFields(q, pageable)
            else -> throw IllegalArgumentException(
                "Invalid search field: $field. Use all, consigneeName, or country."
            )
        }
        return ConsigneeMapPageResponse(
            content = pg.content,
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    private fun sanitizeConsigneeMapSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    /**
     * Resolves consignee address from Consignee Map (`booking_mappings`).
     * When multiple rows share the same name, prefers matches on [country] and/or [pod].
     */
    @Transactional(readOnly = true)
    fun resolveConsigneeAddress(consigneeName: String?, country: String?, pod: String?): String {
        val name = consigneeName?.trim().orEmpty()
        if (name.isEmpty()) return ""
        val rows = repo.findAllByConsigneeNameIgnoreCaseOrderByIdAsc(name)
        if (rows.isEmpty()) return ""
        if (rows.size == 1) return rows.first().consigneeAddress?.trim().orEmpty()
        val countryQ = country?.trim()?.lowercase().orEmpty()
        val podQ = pod?.trim()?.lowercase().orEmpty()
        fun tokens(raw: String?): List<String> =
            raw.orEmpty().split(Regex("[;,\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        fun matchesCountry(m: BookingMapping): Boolean {
            if (countryQ.isEmpty()) return false
            return tokens(m.country).any { it.equals(countryQ, ignoreCase = true) }
        }
        fun matchesPod(m: BookingMapping): Boolean {
            if (podQ.isEmpty()) return false
            return tokens(m.pod).any { t ->
                t.equals(podQ, ignoreCase = true) ||
                    podQ.contains(t, ignoreCase = true) ||
                    t.contains(podQ, ignoreCase = true)
            }
        }
        val both = rows.filter { matchesCountry(it) && matchesPod(it) }
        if (both.isNotEmpty()) return both.first().consigneeAddress?.trim().orEmpty()
        val byCountry = rows.filter { matchesCountry(it) }
        if (byCountry.isNotEmpty()) return byCountry.first().consigneeAddress?.trim().orEmpty()
        val byPod = rows.filter { matchesPod(it) }
        if (byPod.isNotEmpty()) return byPod.first().consigneeAddress?.trim().orEmpty()
        return rows.first().consigneeAddress?.trim().orEmpty()
    }
}
