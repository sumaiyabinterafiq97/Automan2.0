package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "purchases")
@JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
data class Purchase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "date")
    val date: String? = null,
    
    
    @Column(name = "chassis", nullable = false)
    val chassis: String,
    
    @Column(name = "car_model_year")
    val carModelYear: String? = null,
    
    @Column(name = "brand")
    val brand: String? = null,
    
    @Column(name = "car_name")
    val carName: String? = null,
    
    @Column(name = "shipment_size")
    @com.fasterxml.jackson.annotation.JsonAlias("vehicleType")
    val shipmentSize: String? = null,
    
    @Column(name = "grade")
    val grade: String? = null,
    
    @Column(name = "`rank`")
    val rank: String? = null,
    
    @Column(name = "color")
    val color: String? = null,
    
    @Column(name = "fuel")
    val fuel: String? = null,
    
    @Column(name = "seat")
    val seat: String? = null,
    
    @Column(name = "door")
    val door: String? = null,
    
    @Column(name = "distance")
    val distance: String? = null,
    
    @Column(name = "options")
    val options: String? = null,
    
    @Column(name = "CC")
    val cc: Int? = null,
    
    @Column(name = "shift")
    val shift: String? = null,
    
    @Column(name = "WD")
    val wd: String? = null,
    
    @Column(name = "drive_type")
    val driveType: String? = null,
    
    @Column(name = "auction_no")
    val auctionNo: String? = null,
    
    @Column(name = "auction_house")
    @com.fasterxml.jackson.annotation.JsonAlias("auctionName")
    val auctionHouse: String? = null,
    
    @Column(name = "stock_location")
    val stockLocation: String? = null,
    
    @Column(name = "pol")
    val pol: String? = null,
    
    @Column(name = "rixo_company")
    val rixoCompany: String? = null,
    
    @Column(name = "client_name")
    val clientName: String? = null,
    
    @Column(name = "consignee", columnDefinition = "TEXT")
    val consignee: String? = null,
    
    @Column(name = "client_id")
    val clientId: Long? = null,
    
    @Column(name = "country")
    val country: String? = null,
    
    @Column(name = "price")
    val price: String? = null,
    
    @Column(name = "auction_fee")
    val auctionFee: String? = null,
    
    @Column(name = "auction_penalty_fee")
    val auctionPenaltyFee: String? = null,
    
    @Column(name = "recycle_fee")
    val recycleFee: String? = null,
    
    @Column(name = "road_tax")
    val roadTax: String? = null,
    
    @Column(name = "tax_total")
    val taxTotal: String? = null,
    
    @Column(name = "total_price")
    val totalPrice: String? = null,
    
    @Column(name = "payment_date")
    val paymentDate: String? = null,
    
    @Column(name = "rixo_requested")
    val rixoRequested: String? = null,
    
    @Column(name = "rixo_confirmed")
    val rixoConfirmed: String? = null,
    
    @Column(name = "notes")
    val notes: String? = null,
    
    @Column(name = "shippment_date")
    val shipmentDate: String? = null,
    
    @Column(name = "`B/L_no`")
    val blNo: String? = null,
    
    @Column(name = "vessel_no")
    val vesselNo: String? = null,
    
    @Column(name = "vessel")
    val vessel: String? = null,
    
    @Column(name = "shipped")
    val shipped: Boolean? = null,
    
    @Column(name = "shipment_charges")
    val shipmentCharges: String? = null,
    
    @Column(name = "freight")
    val freight: String? = null,
    
    @Column(name = "storage_charges")
    val storageCharges: String? = null,
    
    @Column(name = "misc_charges")
    val miscCharges: String? = null,
    
    @Column(name = "inspection_fee")
    val inspectionFee: String? = null,
    
    @Column(name = "commission")
    val commission: String? = null,
    
    @Column(name = "rixo_price")
    val rixoPrice: String? = null,
    
    @Column(name = "venue_id")
    val venueId: String? = null,
    
    @Column(name = "number_cut")
    val numberCut: String? = null,
    
    @Column(name = "shaken")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    val shaken: Boolean? = null,
    
    @Column(name = "repair_company")
    val repairCompany: String? = null,
    
    @Column(name = "repair_charges")
    val repairCharges: String? = null,
    
    
    @Column(name = "profit")
    val profit: java.math.BigDecimal? = null,
    
    @Column(name = "is_package_mode")
    val isPackageMode: Boolean? = null,
    
    @Column(name = "total_cnf_price")
    val totalCnfPrice: java.math.BigDecimal? = null,
    
    @Column(name = "total_fob_price")
    val totalFobPrice: java.math.BigDecimal? = null,
    
    @Column(name = "booking_id")
    val bookingId: Long? = null,
    
    @Column(name = "car_pictures", columnDefinition = "TEXT")
    val carPictures: String? = null,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
