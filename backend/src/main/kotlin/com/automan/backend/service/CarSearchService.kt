package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.dto.CarInfo
import com.automan.backend.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CarSearchService(
    private val purchaseRepository: PurchaseRepository
) {
    
    @Transactional(readOnly = true)
    fun searchUnshippedCars(
        consigneeCountry: String? = null,
        polPort: String? = null,
        chassisSearch: String? = null
    ): List<CarInfo> {
        val cars = when {
            consigneeCountry != null && polPort != null -> {
                purchaseRepository.findUnshippedCarsByCountryAndPolPort(consigneeCountry, polPort)
            }
            consigneeCountry != null -> {
                purchaseRepository.findUnshippedCarsByCountry(consigneeCountry)
            }
            polPort != null -> {
                purchaseRepository.findUnshippedCarsByPolPort(polPort)
            }
            chassisSearch != null -> {
                purchaseRepository.findUnshippedCarsByChassisContaining(chassisSearch)
            }
            else -> {
                purchaseRepository.findUnshippedCars()
            }
        }
        
        return cars.map { mapToCarInfo(it) }
    }
    
    @Transactional(readOnly = true)
    fun getCarsByBooking(bookingId: Long): List<CarInfo> {
        val cars = purchaseRepository.findByBookingId(bookingId)
        return cars.map { mapToCarInfo(it) }
    }
    
    fun addCarsToBooking(bookingId: Long, carIds: List<Long>): Int {
        return purchaseRepository.updateBookingIdForCars(bookingId, carIds)
    }
    
    fun removeCarsFromBooking(bookingId: Long): Int {
        return purchaseRepository.removeCarsFromBooking(bookingId)
    }
    
    @Transactional(readOnly = true)
    fun getUnshippedCarsCount(): Long {
        return purchaseRepository.findUnshippedCars().size.toLong()
    }
    
    @Transactional(readOnly = true)
    fun getUnshippedCarsCountByCountry(country: String): Long {
        return purchaseRepository.findUnshippedCarsByCountry(country).size.toLong()
    }
    
    @Transactional(readOnly = true)
    fun getUnshippedCarsCountByPolPort(polPort: String): Long {
        return purchaseRepository.findUnshippedCarsByPolPort(polPort).size.toLong()
    }
    
    @Transactional(readOnly = true)
    fun getBookingCarCount(bookingId: Long): Long {
        return purchaseRepository.findByBookingId(bookingId).size.toLong()
    }
    
    private fun mapToCarInfo(purchase: Purchase): CarInfo {
        return CarInfo(
            id = purchase.id!!,
            chassis = purchase.chassis,
            carName = purchase.carName,
            carModelYear = purchase.carModelYear,
            brand = purchase.brand
        )
    }
}
