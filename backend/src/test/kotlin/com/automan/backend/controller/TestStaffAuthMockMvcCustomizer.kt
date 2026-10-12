package com.automan.backend.controller

import com.automan.backend.model.User
import com.automan.backend.model.UserRole
import com.automan.backend.repository.UserRepository
import com.automan.backend.security.AuthPolicy
import com.automan.backend.security.AuthSessionService
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcConfigurer
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer

/**
 * Existing MockMvc tests call staff APIs without a session.
 * This adds an Admin bearer token unless the test already set Authorization
 * or asked to stay anonymous with [ANONYMOUS_HEADER].
 * It does not run for public auth endpoints, so first-admin setup can still see an empty user table.
 */
@Component
@Profile("test")
class TestStaffAuthMockMvcCustomizer : MockMvcBuilderCustomizer {
    override fun customize(builder: ConfigurableMockMvcBuilder<*>) {
        builder.apply(TestStaffAuthConfigurer())
    }
}

object TestStaffAuth {
    const val EMAIL = "test-staff-admin@automan.test"
    const val ANONYMOUS_HEADER = "X-Automan-Test-Anonymous"
}

private class TestStaffAuthConfigurer : MockMvcConfigurer {
    override fun beforeMockMvcCreated(
        builder: ConfigurableMockMvcBuilder<*>,
        context: WebApplicationContext,
    ): RequestPostProcessor {
        val users = context.getBean(UserRepository::class.java)
        val sessions = context.getBean(AuthSessionService::class.java)
        val tx = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))
        tx.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        var cachedToken: String? = null
        return RequestPostProcessor { request ->
            if (request.getHeader(TestStaffAuth.ANONYMOUS_HEADER) == "1") return@RequestPostProcessor request
            if (!request.getHeader("Authorization").isNullOrBlank()) return@RequestPostProcessor request
            val path = request.requestURI ?: "/"
            if (AuthPolicy.isPublic(request.method ?: "GET", path)) return@RequestPostProcessor request
            val token = tx.execute<String> {
                synchronized(this) {
                    val cached = cachedToken
                    if (cached != null && sessions.authenticate(cached) != null) {
                        cached
                    } else {
                        ensureAdminToken(users, sessions).also { cachedToken = it }
                    }
                }
            }
            if (!token.isNullOrBlank()) {
                request.addHeader("Authorization", "Bearer $token")
            }
            request
        }
    }

    private fun ensureAdminToken(users: UserRepository, sessions: AuthSessionService): String {
        val encoder = BCryptPasswordEncoder()
        val user = users.findByEmail(TestStaffAuth.EMAIL) ?: users.save(
            User(
                email = TestStaffAuth.EMAIL,
                name = "Test Staff Admin",
                passwordHash = encoder.encode("TestAdmin1"),
                role = UserRole.ADMIN,
            )
        )
        return sessions.createSession(user)
    }
}
