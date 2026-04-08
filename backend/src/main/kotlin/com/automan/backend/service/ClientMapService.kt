package com.automan.backend.service

import com.automan.backend.model.ClientMap
import com.automan.backend.repository.ClientMapRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

data class ClientMapSaveResult(
    val map: ClientMap,
    /** True when new values were merged into an existing row (same client name, unique name rule). */
    val mergedIntoExisting: Boolean = false,
)

@Service
class ClientMapService(
    private val clientMapRepository: ClientMapRepository,
) {

    fun listAll(): List<ClientMap> =
        clientMapRepository.findAll(Sort.by(Sort.Order.desc("id")))

    fun findById(id: Long): ClientMap? =
        clientMapRepository.findById(id).orElse(null)

    @Transactional
    fun create(body: Map<String, Any?>): ClientMapSaveResult {
        val name = requiredClientName(body)
        val existing = clientMapRepository.findByClientNameIgnoreCase(name)
        if (existing != null) {
            val merged = mergeIncomingIntoExisting(existing, body)
            return ClientMapSaveResult(merged, mergedIntoExisting = true)
        }
        val now = LocalDateTime.now()
        val row = ClientMap(
            id = null,
            clientName = name,
            country = mergeStr(body, null, "country"),
            pod = mergeStr(body, null, "pod"),
            address = mergeStr(body, null, "address"),
            bankInfo = mergeStr(body, null, "bankInfo", "bank_info"),
            consignee = mergeStr(body, null, "consignee"),
            debitLimit = mergeDecimal(body, null, "debitLimit", "debit_limit"),
            createdAt = now,
            updatedAt = now,
        )
        return ClientMapSaveResult(clientMapRepository.save(row), mergedIntoExisting = false)
    }

    @Transactional
    fun update(id: Long, body: Map<String, Any?>): ClientMapSaveResult {
        val current = clientMapRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Mapping not found")
        val name = requiredClientName(body)
        val other = clientMapRepository.findByClientNameIgnoreCase(name)
        if (other != null && other.id != current.id) {
            val merged = mergeIncomingIntoExisting(other, body)
            clientMapRepository.deleteById(current.id!!)
            return ClientMapSaveResult(merged, mergedIntoExisting = true)
        }
        val now = LocalDateTime.now()
        val saved = clientMapRepository.save(
            current.copy(
                clientName = name,
                country = mergeStr(body, current.country, "country"),
                pod = mergeStr(body, current.pod, "pod"),
                address = mergeStr(body, current.address, "address"),
                bankInfo = mergeStr(body, current.bankInfo, "bankInfo", "bank_info"),
                consignee = mergeStr(body, current.consignee, "consignee"),
                debitLimit = mergeDecimal(body, current.debitLimit, "debitLimit", "debit_limit"),
                createdAt = current.createdAt,
                updatedAt = now,
            ),
        )
        return ClientMapSaveResult(saved, mergedIntoExisting = false)
    }

    @Transactional
    fun delete(id: Long): Boolean {
        if (!clientMapRepository.existsById(id)) return false
        clientMapRepository.deleteById(id)
        return true
    }

    /**
     * Append distinct tokens from [body] onto [target] (semicolon-separated in DB).
     * Does not split bank strings on commas (only `;` / newlines).
     */
    private fun mergeIncomingIntoExisting(target: ClientMap, body: Map<String, Any?>): ClientMap {
        val now = LocalDateTime.now()
        val inCountry = extractIncomingString(body, "country")
        val inPod = extractIncomingString(body, "pod")
        val inAddress = extractIncomingString(body, "address")
        val inBank = extractIncomingString(body, "bankInfo", "bank_info")
        val inConsignee = extractIncomingString(body, "consignee")
        val incomingDebit = extractIncomingDecimal(body)
        return clientMapRepository.save(
            target.copy(
                country = mergeTokenFields(target.country, inCountry, TokenSplit.COMMA_SEMICOLON_NEWLINE),
                pod = mergeTokenFields(target.pod, inPod, TokenSplit.COMMA_SEMICOLON_NEWLINE),
                address = mergeTokenFields(target.address, inAddress, TokenSplit.SEMICOLON_NEWLINE_ONLY),
                bankInfo = mergeTokenFields(target.bankInfo, inBank, TokenSplit.SEMICOLON_NEWLINE_ONLY),
                consignee = mergeTokenFields(target.consignee, inConsignee, TokenSplit.COMMA_SEMICOLON_NEWLINE),
                debitLimit = mergeDebitLimits(target.debitLimit, incomingDebit),
                createdAt = target.createdAt,
                updatedAt = now,
            ),
        )
    }

    private enum class TokenSplit {
        /** Matches frontend chip lists: country, POD, consignee */
        COMMA_SEMICOLON_NEWLINE,
        /** Address lines and bank info: commas stay inside a value */
        SEMICOLON_NEWLINE_ONLY,
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
        }
        return regex.split(s).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun mergeDebitLimits(a: BigDecimal?, b: BigDecimal?): BigDecimal? {
        if (a == null) return b
        if (b == null) return a
        return a.max(b)
    }

    private fun extractIncomingString(body: Map<String, Any?>, vararg keys: String): String? {
        for (k in keys) {
            if (body.containsKey(k)) {
                val v = body[k] ?: return null
                val s = v.toString().trim()
                return s.ifEmpty { null }
            }
        }
        return null
    }

    private fun extractIncomingDecimal(body: Map<String, Any?>): BigDecimal? {
        for (k in listOf("debitLimit", "debit_limit")) {
            if (body.containsKey(k)) {
                return parseDecimal(body[k])
            }
        }
        return null
    }

    private fun requiredClientName(body: Map<String, Any?>): String {
        val raw = body["clientName"] ?: body["client_name"]
        val name = raw?.toString()?.trim().orEmpty()
        if (name.isBlank()) throw IllegalArgumentException("Client name is required")
        return name
    }

    private fun mergeStr(body: Map<String, Any?>, existing: String?, vararg keys: String): String? {
        for (k in keys) {
            if (body.containsKey(k)) {
                val v = body[k] ?: return null
                val s = v.toString().trim()
                return s.ifEmpty { null }
            }
        }
        return existing
    }

    private fun mergeDecimal(body: Map<String, Any?>, existing: BigDecimal?, vararg keys: String): BigDecimal? {
        for (k in keys) {
            if (body.containsKey(k)) {
                return parseDecimal(body[k])
            }
        }
        return existing
    }

    private fun parseDecimal(v: Any?): BigDecimal? {
        if (v == null) return null
        return when (v) {
            is BigDecimal -> v
            is Number -> BigDecimal.valueOf(v.toDouble())
            else -> {
                val s = v.toString().trim()
                if (s.isEmpty()) return null
                s.toBigDecimalOrNull()
            }
        }
    }
}
