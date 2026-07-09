package com.automan.backend.service.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MediaFileValidatorTest {
    @Test
    fun `sanitize chassis replaces unsafe characters`() {
        assertEquals("ABC_1234567", MediaFileValidator.sanitizeChassisForPath("ABC/1234567"))
    }

    @Test
    fun `rejects unsupported content type`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
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
}
