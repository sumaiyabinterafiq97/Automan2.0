package com.automan.backend.util

import com.automan.backend.config.AppConstants

/**
 * Logger utility for backend
 * Conditionally logs based on DEBUG_LOGGING flag
 */
object Logger {
    fun log(message: String, vararg args: Any?) {
        if (AppConstants.DEBUG_LOGGING) {
            val formattedMessage = formatMessage(message, *args)
            println(formattedMessage)
        }
    }
    
    fun warn(message: String, vararg args: Any?) {
        // Always log warnings
        val formattedMessage = formatMessage(message, *args)
        println("⚠️ WARNING: $formattedMessage")
    }
    
    fun error(message: String, vararg args: Any?) {
        // Always log errors
        val formattedMessage = formatMessage(message, *args)
        System.err.println("❌ ERROR: $formattedMessage")
    }
    
    fun error(message: String, throwable: Throwable, vararg args: Any?) {
        // Always log errors with stack trace
        val formattedMessage = formatMessage(message, *args)
        System.err.println("❌ ERROR: $formattedMessage")
        throwable.printStackTrace()
    }
    
    fun debug(message: String, vararg args: Any?) {
        if (AppConstants.DEBUG_LOGGING) {
            val formattedMessage = formatMessage(message, *args)
            println("🔍 DEBUG: $formattedMessage")
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
