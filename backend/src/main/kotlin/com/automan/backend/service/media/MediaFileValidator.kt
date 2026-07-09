package com.automan.backend.service.media

object MediaFileValidator {
    private val allowedContentTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
    )

    private val magicSignatures = listOf(
        "image/jpeg" to listOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())),
        "image/png" to listOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)),
        "image/gif" to listOf("GIF87a".toByteArray(), "GIF89a".toByteArray()),
        "image/webp" to listOf(byteArrayOf(0x52, 0x49, 0x46, 0x46)),
    )

    fun validate(contentType: String, bytes: ByteArray, maxBytes: Long) {
        val normalizedType = contentType.trim().lowercase()
        require(normalizedType in allowedContentTypes) {
            "Unsupported image type: $contentType"
        }
        require(bytes.isNotEmpty()) { "Empty file" }
        require(bytes.size <= maxBytes) {
            "File exceeds maximum size of ${maxBytes / (1024 * 1024)} MB"
        }
        require(matchesMagic(normalizedType, bytes)) {
            "File content does not match declared image type"
        }
        if (normalizedType == "image/webp") {
            require(bytes.size >= 12 && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
                "Invalid WebP file"
            }
        }
    }

    fun extensionFor(contentType: String): String = when (contentType.trim().lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "bin"
    }

    fun sanitizeChassisForPath(chassis: String): String =
        chassis.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "unknown" }
            .take(100)

    private fun matchesMagic(contentType: String, bytes: ByteArray): Boolean {
        val signatures = magicSignatures.firstOrNull { it.first == contentType }?.second ?: return false
        return signatures.any { sig -> bytes.size >= sig.size && bytes.copyOfRange(0, sig.size).contentEquals(sig) }
    }
}
