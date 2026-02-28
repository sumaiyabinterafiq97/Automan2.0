package com.automan.backend.service

import com.automan.backend.util.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class EmailService(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    @Value("\${app.mail.admin-email:automan.dev.team@gmail.com}")
    private lateinit var adminEmail: String

    @Value("\${app.mail.resend-api-key:}")
    private lateinit var resendApiKey: String

    @Value("\${app.mail.from:Automan <onboarding@resend.dev>}")
    private lateinit var fromAddress: String

    @Value("\${app.verification-base-url:http://localhost:8083/api}")
    private lateinit var verificationBaseUrl: String

    @Value("\${app.frontend-url:http://localhost:8080}")
    private lateinit var frontendUrl: String

    private val resendUrl = "https://api.resend.com/emails"

    fun sendSignupVerificationToAdmin(userEmail: String, userName: String, role: String, token: String) {
        if (resendApiKey.isBlank()) {
            Logger.warn("[EMAIL] Resend API key not set (RESEND_API_KEY) - no verification email sent to admin for $userEmail")
            return
        }
        try {
            val approveUrl = "$verificationBaseUrl/auth/verify-signup?token=$token&action=approve"
            val rejectUrl = "$verificationBaseUrl/auth/verify-signup?token=$token&action=reject"
            val text = """
                A new user has requested access.
                Email: $userEmail
                Name: $userName
                Role: $role

                Approve: $approveUrl
                Reject: $rejectUrl
            """.trimIndent()
            val html = """
                <p>A new user has requested access.</p>
                <p><strong>Email:</strong> $userEmail<br><strong>Name:</strong> $userName<br><strong>Role:</strong> $role</p>
                <p><a href="$approveUrl" style="display:inline-block;padding:10px 20px;background:#28a745;color:white;text-decoration:none;border-radius:6px;margin-right:8px;">Approve</a>
                <a href="$rejectUrl" style="display:inline-block;padding:10px 20px;background:#dc3545;color:white;text-decoration:none;border-radius:6px;">Reject</a></p>
            """.trimIndent()
            sendViaResend(
                to = listOf(adminEmail),
                subject = "New signup approval: $userEmail",
                text = text,
                html = html
            )
            Logger.log("[EMAIL] Sent signup verification to admin for $userEmail")
        } catch (e: Exception) {
            Logger.warn("[EMAIL] Failed to send verification to admin for $userEmail: ${e.message}")
        }
    }

    fun sendApprovedToUser(userEmail: String, userName: String) {
        if (resendApiKey.isBlank()) {
            Logger.warn("[EMAIL] Resend API key not set - skipping approval email to $userEmail")
            return
        }
        try {
            val signInUrl = frontendUrl.trimEnd('/') + "/#/"
            val text = """
                Hi $userName,

                Your account has been approved. You can now sign in at the application.

                Sign in: $signInUrl

                Best regards,
                Automan Team
            """.trimIndent()
            val html = """
                <p>Hi $userName,</p>
                <p>Your account has been approved. You can now sign in at the application.</p>
                <p><a href="$signInUrl" style="display:inline-block;padding:12px 24px;background:#667eea;color:white;text-decoration:none;border-radius:6px;font-weight:500;">Sign In</a></p>
                <p>Best regards,<br>Automan Team</p>
            """.trimIndent()
            sendViaResend(
                to = listOf(userEmail),
                subject = "Your account has been approved",
                text = text,
                html = html
            )
            Logger.log("[EMAIL] Sent approval notification to $userEmail")
        } catch (e: Exception) {
            Logger.warn("[EMAIL] Failed to send approval to $userEmail: ${e.message}")
        }
    }

    fun sendRejectedToUser(userEmail: String, userName: String) {
        if (resendApiKey.isBlank()) {
            Logger.warn("[EMAIL] Resend API key not set - skipping rejection email to $userEmail")
            return
        }
        try {
            val text = """
                Hi $userName,

                Your signup request was not approved. If you believe this is an error, please contact the administrator.

                Best regards,
                Automan Team
            """.trimIndent()
            val html = """
                <p>Hi $userName,</p>
                <p>Your signup request was not approved. If you believe this is an error, please contact the administrator.</p>
                <p>Best regards,<br>Automan Team</p>
            """.trimIndent()
            sendViaResend(
                to = listOf(userEmail),
                subject = "Your signup request was not approved",
                text = text,
                html = html
            )
            Logger.log("[EMAIL] Sent rejection notification to $userEmail")
        } catch (e: Exception) {
            Logger.warn("[EMAIL] Failed to send rejection to $userEmail: ${e.message}")
        }
    }

    private fun sendViaResend(to: List<String>, subject: String, text: String, html: String? = null) {
        val body = mutableMapOf<String, Any>(
            "from" to fromAddress,
            "to" to to,
            "subject" to subject,
            "text" to text
        )
        if (!html.isNullOrBlank()) body["html"] = html
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(resendApiKey.trim())
        }
        val entity = HttpEntity(objectMapper.writeValueAsString(body), headers)
        restTemplate.postForEntity(resendUrl, entity, String::class.java)
    }
}
