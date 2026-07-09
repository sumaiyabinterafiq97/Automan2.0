package com.automan.backend.service

import com.automan.backend.dto.CarPictureMigrationResultDto
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseMedia
import com.automan.backend.repository.PurchaseMediaRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.service.media.MediaFileValidator
import com.automan.backend.service.media.MediaStorageService
import com.automan.backend.util.Logger
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

@Service
class CarPictureMigrationService(
    private val purchaseRepository: PurchaseRepository,
    private val purchaseMediaRepository: PurchaseMediaRepository,
    private val purchaseMediaService: PurchaseMediaService,
    private val purchaseExtendedAttributesService: PurchaseExtendedAttributesService,
    private val mediaStorageProvider: ObjectProvider<MediaStorageService>,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun migrateLegacyCarPictures(batchSize: Int = 50, dryRun: Boolean = true): CarPictureMigrationResultDto {
        if (!purchaseMediaService.isEnabled()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "R2 media storage is not enabled")
        }
        val storage = mediaStorageProvider.ifAvailable
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "R2 media storage is not configured")

        val purchases = purchaseRepository.findAll()
            .map { purchaseExtendedAttributesService.applyForRead(it) }
            .filter { hasLegacyPictures(it) }
            .filter { purchaseMediaRepository.countByPurchaseIdAndDeletedAtIsNull(it.id ?: -1) == 0L }
            .take(batchSize.coerceIn(1, 200))

        var uploadedFiles = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (purchase in purchases) {
            val purchaseId = purchase.id ?: continue
            try {
                val pictures = parseLegacyPictures(purchase.carPictures)
                if (pictures.isEmpty()) {
                    skipped++
                    continue
                }
                if (dryRun) {
                    uploadedFiles += pictures.size
                    continue
                }
                var sortOrder = 0
                for (picture in pictures) {
                    val decoded = decodeDataUrl(picture.data) ?: continue
                    MediaFileValidator.validate(decoded.contentType, decoded.bytes, 5L * 1024L * 1024L)
                    val ext = MediaFileValidator.extensionFor(decoded.contentType)
                    val fileKey = "purchases/${MediaFileValidator.sanitizeChassisForPath(purchase.chassis)}/${UUID.randomUUID()}.$ext"
                    storage.upload(fileKey, decoded.contentType, ByteArrayInputStream(decoded.bytes), decoded.bytes.size.toLong())
                    purchaseMediaRepository.save(
                        PurchaseMedia(
                            purchaseId = purchaseId,
                            chassis = purchase.chassis,
                            fileKey = fileKey,
                            originalName = picture.id,
                            contentType = decoded.contentType,
                            fileSize = decoded.bytes.size,
                            sortOrder = sortOrder++,
                            createdBy = "migration",
                        ),
                    )
                    uploadedFiles++
                }
                clearLegacyCarPictures(purchase)
            } catch (e: Exception) {
                failed++
                val msg = "purchaseId=$purchaseId chassis=${purchase.chassis}: ${e.message}"
                errors.add(msg)
                Logger.error("CarPictureMigrationService: %s", msg)
            }
        }

        return CarPictureMigrationResultDto(
            dryRun = dryRun,
            processedPurchases = purchases.size,
            uploadedFiles = uploadedFiles,
            skippedPurchases = skipped,
            failedPurchases = failed,
            errors = errors,
        )
    }

    private fun hasLegacyPictures(purchase: Purchase): Boolean {
        val raw = purchase.carPictures?.trim().orEmpty()
        return raw.isNotEmpty() && raw != "[]" && !raw.equals("null", ignoreCase = true)
    }

    private fun parseLegacyPictures(raw: String?): List<LegacyPicture> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "[]") return emptyList()
        val parsed: Any = runCatching { objectMapper.readValue(value, Any::class.java) }.getOrElse {
            runCatching { objectMapper.readValue(objectMapper.readValue(value, String::class.java), Any::class.java) }
                .getOrNull() ?: return emptyList()
        }
        val listType = object : TypeReference<List<Map<String, Any?>>>() {}
        val items: List<Map<String, Any?>> = when (parsed) {
            is List<*> -> objectMapper.convertValue(parsed, listType)
            else -> return emptyList()
        }
        return items.mapNotNull { item ->
            val data = item["data"]?.toString()?.trim().orEmpty()
            if (data.isEmpty()) return@mapNotNull null
            LegacyPicture(
                id = item["id"]?.toString(),
                data = data,
            )
        }
    }

    private fun decodeDataUrl(dataUrl: String): DecodedImage? {
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return null
        val meta = dataUrl.substring(0, comma)
        val base64 = dataUrl.substring(comma + 1)
        val contentType = meta.substringAfter("data:").substringBefore(";").trim().lowercase()
        if (contentType.isEmpty()) return null
        val bytes = Base64.getDecoder().decode(base64)
        return DecodedImage(contentType, bytes)
    }

    private fun clearLegacyCarPictures(purchase: Purchase) {
        val purchaseId = purchase.id ?: return
        val cleared = purchase.copy(carPictures = null)
        purchaseRepository.save(
            purchaseExtendedAttributesService.syncFromPurchase(cleared),
        )
        Logger.debug("CarPictureMigrationService cleared legacy carPictures for purchaseId=%d", purchaseId)
    }

    private data class LegacyPicture(val id: String?, val data: String)
    private data class DecodedImage(val contentType: String, val bytes: ByteArray)
}
