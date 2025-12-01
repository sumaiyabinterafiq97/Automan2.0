package com.automan.backend.service

import com.automan.backend.model.*
import com.automan.backend.repository.*
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class DataSeederService(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val purchaseRepository: PurchaseRepository,
    private val vesselRepository: VesselRepository,
    private val rixoPriceRepository: RixoPriceRepository
) : CommandLineRunner {

    private val passwordEncoder = BCryptPasswordEncoder()

    @Transactional
    override fun run(vararg args: String?) {
        println("🌱 Starting database seeding...")
        
        // Only seed if database is empty
        if (userRepository.count() > 0) {
            println("📊 Database already has data, skipping seeding")
            return
        }

        try {
            seedUsers()
            seedClients()
            seedVessels()
            seedRixoPrices()
            seedPurchases()
            
            println("✅ Database seeding completed successfully!")
        } catch (e: Exception) {
            println("❌ Error during database seeding: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun seedUsers() {
        println("👤 Seeding users...")
        
        val adminUser = User(
            email = "admin@automan.com",
            name = "System Administrator",
            passwordHash = passwordEncoder.encode("admin123"),
            role = UserRole.ADMIN
        )
        userRepository.save(adminUser)
        println("✅ Created admin user: admin@automan.com (password: admin123)")
    }

    private fun seedClients() {
        println("🏢 Seeding clients...")
        
        val client = Client(
            clientNumber = "CL001",
            clientName = "CROWN EAGLE",
            phone = "+1-555-0123",
            address = "123 Business Ave, Tokyo, Japan",
            status = ClientStatus.ACTIVE,
            currentBalance = 50000.0
        )
        clientRepository.save(client)
        println("✅ Created client: CROWN EAGLE")
    }

    private fun seedVessels() {
        println("🚢 Seeding vessels...")
        
        val vessels = listOf(
            Vessel(
                vesselNo = "V001",
                vesselName = "OCEAN EXPRESS",
                company = "Maritime Shipping Co."
            ),
            Vessel(
                vesselNo = "V002", 
                vesselName = "PACIFIC CARRIER",
                company = "Global Transport Ltd."
            )
        )
        vesselRepository.saveAll(vessels)
        println("✅ Created ${vessels.size} vessels")
    }

    private fun seedRixoPrices() {
        println("💰 Seeding Rixo prices...")
        
        val rixoPrices = listOf(
            RixoPrice(
                auctionHouse = "AUCNETVAA (SAKURA)",
                stockLocation = "GLOBAL KAWASAKI",
                rixoCompany = "YAMAZAKI",
                rixoPrice = "138000",
                shipmentSize = "20ft",
                venueId = "V001"
            ),
            RixoPrice(
                auctionHouse = "USS YOKOHAMA",
                stockLocation = "KLC",
                rixoCompany = "KLC",
                rixoPrice = "460000",
                shipmentSize = "40ft",
                venueId = "V002"
            ),
            RixoPrice(
                auctionHouse = "TAA KINKI",
                stockLocation = "GLOBAL HAKATA",
                rixoCompany = "LOGICO",
                rixoPrice = "1516000",
                shipmentSize = "20ft",
                venueId = "V001"
            )
        )
        rixoPriceRepository.saveAll(rixoPrices)
        println("✅ Created ${rixoPrices.size} Rixo prices")
    }

    private fun seedPurchases() {
        println("🚗 Seeding purchases...")
        
        val client = clientRepository.findByClientNumber("CL001")
        if (client == null) {
            println("❌ Client not found, cannot create purchases")
            return
        }

        val purchases = listOf(
            Purchase(
                chassis = "VY12-265058",
                carName = "NV 150 AD",
                auctionHouse = "AUCNETVAA (SAKURA)",
                stockLocation = "GLOBAL KAWASAKI",
                clientName = "CROWN EAGLE",
                rixoCompany = "YAMAZAKI",
                rixoPrice = "138000",
                price = "138000",
                date = "June 2, 2025",
                brand = "NISSAN",
                carModelYear = "2012",
                color = "White",
                grade = "A",
                fuel = "Gasoline",
                door = "4",
                seat = "5",
                displacement = "1500cc",
                country = "Japan",
                destination = "Tokyo",
                distance = "50000km",
                options = "AC, Power Steering",
                notes = "Good condition, minor scratches",
                clientId = client.id,
                isPackageMode = false
            ),
            Purchase(
                chassis = "ANH20-8170371",
                carName = "VELLFIRE",
                auctionHouse = "USS YOKOHAMA",
                stockLocation = "KLC",
                clientName = "CROWN EAGLE",
                rixoCompany = "KLC",
                rixoPrice = "460000",
                price = "460000",
                date = "June 3, 2025",
                brand = "TOYOTA",
                carModelYear = "2020",
                color = "Black",
                grade = "A",
                fuel = "Hybrid",
                door = "5",
                seat = "7",
                displacement = "2400cc",
                country = "Japan",
                destination = "Osaka",
                distance = "30000km",
                options = "AC, Power Steering, Navigation",
                notes = "Excellent condition, low mileage",
                clientId = client.id,
                isPackageMode = false
            ),
            Purchase(
                chassis = "AVU65-0007399",
                carName = "HARRIER",
                auctionHouse = "TAA KINKI",
                stockLocation = "GLOBAL HAKATA",
                clientName = "CROWN EAGLE",
                rixoCompany = "LOGICO",
                rixoPrice = "1516000",
                price = "1516000",
                date = "June 4, 2025",
                brand = "TOYOTA",
                carModelYear = "2021",
                color = "Silver",
                grade = "A",
                fuel = "Gasoline",
                door = "5",
                seat = "5",
                displacement = "2000cc",
                country = "Japan",
                destination = "Fukuoka",
                distance = "25000km",
                options = "AC, Power Steering, Navigation, Sunroof",
                notes = "Premium model, excellent condition",
                clientId = client.id,
                isPackageMode = false
            )
        )
        
        purchaseRepository.saveAll(purchases)
        println("✅ Created ${purchases.size} purchases")
    }
}
