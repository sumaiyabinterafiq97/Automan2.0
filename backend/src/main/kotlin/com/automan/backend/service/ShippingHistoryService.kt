package com.automan.backend.service

import com.automan.backend.dto.ShippingHistoryBatchRequest
import com.automan.backend.dto.ShippingHistoryInvoiceHeaderDto
import com.automan.backend.dto.ShippingHistoryInvoiceLineDto
import com.automan.backend.dto.ShippingHistoryInvoiceSliceDto
import com.automan.backend.dto.ShippingHistoryRowDto
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.ShippingHistoryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class ShippingHistoryService(
    private val shippingHistoryRepository: ShippingHistoryRepository,
    private val purchaseService: PurchaseService,
) {

    fun listAllRows(): List<ShippingHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        return shippingHistoryRepository.findAll(sort).map { e ->
            ShippingHistoryRowDto(
                id = e.id ?: 0L,
                country = e.country,
                consignee = e.consignee,
                shipmentDate = e.shipmentDate?.toString(),
                pol = e.pol,
                pod = e.pod,
                bookingId = e.bookingId,
                vessel = e.vessel,
                priceType = e.priceType,
                chassis = e.chassis,
                clientName = e.clientName,
                amount = e.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                createdAt = e.createdAt?.toString(),
            )
        }
    }

    @Transactional
    fun saveBatch(request: ShippingHistoryBatchRequest): Int {
        if (request.items.isEmpty()) {
            throw IllegalArgumentException("items must not be empty")
        }
        val shipmentDate: LocalDate? = request.shipmentDate?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            try {
                LocalDate.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        val priceType = request.priceType?.trim()?.takeIf { it.isNotEmpty() }
        val bookingKey = request.bookingId?.trim().orEmpty()
        val country = request.country?.trim()?.takeIf { it.isNotEmpty() }
        val consignee = request.consignee?.trim()?.takeIf { it.isNotEmpty() }
        val pol = request.pol?.trim()?.takeIf { it.isNotEmpty() }
        val pod = request.pod?.trim()?.takeIf { it.isNotEmpty() }
        val vessel = request.vessel?.trim()?.takeIf { it.isNotEmpty() }
        val bookingIdStored = bookingKey.takeIf { it.isNotEmpty() }

        var count = 0
        for (item in request.items) {
            val chassis = item.chassis.trim()
            if (chassis.isEmpty()) {
                throw IllegalArgumentException("chassis must not be blank")
            }
            val fromPurchase = purchaseService.getPurchaseByChassis(chassis)
                ?.clientName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val fromRequest = item.clientName?.trim()?.takeIf { it.isNotEmpty() }
            val clientName = fromPurchase ?: fromRequest
            val amount = item.amount ?: BigDecimal.ZERO

            val existing = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)
            val toSave = if (existing != null) {
                existing.copy(
                    country = country,
                    consignee = consignee,
                    shipmentDate = shipmentDate,
                    pol = pol,
                    pod = pod,
                    bookingId = bookingIdStored,
                    vessel = vessel,
                    priceType = priceType,
                    chassis = chassis,
                    clientName = clientName,
                    amount = amount,
                    createdAt = existing.createdAt,
                )
            } else {
                ShippingHistory(
                    country = country,
                    consignee = consignee,
                    shipmentDate = shipmentDate,
                    pol = pol,
                    pod = pod,
                    bookingId = bookingIdStored,
                    vessel = vessel,
                    priceType = priceType,
                    chassis = chassis,
                    clientName = clientName,
                    amount = amount,
                )
            }
            shippingHistoryRepository.save(toSave)
            count++
        }
        return count
    }

    fun distinctVesselsForInvoiceClient(clientName: String): List<String> {
        val key = clientName.trim()
        if (key.isEmpty()) return emptyList()
        return shippingHistoryRepository.findDistinctVesselsForInvoiceClient(key)
    }

    /**
     * Invoice lines for client + vessel. Excludes chassis whose purchase has invoice_confirmed true
     * (already invoiced). Header is taken from the first included row only.
     */
    fun invoiceSlice(clientName: String, vessel: String): ShippingHistoryInvoiceSliceDto {
        val cn = clientName.trim()
        val v = vessel.trim()
        val rows = shippingHistoryRepository.findInvoiceRowsOrderByIdAsc(cn, v)
        if (rows.isEmpty()) {
            return ShippingHistoryInvoiceSliceDto(
                header = ShippingHistoryInvoiceHeaderDto(),
                lines = emptyList(),
            )
        }
        val included = rows.mapNotNull { row ->
            val p = purchaseService.getPurchaseByChassis(row.chassis)
            if (p?.invoiceConfirmed == true) null else row to p
        }
        if (included.isEmpty()) {
            return ShippingHistoryInvoiceSliceDto(
                header = ShippingHistoryInvoiceHeaderDto(),
                lines = emptyList(),
            )
        }
        val first = included.first().first
        val header = ShippingHistoryInvoiceHeaderDto(
            shipmentDate = first.shipmentDate?.toString(),
            pol = first.pol,
            pod = first.pod,
            priceType = first.priceType,
        )
        val lines = included.map { (row, p) ->
            ShippingHistoryInvoiceLineDto(
                shippingHistoryId = row.id ?: 0L,
                chassis = row.chassis,
                amount = row.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                carName = p?.carName,
                carModelYear = p?.carModelYear,
                purchaseId = p?.id,
            )
        }
        return ShippingHistoryInvoiceSliceDto(header, lines)
    }
}
