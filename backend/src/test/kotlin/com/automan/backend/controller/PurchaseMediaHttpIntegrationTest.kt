package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseMediaRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.service.media.MediaStorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * Purchase media HTTP against H2 with a mocked [MediaStorageService].
 * R2 credentials are dummy so storage is considered enabled; the mock never opens a network client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "automan.media.r2.enabled=true",
        "automan.media.r2.account-id=test-account",
        "automan.media.r2.access-key-id=test-key",
        "automan.media.r2.secret-access-key=test-secret",
        "automan.media.r2.bucket-name=test-bucket",
        "automan.media.r2.endpoint=https://example.invalid",
    ],
)
class PurchaseMediaHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var purchaseMediaRepository: PurchaseMediaRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @MockBean private lateinit var mediaStorage: MediaStorageService

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val purchaseIds = mutableListOf<Long>()

    @BeforeEach
    fun stubStorage() {
        `when`(
            mediaStorage.presignedGetUrl(
                anyString() ?: "",
                any(Duration::class.java) ?: Duration.ZERO,
            ),
        ).thenReturn("https://signed.test/object")
    }

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            val ids = purchaseIds.toSet()
            purchaseMediaRepository.findAll()
                .filter { it.purchaseId in ids }
                .forEach { purchaseMediaRepository.delete(it) }
            ids.forEach { id ->
                if (purchaseRepository.existsById(id)) purchaseRepository.deleteById(id)
            }
        }
        purchaseIds.clear()
    }

    @Test
    fun uploadStoresTheRowAndRejectsANonImage() {
        val purchase = savePurchase()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)

        mockMvc.perform(
            multipart("/purchases/${purchase.id}/media")
                .file(MockMultipartFile("file", "front.png", "image/png", png)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchaseId").value(purchase.id))
            .andExpect(jsonPath("$.chassis").value(purchase.chassis))
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.originalName").value("front.png"))
            .andExpect(jsonPath("$.sortOrder").value(0))
            .andExpect(jsonPath("$.url").value("https://signed.test/object"))

        val rows = purchaseMediaRepository.findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchase.id!!)
        assertEquals(1, rows.size)
        assertTrue(rows[0].fileKey.startsWith("purchases/"))
        assertTrue(rows[0].fileKey.endsWith(".png"))
        verify(mediaStorage).upload(
            eq(rows[0].fileKey) ?: "",
            eq("image/png") ?: "",
            any(InputStream::class.java) ?: java.io.ByteArrayInputStream(ByteArray(0)),
            eq(png.size.toLong()),
        )

        mockMvc.perform(get("/purchases/${purchase.id}/media/${rows[0].id}/url"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.url").value("https://signed.test/object"))

        val before = purchaseMediaRepository.count()
        mockMvc.perform(
            multipart("/purchases/${purchase.id}/media")
                .file(MockMultipartFile("file", "notes.pdf", "application/pdf", "%PDF-1.4".toByteArray())),
        )
            .andExpect(status().isBadRequest)
        assertEquals(before, purchaseMediaRepository.count())
        assertEquals(1, purchaseMediaRepository.findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchase.id!!).size)
    }

    @Test
    fun deleteRemovesOnlyThatMediaAndCannotDeleteAnotherPurchase() {
        val owner = savePurchase()
        val other = savePurchase()
        val kept = uploadPng(owner.id!!, "kept.png")
        val removed = uploadPng(owner.id!!, "removed.png")
        val foreign = uploadPng(other.id!!, "foreign.png")

        mockMvc.perform(delete("/purchases/${other.id}/media/${removed}"))
            .andExpect(status().isNotFound)
        assertNull(purchaseMediaRepository.findById(removed).orElseThrow().deletedAt)
        verify(mediaStorage, never()).delete(anyString() ?: "")

        mockMvc.perform(delete("/purchases/${owner.id}/media/$removed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertTrue(purchaseMediaRepository.findById(removed).orElseThrow().deletedAt != null)
        assertNull(purchaseMediaRepository.findById(kept).orElseThrow().deletedAt)
        assertNull(purchaseMediaRepository.findById(foreign).orElseThrow().deletedAt)
        val removedKey = purchaseMediaRepository.findById(removed).orElseThrow().fileKey
        verify(mediaStorage).delete(eq(removedKey) ?: "")
        verify(mediaStorage, never()).delete(eq(purchaseMediaRepository.findById(foreign).orElseThrow().fileKey) ?: "")
    }

    @Test
    fun reorderChangesOnlyTheRequestedPurchase() {
        val owner = savePurchase()
        val other = savePurchase()
        val first = uploadPng(owner.id!!, "one.png")
        val second = uploadPng(owner.id!!, "two.png")
        val untouched = uploadPng(other.id!!, "other.png")

        mockMvc.perform(
            put("/purchases/${owner.id}/media/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mediaIds":[$second,$first]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(second))
            .andExpect(jsonPath("$[0].sortOrder").value(0))
            .andExpect(jsonPath("$[1].id").value(first))
            .andExpect(jsonPath("$[1].sortOrder").value(1))

        val ordered = purchaseMediaRepository.findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner.id!!)
        assertEquals(listOf(second, first), ordered.map { it.id })
        assertEquals(0, purchaseMediaRepository.findById(untouched).orElseThrow().sortOrder)

        mockMvc.perform(
            put("/purchases/${owner.id}/media/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mediaIds":[$first]}"""),
        )
            .andExpect(status().isBadRequest)
        assertEquals(listOf(second, first), purchaseMediaRepository.findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner.id!!).map { it.id })
    }

    private fun uploadPng(purchaseId: Long, name: String): Long {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val body = mockMvc.perform(
            multipart("/purchases/$purchaseId/media")
                .file(MockMultipartFile("file", name, "image/png", png)),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toLong()
            ?: error("upload response had no id: $body")
        return id
    }

    private fun savePurchase(): Purchase {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = "P0MED-${UUID.randomUUID().toString().take(8)}-1",
                carName = "Media Car",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
        purchaseIds.add(saved.id!!)
        return saved
    }
}
