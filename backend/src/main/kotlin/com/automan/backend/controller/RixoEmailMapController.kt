package com.automan.backend.controller

import com.automan.backend.service.RixoEmailMapService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/rixo-email-map", "/api/rixo-email-map"])
class RixoEmailMapController(
    private val rixoEmailMapService: RixoEmailMapService,
) {

    @GetMapping("/mappings")
    fun listAll(): ResponseEntity<Map<String, Any>> {
        val data = rixoEmailMapService.findAllAsMaps()
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to data,
                "count" to data.size,
            ),
        )
    }

    @GetMapping("/by-company")
    fun byCompany(@RequestParam(defaultValue = "") company: String): ResponseEntity<Map<String, Any>> {
        val emails = rixoEmailMapService.emailsForCompany(company)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "emails" to emails,
            ),
        )
    }

    @PostMapping("/mappings/add")
    fun add(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val saved = rixoEmailMapService.create(
                body["rixoCompany"]?.toString().orEmpty(),
                body["email"]?.toString().orEmpty(),
            )
            ResponseEntity.ok(savedBody("Rixo email added", saved.rixoCompany, saved.email, saved.id))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to add"))
        }
    }

    @PutMapping("/mappings/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val saved = rixoEmailMapService.update(
                id,
                body["rixoCompany"]?.toString().orEmpty(),
                body["email"]?.toString().orEmpty(),
            )
            ResponseEntity.ok(savedBody("Rixo email updated", saved.rixoCompany, saved.email, saved.id))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to update"))
        }
    }

    @DeleteMapping("/mappings/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            rixoEmailMapService.delete(id)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Deleted"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to delete"))
        }
    }

    private fun savedBody(message: String, company: String, email: String, id: Long?): Map<String, Any> =
        mapOf(
            "success" to true,
            "message" to message,
            "data" to mapOf(
                "id" to (id ?: 0L),
                "rixoCompany" to company,
                "email" to email,
            ),
        )

    private fun errorBody(message: String) = mapOf("success" to false, "message" to message)
}
