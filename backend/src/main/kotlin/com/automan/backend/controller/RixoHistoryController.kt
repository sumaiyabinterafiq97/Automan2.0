package com.automan.backend.controller

import com.automan.backend.dto.RixoHistoryRowDto
import com.automan.backend.service.RixoHistoryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rixo-history")
class RixoHistoryController(
    private val rixoHistoryService: RixoHistoryService,
) {
    @GetMapping
    fun list(): List<RixoHistoryRowDto> = rixoHistoryService.listAllRows()

    @GetMapping("/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(rixoHistoryService.listRowsPage(page, size, sort, order))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/page-search")
    fun searchPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(rixoHistoryService.searchRowsPage(q, page, size, sort, order))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /** Mark all purchases under selected history rows as `rixo_confirmed = TRUE`. */
    @PostMapping("/confirm-selected")
    fun confirmSelected(@RequestBody request: Map<String, Any>): Map<String, Any> {
        val idsRaw = request["historyIds"]
        val ids = when (idsRaw) {
            is List<*> -> idsRaw.mapNotNull {
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull()
                    else -> null
                }
            }.distinct()
            else -> emptyList()
        }
        val result = rixoHistoryService.confirmSelectedHistoryRows(ids)
        return mapOf(
            "selectedRows" to result.selectedRows,
            "updatedPurchases" to result.updatedPurchases,
            "matchedChassisTokens" to result.matchedChassisTokens,
            "skippedRowsWithoutChassis" to result.skippedRowsWithoutChassis,
        )
    }

    /** Delete selected history rows and reset affected purchases when chassis is no longer in any row. */
    @PostMapping("/remove-selected")
    fun removeSelected(@RequestBody request: Map<String, Any>): Map<String, Any> {
        val idsRaw = request["historyIds"]
        val ids = when (idsRaw) {
            is List<*> -> idsRaw.mapNotNull {
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull()
                    else -> null
                }
            }.distinct()
            else -> emptyList()
        }
        val deleted = rixoHistoryService.deleteHistoryRows(ids)
        return mapOf("deletedRows" to deleted)
    }

    /** Remove one history row; affected purchases are reset when chassis no longer appears in any row. */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            val ok = rixoHistoryService.deleteHistoryRow(id)
            if (ok) {
                ResponseEntity.ok(mapOf("deleted" to true, "id" to id))
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /**
     * Remove one chassis token from a stored history row (`chassis` is `;`-separated segments).
     * Downgrades purchases when the chassis disappears from **all** Rixo history rows.
     */
    @PostMapping("/remove-chassis")
    fun removeChassis(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        return try {
            val historyIdRaw = body["historyId"]
            val historyId = when (historyIdRaw) {
                is Number -> historyIdRaw.toLong()
                is String -> historyIdRaw.toLongOrNull()
                else -> null
            } ?: return ResponseEntity.badRequest().body(mapOf("error" to "historyId is required"))
            val chassisToken = (body["chassisToken"] as? String)?.trim().orEmpty()
            if (chassisToken.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf("error" to "chassisToken is required"))
            }
            ResponseEntity.ok(rixoHistoryService.removeChassisTokenFromHistoryRow(historyId, chassisToken))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }
}
