package com.automan.purchase.utils

import com.automan.purchase.escapeHtml
import com.automan.purchase.extractNumericFromDbValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for utility functions
 */
class UtilsTest {
    
    @Test
    fun testEscapeHtml_Basic() {
        val input = "<script>alert('xss')</script>"
        val result = escapeHtml(input)
        assertEquals("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;&#x2F;script&gt;", result)
    }
    
    @Test
    fun testEscapeHtml_WithAmpersand() {
        val input = "Tom & Jerry"
        val result = escapeHtml(input)
        assertEquals("Tom &amp; Jerry", result)
    }
    
    @Test
    fun testEscapeHtml_Null() {
        val result = escapeHtml(null)
        assertEquals("", result)
    }
    
    @Test
    fun testEscapeHtml_Empty() {
        val result = escapeHtml("")
        assertEquals("", result)
    }
    
    @Test
    fun testExtractNumericFromDbValue_PlainNumber() {
        val result = extractNumericFromDbValue("1000")
        assertEquals("1000", result)
    }
    
    @Test
    fun testExtractNumericFromDbValue_WithCurrency() {
        val result = extractNumericFromDbValue("¥1,000")
        assertEquals("1000", result)
    }
    
    @Test
    fun testExtractNumericFromDbValue_WithCommas() {
        val result = extractNumericFromDbValue("1,000,000")
        assertEquals("1000000", result)
    }
    
    @Test
    fun testExtractNumericFromDbValue_Null() {
        val result = extractNumericFromDbValue(null)
        assertEquals("", result)
    }
    
    @Test
    fun testExtractNumericFromDbValue_Empty() {
        val result = extractNumericFromDbValue("")
        assertEquals("", result)
    }
    
    @Test
    fun testExtractNumericFromDbValue_WithDecimal() {
        val result = extractNumericFromDbValue("1000.50")
        assertEquals("1000.50", result)
    }
}
