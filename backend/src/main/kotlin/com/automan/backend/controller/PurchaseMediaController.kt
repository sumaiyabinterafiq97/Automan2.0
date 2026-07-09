package com.automan.backend.controller

import com.automan.backend.dto.MediaConfigDto
import com.automan.backend.dto.PurchaseMediaDto
import com.automan.backend.dto.PurchaseMediaOrderRequest
import com.automan.backend.config.MediaStorageProperties
import com.automan.backend.service.PurchaseMediaService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/purchases/{purchaseId}/media")
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
class PurchaseMediaController(
    private val purchaseMediaService: PurchaseMediaService,
) {
    @GetMapping
    fun listMedia(@PathVariable purchaseId: Long): ResponseEntity<List<PurchaseMediaDto>> =
        ResponseEntity.ok(purchaseMediaService.listMedia(purchaseId))

    @PostMapping
    fun upload(
        @PathVariable purchaseId: Long,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<PurchaseMediaDto> =
        ResponseEntity.ok(purchaseMediaService.upload(purchaseId, file))

    @PutMapping("/order")
    fun reorder(
        @PathVariable purchaseId: Long,
        @RequestBody request: PurchaseMediaOrderRequest,
    ): ResponseEntity<List<PurchaseMediaDto>> =
        ResponseEntity.ok(purchaseMediaService.reorder(purchaseId, request.mediaIds))

    @DeleteMapping("/{mediaId}")
    fun delete(
        @PathVariable purchaseId: Long,
        @PathVariable mediaId: Long,
    ): ResponseEntity<Map<String, Boolean>> {
        purchaseMediaService.delete(purchaseId, mediaId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/{mediaId}/url")
    fun presignedUrl(
        @PathVariable purchaseId: Long,
        @PathVariable mediaId: Long,
    ): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("url" to purchaseMediaService.presignedUrl(purchaseId, mediaId)))
}

@RestController
@RequestMapping("/config")
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
class MediaConfigController(
    private val properties: MediaStorageProperties,
) {
    @GetMapping("/media")
    fun mediaConfig(): ResponseEntity<MediaConfigDto> =
        ResponseEntity.ok(
            MediaConfigDto(
                r2Enabled = properties.isConfigured(),
                maxFileSizeBytes = properties.maxFileSizeBytes,
                maxFilesPerPurchase = properties.maxFilesPerPurchase,
            ),
        )
}
