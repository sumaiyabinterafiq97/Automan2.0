package com.automan.backend.service

import com.automan.backend.model.Vessel
import com.automan.backend.repository.VesselRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class VesselService(
    private val vesselRepository: VesselRepository
) {
    
    @Transactional(readOnly = true)
    fun getAllVessels(): List<Vessel> {
        return vesselRepository.findAllByOrderByVesselNameAsc()
    }
    
    @Transactional(readOnly = true)
    fun getVesselByNo(vesselNo: String): Vessel? {
        return vesselRepository.findByVesselNo(vesselNo)
    }
    
    @Transactional(readOnly = true)
    fun getVesselsByCompany(company: String): List<Vessel> {
        return vesselRepository.findByCompanyOrderByVesselNameAsc(company)
    }
    
    @Transactional(readOnly = true)
    fun searchVesselsByName(name: String): List<Vessel> {
        return vesselRepository.findByVesselNameContainingIgnoreCaseOrderByVesselNameAsc(name)
    }
    
    @Transactional(readOnly = true)
    fun getAllCompanies(): List<String> {
        return vesselRepository.findDistinctCompanies()
    }
    
    fun createVessel(vesselNo: String, vesselName: String, company: String?): Vessel {
        if (vesselRepository.existsByVesselNo(vesselNo)) {
            throw IllegalArgumentException("Vessel with number $vesselNo already exists")
        }
        
        val vessel = Vessel(
            vesselNo = vesselNo,
            vesselName = vesselName,
            company = company
        )
        
        return vesselRepository.save(vessel)
    }
    
    fun updateVessel(vesselNo: String, vesselName: String, company: String?): Vessel {
        val existingVessel = vesselRepository.findByVesselNo(vesselNo)
            ?: throw IllegalArgumentException("Vessel not found with number: $vesselNo")
        
        val updatedVessel = existingVessel.copy(
            vesselName = vesselName,
            company = company
        )
        
        return vesselRepository.save(updatedVessel)
    }
    
    fun deleteVessel(vesselNo: String): Boolean {
        if (!vesselRepository.existsByVesselNo(vesselNo)) {
            throw IllegalArgumentException("Vessel not found with number: $vesselNo")
        }
        
        vesselRepository.deleteById(vesselNo)
        return true
    }
    
    @Transactional(readOnly = true)
    fun vesselExists(vesselNo: String): Boolean {
        return vesselRepository.existsByVesselNo(vesselNo)
    }
}
