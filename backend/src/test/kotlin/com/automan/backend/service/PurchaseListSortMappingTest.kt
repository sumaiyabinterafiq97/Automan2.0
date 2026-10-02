package com.automan.backend.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PurchaseListSortMappingTest {

    @Test
    fun auctionNoIsNotPassedToJpaAsAnEntityAttribute() {
        assertNull(PurchaseListSortMapping.jpaProperty("auctionNo"))
        assertNull(PurchaseListSortMapping.jpaProperty("auction_no"))
        assertNull(PurchaseListSortMapping.jpaProperty("notes"))
        assertTrue(PurchaseListSortMapping.sortsInMemory("auctionNo"))
        assertTrue(PurchaseListSortMapping.sortsInMemory("notes"))
        assertTrue(PurchaseListSortMapping.sortsInMemory("price"))
    }

    @Test
    fun persistentColumnsMapToRealJpaProperties() {
        assertEquals("chassis", PurchaseListSortMapping.jpaProperty("chassis"))
        assertEquals("clientName", PurchaseListSortMapping.jpaProperty("clientName"))
        assertEquals("manufactureYear", PurchaseListSortMapping.jpaProperty("manufactureYear"))
        assertEquals("auctionHouse", PurchaseListSortMapping.jpaProperty("auctionHouse"))
        assertFalse(PurchaseListSortMapping.sortsInMemory("chassis"))
        assertFalse(PurchaseListSortMapping.sortsInMemory("date"))
        assertNull(PurchaseListSortMapping.jpaProperty("notARealColumn"))
        assertFalse(PurchaseListSortMapping.sortsInMemory("notARealColumn"))
    }
}
