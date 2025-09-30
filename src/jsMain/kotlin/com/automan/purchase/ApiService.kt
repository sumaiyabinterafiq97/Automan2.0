package com.automan.purchase

import kotlinx.coroutines.await
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Headers
import org.w3c.files.File

@Serializable
data class ApiPurchase(
    val id: Long? = null,
    val date: String? = null,
    val lotNumber: String,
    val chassis: String,
    val carModelYear: String? = null,
    val brand: String? = null,
    val carName: String? = null,
    val grade: String? = null,
    val rank: String? = null,
    val color: String? = null,
    val displacement: String? = null,
    val fuel: String? = null,
    val seat: String? = null,
    val door: String? = null,
    val distance: String? = null,
    val options: String? = null,
    val auctionNo: String? = null,
    val auctionHouse: String? = null,
    val stockLocation: String? = null,
    val rixoCompany: String? = null,
    val clientName: String? = null,
    val country: String? = null,
    val price: String? = null,
    val auctionFee: String? = null,
    val recycleFee: String? = null,
    val roadTax: String? = null,
    val totalPrice: String? = null,
    val paymentDate: String? = null,
    val rixoRequested: String? = null,
    val rixoConfirmed: String? = null,
    val notes: String? = null,
    val shipmentDate: String? = null,
    val blNo: String? = null,
    val vesselNo: String? = null,
    val destination: String? = null,
    val shipmentCharges: String? = null,
    val freight: String? = null,
    val storageCharges: String? = null,
    val miscCharges: String? = null,
    val inspectionFee: String? = null,
    val commission: String? = null,
    val rixoPrice: String? = null,
    val repairCompany: String? = null,
    val repairCharges: String? = null
)

@Serializable
data class ImportResponse(
    val success: Boolean,
    val message: String,
    val importedCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val totalProcessed: Int,
    val importedPurchases: List<ApiPurchase> = emptyList(),
    val duplicateDetails: List<String> = emptyList(),
    val errorDetails: List<String> = emptyList()
)

object ApiService {
    private const val BASE_URL = "/api"
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun fetchPurchases(): List<Purchase> {
        val response = window.fetch("$BASE_URL/purchases").await()
        if (!response.ok) {
            throw Exception("Failed to fetch purchases: ${response.status}")
        }
        
        val jsonString = response.text().await()
        val apiPurchases = json.decodeFromString<List<ApiPurchase>>(jsonString)
        
        return apiPurchases.map { apiPurchase ->
            Purchase(
                id = apiPurchase.id,
                date = apiPurchase.date,
                lotNumber = apiPurchase.lotNumber,
                chassis = apiPurchase.chassis,
                carModelYear = apiPurchase.carModelYear,
                brand = apiPurchase.brand,
                carName = apiPurchase.carName,
                grade = apiPurchase.grade,
                rank = apiPurchase.rank,
                color = apiPurchase.color,
                displacement = apiPurchase.displacement,
                fuel = apiPurchase.fuel,
                seat = apiPurchase.seat,
                door = apiPurchase.door,
                distance = apiPurchase.distance,
                options = apiPurchase.options,
                auctionNo = apiPurchase.auctionNo,
                auctionHouse = apiPurchase.auctionHouse,
                stockLocation = apiPurchase.stockLocation,
                rixoCompany = apiPurchase.rixoCompany,
                clientName = apiPurchase.clientName,
                country = apiPurchase.country,
                price = apiPurchase.price,
                auctionFee = apiPurchase.auctionFee,
                recycleFee = apiPurchase.recycleFee,
                roadTax = apiPurchase.roadTax,
                totalPrice = apiPurchase.totalPrice,
                paymentDate = apiPurchase.paymentDate,
                rixoRequested = apiPurchase.rixoRequested,
                rixoConfirmed = apiPurchase.rixoConfirmed,
                notes = apiPurchase.notes,
                shipmentDate = apiPurchase.shipmentDate,
                blNo = apiPurchase.blNo,
                vesselNo = apiPurchase.vesselNo,
                destination = apiPurchase.destination,
                shipmentCharges = apiPurchase.shipmentCharges,
                freight = apiPurchase.freight,
                storageCharges = apiPurchase.storageCharges,
                miscCharges = apiPurchase.miscCharges,
                inspectionFee = apiPurchase.inspectionFee,
                commission = apiPurchase.commission,
                rixoPrice = apiPurchase.rixoPrice,
                repairCompany = apiPurchase.repairCompany,
                repairCharges = apiPurchase.repairCharges
            )
        }
    }
    
    suspend fun createPurchase(purchase: Purchase): Purchase {
        val apiPurchase = ApiPurchase(
            date = purchase.date,
            lotNumber = purchase.lotNumber,
            chassis = purchase.chassis,
            carModelYear = purchase.carModelYear,
            brand = purchase.brand,
            carName = purchase.carName,
            grade = purchase.grade,
            rank = purchase.rank,
            color = purchase.color,
            displacement = purchase.displacement,
            fuel = purchase.fuel,
            seat = purchase.seat,
            door = purchase.door,
            distance = purchase.distance,
            options = purchase.options,
            auctionNo = purchase.auctionNo,
            auctionHouse = purchase.auctionHouse,
            stockLocation = purchase.stockLocation,
            rixoCompany = purchase.rixoCompany,
            clientName = purchase.clientName,
            country = purchase.country,
            price = purchase.price,
            auctionFee = purchase.auctionFee,
            recycleFee = purchase.recycleFee,
            roadTax = purchase.roadTax,
            totalPrice = purchase.totalPrice,
            paymentDate = purchase.paymentDate,
            rixoRequested = purchase.rixoRequested,
            rixoConfirmed = purchase.rixoConfirmed,
            notes = purchase.notes,
            shipmentDate = purchase.shipmentDate,
            blNo = purchase.blNo,
            vesselNo = purchase.vesselNo,
            destination = purchase.destination,
            shipmentCharges = purchase.shipmentCharges,
            freight = purchase.freight,
            storageCharges = purchase.storageCharges,
            miscCharges = purchase.miscCharges,
            inspectionFee = purchase.inspectionFee,
            commission = purchase.commission,
            rixoPrice = purchase.rixoPrice,
            repairCompany = purchase.repairCompany,
            repairCharges = purchase.repairCharges
        )
        
        val jsonString = json.encodeToString(ApiPurchase.serializer(), apiPurchase)
        
        val headers = Headers().apply { append("Content-Type", "application/json") }
        val init = RequestInit(
            method = "POST",
            headers = headers,
            body = jsonString
        )
        
        val response = window.fetch("$BASE_URL/purchases", init).await()
        if (!response.ok) throw Exception("Failed to create purchase: ${response.status}")
        
        val created = json.decodeFromString<ApiPurchase>(response.text().await())
        return Purchase(
            id = created.id,
            date = created.date,
            lotNumber = created.lotNumber,
            chassis = created.chassis,
            carModelYear = created.carModelYear,
            brand = created.brand,
            carName = created.carName,
            grade = created.grade,
            rank = created.rank,
            color = created.color,
            displacement = created.displacement,
            fuel = created.fuel,
            seat = created.seat,
            door = created.door,
            distance = created.distance,
            options = created.options,
            auctionNo = created.auctionNo,
                auctionHouse = created.auctionHouse,
            stockLocation = created.stockLocation,
            rixoCompany = created.rixoCompany,
            clientName = created.clientName,
            country = created.country,
            price = created.price,
            auctionFee = created.auctionFee,
            recycleFee = created.recycleFee,
            roadTax = created.roadTax,
            totalPrice = created.totalPrice,
            paymentDate = created.paymentDate,
            rixoRequested = created.rixoRequested,
            rixoConfirmed = created.rixoConfirmed,
            notes = created.notes,
            shipmentDate = created.shipmentDate,
            blNo = created.blNo,
            vesselNo = created.vesselNo,
            destination = created.destination,
            shipmentCharges = created.shipmentCharges,
            freight = created.freight,
            storageCharges = created.storageCharges,
            miscCharges = created.miscCharges,
            inspectionFee = created.inspectionFee,
            commission = created.commission,
            rixoPrice = created.rixoPrice,
            repairCompany = created.repairCompany,
            repairCharges = created.repairCharges
        )
    }
    
    suspend fun searchPurchases(query: String): List<Purchase> {
        val encodedQuery = query.replace(" ", "%20")
        val response = window.fetch("$BASE_URL/purchases/search?query=$encodedQuery").await()
        if (!response.ok) {
            throw Exception("Failed to search purchases: ${response.status}")
        }
        
        val jsonString = response.text().await()
        val apiPurchases = json.decodeFromString<List<ApiPurchase>>(jsonString)
        
        return apiPurchases.map { apiPurchase ->
            Purchase(
                id = apiPurchase.id,
                date = apiPurchase.date,
                lotNumber = apiPurchase.lotNumber,
                chassis = apiPurchase.chassis,
                carModelYear = apiPurchase.carModelYear,
                brand = apiPurchase.brand,
                carName = apiPurchase.carName,
                grade = apiPurchase.grade,
                rank = apiPurchase.rank,
                color = apiPurchase.color,
                displacement = apiPurchase.displacement,
                fuel = apiPurchase.fuel,
                seat = apiPurchase.seat,
                door = apiPurchase.door,
                distance = apiPurchase.distance,
                options = apiPurchase.options,
                auctionNo = apiPurchase.auctionNo,
                auctionHouse = apiPurchase.auctionHouse,
                stockLocation = apiPurchase.stockLocation,
                rixoCompany = apiPurchase.rixoCompany,
                clientName = apiPurchase.clientName,
                country = apiPurchase.country,
                price = apiPurchase.price,
                auctionFee = apiPurchase.auctionFee,
                recycleFee = apiPurchase.recycleFee,
                roadTax = apiPurchase.roadTax,
                totalPrice = apiPurchase.totalPrice,
                paymentDate = apiPurchase.paymentDate,
                rixoRequested = apiPurchase.rixoRequested,
                rixoConfirmed = apiPurchase.rixoConfirmed,
                notes = apiPurchase.notes,
                shipmentDate = apiPurchase.shipmentDate,
                blNo = apiPurchase.blNo,
                vesselNo = apiPurchase.vesselNo,
                destination = apiPurchase.destination,
                shipmentCharges = apiPurchase.shipmentCharges,
                freight = apiPurchase.freight,
                storageCharges = apiPurchase.storageCharges,
                miscCharges = apiPurchase.miscCharges,
                inspectionFee = apiPurchase.inspectionFee,
                commission = apiPurchase.commission,
                rixoPrice = apiPurchase.rixoPrice,
                repairCompany = apiPurchase.repairCompany,
                repairCharges = apiPurchase.repairCharges
            )
        }
    }
    
    suspend fun sortPurchases(field: String, order: String): List<Purchase> {
        val response = window.fetch("$BASE_URL/purchases/sort?field=$field&order=$order").await()
        if (!response.ok) {
            throw Exception("Failed to sort purchases: ${response.status}")
        }
        
        val jsonString = response.text().await()
        val apiPurchases = json.decodeFromString<List<ApiPurchase>>(jsonString)
        
        return apiPurchases.map { apiPurchase ->
            Purchase(
                id = apiPurchase.id,
                date = apiPurchase.date,
                lotNumber = apiPurchase.lotNumber,
                chassis = apiPurchase.chassis,
                carModelYear = apiPurchase.carModelYear,
                brand = apiPurchase.brand,
                carName = apiPurchase.carName,
                grade = apiPurchase.grade,
                rank = apiPurchase.rank,
                color = apiPurchase.color,
                displacement = apiPurchase.displacement,
                fuel = apiPurchase.fuel,
                seat = apiPurchase.seat,
                door = apiPurchase.door,
                distance = apiPurchase.distance,
                options = apiPurchase.options,
                auctionNo = apiPurchase.auctionNo,
                auctionHouse = apiPurchase.auctionHouse,
                stockLocation = apiPurchase.stockLocation,
                rixoCompany = apiPurchase.rixoCompany,
                clientName = apiPurchase.clientName,
                country = apiPurchase.country,
                price = apiPurchase.price,
                auctionFee = apiPurchase.auctionFee,
                recycleFee = apiPurchase.recycleFee,
                roadTax = apiPurchase.roadTax,
                totalPrice = apiPurchase.totalPrice,
                paymentDate = apiPurchase.paymentDate,
                rixoRequested = apiPurchase.rixoRequested,
                rixoConfirmed = apiPurchase.rixoConfirmed,
                notes = apiPurchase.notes,
                shipmentDate = apiPurchase.shipmentDate,
                blNo = apiPurchase.blNo,
                vesselNo = apiPurchase.vesselNo,
                destination = apiPurchase.destination,
                shipmentCharges = apiPurchase.shipmentCharges,
                freight = apiPurchase.freight,
                storageCharges = apiPurchase.storageCharges,
                miscCharges = apiPurchase.miscCharges,
                inspectionFee = apiPurchase.inspectionFee,
                commission = apiPurchase.commission,
                rixoPrice = apiPurchase.rixoPrice,
                repairCompany = apiPurchase.repairCompany,
                repairCharges = apiPurchase.repairCharges
            )
        }
    }
    
    suspend fun updatePurchase(purchase: Purchase): Purchase {
        val apiPurchase = ApiPurchase(
            id = purchase.id,
            date = purchase.date,
            lotNumber = purchase.lotNumber,
            chassis = purchase.chassis,
            carModelYear = purchase.carModelYear,
            brand = purchase.brand,
            carName = purchase.carName,
            grade = purchase.grade,
            rank = purchase.rank,
            color = purchase.color,
            displacement = purchase.displacement,
            fuel = purchase.fuel,
            seat = purchase.seat,
            door = purchase.door,
            distance = purchase.distance,
            options = purchase.options,
            auctionNo = purchase.auctionNo,
            auctionHouse = purchase.auctionHouse,
            stockLocation = purchase.stockLocation,
            rixoCompany = purchase.rixoCompany,
            clientName = purchase.clientName,
            country = purchase.country,
            price = purchase.price,
            auctionFee = purchase.auctionFee,
            recycleFee = purchase.recycleFee,
            roadTax = purchase.roadTax,
            totalPrice = purchase.totalPrice,
            paymentDate = purchase.paymentDate,
            rixoRequested = purchase.rixoRequested,
            rixoConfirmed = purchase.rixoConfirmed,
            notes = purchase.notes,
            shipmentDate = purchase.shipmentDate,
            blNo = purchase.blNo,
            vesselNo = purchase.vesselNo,
            destination = purchase.destination,
            shipmentCharges = purchase.shipmentCharges,
            freight = purchase.freight,
            storageCharges = purchase.storageCharges,
            miscCharges = purchase.miscCharges,
            inspectionFee = purchase.inspectionFee,
            commission = purchase.commission,
            rixoPrice = purchase.rixoPrice,
            repairCompany = purchase.repairCompany,
            repairCharges = purchase.repairCharges
        )
        
        val jsonString = json.encodeToString(ApiPurchase.serializer(), apiPurchase)
        
        val headers = Headers().apply { append("Content-Type", "application/json") }
        val init = RequestInit(
            method = "PUT",
            headers = headers,
            body = jsonString
        )
        
        val response = window.fetch("$BASE_URL/purchases/${purchase.id}", init).await()
        if (!response.ok) throw Exception("Failed to update purchase: ${response.status}")
        
        val updated = json.decodeFromString<ApiPurchase>(response.text().await())
        return Purchase(
            id = updated.id,
            date = updated.date,
            lotNumber = updated.lotNumber,
            chassis = updated.chassis,
            carModelYear = updated.carModelYear,
            brand = updated.brand,
            carName = updated.carName,
            grade = updated.grade,
            rank = updated.rank,
            color = updated.color,
            displacement = updated.displacement,
            fuel = updated.fuel,
            seat = updated.seat,
            door = updated.door,
            distance = updated.distance,
            options = updated.options,
            auctionNo = updated.auctionNo,
                auctionHouse = updated.auctionHouse,
            stockLocation = updated.stockLocation,
            rixoCompany = updated.rixoCompany,
            clientName = updated.clientName,
            country = updated.country,
            price = updated.price,
            auctionFee = updated.auctionFee,
            recycleFee = updated.recycleFee,
            roadTax = updated.roadTax,
            totalPrice = updated.totalPrice,
            paymentDate = updated.paymentDate,
            rixoRequested = updated.rixoRequested,
            rixoConfirmed = updated.rixoConfirmed,
            notes = updated.notes,
            shipmentDate = updated.shipmentDate,
            blNo = updated.blNo,
            vesselNo = updated.vesselNo,
            destination = updated.destination,
            shipmentCharges = updated.shipmentCharges,
            freight = updated.freight,
            storageCharges = updated.storageCharges,
            miscCharges = updated.miscCharges,
            inspectionFee = updated.inspectionFee,
            commission = updated.commission,
            rixoPrice = updated.rixoPrice,
            repairCompany = updated.repairCompany,
            repairCharges = updated.repairCharges
        )
    }
    
    suspend fun deletePurchase(purchaseId: Long) {
        val init = RequestInit(method = "DELETE")
        val response = window.fetch("$BASE_URL/purchases/$purchaseId", init).await()
        if (!response.ok) throw Exception("Failed to delete purchase: ${response.status}")
    }
    
    suspend fun importPurchases(file: File): ImportResponse {
        val formData = js("new FormData()")
        formData.append("file", file)
        
        val init = RequestInit(
            method = "POST",
            body = formData
        )
        
        val response = window.fetch("$BASE_URL/purchases/import", init).await()
        if (!response.ok) throw Exception("Failed to import purchases: ${response.status}")
        
        val jsonString = response.text().await()
        return json.decodeFromString<ImportResponse>(jsonString)
    }
}