package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonInclude

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
    
    /** Phase 5 drop 1: canonical on purchase_vehicle_overrides + car_brand_mapping. */
    @Transient
    val carModelYear: String? = null,
    
    @Column(name = "brand")
    val brand: String? = null,
    
    @Column(name = "car_name")
    val carName: String? = null,
    
    @field:JsonAlias("vehicleType")
    @Transient
    val shipmentSize: String? = null,
    
    @Transient
    val grade: String? = null,
    
    @Transient
    val rank: String? = null,
    
    @Transient
    val color: String? = null,
    
    @Transient
    val fuel: String? = null,
    
    @Transient
    val seat: String? = null,
    
    @Transient
    val door: String? = null,
    
    @Transient
    val distance: String? = null,
    
    /** Phase 4 drop 1: stored in extended_attributes JSON only. */
    @Transient
    val options: String? = null,
    
    @Transient
    val cc: Int? = null,
    
    @Transient
    val shift: String? = null,
    
    @Transient
    val wd: String? = null,
    
    @Transient
    val driveType: String? = null,
    
    @Transient
    val auctionNo: String? = null,
    
    @Column(name = "auction_house")
    @com.fasterxml.jackson.annotation.JsonAlias("auctionName")
    val auctionHouse: String? = null,
    
    @Column(name = "stock_location")
    val stockLocation: String? = null,
    
    @Column(name = "pol")
    val pol: String? = null,

    /** Port of discharge (POD); JSON may use "destination" as alias. */
    @field:JsonAlias("destination")
    @Column(name = "pod")
    val pod: String? = null,
    
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
    
    /** Phase 5 drop 3: canonical on purchase_cost_lines. */
    @Transient
    val price: String? = null,
    
    @Transient
    val auctionFee: String? = null,
    
    @Transient
    val auctionPenaltyFee: String? = null,
    
    @Transient
    val recycleFee: String? = null,
    
    @Transient
    val roadTax: String? = null,
    
    @Transient
    val taxTotal: String? = null,
    
    @Column(name = "total_price")
    val totalPrice: String? = null,
    
    @Transient
    val paymentDate: String? = null,
    
    @Transient
    val rixoRequested: String? = null,
    
    @Transient
    val rixoConfirmed: String? = null,
    
    @Transient
    val notes: String? = null,

    /** Phase 4 drop 2: canonical on shipping_history. */
    @Transient
    val shipmentDate: String? = null,

    @Transient
    val blNo: String? = null,

    /** Shipment vessel name; JSON may still send `vesselNo` from older clients. */
    @field:JsonAlias("vesselNo")
    @Transient
    val vessel: String? = null,
    
    @field:JsonProperty("bookingRequested")
    @field:JsonAlias("booking_requested")
    @Transient
    val bookingRequested: Boolean = false,



    @Transient
    val invoiceConfirmed: Boolean? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", length = 32)
    val workflowStatus: WorkflowStatus? = null,

    @Column(name = "workflow_status_updated_at")
    val workflowStatusUpdatedAt: LocalDateTime? = null,
    
    @Transient
    val shipmentCharges: String? = null,
    
    @Transient
    val freight: String? = null,
    
    @Transient
    val storageCharges: String? = null,
    
    @Transient
    val miscCharges: String? = null,
    
    @Transient
    val inspectionFee: String? = null,
    
    @Transient
    val commission: String? = null,
    
    @Transient
    val rixoPrice: String? = null,
    
    @Transient
    val venueId: String? = null,

    @Transient
    val numberCut: String? = null,

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Transient
    val shaken: Boolean? = null,

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Transient
    val negotiate: Boolean? = null,

    /** Domestic (Japan) sale — export/shipping fields are not used when true. */
    @Column(name = "`local`")
    val local: Boolean = false,

    /** Manufacture year (YYYY only). */
    @Column(name = "manufacture_year", length = 4)
    val manufactureYear: String? = null,
    
    @Column(name = "repair_company")
    val repairCompany: String? = null,
    
    @Transient
    val repairCharges: String? = null,
    
    @Transient
    val profit: java.math.BigDecimal? = null,
    
    @Transient
    val isPackageMode: Boolean? = null,
    
    @Column(name = "booking_id")
    val bookingId: Long? = null,
    
    @Transient
    val carPictures: String? = null,

    /** Phase 4: canonical store for cold fields; not exposed in API (flat keys unchanged). */
    @field:com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "extended_attributes", columnDefinition = "JSON")
    val extendedAttributesJson: String? = null,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
