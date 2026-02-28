package com.automan.purchase

/**
 * Application Constants
 * Centralized location for all hardcoded values
 */
object AppConstants {
    // Pagination
    const val DEFAULT_ITEMS_PER_PAGE = 20
    val PAGINATION_OPTIONS = listOf(10, 20, 50, 100) // Cannot be const in Kotlin/JS
    
    // Date formats
    const val DATE_INPUT_FORMAT = "YYYY-MM-DD"
    const val DATE_DISPLAY_FORMAT = "MMMM dd, yyyy(EEEE)"
    
    // Timeouts and delays (milliseconds)
    const val DOM_READY_DELAY = 100
    const val FORM_POPULATION_DELAY = 200
    const val FALLBACK_DELAY = 500
    const val CHASSIS_LOAD_DELAY = 600
    
    // URL cleanup delays (milliseconds)
    const val URL_REVOKE_DELAY_SHORT = 1000
    const val URL_REVOKE_DELAY_LONG = 300000 // 5 minutes
    
    // Default values
    const val DEFAULT_COUNTRY = "PAKISTAN"
    
    // Grid layouts
    const val DEFAULT_GRID_COLUMNS = 4
    const val DEFAULT_GRID_GAP = 12
    
    // Tax calculation
    const val DEFAULT_TAX_PERCENTAGE = 10
    
    // Validation
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100
    const val MIN_DAY = 1
    const val MAX_DAY = 31
    
    // Message display
    const val MESSAGE_AUTO_HIDE_DELAY = 5000 // 5 seconds
    
    // Modal animations
    const val MODAL_ANIMATION_DURATION = 300 // milliseconds
    
    // Debounce delays
    const val DEBOUNCE_DELAY = 300 // milliseconds
    const val SEARCH_DEBOUNCE_DELAY = 500 // milliseconds
    
    // Retry settings
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY = 1000 // milliseconds
    
    // File upload
    const val MAX_FILE_SIZE_MB = 10
    const val MAX_IMAGE_SIZE_MB = 5
    
    // Pagination
    const val DEFAULT_PAGE_NUMBER = 1
    const val MAX_PAGE_SIZE = 100
    
    // Device breakpoints (pixels)
    const val MOBILE_MAX_WIDTH = 767
    const val TABLET_MAX_WIDTH = 1024
    
    // Column limits per device
    const val MOBILE_MAX_COLUMNS = 4
    const val TABLET_MAX_COLUMNS = 6
    const val DESKTOP_MAX_COLUMNS = 9
}
