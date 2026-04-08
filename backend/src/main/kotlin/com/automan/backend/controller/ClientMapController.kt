package com.automan.backend.controller

import com.automan.backend.model.ClientMap
import com.automan.backend.service.ClientMapService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/client-map", "/api/client-map"])
@CrossOrigin(origins = [
    "http://localhost:8080",
    "http://localhost:8081",
    "http://localhost:8083",
    "http://localhost:8084",
    "http://localhost:8085",
    "http://localhost:8089",
    "http://localhost:8090",
    "http://localhost:9090",
])
class ClientMapController(
    private val clientMapService: ClientMapService,
) {

    @GetMapping("/mappings")
    fun getAllMappings(): ResponseEntity<Map<String, Any>> {
        return try {
            val rows = clientMapService.listAll().map { it.toResponseMap() }
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "data" to rows,
                ),
            )
        } catch (e: Exception) {
            Logger.error("Failed to load client map: ${e.message}", e)
            ResponseEntity.status(500).body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to load client map: ${e.message}",
                    "data" to emptyList<Map<String, Any?>>(),
                ),
            )
        }
    }

    @GetMapping("/mappings/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        val row = clientMapService.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to row.toResponseMap(),
            ),
        )
    }

    @PostMapping("/mappings")
    fun create(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val result = clientMapService.create(body)
            val msg =
                if (result.mergedIntoExisting) "Values merged into existing client map"
                else "Client map row created"
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to msg,
                    "merged" to result.mergedIntoExisting,
                    "data" to result.map.toResponseMap(),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            Logger.error("Error creating client map: ${e.message}", e)
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to create: ${e.message}"))
        }
    }

    @PutMapping("/mappings/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val result = clientMapService.update(id, body)
            val msg =
                if (result.mergedIntoExisting) "Merged into existing client map; duplicate row removed"
                else "Client map row updated"
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to msg,
                    "merged" to result.mergedIntoExisting,
                    "data" to result.map.toResponseMap(),
                ),
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(404).body(mapOf("success" to false, "message" to "Not found"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            Logger.error("Error updating client map: ${e.message}", e)
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to update: ${e.message}"))
        }
    }

    @DeleteMapping("/mappings/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            if (!clientMapService.delete(id)) {
                return ResponseEntity.status(404).body(mapOf("success" to false, "message" to "Not found"))
            }
            ResponseEntity.ok(mapOf("success" to true, "message" to "Deleted successfully"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to delete: ${e.message}"))
        }
    }

    private fun ClientMap.toResponseMap(): Map<String, Any?> =
        mapOf(
            "id" to id,
            "clientName" to clientName,
            "country" to country,
            "pod" to pod,
            "address" to address,
            "bankInfo" to bankInfo,
            "consignee" to consignee,
            "debitLimit" to debitLimit,
        )
}
