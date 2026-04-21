package com.automan.backend.service

import com.automan.backend.dto.RixoHistoryRowDto
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class RixoHistoryService(
    private val rixoHistoryRepository: RixoHistoryRepository,
    private val purchaseRepository: PurchaseRepository,
) {

    fun listAllRows(): List<RixoHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        return rixoHistoryRepository.findAll(sort).map { e ->
            RixoHistoryRowDto(
                id = e.id ?: 0L,
                buyingDate = e.buyingDate?.toString(),
                rixoCompany = e.rixoCompany,
                message = e.message,
                chassis = e.chassis,
            )
        }
    }

    /**
     * Persists one row: [transportData] extraMessage → message; chassis from purchases in [selectedIds] order.
     */
    @Transactional
    fun saveFromTransport(selectedIds: List<Long>, transportData: Map<String, String>) {
        val buyingDateStr = transportData["buyingDate"]?.trim().orEmpty()
        val buyingDate: LocalDate? = if (buyingDateStr.isNotEmpty()) {
            try {
                LocalDate.parse(buyingDateStr)
            } catch (_: DateTimeParseException) {
                null
            }
        } else {
            null
        }

        val rixoCompany = transportData["rixoCompany"]?.trim()?.takeIf { it.isNotEmpty() }
        val message = transportData["extraMessage"]?.trim()?.takeIf { it.isNotEmpty() }

        val chassisJoined = selectedIds.mapNotNull { id ->
            purchaseRepository.findById(id).orElse(null)?.chassis?.trim()?.takeIf { it.isNotEmpty() }
        }.joinToString(";")

        val row = RixoHistory(
            buyingDate = buyingDate,
            rixoCompany = rixoCompany,
            message = message,
            chassis = chassisJoined.takeIf { it.isNotEmpty() },
        )
        rixoHistoryRepository.save(row)
    }
}
