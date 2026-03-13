package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement

/**
 * Escape HTML to prevent XSS attacks
 */
fun escapeHtml(text: String?): String {
    if (text == null || text.isEmpty()) return ""
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
        .replace("/", "&#x2F;")
}

/**
 * Safe localStorage wrapper with error handling
 */
fun safeLocalStorageGet(key: String): String? {
    return try {
        window.localStorage.getItem(key)
    } catch (e: dynamic) {
        Logger.warn("localStorage.getItem failed for key '$key': ${e.toString()}")
        null
    }
}

fun safeLocalStorageSet(key: String, value: String): Boolean {
    return try {
        window.localStorage.setItem(key, value)
        true
    } catch (e: dynamic) {
        Logger.warn("localStorage.setItem failed for key '$key': ${e.toString()}")
        false
    }
}

fun safeLocalStorageRemove(key: String): Boolean {
    return try {
        window.localStorage.removeItem(key)
        true
    } catch (e: dynamic) {
        Logger.warn("localStorage.removeItem failed for key '$key': ${e.toString()}")
        false
    }
}

// API base URL - use relative path so nginx can proxy to backend
val API_BASE_URL = "/api"

// Helper function to get API URL
fun apiUrl(path: String): String {
    // Remove leading slash if present - use trimStart to be safe
    val cleanPath = path.trimStart('/')
    val fullUrl = "$API_BASE_URL/$cleanPath"
    Logger.debug("apiUrl() called with path: '$path', fullUrl: '$fullUrl'")
    return fullUrl
}

// Helper function to safely extract numeric value from database field (handles strings with ¥, numbers, null, etc.)
fun extractNumericFromDbValue(value: dynamic): String {
    if (value == null || value == js("undefined")) return ""
    val str: String = when {
        value is String -> value
        else -> {
            try {
                val s = value.toString()
                if (s is String) s else ""
            } catch (e: dynamic) {
                ""
            }
        }
    }
    // Use JavaScript-compatible check instead of Kotlin's isEmpty()
    if (str.length == 0) return ""
    // Remove currency symbols (including corrupted "Â¥"), commas, spaces - keep only numbers and decimal point
    // First remove corrupted "Â¥" pattern, then regular "¥", then commas and spaces, then any other non-numeric except decimal point
    return str.replace(Regex("Â¥"), "").replace(Regex("[¥,\\s]"), "").replace(Regex("[^0-9.]"), "")
}

/**
 * Device Detection Utilities for Responsive Design
 */

/**
 * Get current device type based on window width
 * @return "mobile", "tablet", or "desktop"
 */
fun getDeviceType(): String {
    val width = window.innerWidth
    return when {
        width <= AppConstants.MOBILE_MAX_WIDTH -> "mobile"
        width <= AppConstants.TABLET_MAX_WIDTH -> "tablet"
        else -> "desktop"
    }
}

/**
 * Get maximum columns allowed for current device
 * @return Maximum number of columns (4 for mobile, 6 for tablet, 9 for desktop)
 */
fun getMaxColumnsForDevice(deviceType: String? = null): Int {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> AppConstants.MOBILE_MAX_COLUMNS
        "tablet" -> AppConstants.TABLET_MAX_COLUMNS
        "desktop" -> AppConstants.DESKTOP_MAX_COLUMNS
        else -> AppConstants.DESKTOP_MAX_COLUMNS
    }
}

/**
 * Get default columns for a specific device type
 * @param deviceType Device type ("mobile", "tablet", or "desktop")
 * @return List of default column keys for the device
 */
fun getDefaultColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("date", "chassis", "carName", "price")
        "tablet" -> listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "price")
        "desktop" -> listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "clientName", "rixoCompany", "price", "brand")
        else -> listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "clientName", "rixoCompany", "price")
    }
}

/**
 * Auto-adjust columns when device changes
 * If saved columns exceed device limit, replace with device defaults
 * @return Adjusted list of columns
 */
fun autoAdjustColumnsForDevice(savedColumns: List<String>, deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    val maxColumns = getMaxColumnsForDevice(device)
    val defaultColumns = getDefaultColumnsForDevice(device)
    
    return if (savedColumns.size > maxColumns) {
        // If saved columns exceed limit, use device defaults
        defaultColumns
    } else {
        // If within limit, keep user's selection (but ensure it's valid)
        savedColumns.filter { it.isNotBlank() }.take(maxColumns)
    }
}

// Helper function to safely extract numeric value from text fields with suffixes (CC, WD, km)
fun extractNumericFromSuffixedValue(value: dynamic): String {
    if (value == null || value == js("undefined")) return ""
    val str: String = when {
        value is String -> value
        else -> {
            try {
                val s = value.toString()
                if (s is String) s else ""
            } catch (e: dynamic) {
                ""
            }
        }
    }
    // Use JavaScript-compatible check instead of Kotlin's isEmpty()
    if (str.length == 0) return ""
    // Remove all non-numeric characters (including CC, WD, km suffixes)
    return str.replace(Regex("[^0-9]"), "")
}

// Date formatting functions
fun formatWithWeekday(isoDate: String?): String {
    if (isoDate == null || isoDate.isBlank()) return ""
    // If already includes weekday, keep as is
    if (isoDate.contains("(") && isoDate.contains(")")) return isoDate
    try {
        val date = js("new Date(isoDate)")
        if (js("isNaN(date)") as Boolean) return isoDate
        val months = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        val month = months[js("date.getMonth()") as Int]
        val dayOfMonth = js("date.getDate()") as Int
        val year = js("date.getFullYear()") as Int
        val weekday = days[js("date.getDay()") as Int]
        return month + dayOfMonth.toString() + ", " + year.toString() + "(" + weekday + ")"
    } catch (e: dynamic) {
        return isoDate
    }
}

fun formatDateForDatabase(isoDate: String?): String {
    if (isoDate == null || isoDate.isBlank()) return ""
    try {
        val date = js("new Date(isoDate)")
        if (js("isNaN(date)") as Boolean) return isoDate
        val months = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        val month = months[js("date.getMonth()") as Int]
        val dayOfMonth = js("date.getDate()") as Int
        val year = js("date.getFullYear()") as Int
        val weekday = days[js("date.getDay()") as Int]
        return month + dayOfMonth.toString() + ", " + year.toString() + "(" + weekday + ")"
    } catch (e: dynamic) {
        return isoDate
    }
}

// Formats carModelYear from YYYY-MM or MM/YYYY to "Month YYYY" format
// Examples: "2025-07" -> "July 2025", "07/2025" -> "July 2025", "7/2025" -> "July 2025"
fun formatCarModelYear(yearStr: String?): String {
    if (yearStr == null || yearStr.isBlank()) return ""
    
    val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    
    try {
        // Handle YYYY-MM format (from month input)
        if (yearStr.contains("-")) {
            val parts = yearStr.split("-")
            if (parts.size == 2) {
                val year = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                if (year != null && month != null && month >= 1 && month <= 12) {
                    return "${months[month - 1]} $year"
                }
            }
        }
        
        // Handle MM/YYYY or M/YYYY format (from database)
        if (yearStr.contains("/")) {
            val parts = yearStr.split("/")
            if (parts.size == 2) {
                val month = parts[0].toIntOrNull()
                val year = parts[1].toIntOrNull()
                if (month != null && year != null && month >= 1 && month <= 12) {
                    return "${months[month - 1]} $year"
                }
            }
        }
        
        // If already in readable format, return as is
        return yearStr
    } catch (e: dynamic) {
        return yearStr
    }
}

/** Extracts only the 4-digit year from car_model_year (e.g. "July 2026" -> "2026", "2025-07" -> "2025"). */
fun carModelYearToYearOnly(yearStr: String?): String {
    if (yearStr == null || yearStr.isBlank()) return ""
    // YYYY-MM
    if (yearStr.contains("-")) {
        val parts = yearStr.split("-")
        if (parts.isNotEmpty()) {
            val y = parts[0].trim()
            if (y.length == 4 && y.all { it.isDigit() }) return y
        }
    }
    // MM/YYYY or M/YYYY
    if (yearStr.contains("/")) {
        val parts = yearStr.split("/")
        if (parts.size >= 2) {
            val y = parts[1].trim()
            if (y.length == 4 && y.all { it.isDigit() }) return y
        }
    }
    // "Month YYYY" (e.g. July 2026) - take last token if it's 4 digits
    val tokens = yearStr.trim().split(Regex("\\s+"))
    for (t in tokens.reversed()) {
        if (t.length == 4 && t.all { it.isDigit() }) return t
    }
    // Already a single year?
    if (yearStr.length == 4 && yearStr.all { it.isDigit() }) return yearStr
    return yearStr
}

fun normalizeDateForComparison(dateStr: String?): String {
    if (dateStr == null || dateStr.isBlank()) return ""
    
    try {
        // Handle format: "24 Apr, 2025" -> convert to "April24, 2025"
        if (dateStr.contains("Apr") && !dateStr.contains("April")) {
            val parts = dateStr.split(", ")
            if (parts.size == 2) {
                val dayMonth = parts[0].trim()
                val year = parts[1].trim()
                val day = dayMonth.split(" ")[0]
                return "April$day, $year"
            }
        }
        
        // Handle format: "April24, 2025(Thursday)" -> extract "April24, 2025"
        if (dateStr.contains("April") && dateStr.contains("(")) {
            val beforeParen = dateStr.split("(")[0].trim()
            return beforeParen
        }
        
        // Handle format: "April24, 2025" -> return as is
        if (dateStr.contains("April")) {
            return dateStr
        }
        
        // If none of the above, try to parse as ISO date and convert
        val date = js("new Date(dateStr)")
        if (!js("isNaN(date)") as Boolean) {
            val months = arrayOf(
                "January","February","March","April","May","June",
                "July","August","September","October","November","December"
            )
            val month = months[js("date.getMonth()") as Int]
            val dayOfMonth = js("date.getDate()") as Int
            val year = js("date.getFullYear()") as Int
            return month + dayOfMonth.toString() + ", " + year.toString()
        }
        
        return dateStr
    } catch (e: dynamic) {
        return dateStr
    }
}

// Converts a stored date label like "June3, 2025(Tuesday)" to ISO yyyy-MM-dd for <input type="date">
fun toIsoFromLabel(dateStr: dynamic): String {
    // Safely convert to string, handling null, undefined, or non-string types
    val dateString: String = when {
        dateStr == null || dateStr == js("undefined") -> ""
        dateStr is String -> dateStr
        else -> {
            try {
                dateStr.toString()
            } catch (e: dynamic) {
                ""
            }
        }
    }
    
    if (dateString.isEmpty() || dateString.trim().isEmpty()) return ""
    
    // If already ISO-like (YYYY-MM-DD), return as-is
    if (dateString.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return dateString
    
    // Handle various date formats
    try {
        // Strip weekday part in parentheses (e.g., "January15, 2026(Wednesday)" -> "January15, 2026")
        val base = dateString.replace(Regex("\\(.*?\\)"), "").trim()
        
        // Handle formats like "24 Apr, 2025" -> "April 24, 2025"
        val monthAbbrevMap = mapOf(
            "Jan" to "January", "Feb" to "February", "Mar" to "March", "Apr" to "April",
            "May" to "May", "Jun" to "June", "Jul" to "July", "Aug" to "August",
            "Sep" to "September", "Oct" to "October", "Nov" to "November", "Dec" to "December"
        )
        var processed = base
        for ((abbrev, full) in monthAbbrevMap) {
            if (processed.contains(abbrev) && !processed.contains(full)) {
                processed = processed.replace(Regex("\\b$abbrev\\b"), full)
                break
            }
        }
        
        // Ensure a space between month and day (e.g., "January15, 2026" -> "January 15, 2026")
        // Handle both "January15" (no space) and "January 15" (with space)
        // Also handle formats like "January20, 2026(Tuesday)" - need to add space before day
        val normalized = processed.replace(Regex("^([A-Za-z]+)(\\d+),\\s*(\\d{4})"), "$1 $2, $3")
        
        // Try parsing with JavaScript Date
        val d = js("new Date(normalized)")
        val isValid = js("!isNaN(d.getTime())") as Boolean
        
        if (!isValid) {
            // Fallback: try parsing common formats manually
            val manualParse = try {
                // Ensure we have space between month and day for manual parsing
                val normalizedWithSpace = normalized.replace(Regex("^([A-Za-z]+)(\\d+)"), "$1 $2")
                val parts = normalizedWithSpace.split(Regex("[, ]+"))
                if (parts.size >= 3) {
                    val monthName = parts[0]
                    val day = parts[1].toIntOrNull()
                    val year = parts[2].toIntOrNull()
                    if (day != null && year != null) {
                        val monthNum = when (monthName.lowercase()) {
                            "january", "jan" -> 1
                            "february", "feb" -> 2
                            "march", "mar" -> 3
                            "april", "apr" -> 4
                            "may" -> 5
                            "june", "jun" -> 6
                            "july", "jul" -> 7
                            "august", "aug" -> 8
                            "september", "sep" -> 9
                            "october", "oct" -> 10
                            "november", "nov" -> 11
                            "december", "dec" -> 12
                            else -> null
                        }
                        if (monthNum != null && day in AppConstants.MIN_DAY..AppConstants.MAX_DAY && year in AppConstants.MIN_YEAR..AppConstants.MAX_YEAR) {
                            val monthStr = if (monthNum < 10) "0$monthNum" else monthNum.toString()
                            val dayStr = if (day < 10) "0$day" else day.toString()
                            return "${year}-${monthStr}-${dayStr}"
                        }
                    }
                }
                ""
            } catch (e: dynamic) {
                console.warn("Date parsing error:", e, "for input:", dateString)
                ""
            }
            return manualParse
        }
        
        // Successfully parsed with Date object
        return buildString {
            val y = js("d.getFullYear()") as Int
            val m = (js("d.getMonth()") as Int) + 1
            val day = js("d.getDate()") as Int
            append(y.toString())
            append("-")
            append(if (m < 10) "0$m" else m.toString())
            append("-")
            append(if (day < 10) "0$day" else day.toString())
        }
    } catch (e: dynamic) {
        console.warn("Date parsing error:", e, "for input:", dateString)
        return ""
    }
}

fun parseDateForSorting(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    
    // Define JavaScript helper function for date parsing (only once)
    js("""
        if (typeof window.parseDateForSorting === 'undefined') {
            window.parseDateForSorting = function(dateStr) {
                if (!dateStr || dateStr.trim() === '') return null;
                
                // Try direct Date parsing
                var date = new Date(dateStr);
                var timestamp = date.getTime();
                if (!isNaN(timestamp) && timestamp > 0) {
                    return timestamp;
                }
                
                // Try yyyy-MM-dd format
                var isoMatch = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
                if (isoMatch) {
                    var year = parseInt(isoMatch[1]);
                    var month = parseInt(isoMatch[2]) - 1;
                    var day = parseInt(isoMatch[3]);
                    date = new Date(year, month, day);
                    timestamp = date.getTime();
                    if (!isNaN(timestamp)) {
                        return timestamp;
                    }
                }
                
                // Try MM/dd/yyyy format
                var usMatch = dateStr.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})/);
                if (usMatch) {
                    var month = parseInt(usMatch[1]) - 1;
                    var day = parseInt(usMatch[2]);
                    var year = parseInt(usMatch[3]);
                    date = new Date(year, month, day);
                    timestamp = date.getTime();
                    if (!isNaN(timestamp)) {
                        return timestamp;
                    }
                }
                
                return null;
            };
        }
    """)
    
    // Call JavaScript function using dynamic typing
    val parseFunc = js("window.parseDateForSorting").unsafeCast<(String) -> dynamic>()
    val result = parseFunc(dateStr).unsafeCast<Double?>()
    
    return if (result != null && !result.isNaN()) {
        result.toLong()
    } else {
        null
    }
}

fun formatConsigneeForUpdate(rawConsignee: String, consigneeCountry: String): String {
    val trimmedRaw = rawConsignee.trim()
    val trimmedCountry = consigneeCountry.trim()

    if (trimmedRaw.isEmpty() && trimmedCountry.isEmpty()) {
        return ""
    }

    // Try to split by newline first
    var parts = trimmedRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    
    // If no newline found, try to detect if it's a concatenated name+address
    // Look for common patterns like company name followed by address (numbers, street names, etc.)
    if (parts.size == 1 && trimmedRaw.length > 50) {
        // Try to split at common separators or patterns
        // Look for patterns like "LTD." or "LTD" followed by address
        val ltdPattern = Regex("(.*?)(LTD\\.?|LIMITED|PVT|PRIVATE)(.*)", RegexOption.IGNORE_CASE)
        val match = ltdPattern.find(trimmedRaw)
        if (match != null) {
            val companyPart = (match.groupValues[1] + match.groupValues[2]).trim()
            val addressPart = match.groupValues[3].trim()
            if (companyPart.isNotEmpty() && addressPart.isNotEmpty()) {
                parts = listOf(companyPart, addressPart)
            }
        }
    }
    
    val name = parts.firstOrNull() ?: ""
    val address = parts.drop(1).joinToString(" ").trim()

    // If consignee already contains country, return formatted name + address
    if (trimmedRaw.contains(trimmedCountry, ignoreCase = true)) {
        return if (address.isNotEmpty()) {
            "$name\n$address"
        } else {
            name
        }
    }

    // Otherwise, prepend country to name
    val formattedName = if (trimmedCountry.isNotEmpty()) {
        "$trimmedCountry - $name"
    } else {
        name
    }

    return if (address.isNotEmpty()) {
        "$formattedName\n$address"
    } else {
        formattedName
    }
}

// Currency formatting functions
fun formatCurrency(amount: Double): String {
    return amount.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
}

fun parseCurrency(currencyString: String): Double {
    // Remove commas and any currency symbols, then parse as double
    val cleanString = currencyString.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
    return cleanString.toDoubleOrNull() ?: 0.0
}

// Input validation and formatting
fun validateAndFormatCurrencyInput(field: HTMLInputElement) {
    val currentValue = field.value
    val numericValue = currentValue.toDoubleOrNull() ?: 0.0
    
    // Validate: only allow positive numbers
    if (numericValue < 0) {
        field.value = "0"
        return
    }
    
    // Format with commas
    field.value = formatCurrency(numericValue)
}

// Message display utility
fun showMessage(message: String, type: String) {
    // Remove existing message
    document.getElementById("message")?.remove()
    
    val messageDiv = document.createElement("div")
    messageDiv.id = "message"
    
    val backgroundColor = when (type) {
        "success" -> "#d4edda"
        "error" -> "#f8d7da"
        "warning" -> "#fff3cd"
        else -> "#d1ecf1"
    }
    
    val color = when (type) {
        "success" -> "#155724"
        "error" -> "#721c24"
        "warning" -> "#856404"
        else -> "#0c5460"
    }
    
    messageDiv.setAttribute("style", "padding: 10px; margin-bottom: 10px; background-color: $backgroundColor; color: $color; border: 1px solid #c3e6cb; border-radius: 4px; position: fixed; top: 20px; right: 20px; z-index: 1001;")
    messageDiv.textContent = message
    
    document.body?.appendChild(messageDiv)
    
    // Auto-remove after configured delay
    window.setTimeout({
        messageDiv.remove()
    }, AppConstants.MESSAGE_AUTO_HIDE_DELAY)
}

