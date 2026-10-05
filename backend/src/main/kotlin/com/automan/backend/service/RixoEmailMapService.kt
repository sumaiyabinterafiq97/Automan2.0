package com.automan.backend.service

import com.automan.backend.model.RixoEmailMap
import com.automan.backend.repository.RixoEmailMapRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RixoEmailMapService(
    private val rixoEmailMapRepository: RixoEmailMapRepository,
) {

    @Transactional(readOnly = true)
    fun findAllAsMaps(): List<Map<String, Any>> =
        rixoEmailMapRepository.findAll(Sort.by(Sort.Direction.ASC, "rixoCompany", "email")).map { toDto(it) }

    @Transactional(readOnly = true)
    fun emailsForCompany(company: String): List<String> {
        val name = company.trim()
        if (name.isEmpty()) return emptyList()
        return rixoEmailMapRepository.findEmailsByCompanyIgnoreCase(name)
    }

    @Transactional
    fun create(rixoCompany: String, email: String): RixoEmailMap {
        val company = normalizeCompany(rixoCompany)
        val address = normalizeEmail(email)
        require(rixoEmailMapRepository.findByCompanyAndEmailIgnoreCase(company, address) == null) {
            "This email is already saved for this Rixo company."
        }
        val now = LocalDateTime.now()
        return rixoEmailMapRepository.save(
            RixoEmailMap(
                rixoCompany = company,
                email = address,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun update(id: Long, rixoCompany: String, email: String): RixoEmailMap {
        val existing = rixoEmailMapRepository.findById(id).orElse(null)
            ?: throw IllegalArgumentException("Rixo email map row not found")
        val company = normalizeCompany(rixoCompany)
        val address = normalizeEmail(email)
        val conflict = rixoEmailMapRepository.findByCompanyAndEmailIgnoreCase(company, address)
        if (conflict != null && conflict.id != existing.id) {
            throw IllegalArgumentException("This email is already saved for this Rixo company.")
        }
        return rixoEmailMapRepository.save(
            existing.copy(
                rixoCompany = company,
                email = address,
                createdAt = existing.createdAt,
                updatedAt = LocalDateTime.now(),
            ),
        )
    }

    @Transactional
    fun delete(id: Long) {
        require(rixoEmailMapRepository.existsById(id)) { "Rixo email map row not found" }
        rixoEmailMapRepository.deleteById(id)
    }

    private fun toDto(row: RixoEmailMap): Map<String, Any> =
        mapOf(
            "id" to (row.id ?: 0L),
            "rixoCompany" to row.rixoCompany,
            "email" to row.email,
        )

    companion object {
        fun normalizeCompany(raw: String?): String {
            val company = raw?.trim().orEmpty()
            require(company.isNotEmpty()) { "Rixo company is required" }
            require(company.length <= 100) { "Rixo company is too long" }
            return company
        }

        fun normalizeEmail(raw: String?): String {
            val email = raw?.trim()?.lowercase().orEmpty()
            require(isPlausibleEmail(email)) { "Enter a valid email address." }
            return email
        }

        fun isPlausibleEmail(raw: String): Boolean {
            val to = raw.trim()
            if (to.length !in 3..254 || to.contains(' ')) return false
            val at = to.indexOf('@')
            if (at <= 0 || at != to.lastIndexOf('@')) return false
            val domain = to.substring(at + 1)
            return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.') && !domain.contains("..")
        }
    }
}
