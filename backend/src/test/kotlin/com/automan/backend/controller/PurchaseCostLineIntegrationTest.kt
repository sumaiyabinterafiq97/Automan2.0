package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseCostLineRepository
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseCostLineIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var purchaseCostLineRepository: PurchaseCostLineRepository

    @BeforeEach
    fun setUp() {
        purchaseCostLineRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun `save-costs dual-writes purchase_cost_lines`() {
        val saved = purchaseRepository.save(basePurchase("COST-DW-1"))
        val id = saved.id!!

        mockMvc.perform(
            put("/purchases/save-costs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"chassis":"COST-DW-1","carPrice":100000,"auctionFee":5000,"auctionPenaltyFee":0,
                    |"rixoPrice":2000,"shippingCharge":3000,"freight":4000,"inspectionFee":500,
                    |"repairFee":600,"mscCharges":700,"profit":800,"isPackageMode":false}""".trimMargin(),
                ),
        )
            .andExpect(status().isOk)

        val lines = purchaseCostLineRepository.findByPurchaseIdOrderBySortOrderAsc(id)
        assert(lines.any { it.costCode == "PRICE" && it.amount.compareTo(BigDecimal("100000")) == 0 })
        assert(lines.any { it.costCode == "FREIGHT" && it.amount.compareTo(BigDecimal("4000")) == 0 })
        assert(lines.any { it.costCode == "PROFIT" && it.amount.compareTo(BigDecimal("800")) == 0 })
    }

    @Test
    fun `PUT keeps a displayed Rixo Price instead of deleting the cost line`() {
        val saved = purchaseRepository.save(basePurchase("RIXO-KEEP-6000"))
        purchaseCostLineRepository.save(
            com.automan.backend.model.PurchaseCostLine(
                purchaseId = saved.id!!,
                costCode = "RIXO_PRICE",
                amount = BigDecimal("6000.00"),
                sortOrder = 14,
            ),
        )

        mockMvc.perform(
            put("/purchases/${saved.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"RIXO-KEEP-6000","rixoPrice":"¥6000"}"""),
        ).andExpect(status().isOk)

        val afterFormSave = purchaseCostLineRepository.findByPurchaseIdOrderBySortOrderAsc(saved.id!!)
            .first { it.costCode == "RIXO_PRICE" }
        assert(afterFormSave.amount.compareTo(BigDecimal("6000")) == 0)

        mockMvc.perform(
            put("/purchases/${saved.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"RIXO-KEEP-6000","rixoPrice":"￥6,000"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rixoPrice").value("6000"))
    }

    @Test
    fun `GET page and get-by-id include rixoPrice from the RIXO_PRICE cost line`() {
        val saved = purchaseRepository.save(basePurchase("RIXO-PAGE-6000"))
        purchaseCostLineRepository.save(
            com.automan.backend.model.PurchaseCostLine(
                purchaseId = saved.id!!,
                costCode = "RIXO_PRICE",
                amount = BigDecimal("6000.00"),
                sortOrder = 14,
            ),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rixoPrice").value("6000"))

        mockMvc.perform(get("/purchases/page").param("page", "0").param("size", "50"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.chassis == 'RIXO-PAGE-6000')].rixoPrice").value("6000"))
    }

    @Test
    fun `GET costs-by-chassis returns values from cost lines`() {
        val saved = purchaseRepository.save(
            basePurchase("COST-READ-1").copy(
                price = "1",
                freight = "1",
            ),
        )
        purchaseCostLineRepository.saveAll(
            listOf(
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = saved.id!!,
                    costCode = "PRICE",
                    amount = BigDecimal("75000"),
                    sortOrder = 1,
                ),
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = saved.id!!,
                    costCode = "FREIGHT",
                    amount = BigDecimal("1200"),
                    sortOrder = 9,
                ),
            ),
        )

        mockMvc.perform(get("/purchases/costs-by-chassis/COST-READ-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.carPrice").value(75000))
            .andExpect(jsonPath("$.freight").value(1200))
            .andExpect(jsonPath("$.chassis").value("COST-READ-1"))
    }

    @Test
    fun `GET costs-by-chassis returns zero when no cost lines`() {
        purchaseRepository.save(basePurchase("COST-FB-1"))

        mockMvc.perform(get("/purchases/costs-by-chassis/COST-FB-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.carPrice").value(0))
            .andExpect(jsonPath("$.shippingCharge").value(0))
            .andExpect(jsonPath("$.profit").value(0))
    }

    @Test
    fun `GET purchase by id returns cost fields from cost lines`() {
        val saved = purchaseRepository.save(basePurchase("COST-GET-1"))
        purchaseCostLineRepository.saveAll(
            listOf(
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = saved.id!!,
                    costCode = "PRICE",
                    amount = BigDecimal("88000"),
                    sortOrder = 1,
                ),
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = saved.id!!,
                    costCode = "AUCTION_FEE",
                    amount = BigDecimal("3300"),
                    sortOrder = 2,
                ),
            ),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.price").value("88000"))
            .andExpect(jsonPath("$.auctionFee").value("3300"))
            .andExpect(jsonPath("$.totalPrice").value("11000.00"))
    }

    @Test
    fun `PUT purchases id dual-writes cost lines on update without duplicate key`() {
        val saved = purchaseRepository.save(
            basePurchase("PUT-DW-1").copy(price = "100", auctionFee = "50"),
        )
        purchaseCostLineRepository.saveAll(
            listOf(
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = saved.id!!,
                    costCode = "PRICE",
                    amount = BigDecimal("100"),
                    sortOrder = 1,
                ),
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = saved.id!!,
                    costCode = "AUCTION_FEE",
                    amount = BigDecimal("50"),
                    sortOrder = 2,
                ),
            ),
        )

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/purchases/${saved.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"price":"¥999","auctionFee":"¥888","chassis":"PUT-DW-1"}"""),
        )
            .andExpect(status().isOk)

        val lines = purchaseCostLineRepository.findByPurchaseIdOrderBySortOrderAsc(saved.id!!)
        assert(lines.count { it.costCode == "PRICE" } == 1)
        assert(lines.first { it.costCode == "PRICE" }.amount.compareTo(BigDecimal("999")) == 0)
    }

    private fun basePurchase(chassis: String): Purchase = Purchase(
        chassis = chassis,
        carName = "Test Car",
        carModelYear = "2020",
        brand = "Toyota",
        country = "Japan",
        color = "White",
        fuel = "Gasoline",
        price = "10000.00",
        auctionFee = "500.00",
        recycleFee = "200.00",
        roadTax = "300.00",
        totalPrice = "11000.00",
        paymentDate = LocalDate.now().toString(),
        rixoRequested = "0",
        rixoConfirmed = "1",
        rixoPrice = "0.00",
        shipmentDate = LocalDate.now().toString(),
        vessel = "V1",
        shipmentCharges = "0",
        freight = "0",
        storageCharges = "0",
        miscCharges = "0",
        inspectionFee = "0",
        commission = "0",
        repairCompany = "",
        repairCharges = "0",
        notes = "",
        numberCut = "0",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        bookingRequested = false,
    )
}
