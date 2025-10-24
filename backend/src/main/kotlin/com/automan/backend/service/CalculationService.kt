package com.automan.backend.service

import com.automan.backend.model.BookingCalculation
import com.automan.backend.model.CalculationType
import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.model.dto.CalculationResponse
import com.automan.backend.repository.BookingCalculationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class CalculationService(
    private val bookingCalculationRepository: BookingCalculationRepository,
    private val countryRulesService: CountryRulesService
) {
    
    fun calculateFreight(request: CalculationRequest): CalculationResponse {
        val totalPrice = request.containerPrice + request.shippingCharge + request.wcCharge + 
                        request.inspectionFee + request.fobPrice + request.freightPrice + request.insurance
        
        val breakdown = mapOf(
            "containerPrice" to request.containerPrice,
            "shippingCharge" to request.shippingCharge,
            "wcCharge" to request.wcCharge,
            "inspectionFee" to request.inspectionFee,
            "fobPrice" to request.fobPrice,
            "freightPrice" to request.freightPrice,
            "insurance" to request.insurance
        )
        
        // Save calculation
        saveCalculation(request.bookingId, CalculationType.FREIGHT, request, totalPrice)
        
        return CalculationResponse(
            success = true,
            message = "Freight calculation completed",
            totalPrice = totalPrice,
            breakdown = breakdown
        )
    }
    
    fun calculateCAF(request: CalculationRequest): CalculationResponse {
        val countryRules = countryRulesService.getCountryRules(request.bookingId)
        val totalPrice = calculateCAFTotal(request, countryRules)
        
        val breakdown = mapOf(
            "containerPrice" to request.containerPrice,
            "shippingCharge" to request.shippingCharge,
            "wcCharge" to request.wcCharge,
            "inspectionFee" to request.inspectionFee,
            "fobPrice" to request.fobPrice,
            "freightPrice" to request.freightPrice,
            "insurance" to request.insurance,
            "countryMultiplier" to (countryRules["multiplier"] ?: 1.0)
        )
        
        // Save calculation
        saveCalculation(request.bookingId, CalculationType.CAF, request, totalPrice)
        
        return CalculationResponse(
            success = true,
            message = "C&F calculation completed",
            totalPrice = totalPrice,
            breakdown = breakdown
        )
    }
    
    fun calculateFOB(request: CalculationRequest): CalculationResponse {
        val totalPrice = request.containerPrice + request.shippingCharge + request.wcCharge + 
                        request.inspectionFee + request.fobPrice + request.freightPrice + request.insurance
        
        val breakdown = mapOf(
            "containerPrice" to request.containerPrice,
            "shippingCharge" to request.shippingCharge,
            "wcCharge" to request.wcCharge,
            "inspectionFee" to request.inspectionFee,
            "fobPrice" to request.fobPrice,
            "freightPrice" to request.freightPrice,
            "insurance" to request.insurance
        )
        
        // Save calculation
        saveCalculation(request.bookingId, CalculationType.FOB, request, totalPrice)
        
        return CalculationResponse(
            success = true,
            message = "FOB calculation completed",
            totalPrice = totalPrice,
            breakdown = breakdown
        )
    }
    
    fun calculatePakistan(request: CalculationRequest): CalculationResponse {
        if (!request.packageOption) {
            throw IllegalArgumentException("Pakistan calculation requires package option to be enabled")
        }
        
        val pakistanRules = countryRulesService.getPakistanRules()
        val baseTotal = request.containerPrice + request.shippingCharge + request.wcCharge + 
                       request.inspectionFee + request.fobPrice + request.freightPrice + request.insurance
        
        val customDuty = baseTotal * (pakistanRules["customDutyRate"] ?: 0.1)
        val otherCharges = baseTotal * (pakistanRules["otherChargesRate"] ?: 0.05)
        val totalPrice = baseTotal + customDuty + otherCharges
        
        val breakdown = mapOf(
            "containerPrice" to request.containerPrice,
            "shippingCharge" to request.shippingCharge,
            "wcCharge" to request.wcCharge,
            "inspectionFee" to request.inspectionFee,
            "fobPrice" to request.fobPrice,
            "freightPrice" to request.freightPrice,
            "insurance" to request.insurance,
            "customDuty" to customDuty,
            "otherCharges" to otherCharges,
            "baseTotal" to baseTotal
        )
        
        // Save calculation
        saveCalculation(request.bookingId, CalculationType.PAKISTAN, request, totalPrice)
        
        return CalculationResponse(
            success = true,
            message = "Pakistan calculation completed",
            totalPrice = totalPrice,
            breakdown = breakdown
        )
    }
    
    @Transactional(readOnly = true)
    fun getCalculationsByBooking(bookingId: Long): List<BookingCalculation> {
        return bookingCalculationRepository.findByBookingId(bookingId)
    }
    
    @Transactional(readOnly = true)
    fun getCalculationByType(bookingId: Long, type: CalculationType): BookingCalculation? {
        return bookingCalculationRepository.findByBookingIdAndCalculationType(bookingId, type)
    }
    
    @Transactional(readOnly = true)
    fun getTotalPriceByBooking(bookingId: Long): Double? {
        return bookingCalculationRepository.sumTotalPriceByBookingId(bookingId)
    }
    
    private fun calculateCAFTotal(request: CalculationRequest, countryRules: Map<String, Double>): Double {
        val baseTotal = request.containerPrice + request.shippingCharge + request.wcCharge + 
                       request.inspectionFee + request.fobPrice + request.freightPrice + request.insurance
        
        val multiplier = countryRules["multiplier"] ?: 1.0
        return baseTotal * multiplier
    }
    
    private fun saveCalculation(
        bookingId: Long, 
        type: CalculationType, 
        request: CalculationRequest, 
        totalPrice: Double
    ) {
        val calculation = BookingCalculation(
            bookingId = bookingId,
            calculationType = type,
            containerPrice = BigDecimal.valueOf(request.containerPrice),
            shippingCharge = BigDecimal.valueOf(request.shippingCharge),
            wcCharge = BigDecimal.valueOf(request.wcCharge),
            inspectionFee = BigDecimal.valueOf(request.inspectionFee),
            fobPrice = BigDecimal.valueOf(request.fobPrice),
            freightPrice = BigDecimal.valueOf(request.freightPrice),
            insurance = BigDecimal.valueOf(request.insurance),
            totalPrice = BigDecimal.valueOf(totalPrice)
        )
        
        bookingCalculationRepository.save(calculation)
    }
}
