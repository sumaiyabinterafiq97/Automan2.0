package com.automan.backend.controller

import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.ShippingHistoryRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShippingHistoryExportIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var shippingHistoryRepository: ShippingHistoryRepository

    @BeforeEach
    fun setUp() {
        shippingHistoryRepository.deleteAll()
    }

    @Test
    fun `GET shipping-history export xlsx returns workbook with all rows`() {
        shippingHistoryRepository.save(
            ShippingHistory(
                country = "PAKISTAN",
                consignee = "OVERSEAS TRANSIT",
                shipmentDate = LocalDate.of(2025, 9, 27),
                pol = "YOKOHAMA",
                pod = "KARACHI-PAKISTAN",
                bookingId = "32",
                vessel = "comic 1",
                carrier = "MSC",
                priceType = "C&F",
                chassis = "SHP-EXP-001",
                clientName = "klkml",
                amount = BigDecimal("303264.00"),
            ),
        )
        shippingHistoryRepository.save(
            ShippingHistory(
                country = "PAKISTAN",
                shipmentDate = LocalDate.of(2025, 9, 27),
                bookingId = "1",
                vessel = "t",
                chassis = "SHP-EXP-002",
                clientName = "client-b",
                amount = BigDecimal("42500.00"),
            ),
        )

        val result = mockMvc.perform(get("/shipping-history/export/xlsx"))
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
            assertEquals(3, sheet.physicalNumberOfRows) // header + 2 rows
            assertEquals("ID", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("Chassis", sheet.getRow(0).getCell(11).stringCellValue)
            val chassisValues = (1..2).map { sheet.getRow(it).getCell(11).stringCellValue }.toSet()
            assertTrue(chassisValues.contains("SHP-EXP-001"))
            assertTrue(chassisValues.contains("SHP-EXP-002"))
        }
    }
}
