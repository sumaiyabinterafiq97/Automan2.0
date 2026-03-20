package com.automan.backend.service

import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.RixoMappingRepository
import org.springframework.stereotype.Service

@Service
class RixoMappingService(
    private val rixoMappingRepository: RixoMappingRepository
) {
    fun findRixoPrice(
        stockLocation: String,
        rixoCompany: String,
        supportedVehicleType: String?
    ): RixoMapping? {
        if (stockLocation.isBlank() || rixoCompany.isBlank()) return null

        val trimmedVehicleType = supportedVehicleType?.trim()?.takeIf { it.isNotBlank() }

        val matches = rixoMappingRepository.findTopMatch(
            stockLocation = stockLocation.trim(),
            rixoCompany = rixoCompany.trim(),
            supportedVehicleType = trimmedVehicleType
        )

        if (matches.isEmpty()) return null
        return matches.firstOrNull()
    }
}

