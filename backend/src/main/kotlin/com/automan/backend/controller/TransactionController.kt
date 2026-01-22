package com.automan.backend.controller

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.TransactionResponse
import com.automan.backend.service.TransactionService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class TransactionController(
    private val transactionService: TransactionService
) {
    
    @PostMapping
    fun createTransaction(@RequestBody request: CreateTransactionRequest): ResponseEntity<TransactionResponse> {
        Logger.debug("TransactionController.createTransaction called")
        
        val response = transactionService.createTransaction(request)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(500).body(response)
        }
    }
}
