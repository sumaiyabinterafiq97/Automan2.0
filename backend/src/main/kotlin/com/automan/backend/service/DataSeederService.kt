package com.automan.backend.service

import com.automan.backend.model.*
import com.automan.backend.repository.*
import com.automan.backend.util.Logger
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class DataSeederService(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val purchaseRepository: PurchaseRepository,
    private val rixoPriceRepository: RixoPriceRepository
) : CommandLineRunner {

    private val passwordEncoder = BCryptPasswordEncoder()

    override fun run(vararg args: String?) {
        Logger.debug("Starting database seeding check...")
        
        // TEMPORARILY DISABLED: Skip all seeding to prevent startup crashes
        // Database already has data from previous runs
        Logger.warn("Seeding disabled - using existing database data")
        return
        
        // Original seeding code commented out to prevent transaction issues
        /*
        val userCount = try {
            userRepository.count()
        } catch (e: Exception) {
            Logger.error("Error checking user count: ${e.message}")
            return
        }

        if (userCount > 0) {
            Logger.warn("Database already has data (${userCount} users), skipping seeding")
            return
        }
        */
    }

    private fun seedUsers() {
        Logger.debug("Seeding users...")
        
        val adminUser = User(
            email = "admin@automan.com",
            name = "System Administrator",
            passwordHash = passwordEncoder.encode("Automan!Ship26Tokyo"),
            role = UserRole.ADMIN
        )
        userRepository.save(adminUser)
        Logger.debug("Created admin user: admin@automan.com")
    }

    private fun seedClients() {
        Logger.debug("Seeding clients...")
        
        val client = Client(
            clientNumber = "CL001",
            clientName = "CROWN EAGLE",
            status = ClientStatus.ACTIVE,
            currentBalance = 50000.0
        )
        clientRepository.save(client)
        Logger.debug("Created client: CROWN EAGLE")
    }


    private fun seedRixoPrices() {
        Logger.debug("Seeding Rixo prices...")
        
        val rixoPrices = listOf(
            RixoPrice(
                auctionHouse = "AUCNETVAA (SAKURA)",
                stockLocation = "GLOBAL KAWASAKI",
                rixoCompany = "YAMAZAKI",
                venueId = "V001"
            ),
            RixoPrice(
                auctionHouse = "USS YOKOHAMA",
                stockLocation = "KLC",
                rixoCompany = "KLC",
                venueId = "V002"
            ),
            RixoPrice(
                auctionHouse = "TAA KINKI",
                stockLocation = "GLOBAL HAKATA",
                rixoCompany = "LOGICO",
                venueId = "V001"
            )
        )
        rixoPriceRepository.saveAll(rixoPrices)
        Logger.debug("Created ${rixoPrices.size} Rixo prices")
    }

    private fun seedPurchases() {
        Logger.debug("Seeding purchases...")
        
        val client = clientRepository.findByClientNumber("CL001")
        if (client == null) {
            Logger.error("Client not found, cannot create purchases")
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
                country = "Japan",
                distance = "50000km",
                options = "AC, Power Steering",
                notes = "Good condition, minor scratches | POD: Tokyo",
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
                country = "Japan",
                distance = "30000km",
                options = "AC, Power Steering, Navigation",
                notes = "Excellent condition, low mileage | POD: Osaka",
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
                country = "Japan",
                distance = "25000km",
                options = "AC, Power Steering, Navigation, Sunroof",
                notes = "Premium model, excellent condition | POD: Fukuoka",
                clientId = client.id,
                isPackageMode = false
            )
        )
        
        purchaseRepository.saveAll(purchases)
        Logger.debug("Created ${purchases.size} purchases")
    }
}
