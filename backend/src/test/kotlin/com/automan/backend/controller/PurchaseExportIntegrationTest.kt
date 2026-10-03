package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            val header = sheet.getRow(0)
            val leading = listOf(
                "Date of purchase",
                "Auction no",
                "Chassis",
                "Registration year",
                "Car name",
                "Supplier name",
                "Transmission",
                "Color",
                "Grade",
                "Car price",
            )
            val titles = (0 until header.lastCellNum).map { header.getCell(it).stringCellValue }
            leading.forEachIndexed { index, title ->
                assertEquals(title, titles[index])
            }
            assertEquals("ID", titles[10])
            assertEquals("Registration Date", titles[11])
            listOf("Chassis", "Color", "Grade").forEach { title ->
                assertEquals(1, titles.count { it == title }, title)
            }
            listOf(
                "Purchase Date",
                "Car Name",
                "Shift",
                "Auction No",
                "Supplier Name",
                "Car Price",
            ).forEach { title ->
                assertFalse(titles.contains(title), title)
            }
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

    @Test
    fun `GET export returns every saved purchase when no filter is sent`() {
        savePurchase(chassis = "ALL-A", clientName = "Crown Eagle")
        savePurchase(chassis = "ALL-B", clientName = "LOGICO")

        assertEquals(setOf("ALL-A", "ALL-B"), chassisIn(exportGet()))
    }

    @Test
    fun `POST client chip returns only that client and ignores page size`() {
        savePurchase(chassis = "CLI-1", clientName = "Crown Eagle")
        savePurchase(chassis = "CLI-2", clientName = "Crown Eagle")
        savePurchase(chassis = "CLI-OTHER", clientName = "LOGICO")

        val body = """
            {"page":0,"size":1,"filters":[{"field":"clientName","operator":"contains","value":"Crown Eagle"}]}
        """.trimIndent()
        assertEquals(setOf("CLI-1", "CLI-2"), chassisIn(exportPost(body)))
    }

    @Test
    fun `POST date range returns only purchases inside that range`() {
        savePurchase(chassis = "DATE-IN", date = "September 20, 2026")
        savePurchase(chassis = "DATE-OUT", date = "August 1, 2026")

        val body = """{"dateFrom":"2026-09-15","dateTo":"2026-09-30","filters":[]}"""
        assertEquals(setOf("DATE-IN"), chassisIn(exportPost(body)))
    }

    @Test
    fun `POST search uses chassis prefix semantics`() {
        savePurchase(chassis = "AAHH40-12345")
        savePurchase(chassis = "OTHER-999")

        val body = """{"q":"AAHH40","field":"chassis","filters":[]}"""
        assertEquals(setOf("AAHH40-12345"), chassisIn(exportPost(body)))
    }

    @Test
    fun `POST client date and supplier returns only the intersection`() {
        savePurchase(
            chassis = "MIX-YES",
            clientName = "Crown Eagle",
            auctionHouse = "USS Tokyo",
            date = "September 20, 2026",
        )
        savePurchase(
            chassis = "MIX-OTHER-CLIENT",
            clientName = "LOGICO",
            auctionHouse = "USS Tokyo",
            date = "September 20, 2026",
        )
        savePurchase(
            chassis = "MIX-OTHER-DATE",
            clientName = "Crown Eagle",
            auctionHouse = "USS Tokyo",
            date = "August 1, 2026",
        )
        savePurchase(
            chassis = "MIX-OTHER-SUPPLIER",
            clientName = "Crown Eagle",
            auctionHouse = "TAA",
            date = "September 20, 2026",
        )

        val body = """
            {
              "dateFrom":"2026-09-01",
              "dateTo":"2026-09-30",
              "filters":[
                {"field":"clientName","operator":"contains","value":"Crown Eagle"},
                {"field":"auctionHouse","operator":"contains","value":"USS Tokyo"}
              ]
            }
        """.trimIndent()
        assertEquals(setOf("MIX-YES"), chassisIn(exportPost(body)))
    }

    @Test
    fun `POST with no matches returns an error and not the full dataset`() {
        savePurchase(chassis = "KEEP-1", clientName = "Crown Eagle")
        savePurchase(chassis = "KEEP-2", clientName = "LOGICO")

        val body = """{"filters":[{"field":"clientName","operator":"contains","value":"Nobody"}]}"""
        val result = mockMvc.perform(
            post("/purchases/export/xlsx")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andReturn()

        val text = result.response.contentAsString
        assertTrue(text.contains("No matching purchases to export"))
        assertFalse(text.contains("KEEP-1"))
        assertFalse(text.contains("KEEP-2"))
    }

    @Test
    fun `a later POST uses the new client and does not keep the previous one`() {
        savePurchase(chassis = "SWAP-A", clientName = "Crown Eagle")
        savePurchase(chassis = "SWAP-B", clientName = "LOGICO")

        val first = """{"filters":[{"field":"clientName","operator":"contains","value":"Crown Eagle"}]}"""
        val second = """{"filters":[{"field":"clientName","operator":"contains","value":"LOGICO"}]}"""
        assertEquals(setOf("SWAP-A"), chassisIn(exportPost(first)))
        assertEquals(setOf("SWAP-B"), chassisIn(exportPost(second)))
    }

    private fun savePurchase(
        chassis: String,
        clientName: String? = null,
        auctionHouse: String? = null,
        date: String? = null,
    ) {
        purchaseRepository.save(
            Purchase(
                chassis = chassis,
                brand = "TOYOTA",
                carName = "RAV4",
                country = "Japan",
                clientName = clientName,
                auctionHouse = auctionHouse,
                date = date,
            ),
        )
    }

    private fun exportGet(): ByteArray =
        mockMvc.perform(get("/purchases/export/xlsx"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsByteArray

    private fun exportPost(json: String): ByteArray =
        mockMvc.perform(
            post("/purchases/export/xlsx")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsByteArray

    private fun chassisIn(bytes: ByteArray): Set<String> {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val header = sheet.getRow(0)
            var chassisCol = -1
            for (i in 0 until header.lastCellNum) {
                if (header.getCell(i)?.stringCellValue == "Chassis") {
                    chassisCol = i
                    break
                }
            }
            assertTrue(chassisCol >= 0, "Chassis column missing")
            return (1..sheet.lastRowNum).mapNotNull { rowIdx ->
                sheet.getRow(rowIdx)?.getCell(chassisCol)?.stringCellValue
            }.toSet()
        }
    }
}
