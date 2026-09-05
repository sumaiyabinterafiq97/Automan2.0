package com.automan.backend.service

import com.automan.backend.config.MediaStorageProperties
import com.automan.backend.dto.PurchaseMediaDto
import com.automan.backend.model.PurchaseMedia
import com.automan.backend.repository.PurchaseMediaRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.service.media.MediaFileValidator
import com.automan.backend.service.media.MediaStorageService
import com.automan.backend.util.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PurchaseMediaService(
    private val purchaseRepository: PurchaseRepository,
    private val purchaseMediaRepository: PurchaseMediaRepository,
    private val properties: MediaStorageProperties,
    private val mediaStorageProvider: ObjectProvider<MediaStorageService>,
) {
    fun isEnabled(): Boolean = properties.isConfigured()

    fun listMedia(purchaseId: Long, includeUrls: Boolean = true): List<PurchaseMediaDto> {
        ensurePurchaseExists(purchaseId)
        val rows = purchaseMediaRepository.findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchaseId)
        return rows.map { toDto(it, includeUrls) }
    }

    @Transactional
    fun upload(purchaseId: Long, file: MultipartFile, createdBy: String? = null): PurchaseMediaDto {
        val storage = requireStorage()
        val purchase = purchaseRepository.findById(purchaseId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found")
        }
        val existingCount = purchaseMediaRepository.countByPurchaseIdAndDeletedAtIsNull(purchaseId)
        if (existingCount >= properties.maxFilesPerPurchase) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Maximum ${properties.maxFilesPerPurchase} images per purchase",
            )
        }

        val bytes = file.bytes
        val contentType = try {
            MediaFileValidator.resolveAndValidate(file.contentType, bytes, properties.maxFileSizeBytes)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }

        val chassis = purchase.chassis
        val ext = MediaFileValidator.extensionFor(contentType)
        val fileKey = "purchases/${MediaFileValidator.sanitizeChassisForPath(chassis)}/${UUID.randomUUID()}.$ext"
        storage.upload(fileKey, contentType, ByteArrayInputStream(bytes), bytes.size.toLong())

        val sortOrder = existingCount.toInt()
        val saved = purchaseMediaRepository.save(
            PurchaseMedia(
                purchaseId = purchaseId,
                chassis = chassis,
                fileKey = fileKey,
                originalName = file.originalFilename?.take(255),
                contentType = contentType,
                fileSize = bytes.size,
                sortOrder = sortOrder,
                createdBy = createdBy,
            ),
        )
        Logger.debug("PurchaseMediaService.upload purchaseId=%d fileKey=%s", purchaseId, fileKey)
        return toDto(saved, includeUrls = true)
    }

    @Transactional
    fun reorder(purchaseId: Long, mediaIds: List<Long>): List<PurchaseMediaDto> {
        ensurePurchaseExists(purchaseId)
        val rows = purchaseMediaRepository.findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchaseId)
        val byId = rows.associateBy { it.id }
        val ordered = mediaIds.mapIndexedNotNull { index, id ->
            byId[id]?.copy(sortOrder = index)
        }
        if (ordered.size != rows.size || ordered.size != mediaIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media order payload")
        }
        return purchaseMediaRepository.saveAll(ordered).map { toDto(it, includeUrls = true) }
    }

    @Transactional
    fun delete(purchaseId: Long, mediaId: Long) {
        val storage = requireStorage()
        val row = purchaseMediaRepository.findByIdAndPurchaseIdAndDeletedAtIsNull(mediaId, purchaseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found")
        try {
            storage.delete(row.fileKey)
        } catch (e: Exception) {
            Logger.error("PurchaseMediaService.delete R2 delete failed for %s: %s", row.fileKey, e.message)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to delete file from storage")
        }
        purchaseMediaRepository.save(row.copy(deletedAt = LocalDateTime.now()))
    }

    fun presignedUrl(purchaseId: Long, mediaId: Long): String {
        val storage = requireStorage()
        val row = purchaseMediaRepository.findByIdAndPurchaseIdAndDeletedAtIsNull(mediaId, purchaseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found")
        return storage.presignedGetUrl(row.fileKey, Duration.ofSeconds(properties.presignedUrlTtlSeconds))
    }

    fun mediaCount(purchaseId: Long): Long =
        purchaseMediaRepository.countByPurchaseIdAndDeletedAtIsNull(purchaseId)

    private fun ensurePurchaseExists(purchaseId: Long) {
        if (!purchaseRepository.existsById(purchaseId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found")
        }
    }

    private fun requireStorage(): MediaStorageService {
        if (!isEnabled()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Purchase media storage is not enabled")
        }
        return mediaStorageProvider.ifAvailable
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Purchase media storage is not configured")
    }

    private fun toDto(row: PurchaseMedia, includeUrls: Boolean): PurchaseMediaDto {
        val url = if (includeUrls && isEnabled()) {
            runCatching {
                mediaStorageProvider.ifAvailable?.presignedGetUrl(
                    row.fileKey,
                    Duration.ofSeconds(properties.presignedUrlTtlSeconds),
                )
            }.getOrNull()
        } else {
            null
        }
        return PurchaseMediaDto(
            id = row.id ?: 0L,
            purchaseId = row.purchaseId,
            chassis = row.chassis,
            originalName = row.originalName,
            contentType = row.contentType,
            fileSize = row.fileSize,
            sortOrder = row.sortOrder,
            url = url,
            createdAt = row.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )
    }
}
