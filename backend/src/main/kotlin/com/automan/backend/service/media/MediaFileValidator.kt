package com.automan.backend.service.media

object MediaFileValidator {
    private val allowedContentTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/bmp",
        "image/tiff",
        "image/heic",
        "image/heif",
    )

    private val aliases = mapOf(
        "image/jpg" to "image/jpeg",
        "image/pjpeg" to "image/jpeg",
        "image/x-png" to "image/png",
        "image/x-bmp" to "image/bmp",
        "image/x-ms-bmp" to "image/bmp",
        "image/tiff-fx" to "image/tiff",
    )

    private val genericDeclaredTypes = setOf(
        "",
        "application/octet-stream",
        "application/octetstream",
        "binary/octet-stream",
        "image/*",
    )

    private val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val gif87 = "GIF87a".toByteArray()
    private val gif89 = "GIF89a".toByteArray()
    private val riff = byteArrayOf(0x52, 0x49, 0x46, 0x46)
    private val webpTag = "WEBP".toByteArray()
    private val bmpMagic = byteArrayOf(0x42, 0x4D)
    private val tiffLe = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
    private val tiffBe = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)
    private val ftyp = "ftyp".toByteArray()
    private val heifBrands = setOf(
        "heic", "heif", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1",
    )

    fun validate(contentType: String, bytes: ByteArray, maxBytes: Long) {
        resolveAndValidate(contentType, bytes, maxBytes)
    }

    fun resolveAndValidate(declaredContentType: String?, bytes: ByteArray, maxBytes: Long): String {
        require(bytes.isNotEmpty()) { "Empty file" }
        require(bytes.size <= maxBytes) {
            "File exceeds maximum size of ${maxBytes / (1024 * 1024)} MB"
        }
        val sniffed = sniffContentType(bytes)
        val declared = canonicalize(declaredContentType)
        val resolved = when {
            declared != null && matchesMagic(declared, bytes) -> declared
            sniffed != null && (declared == null || declared in genericDeclaredTypes || !matchesMagic(declared, bytes)) -> sniffed
            else -> null
        } ?: throw IllegalArgumentException(
            "Unsupported image type: ${declaredContentType ?: "unknown"}",
        )
        require(resolved in allowedContentTypes) {
            "Unsupported image type: $resolved"
        }
        if (resolved == "image/webp") {
            require(bytes.size >= 12 && bytes.copyOfRange(8, 12).contentEquals(webpTag)) {
                "Invalid WebP file"
            }
        }
        return resolved
    }

    fun sniffContentType(bytes: ByteArray): String? = when {
        startsWith(bytes, jpegMagic) -> "image/jpeg"
        startsWith(bytes, pngMagic) -> "image/png"
        startsWith(bytes, gif87) || startsWith(bytes, gif89) -> "image/gif"
        isWebp(bytes) -> "image/webp"
        startsWith(bytes, bmpMagic) -> "image/bmp"
        startsWith(bytes, tiffLe) || startsWith(bytes, tiffBe) -> "image/tiff"
        isHeif(bytes) -> if (majorBrand(bytes) == "heif") "image/heif" else "image/heic"
        else -> null
    }

    fun extensionFor(contentType: String): String = when (canonicalize(contentType)) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        "image/tiff" -> "tif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "bin"
    }

    fun sanitizeChassisForPath(chassis: String): String =
        chassis.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "unknown" }
            .take(100)

    private fun canonicalize(contentType: String?): String? {
        val normalized = contentType?.trim()?.lowercase() ?: return null
        if (normalized.isEmpty()) return null
        return aliases[normalized] ?: normalized
    }

    private fun matchesMagic(contentType: String, bytes: ByteArray): Boolean = when (contentType) {
        "image/jpeg" -> startsWith(bytes, jpegMagic)
        "image/png" -> startsWith(bytes, pngMagic)
        "image/gif" -> startsWith(bytes, gif87) || startsWith(bytes, gif89)
        "image/webp" -> isWebp(bytes)
        "image/bmp" -> startsWith(bytes, bmpMagic)
        "image/tiff" -> startsWith(bytes, tiffLe) || startsWith(bytes, tiffBe)
        "image/heic", "image/heif" -> isHeif(bytes)
        else -> false
    }

    private fun isWebp(bytes: ByteArray): Boolean =
        startsWith(bytes, riff) &&
            bytes.size >= 12 &&
            bytes.copyOfRange(8, 12).contentEquals(webpTag)

    private fun isHeif(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        if (!bytes.copyOfRange(4, 8).contentEquals(ftyp)) return false
        val brand = majorBrand(bytes) ?: return false
        if (brand in heifBrands) return true
        // Compatible brands follow at offset 16 in 4-byte slots.
        var offset = 16
        while (offset + 4 <= bytes.size && offset < 64) {
            val compat = bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            if (compat in heifBrands) return true
            offset += 4
        }
        return false
    }

    private fun majorBrand(bytes: ByteArray): String? =
        if (bytes.size >= 12) bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) else null

    private fun startsWith(bytes: ByteArray, signature: ByteArray): Boolean =
        bytes.size >= signature.size && bytes.copyOfRange(0, signature.size).contentEquals(signature)
}
