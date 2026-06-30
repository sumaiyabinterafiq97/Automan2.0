package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseVehicleOverride
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseVehicleOverrideRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Phase 3 / 5: vehicle spec overrides vs [car_brand_mapping] baseline.
 * Write: purchase_vehicle_overrides JSON only (V53 dropped legacy spec columns).
 * Read adapter: override JSON → map baseline → legacy column fallback.
 */
@Service
class PurchaseVehicleOverrideService(
    private val purchaseVehicleOverrideRepository: PurchaseVehicleOverrideRepository,
    private val carBrandMappingRepository: CarBrandMappingRepository,
    private val objectMapper: ObjectMapper,
) {
    data class SpecFieldMapping(
        val jsonKey: String,
        val purchaseValue: (Purchase) -> String?,
        val mapValue: (CarBrandMapping) -> String?,
        val apply: (Purchase, String?) -> Purchase,
        val alwaysOverrideWhenSet: Boolean = false,
    )

    companion object {
        private val OVERRIDES_TYPE = object : TypeReference<Map<String, String>>() {}

        val SPEC_FIELD_MAPPINGS: List<SpecFieldMapping> = listOf(
            SpecFieldMapping("carModelYear", { it.carModelYear }, { it.carModelYear }, apply = { p, v -> p.copy(carModelYear = v) }),
            SpecFieldMapping("shipmentSize", { it.shipmentSize }, { it.vehicleType }, apply = { p, v -> p.copy(shipmentSize = v) }),
            SpecFieldMapping("grade", { it.grade }, { it.grade }, apply = { p, v -> p.copy(grade = v) }),
            SpecFieldMapping("rank", { it.rank }, { it.rank }, apply = { p, v -> p.copy(rank = v) }),
            SpecFieldMapping("color", { it.color }, { it.color }, apply = { p, v -> p.copy(color = v) }),
            SpecFieldMapping("fuel", { it.fuel }, { it.fuel }, apply = { p, v -> p.copy(fuel = v) }),
            SpecFieldMapping("seat", { it.seat }, { it.seat }, apply = { p, v -> p.copy(seat = v) }),
            SpecFieldMapping("door", { it.door }, { it.door }, apply = { p, v -> p.copy(door = v) }),
            SpecFieldMapping(
                "distance",
                { it.distance },
                { null },
                apply = { p, v -> p.copy(distance = v) },
                alwaysOverrideWhenSet = true,
            ),
            SpecFieldMapping(
                "cc",
                { it.cc?.toString() },
                { it.cc },
                apply = { p, v -> p.copy(cc = v?.toIntOrNull()) },
            ),
            SpecFieldMapping("shift", { it.shift }, { it.shift }, apply = { p, v -> p.copy(shift = v) }),
            SpecFieldMapping("wd", { it.wd }, { it.wd }, apply = { p, v -> p.copy(wd = v) }),
            SpecFieldMapping("driveType", { it.driveType }, { it.driveType }, apply = { p, v -> p.copy(driveType = v) }),
        )

        fun normalize(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            return trimmed.replace(Regex("\\s+"), " ").lowercase()
        }

        fun firstSemicolonToken(raw: String?): String {
            val s = raw?.trim().orEmpty()
            if (s.isEmpty()) return ""
            return s.split(";").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: s
        }
    }

    @Transactional(readOnly = true)
    fun resolveMapBaseline(purchase: Purchase): CarBrandMapping? {
        val chassis = purchase.chassis.trim()
        if (chassis.isEmpty()) return null

        var candidates = carBrandMappingRepository.findByChassis(chassis)
        if (candidates.isEmpty()) {
            candidates = carBrandMappingRepository.findBestPrefixMatchForChassis(chassis)
        }
        if (candidates.isEmpty()) return null

        purchase.brand?.trim()?.takeIf { it.isNotEmpty() }?.let { brand ->
            candidates.firstOrNull { it.carBrand.equals(brand, ignoreCase = true) }?.let { return it }
        }
        purchase.carName?.trim()?.takeIf { it.isNotEmpty() }?.let { carName ->
            candidates.firstOrNull { it.carName.equals(carName, ignoreCase = true) }?.let { return it }
        }
        return candidates.first()
    }

    @Transactional
    fun syncFromPurchase(purchase: Purchase) {
        val purchaseId = purchase.id ?: return
        val baseline = resolveMapBaseline(purchase)
        val overrides = linkedMapOf<String, String>()

        for (mapping in SPEC_FIELD_MAPPINGS) {
            val actual = mapping.purchaseValue(purchase)?.trim().orEmpty()
            val baselineRaw = baseline?.let { mapping.mapValue(it) }
            val baselineNorm = normalize(firstSemicolonToken(baselineRaw))

            when {
                mapping.alwaysOverrideWhenSet && actual.isNotEmpty() ->
                    overrides[mapping.jsonKey] = actual
                actual.isEmpty() -> Unit
                mapping.jsonKey == "cc" -> {
                    val actualNorm = normalize(firstSemicolonToken(actual))
                    if (baseline == null || actualNorm != baselineNorm) {
                        overrides[mapping.jsonKey] = actual
                    }
                }
                baseline == null || normalize(actual) != baselineNorm ->
                    overrides[mapping.jsonKey] = actual
            }
        }

        val existing = purchaseVehicleOverrideRepository.findByPurchaseId(purchaseId)
        val now = LocalDateTime.now()
        if (overrides.isEmpty()) {
            if (existing != null) {
                purchaseVehicleOverrideRepository.delete(existing)
            }
            return
        }

        val json = objectMapper.writeValueAsString(overrides)
        purchaseVehicleOverrideRepository.save(
            existing?.copy(overridesJson = json, updatedAt = now)
                ?: PurchaseVehicleOverride(
                    purchaseId = purchaseId,
                    overridesJson = json,
                    createdAt = now,
                    updatedAt = now,
                ),
        )
    }

    @Transactional(readOnly = true)
    fun applyForRead(purchase: Purchase): Purchase {
        var merged = applyMapBaselineForRead(purchase)
        val purchaseId = purchase.id ?: return merged
        val stored = purchaseVehicleOverrideRepository.findByPurchaseId(purchaseId) ?: return merged
        val overrides = parseOverrides(stored.overridesJson)
        if (overrides.isEmpty()) return merged

        for (mapping in SPEC_FIELD_MAPPINGS) {
            val overrideValue = overrides[mapping.jsonKey] ?: continue
            merged = mapping.apply(merged, overrideValue.trim().ifBlank { null })
        }
        return merged
    }

    /** Fills empty spec fields from [car_brand_mapping] baseline (lowest read priority). */
    private fun applyMapBaselineForRead(purchase: Purchase): Purchase {
        val baseline = resolveMapBaseline(purchase) ?: return purchase
        var merged = purchase
        for (mapping in SPEC_FIELD_MAPPINGS) {
            val current = mapping.purchaseValue(merged)?.trim().orEmpty()
            if (current.isNotEmpty()) continue
            val baselineRaw = mapping.mapValue(baseline)?.trim()?.ifBlank { null } ?: continue
            merged = mapping.apply(merged, firstSemicolonToken(baselineRaw))
        }
        return merged
    }

    private fun parseOverrides(json: String): Map<String, String> =
        try {
            val parsed = objectMapper.readValue(json, OVERRIDES_TYPE)
            parsed.filterValues { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
}
