package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

class ShippingSnapshotServiceTest {

    private val repository = mock(ShippingHistoryRepository::class.java)
    private val service = ShippingSnapshotService(repository)

    @Test
    fun applyForRead_prefers_shipping_history_vessel_and_date() {
        val snapshot = ShippingHistory(
            id = 1L,
            chassis = "SHP-1",
            vessel = "CANONICAL-VESSEL",
            shipmentDate = LocalDate.parse("2026-07-15"),
            amount = BigDecimal.ZERO,
        )
        val purchase = Purchase(id = 10L, chassis = "SHP-1")

        val merged = service.applyForRead(purchase, snapshot)

        assertEquals("CANONICAL-VESSEL", merged.vessel)
        assertEquals("2026-07-15", merged.shipmentDate)
    }

    @Test
    fun applyForRead_prefers_shipping_history_blNo() {
        val snapshot = ShippingHistory(
            id = 1L,
            chassis = "SHP-1",
            vessel = "CANONICAL-VESSEL",
            shipmentDate = LocalDate.parse("2026-07-15"),
            blNo = "BL-FROM-SH",
            amount = BigDecimal.ZERO,
        )
        val purchase = Purchase(id = 10L, chassis = "SHP-1", blNo = "legacy-bl")

        val merged = service.applyForRead(purchase, snapshot)

        assertEquals("BL-FROM-SH", merged.blNo)
    }

    @Test
    fun applyForRead_returns_purchase_unchanged_when_no_snapshot() {
        val purchase = Purchase(id = 11L, chassis = "NO-SH", vessel = "only-purchase")
        assertEquals(purchase, service.applyForRead(purchase, null))
    }

    @Test
    fun resolveSnapshot_loads_by_chassis() {
        val snapshot = ShippingHistory(id = 2L, chassis = "X", amount = BigDecimal.ZERO)
        `when`(repository.findFirstByChassisOrderByIdDesc("X")).thenReturn(snapshot)
        assertEquals(snapshot, service.resolveSnapshot("X"))
    }

    @Test
    fun applyForReadBatch_maps_multiple_chassis() {
        val snapshots = listOf(
            ShippingHistory(id = 1L, chassis = "A", vessel = "V-A", amount = BigDecimal.ZERO),
            ShippingHistory(id = 2L, chassis = "B", vessel = "V-B", amount = BigDecimal.ZERO),
        )
        `when`(repository.findByChassisIn(listOf("A", "B"))).thenReturn(snapshots)

        val merged = service.applyForReadBatch(
            listOf(
                Purchase(id = 1L, chassis = "A", vessel = "old-a"),
                Purchase(id = 2L, chassis = "B", vessel = "old-b"),
            ),
        )

        assertEquals("V-A", merged[0].vessel)
        assertEquals("V-B", merged[1].vessel)
    }

    @Test
    fun resolveSnapshot_returns_null_for_blank_chassis() {
        assertNull(service.resolveSnapshot("  "))
    }

    @Test
    fun syncFromPurchase_persists_weekday_shipment_date_label() {
        `when`(repository.findFirstByChassisOrderByIdDesc("CH-1")).thenReturn(null)

        service.syncFromPurchase(
            Purchase(
                id = 1L,
                chassis = "CH-1",
                blNo = "BL-1",
                vessel = "VESSEL-1",
                shipmentDate = "June 22, 2026 (Monday)",
            ),
        )

        verify(repository).save(
            argThat { row: ShippingHistory ->
                row.chassis == "CH-1" &&
                    row.blNo == "BL-1" &&
                    row.vessel == "VESSEL-1" &&
                    row.shipmentDate == LocalDate.parse("2026-06-22")
            },
        )
    }
}
