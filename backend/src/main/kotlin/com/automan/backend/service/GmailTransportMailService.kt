package com.automan.backend.service

import com.automan.backend.util.PdfFilenameUtils
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.util.Properties

class GmailNotConfiguredException : RuntimeException("Gmail is not configured yet")

/**
 * Sends the Rixo transport PDF through Gmail SMTP.
 * Signup mail stays on Resend in [EmailService]. This sender does nothing until
 * GMAIL_USERNAME and GMAIL_APP_PASSWORD are set.
 */
@Service
class GmailTransportMailService(
    @Value("\${app.gmail.username:}") private val username: String,
    @Value("\${app.gmail.app-password:}") private val appPassword: String,
    @Value("\${app.gmail.host:smtp.gmail.com}") private val host: String,
    @Value("\${app.gmail.port:587}") private val port: Int,
) {
    fun isConfigured(): Boolean = username.trim().isNotBlank() && normalizedAppPassword().isNotBlank()

    /** Google displays the 16-character app password in groups of four. SMTP needs it without spaces. */
    private fun normalizedAppPassword(): String = appPassword.replace(Regex("\\s+"), "")

    fun sendRixoTransportPdf(
        to: String,
        rixoCompany: String,
        buyingDate: String,
        headMessage: String,
        pdfBytes: ByteArray,
        emailSubject: String = "",
        emailBody: String = "",
    ) {
        if (!isConfigured()) throw GmailNotConfiguredException()
        val company = rixoCompany.trim().ifEmpty { "Undefined" }
        val date = buyingDate.trim()
        val subject = emailSubject.trim().ifEmpty { defaultSubject(company, date) }
        val body = emailBody.trim().ifEmpty { headMessage.trim() }.ifEmpty { DEFAULT_HEAD_MESSAGE }
        val filename = PdfFilenameUtils.build(
            "RixoTransport",
            company,
            PdfFilenameUtils.dateToken(date),
        )
        val sender = JavaMailSenderImpl().apply {
            this.host = this@GmailTransportMailService.host.ifBlank { "smtp.gmail.com" }
            this.port = if (this@GmailTransportMailService.port > 0) this@GmailTransportMailService.port else 587
            this.username = this@GmailTransportMailService.username.trim()
            this.password = this@GmailTransportMailService.normalizedAppPassword()
            javaMailProperties = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }
        val message: MimeMessage = sender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")
        helper.setFrom(username.trim())
        helper.setTo(to.trim())
        helper.setSubject(subject)
        helper.setText(body, false)
        helper.addAttachment(filename, ByteArrayResource(pdfBytes), "application/pdf")
        sender.send(message)
    }

    companion object {
        const val DEFAULT_HEAD_MESSAGE =
            "いつもお世話になっております。\n下記の車両の陸送手配をお願いいたします。"

        private val RECIPIENT = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun isValidRecipient(raw: String?): Boolean {
            val to = raw?.trim().orEmpty()
            if (to.length !in 3..254) return false
            return RECIPIENT.matches(to)
        }

        /** Mail subject when the dialog leaves it blank: company and buying date, without 陸送依頼. */
        fun defaultSubject(company: String, date: String): String {
            val name = company.trim().ifEmpty { "Undefined" }
            val day = date.trim()
            return if (day.isEmpty()) name else "$name - $day"
        }
    }
}
