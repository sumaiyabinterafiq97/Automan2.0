package com.automan.backend.config

import org.springframework.context.annotation.Configuration

/**
 * WebConfig - Removed duplicate CORS configuration
 * CORS is now handled by WebCorsConfig to avoid conflicts
 */
@Configuration
class WebConfig {
    // CORS configuration moved to WebCorsConfig to avoid conflicts
}
