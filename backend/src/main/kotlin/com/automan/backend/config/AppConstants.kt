package com.automan.backend.config

/**
 * Application Constants for Backend
 * Centralized location for all hardcoded values
 */
object AppConstants {
    // CSV Import
    const val MAX_CSV_FILE_SIZE_MB = 10
    const val DEFAULT_CHARSET = "UTF-8"
    
    // Date formats
    const val DATE_FORMAT_PATTERN = "MMMM dd, yyyy(EEEE)"
    
    // Pagination (if needed in backend)
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100

    /** Option A: warn when owed amount reaches this fraction of credit limit (default 90%). */
    const val CREDIT_LIMIT_NEAR_FRACTION = 0.9

    /** Phase 3: when true, reject invoice save if projected balance exceeds credit limit. */
    const val BLOCK_INVOICE_WHEN_OVER_CREDIT_LIMIT = true
}
