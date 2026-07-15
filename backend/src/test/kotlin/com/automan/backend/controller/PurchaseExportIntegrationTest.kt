package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseExportIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `GET purchases export xlsx returns workbook with all rows`() {
        purchaseRepository.save(
            Purchase(
                chassis = "EXP-001",
                brand = "TOYOTA",
                carName = "RAV4",
                country = "Japan",
                totalPrice = "1500000",
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "EXP-002",
                brand = "HONDA",
                carName = "CIVIC",
                country = "Japan",
                totalPrice = "900000",
            ),
        )

        val result = mockMvc.perform(get("/purchases/export/xlsx"))
            .andExpect(status().isOk)
            .andExpect(
                header().string(
                    "Content-Type",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ),
            )
            .andExpect(header().exists("Content-Disposition"))
            .andReturn()

        val bytes = result.response.contentAsByteArray
        assertTrue(bytes.isNotEmpty())

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals(3, sheet.physicalNumberOfRows) // header + 2 purchases
            assertEquals("ID", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("Chassis", sheet.getRow(0).getCell(2).stringCellValue)
        }
    }

    @Test
    fun `GET purchases export xlsx truncates oversized string cells instead of 500`() {
        val oversizedPictures = "x".repeat(40_000)
        purchaseRepository.save(
            Purchase(
                chassis = "EXP-LONG",
                brand = "NISSAN",
                carName = "NOTE",
                country = "Japan",
                totalPrice = "100000",
                extendedAttributesJson = """{"carPictures":"$oversizedPictures"}""",
            ),
        )

        val result = mockMvc.perform(get("/purchases/export/xlsx"))
            .andExpect(status().isOk)
            .andReturn()

        val bytes = result.response.contentAsByteArray
        assertTrue(bytes.isNotEmpty())

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals(2, sheet.physicalNumberOfRows) // header + 1 purchase
            val header = sheet.getRow(0)
            var carPicturesCol = -1
            for (i in 0 until header.lastCellNum) {
                if (header.getCell(i)?.stringCellValue == "Car Pictures") {
                    carPicturesCol = i
                    break
                }
            }
            assertTrue(carPicturesCol >= 0, "Car Pictures column missing")
            val cellValue = sheet.getRow(1).getCell(carPicturesCol).stringCellValue
            assertTrue(cellValue.length <= 32767)
            assertTrue(cellValue.endsWith("…[truncated]"))
        }
    }
}
