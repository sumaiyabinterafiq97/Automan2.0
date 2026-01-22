package com.automan.backend.config

/**
 * Application Constants for Backend
 * Centralized location for all hardcoded values
 */
object AppConstants {
    // Logging
    const val DEBUG_LOGGING = true // TODO: Make this configurable via application.yml
    
    // CSV Import
    const val MAX_CSV_FILE_SIZE_MB = 10
    const val DEFAULT_CHARSET = "UTF-8"
    
    // Date formats
    const val DATE_FORMAT_PATTERN = "MMMM dd, yyyy(EEEE)"
    
    // Pagination (if needed in backend)
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100
}
