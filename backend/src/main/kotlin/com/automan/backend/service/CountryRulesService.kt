package com.automan.backend.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
open class CountryRulesService {
    
    fun getCountryRules(country: String): Map<String, Double> {
        val normalizedCountry = country.uppercase()
        
        return when (normalizedCountry) {
            "PAKISTAN" -> getPakistanRules()
            "SOUTH AFRICA" -> getSouthAfricaRules()
            "KENYA" -> getKenyaRules()
            "TANZANIA" -> getTanzaniaRules()
            "UGANDA" -> getUgandaRules()
            "GHANA" -> getGhanaRules()
            "NIGERIA" -> getNigeriaRules()
            else -> getDefaultRules()
        }
    }
    
    fun getPakistanRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.15, // 15% markup for Pakistan
            "customDutyRate" to 0.25, // 25% custom duty
            "otherChargesRate" to 0.08, // 8% other charges
            "shippingMultiplier" to 1.1, // 10% shipping markup
            "insuranceMultiplier" to 1.05 // 5% insurance markup
        )
    }
    
    fun getSouthAfricaRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.12, // 12% markup for South Africa
            "customDutyRate" to 0.20, // 20% custom duty
            "otherChargesRate" to 0.06, // 6% other charges
            "shippingMultiplier" to 1.08, // 8% shipping markup
            "insuranceMultiplier" to 1.03 // 3% insurance markup
        )
    }
    
    fun getKenyaRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.18, // 18% markup for Kenya
            "customDutyRate" to 0.30, // 30% custom duty
            "otherChargesRate" to 0.10, // 10% other charges
            "shippingMultiplier" to 1.12, // 12% shipping markup
            "insuranceMultiplier" to 1.06 // 6% insurance markup
        )
    }
    
    fun getTanzaniaRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.20, // 20% markup for Tanzania
            "customDutyRate" to 0.35, // 35% custom duty
            "otherChargesRate" to 0.12, // 12% other charges
            "shippingMultiplier" to 1.15, // 15% shipping markup
            "insuranceMultiplier" to 1.08 // 8% insurance markup
        )
    }
    
    fun getUgandaRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.22, // 22% markup for Uganda
            "customDutyRate" to 0.40, // 40% custom duty
            "otherChargesRate" to 0.15, // 15% other charges
            "shippingMultiplier" to 1.18, // 18% shipping markup
            "insuranceMultiplier" to 1.10 // 10% insurance markup
        )
    }
    
    fun getGhanaRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.16, // 16% markup for Ghana
            "customDutyRate" to 0.25, // 25% custom duty
            "otherChargesRate" to 0.08, // 8% other charges
            "shippingMultiplier" to 1.10, // 10% shipping markup
            "insuranceMultiplier" to 1.05 // 5% insurance markup
        )
    }
    
    fun getNigeriaRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.25, // 25% markup for Nigeria
            "customDutyRate" to 0.45, // 45% custom duty
            "otherChargesRate" to 0.20, // 20% other charges
            "shippingMultiplier" to 1.20, // 20% shipping markup
            "insuranceMultiplier" to 1.12 // 12% insurance markup
        )
    }
    
    fun getDefaultRules(): Map<String, Double> {
        return mapOf(
            "multiplier" to 1.10, // 10% default markup
            "customDutyRate" to 0.15, // 15% default custom duty
            "otherChargesRate" to 0.05, // 5% default other charges
            "shippingMultiplier" to 1.05, // 5% default shipping markup
            "insuranceMultiplier" to 1.02 // 2% default insurance markup
        )
    }
    
    fun getAllSupportedCountries(): List<String> {
        return listOf(
            "PAKISTAN",
            "SOUTH AFRICA", 
            "KENYA",
            "TANZANIA",
            "UGANDA",
            "GHANA",
            "NIGERIA"
        )
    }
    
    fun isCountrySupported(country: String): Boolean {
        return getAllSupportedCountries().contains(country.uppercase())
    }
}
