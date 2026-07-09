package com.automan.backend.controller

import com.automan.backend.dto.CarPictureMigrationResultDto
import com.automan.backend.service.CarPictureMigrationService
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/car-pictures")
@CrossOrigin(
    origins = [
        "http://localhost:8080",
        "http://localhost:8081",
        "http://localhost:8083",
        "http://localhost:8084",
        "http://localhost:8085",
        "http://localhost:8089",
        "http://localhost:8090",
        "http://localhost:9090",
    ],
)
class CarPictureMigrationController(
    private val carPictureMigrationService: CarPictureMigrationService,
) {
    @PostMapping("/migrate")
    fun migrate(
        @RequestParam(defaultValue = "50") batch: Int,
        @RequestParam(defaultValue = "true") dryRun: Boolean,
    ): CarPictureMigrationResultDto =
        carPictureMigrationService.migrateLegacyCarPictures(batchSize = batch, dryRun = dryRun)
}
