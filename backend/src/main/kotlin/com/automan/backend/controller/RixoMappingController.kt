package com.automan.backend.controller

import com.automan.backend.service.RixoMappingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rixo-mapping")
class RixoMappingController(
    private val rixoMappingService: RixoMappingService
) {
    @GetMapping("/lookup")
    fun lookupRixoPrice(
        @RequestParam stockLocation: String,
        @RequestParam rixoCompany: String,
        @RequestParam(required = false) supportedVehicleType: String?
    ): ResponseEntity<Map<String, Any?>> {
        val match = rixoMappingService.findRixoPrice(
            stockLocation = stockLocation,
            rixoCompany = rixoCompany,
            supportedVehicleType = supportedVehicleType
        )

        return if (match == null) {
            ResponseEntity.ok(mapOf(
                "success" to false,
                "message" to "No rixo mapping found for these values",
                "data" to null
            ))
        } else {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to mapOf(
                    "rixoPrice" to match.rixoPrice
                )
            ))
        }
    }
}

