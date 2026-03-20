package com.automan.backend.controller

import com.automan.backend.service.MasterMenuService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class MasterMenuValueRequest(
    val value: String,
    val originalValue: String? = null,
)

data class MasterMenuFieldRequest(
    val fieldName: String,
)

@RestController
@RequestMapping(value = ["/master-menu", "/api/master-menu"])
@CrossOrigin(
    origins = [
        "http://localhost:8080",
        "http://localhost:8081",
        "http://localhost:8083",
        "http://localhost:8084",
        "http://localhost:8085",
        "http://localhost:8089",
        "http://localhost:8090",
        "http://localhost:9090",
    ],
)
class MasterMenuController(
    private val masterMenuService: MasterMenuService,
) {
    @GetMapping("/fields")
    fun getFields(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(masterMenuService.getAllFieldNames())
    }

    @PostMapping("/fields")
    fun addField(
        @RequestBody request: MasterMenuFieldRequest,
    ): ResponseEntity<List<String>> {
        Logger.debug("MasterMenuController.addField field='%s'", request.fieldName)
        return ResponseEntity.ok(masterMenuService.addField(request.fieldName))
    }

    @DeleteMapping("/fields/{fieldName}")
    fun deleteField(
        @PathVariable fieldName: String,
    ): ResponseEntity<List<String>> {
        Logger.debug("MasterMenuController.deleteField field='%s'", fieldName)
        return ResponseEntity.ok(masterMenuService.deleteField(fieldName))
    }


    @GetMapping("/{fieldName}")
    fun getValues(@PathVariable fieldName: String): ResponseEntity<List<String>> {
        val values = masterMenuService.getValues(fieldName)
        return ResponseEntity.ok(values)
    }

    @PostMapping("/{fieldName}")
    fun addValue(
        @PathVariable fieldName: String,
        @RequestBody request: MasterMenuValueRequest,
    ): ResponseEntity<List<String>> {
        Logger.debug("MasterMenuController.addValue field='%s' value='%s'", fieldName, request.value)
        val updated = masterMenuService.addValue(fieldName, request.value)
        return ResponseEntity.ok(updated)
    }

    @PutMapping("/{fieldName}")
    fun updateValue(
        @PathVariable fieldName: String,
        @RequestBody request: MasterMenuValueRequest,
    ): ResponseEntity<List<String>> {
        val original = request.originalValue
        if (original.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        Logger.debug(
            "MasterMenuController.updateValue field='%s' original='%s' new='%s'",
            fieldName,
            original,
            request.value,
        )
        val updated = masterMenuService.updateValue(fieldName, original, request.value)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{fieldName}")
    fun deleteValue(
        @PathVariable fieldName: String,
        @RequestParam value: String,
    ): ResponseEntity<List<String>> {
        Logger.debug("MasterMenuController.deleteValue field='%s' value='%s'", fieldName, value)
        val updated = masterMenuService.deleteValue(fieldName, value)
        return ResponseEntity.ok(updated)
    }
}

