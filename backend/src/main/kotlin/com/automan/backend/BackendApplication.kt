package com.automan.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [FlywayAutoConfiguration::class])
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}
