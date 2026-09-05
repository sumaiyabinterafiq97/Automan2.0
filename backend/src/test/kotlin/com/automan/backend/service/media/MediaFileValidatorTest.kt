package com.automan.backend.service.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaFileValidatorTest {
    @Test
    fun `sanitize chassis replaces unsafe characters`() {
        assertEquals("ABC_1234567", MediaFileValidator.sanitizeChassisForPath("ABC/1234567"))
    }

    @Test
    fun `rejects unsupported content type`() {
        val bytes = "%PDF-1.4".toByteArray()
        assertThrows<IllegalArgumentException> {
            MediaFileValidator.validate("application/pdf", bytes, 1024)
        }
    }

    @Test
    fun `accepts png magic bytes`() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        MediaFileValidator.validate("image/png", bytes, 1024)
        assertEquals("png", MediaFileValidator.extensionFor("image/png"))
    }

    @Test
    fun `accepts gif bmp tiff and heic magics`() {
        MediaFileValidator.validate("image/gif", "GIF89a".toByteArray() + byteArrayOf(0), 1024)
        assertEquals("gif", MediaFileValidator.extensionFor("image/gif"))

        MediaFileValidator.validate("image/bmp", byteArrayOf(0x42, 0x4D, 0, 0, 0, 0), 1024)
        assertEquals("bmp", MediaFileValidator.extensionFor("image/bmp"))

        MediaFileValidator.validate("image/tiff", byteArrayOf(0x49, 0x49, 0x2A, 0x00), 1024)
        assertEquals("tif", MediaFileValidator.extensionFor("image/tiff"))

        val heic = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            0x66, 0x74, 0x79, 0x70,
            0x68, 0x65, 0x69, 0x63,
        )
        MediaFileValidator.validate("image/heic", heic, 1024)
        assertEquals("heic", MediaFileValidator.extensionFor("image/heic"))
        assertEquals("heif", MediaFileValidator.extensionFor("image/heif"))
    }

    @Test
    fun `sniffs jpeg when declared type is missing or octet-stream`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals("image/jpeg", MediaFileValidator.sniffContentType(jpeg))
        assertEquals(
            "image/jpeg",
            MediaFileValidator.resolveAndValidate(null, jpeg, 1024),
        )
        assertEquals(
            "image/jpeg",
            MediaFileValidator.resolveAndValidate("application/octet-stream", jpeg, 1024),
        )
        assertEquals("jpg", MediaFileValidator.extensionFor("image/jpg"))
    }

    @Test
    fun `sniff returns null for pdf`() {
        assertNull(MediaFileValidator.sniffContentType("%PDF-1.4".toByteArray()))
    }
}
