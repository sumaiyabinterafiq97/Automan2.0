package com.automan.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * WebConfig - Removed duplicate CORS configuration
 * CORS is now handled by WebCorsConfig to avoid conflicts
 */
@Configuration
class WebConfig {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
