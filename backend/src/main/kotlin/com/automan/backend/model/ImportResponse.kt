package com.automan.backend.model

data class ImportResponse(
    val success: Boolean,
    val message: String,
    val importedCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
    val totalProcessed: Int,
    val importedPurchases: List<Purchase> = emptyList(),
    val duplicateDetails: List<String> = emptyList(),
    val errorDetails: List<String> = emptyList()
)
