package com.automan.backend.util

import org.slf4j.LoggerFactory

/**
 * Logger utility for backend
 * Wraps SLF4J for standard Spring Boot logging
 */
object Logger {
    private val log = LoggerFactory.getLogger(Logger::class.java)

    fun log(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        log.info(formattedMessage)
    }
    
    fun warn(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        log.warn(formattedMessage)
    }
    
    fun error(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        log.error(formattedMessage)
    }
    
    fun error(message: String, throwable: Throwable, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        log.error(formattedMessage, throwable)
    }
    
    fun debug(message: String, vararg args: Any?) {
        val formattedMessage = formatMessage(message, *args)
        log.debug(formattedMessage)
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
