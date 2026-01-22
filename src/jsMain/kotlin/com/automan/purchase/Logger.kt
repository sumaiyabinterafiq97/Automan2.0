package com.automan.purchase

/**
 * Logger utility for conditional logging
 * Set DEBUG_MODE to false in production to disable debug logs
 */
object Logger {
    // Set to false in production builds
    private val DEBUG_MODE = true // TODO: Make this configurable via build config
    private val ERROR_LOGGING = true // Always log errors
    private val WARN_LOGGING = true // Always log warnings
    
    fun log(message: String, vararg args: Any?) {
        if (DEBUG_MODE) {
            val formattedMessage = formatMessage(message, *args)
            js("console.log(formattedMessage)")
        }
    }
    
    fun warn(message: String, vararg args: Any?) {
        if (WARN_LOGGING) {
            val formattedMessage = formatMessage(message, *args)
            js("console.warn(formattedMessage)")
        }
    }
    
    fun error(message: String, vararg args: Any?) {
        if (ERROR_LOGGING) {
            val formattedMessage = formatMessage(message, *args)
            js("console.error(formattedMessage)")
        }
    }
    
    fun debug(message: String, vararg args: Any?) {
        if (DEBUG_MODE) {
            val formattedMessage = formatMessage(message, *args)
            js("console.debug(formattedMessage)")
        }
    }
    
    private fun formatMessage(message: String, vararg args: Any?): String {
        if (args.isEmpty()) return message
        // Simple string interpolation replacement
        var result = message
        args.forEachIndexed { index, arg ->
            result = result.replace("${'$'}{${index + 1}}", arg?.toString() ?: "null")
        }
        // Also handle %s style formatting
        args.forEach { arg ->
            result = result.replaceFirst("%s", arg?.toString() ?: "null")
        }
        return result
    }
}
