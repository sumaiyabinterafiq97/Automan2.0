package com.automan.backend.controller

import com.automan.backend.dto.RixoHistoryRowDto
import com.automan.backend.service.RixoHistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rixo-history")
class RixoHistoryController(
    private val rixoHistoryService: RixoHistoryService,
) {
    @GetMapping
    fun list(): List<RixoHistoryRowDto> = rixoHistoryService.listAllRows()
}
