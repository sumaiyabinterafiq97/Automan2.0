package com.automan.backend.service

import com.automan.backend.model.BookingStatus
import com.automan.backend.repository.BookingRepository
import com.automan.backend.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BookingValidationService(
    private val bookingRepository: BookingRepository,
    private val purchaseRepository: PurchaseRepository
) {
    
    fun validateBookingCompleteness(bookingId: Long): Map<String, Any> {
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { IllegalArgumentException("Booking not found with id: $bookingId") }
        
        val cars = purchaseRepository.findByBookingId(bookingId)
        val validationResults = mutableMapOf<String, Any>()
        
        // Check basic booking info
        validationResults["hasVesselInfo"] = !booking.vesselNo.isNullOrBlank() && !booking.vesselName.isNullOrBlank()
        validationResults["hasConsigneeCountry"] = !booking.consigneeCountry.isNullOrBlank()
        validationResults["hasPolPort"] = !booking.polPort.isNullOrBlank()
        validationResults["hasBookingDate"] = booking.bookingDate != null
        
        // Check if cars are assigned
        validationResults["hasCars"] = cars.isNotEmpty()
        validationResults["carCount"] = cars.size
        
        // Check if all cars have required info
        val carsWithCompleteInfo = cars.count { car ->
            !car.chassis.isNullOrBlank() && 
            !car.carName.isNullOrBlank() && 
            !car.carModelYear.isNullOrBlank()
        }
        validationResults["carsWithCompleteInfo"] = carsWithCompleteInfo
        validationResults["allCarsHaveCompleteInfo"] = carsWithCompleteInfo == cars.size
        
        // Check booking status
        validationResults["isDraft"] = booking.status == BookingStatus.DRAFT
        validationResults["isConfirmed"] = booking.status == BookingStatus.CONFIRMED
        validationResults["isShipped"] = booking.status == BookingStatus.SHIPPED
        
        // Overall completeness score
        val completenessScore = calculateCompletenessScore(validationResults)
        validationResults["completenessScore"] = completenessScore
        validationResults["isComplete"] = completenessScore >= 80.0
        
        return validationResults
    }
    
    fun validateBookingForConfirmation(bookingId: Long): Map<String, Any> {
        val validation = validateBookingCompleteness(bookingId)
        val canConfirm = mutableMapOf<String, Any>()
        
        // Required fields for confirmation
        val requiredFields = listOf(
            "hasVesselInfo",
            "hasConsigneeCountry", 
            "hasPolPort",
            "hasBookingDate",
            "hasCars",
            "allCarsHaveCompleteInfo"
        )
        
        val missingFields = requiredFields.filter { field ->
            validation[field] != true
        }
        
        canConfirm["canConfirm"] = missingFields.isEmpty()
        canConfirm["missingFields"] = missingFields
        canConfirm["validation"] = validation
        
        return canConfirm
    }
    
    fun validateBookingForShipping(bookingId: Long): Map<String, Any> {
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { IllegalArgumentException("Booking not found with id: $bookingId") }
        
        val canShip = mutableMapOf<String, Any>()
        
        // Must be confirmed to ship
        canShip["isConfirmed"] = booking.status == BookingStatus.CONFIRMED
        
        // Check if all required shipping info is present
        canShip["hasVesselInfo"] = !booking.vesselNo.isNullOrBlank() && !booking.vesselName.isNullOrBlank()
        canShip["hasConsigneeCountry"] = !booking.consigneeCountry.isNullOrBlank()
        canShip["hasPolPort"] = !booking.polPort.isNullOrBlank()
        
        val cars = purchaseRepository.findByBookingId(bookingId)
        canShip["hasCars"] = cars.isNotEmpty()
        
        val canShipBooking = canShip["isConfirmed"] == true && 
                           canShip["hasVesselInfo"] == true && 
                           canShip["hasConsigneeCountry"] == true && 
                           canShip["hasPolPort"] == true && 
                           canShip["hasCars"] == true
        
        canShip["canShip"] = canShipBooking
        
        return canShip
    }
    
    fun getBookingValidationSummary(bookingId: Long): Map<String, Any> {
        val validation = validateBookingCompleteness(bookingId)
        val confirmationCheck = validateBookingForConfirmation(bookingId)
        val shippingCheck = validateBookingForShipping(bookingId)
        
        return mapOf(
            "bookingId" to bookingId,
            "completeness" to validation,
            "confirmation" to confirmationCheck,
            "shipping" to shippingCheck,
            "overallStatus" to getOverallStatus(validation, confirmationCheck, shippingCheck)
        )
    }
    
    private fun calculateCompletenessScore(validation: Map<String, Any>): Double {
        val totalChecks = 6.0
        val passedChecks = listOf(
            validation["hasVesselInfo"],
            validation["hasConsigneeCountry"],
            validation["hasPolPort"],
            validation["hasBookingDate"],
            validation["hasCars"],
            validation["allCarsHaveCompleteInfo"]
        ).count { it == true }
        
        return (passedChecks / totalChecks) * 100.0
    }
    
    private fun getOverallStatus(
        completeness: Map<String, Any>,
        confirmation: Map<String, Any>,
        shipping: Map<String, Any>
    ): String {
        return when {
            shipping["canShip"] == true -> "READY_TO_SHIP"
            confirmation["canConfirm"] == true -> "READY_TO_CONFIRM"
            completeness["completenessScore"] as Double >= 60.0 -> "IN_PROGRESS"
            else -> "INCOMPLETE"
        }
    }
}
