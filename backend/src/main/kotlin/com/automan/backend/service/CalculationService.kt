package com.automan.backend.service

import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.model.dto.CalculationResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CalculationService(
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
        
        return CalculationResponse(
            success = true,
            message = "Freight calculation completed",
            totalPrice = totalPrice,
            breakdown = breakdown
        )
    }
    
    fun calculateCAF(request: CalculationRequest): CalculationResponse {
        val country = request.country ?: "DEFAULT"
        val countryRules = countryRulesService.getCountryRules(country)
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
        
        return CalculationResponse(
            success = true,
            message = "Pakistan calculation completed",
            totalPrice = totalPrice,
            breakdown = breakdown
        )
    }
    
    private fun calculateCAFTotal(request: CalculationRequest, countryRules: Map<String, Double>): Double {
        val baseTotal = request.containerPrice + request.shippingCharge + request.wcCharge + 
                       request.inspectionFee + request.fobPrice + request.freightPrice + request.insurance
        
        val multiplier = countryRules["multiplier"] ?: 1.0
        return baseTotal * multiplier
    }
}
