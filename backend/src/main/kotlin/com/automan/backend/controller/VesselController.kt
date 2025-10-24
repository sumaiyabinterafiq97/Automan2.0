package com.automan.backend.controller

import com.automan.backend.model.Vessel
import com.automan.backend.service.VesselService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vessels")
class VesselController(
    private val vesselService: VesselService
) {

    @GetMapping
    fun getAllVessels(): ResponseEntity<List<Vessel>> {
        return try {
            val vessels = vesselService.getAllVessels()
            ResponseEntity.ok(vessels)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/{vesselNo}")
    fun getVesselByNo(@PathVariable vesselNo: String): ResponseEntity<Vessel> {
        return try {
            val vessel = vesselService.getVesselByNo(vesselNo)
            if (vessel != null) {
                ResponseEntity.ok(vessel)
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping
    fun createVessel(@RequestBody vessel: Vessel): ResponseEntity<Vessel> {
        return try {
            val createdVessel = vesselService.createVessel(vessel.vesselNo, vessel.vesselName, vessel.company)
            ResponseEntity.status(HttpStatus.CREATED).body(createdVessel)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}
