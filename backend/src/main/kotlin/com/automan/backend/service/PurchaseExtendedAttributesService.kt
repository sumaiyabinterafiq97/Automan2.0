package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Phase 4: extended_attributes JSON is canonical for cold fields (legacy columns dropped V51).
 * Read adapter maps JSON keys to flat API properties; write path persists JSON only.
 */
@Service
class PurchaseExtendedAttributesService(
    private val purchaseRepository: PurchaseRepository,
    private val objectMapper: ObjectMapper,
) {
    data class ExtendedFieldMapping(
        val jsonKey: String,
        val readString: (Purchase) -> String?,
        val applyString: (Purchase, String?) -> Purchase,
        val readBoolean: (Purchase) -> Boolean? = { null },
        val applyBoolean: (Purchase, Boolean?) -> Purchase = { p, _ -> p },
    )

    companion object {
        private val ATTRIBUTES_TYPE = object : TypeReference<Map<String, Any>>() {}

        val EXTENDED_FIELD_MAPPINGS: List<ExtendedFieldMapping> = listOf(
            ExtendedFieldMapping("options", { it.options }, { p, v -> p.copy(options = v) }),
            ExtendedFieldMapping("auctionNo", { it.auctionNo }, { p, v -> p.copy(auctionNo = v) }),
            ExtendedFieldMapping("paymentDate", { it.paymentDate }, { p, v -> p.copy(paymentDate = v) }),
            ExtendedFieldMapping("notes", { it.notes }, { p, v -> p.copy(notes = v) }),
            ExtendedFieldMapping("venueId", { it.venueId }, { p, v -> p.copy(venueId = v) }),
            ExtendedFieldMapping("numberCut", { it.numberCut }, { p, v -> p.copy(numberCut = v) }),
            ExtendedFieldMapping(
                "shaken",
                { null },
                { p, _ -> p },
                readBoolean = { it.shaken },
                applyBoolean = { p, v -> p.copy(shaken = v ?: false) },
            ),
            ExtendedFieldMapping(
                "negotiate",
                { null },
                { p, _ -> p },
                readBoolean = { it.negotiate },
                applyBoolean = { p, v -> p.copy(negotiate = v ?: false) },
            ),
            ExtendedFieldMapping(
                "isPackageMode",
                { null },
                { p, _ -> p },
                readBoolean = { it.isPackageMode },
                applyBoolean = { p, v -> p.copy(isPackageMode = v) },
            ),
            ExtendedFieldMapping("carPictures", { it.carPictures }, { p, v -> p.copy(carPictures = v) }),
        )

        fun isBlankString(value: String?): Boolean = value.isNullOrBlank()
    }

    @Transactional
    fun syncFromPurchase(purchase: Purchase): Purchase {
        val purchaseId = purchase.id ?: return purchase
        val attributes = buildAttributesMap(purchase)
        val json = if (attributes.isEmpty()) null else objectMapper.writeValueAsString(attributes)
        return purchaseRepository.save(purchase.copy(extendedAttributesJson = json))
    }

    @Transactional(readOnly = true)
    fun applyForRead(purchase: Purchase): Purchase {
        val raw = purchase.extendedAttributesJson
        if (raw.isNullOrBlank()) return purchase

        val attributes = parseAttributes(raw)
        if (attributes.isEmpty()) return purchase

        var merged = purchase
        for (mapping in EXTENDED_FIELD_MAPPINGS) {
            if (!attributes.containsKey(mapping.jsonKey)) continue
            val value = attributes[mapping.jsonKey]
            merged = when (mapping.jsonKey) {
                "shaken", "negotiate", "isPackageMode" ->
                    mapping.applyBoolean(merged, parseBoolean(value))
                else ->
                    mapping.applyString(merged, value?.toString()?.trim()?.ifBlank { null })
            }
        }
        return merged
    }

    private fun buildAttributesMap(purchase: Purchase): Map<String, Any> {
        val attributes = linkedMapOf<String, Any>()
        for (mapping in EXTENDED_FIELD_MAPPINGS) {
            when (mapping.jsonKey) {
                "shaken", "negotiate", "isPackageMode" -> {
                    mapping.readBoolean(purchase)?.let { attributes[mapping.jsonKey] = it }
                }
                else -> {
                    val value = mapping.readString(purchase)?.trim()
                    if (!value.isNullOrEmpty()) {
                        attributes[mapping.jsonKey] = value
                    }
                }
            }
        }
        return attributes
    }

    private fun parseAttributes(json: String): Map<String, Any> =
        try {
            objectMapper.readValue(json, ATTRIBUTES_TYPE).filterValues { it != null }
        } catch (_: Exception) {
            emptyMap()
        }

    private fun parseBoolean(value: Any?): Boolean? =
        when (value) {
            null -> null
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> {
                when (value.toString().trim().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> null
                }
            }
        }
}
