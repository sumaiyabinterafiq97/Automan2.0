package com.automan.backend.service

import com.automan.backend.dto.RixoHistoryRowDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RixoHistoryConfirmedSortTest {

    private fun row(id: Long, confirmed: Boolean) = RixoHistoryRowDto(
        id = id,
        rixoConfirmed = confirmed,
    )

    @Test
    fun `recognizes rixoConfirmed sort keys only`() {
        assertTrue(RixoHistoryService.isRixoConfirmedSortFieldKey("rixoConfirmed"))
        assertTrue(RixoHistoryService.isRixoConfirmedSortFieldKey("rixo_confirmed"))
        assertTrue(RixoHistoryService.isRixoConfirmedSortFieldKey(" RIXOCONFIRMED "))
        assertFalse(RixoHistoryService.isRixoConfirmedSortFieldKey("rixoConfirmedDate"))
        assertFalse(RixoHistoryService.isRixoConfirmedSortFieldKey("buyingDate"))
        assertFalse(RixoHistoryService.isRixoConfirmedSortFieldKey(null))
    }

    @Test
    fun `desc puts confirmed first then higher id`() {
        val sorted = RixoHistoryService.sortEnrichedByRixoConfirmed(
            listOf(
                row(1L, confirmed = true),
                row(4L, confirmed = false),
                row(3L, confirmed = true),
                row(2L, confirmed = false),
            ),
            "desc",
        )
        assertEquals(listOf(3L, 1L, 4L, 2L), sorted.map { it.id })
    }

    @Test
    fun `asc puts unconfirmed first then higher id`() {
        val sorted = RixoHistoryService.sortEnrichedByRixoConfirmed(
            listOf(
                row(1L, confirmed = true),
                row(4L, confirmed = false),
                row(3L, confirmed = true),
                row(2L, confirmed = false),
            ),
            "asc",
        )
        assertEquals(listOf(4L, 2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `blank order defaults to confirmed first`() {
        val sorted = RixoHistoryService.sortEnrichedByRixoConfirmed(
            listOf(row(1L, confirmed = false), row(2L, confirmed = true)),
            null,
        )
        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }
}
