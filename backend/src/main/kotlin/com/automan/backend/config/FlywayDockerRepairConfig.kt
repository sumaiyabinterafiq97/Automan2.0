package com.automan.backend.config

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Clears failed migration rows in [flyway_schema_history] before migrate so a half-applied
 * migration (e.g. V2) does not block startup forever (nginx 502 when backend exits).
 */
@Configuration
@Profile("docker")
class FlywayDockerRepairConfig {

    @Bean
    fun flywayMigrationStrategy(): FlywayMigrationStrategy {
        return FlywayMigrationStrategy { flyway: Flyway ->
            flyway.repair()
            flyway.migrate()
        }
    }
}
