package com.automan.backend.service

import com.automan.backend.model.MasterMenu
import com.automan.backend.repository.MasterMenuRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MasterMenuService(
    private val masterMenuRepository: MasterMenuRepository,
) {

    private fun getDelimiter(fieldName: String): Char {
        return if (fieldName.equals("bank_accounts", ignoreCase = true)) ';' else ','
    }

    fun getValues(fieldName: String): List<String> {
        val row = masterMenuRepository.findByFieldNameIgnoreCase(fieldName) ?: return emptyList()
        val raw = row.fieldValues ?: return emptyList()
        val delimiter = getDelimiter(row.fieldName)
        return raw.split(delimiter)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun addValue(fieldName: String, value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return getValues(fieldName)

        val current = getValues(fieldName)
        if (current.any { it.equals(trimmed, ignoreCase = true) }) {
            Logger.debug("MasterMenuService.addValue: value '%s' already exists for field '%s'", trimmed, fieldName)
            return current
        }
        return saveValues(fieldName, current + trimmed)
    }

    fun updateValue(fieldName: String, originalValue: String, newValue: String): List<String> {
        val trimmed = newValue.trim()
        if (trimmed.isEmpty()) return getValues(fieldName)

        val current = getValues(fieldName)
        val updated = current.map {
            if (it.equals(originalValue, ignoreCase = true)) trimmed else it
        }.distinct()
        return saveValues(fieldName, updated)
    }

    fun deleteValue(fieldName: String, value: String): List<String> {
        val current = getValues(fieldName)
        val updated = current.filterNot { it.equals(value, ignoreCase = true) }
        return saveValues(fieldName, updated)
    }

    private fun saveValues(fieldName: String, values: List<String>): List<String> {
        val delimiter = getDelimiter(fieldName)
        val csv = values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(delimiter.toString())

        val existing = masterMenuRepository.findByFieldNameIgnoreCase(fieldName)
        if (existing != null) {
            masterMenuRepository.save(existing.copy(fieldValues = csv))
        } else {
            masterMenuRepository.save(MasterMenu(fieldName = fieldName, fieldValues = csv))
        }
        Logger.debug("MasterMenuService.saveValues: field='%s', values=%s", fieldName, csv)
        return getValues(fieldName)
    }
}

