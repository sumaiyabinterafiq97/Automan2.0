package com.automan.backend.service

import com.automan.backend.repository.UserRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PurchaseListViewPrefs(
    val sortField: String? = null,
    val sortOrder: String? = null,
    val sortOrderByField: Map<String, String>? = null,
    val columns: List<String>? = null,
)

data class UserViewsDto(
    val purchaseList: PurchaseListViewPrefs? = null,
)

@Service
class UserViewsService(
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val SORT_FIELD_RE = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,63}$")
        private val KNOWN_COLUMN_KEYS = setOf(
            "date", "chassis", "carModelYear", "manufactureYear", "brand", "carName", "shipmentSize",
            "grade", "rank", "color", "fuel", "seat", "door", "distance", "options", "cc", "shift", "wd",
            "driveType", "auctionNo", "auctionHouse", "stockLocation", "pol", "pod", "rixoCompany",
            "venueId", "clientName", "consignee", "clientId", "country", "price", "auctionFee",
            "auctionPenaltyFee", "recycleFee", "roadTax", "taxTotal", "totalPrice", "paymentDate",
            "rixoRequested", "rixoConfirmed", "rixoPrice", "notes", "shipmentDate", "blNo", "vessel",
            "bookingRequested", "invoiceConfirmed", "shipmentCharges", "freight", "storageCharges",
            "miscCharges", "inspectionFee", "commission", "numberCut", "shaken", "negotiate", "local",
            "repairCompany", "repairCharges", "profit", "isPackageMode", "bookingId", "carPictures",
            "destination",
        )
    }

    @Transactional(readOnly = true)
    fun getViews(userId: Long): UserViewsDto {
        val user = userRepository.findById(userId).orElse(null)
            ?: throw NoSuchElementException("User not found")
        return parseViews(user.views)
    }

    /**
     * Deep-merge [incoming] into existing views JSON (only known top-level keys).
     * Returns the saved views DTO.
     */
    @Transactional
    fun mergeViews(userId: Long, incoming: UserViewsDto): UserViewsDto {
        val user = userRepository.findById(userId).orElse(null)
            ?: throw NoSuchElementException("User not found")
        val root = parseToObjectNode(user.views)
        incoming.purchaseList?.let { pl ->
            val plNode = if (root.has("purchaseList") && root.get("purchaseList").isObject) {
                (root.get("purchaseList") as ObjectNode).deepCopy()
            } else {
                objectMapper.createObjectNode()
            }
            pl.sortField?.let { f -> sanitizeSortField(f)?.let { plNode.put("sortField", it) } }
            pl.sortOrder?.let { plNode.put("sortOrder", sanitizeSortOrder(it)) }
            pl.sortOrderByField?.let { map ->
                val mapNode = objectMapper.createObjectNode()
                map.forEach { (k, v) ->
                    val field = sanitizeSortField(k) ?: return@forEach
                    mapNode.put(field, sanitizeSortOrder(v))
                }
                plNode.set<JsonNode>("sortOrderByField", mapNode)
            }
            pl.columns?.let { cols ->
                val arr = objectMapper.createArrayNode()
                sanitizeColumns(cols).forEach { arr.add(it) }
                plNode.set<JsonNode>("columns", arr)
            }
            root.set<JsonNode>("purchaseList", plNode)
        }
        val json = objectMapper.writeValueAsString(root)
        userRepository.save(user.copy(views = json))
        return parseViews(json)
    }

    private fun parseViews(raw: String?): UserViewsDto {
        if (raw.isNullOrBlank()) return UserViewsDto()
        return try {
            objectMapper.readValue(raw, UserViewsDto::class.java)
        } catch (_: Exception) {
            UserViewsDto()
        }
    }

    private fun parseToObjectNode(raw: String?): ObjectNode {
        if (raw.isNullOrBlank()) return objectMapper.createObjectNode()
        return try {
            val node = objectMapper.readTree(raw)
            if (node is ObjectNode) node else objectMapper.createObjectNode()
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
    }

    private fun sanitizeSortField(raw: String): String? {
        val t = raw.trim()
        if (!SORT_FIELD_RE.matches(t)) return null
        return t
    }

    private fun sanitizeSortOrder(raw: String): String =
        if (raw.trim().equals("asc", ignoreCase = true)) "asc" else "desc"

    private fun sanitizeColumns(cols: List<String>): List<String> {
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (c in cols) {
            val key = c.trim()
            if (key !in KNOWN_COLUMN_KEYS) continue
            if (seen.add(key)) out.add(key)
        }
        val rest = out.filter { it != "date" && it != "chassis" }
        return (listOf("date", "chassis") + rest).distinct().take(11)
    }
}
