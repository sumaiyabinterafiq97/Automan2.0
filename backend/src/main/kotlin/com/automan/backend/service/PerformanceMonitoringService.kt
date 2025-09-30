package com.automan.backend.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

@Service
class PerformanceMonitoringService {
    
    private val operationMetrics = ConcurrentHashMap<String, OperationMetrics>()
    private val systemMetrics = SystemMetrics()
    
    data class OperationMetrics(
        val operationName: String,
        val totalCalls: AtomicLong = AtomicLong(0),
        val totalTime: AtomicLong = AtomicLong(0),
        val minTime: AtomicLong = AtomicLong(Long.MAX_VALUE),
        val maxTime: AtomicLong = AtomicLong(0),
        val errorCount: AtomicInteger = AtomicInteger(0)
    )
    
    data class SystemMetrics(
        val totalImports: AtomicLong = AtomicLong(0),
        val totalTransactions: AtomicLong = AtomicLong(0),
        val totalClients: AtomicLong = AtomicLong(0),
        val averageImportTime: AtomicLong = AtomicLong(0),
        val systemUptime: Long = System.currentTimeMillis()
    )
    
    fun recordOperation(operationName: String, startTime: Long, success: Boolean = true) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        val metrics = operationMetrics.computeIfAbsent(operationName) { 
            OperationMetrics(operationName) 
        }
        
        metrics.totalCalls.incrementAndGet()
        metrics.totalTime.addAndGet(duration)
        
        // Update min/max times
        var currentMin = metrics.minTime.get()
        while (duration < currentMin && !metrics.minTime.compareAndSet(currentMin, duration)) {
            currentMin = metrics.minTime.get()
        }
        
        var currentMax = metrics.maxTime.get()
        while (duration > currentMax && !metrics.maxTime.compareAndSet(currentMax, duration)) {
            currentMax = metrics.maxTime.get()
        }
        
        if (!success) {
            metrics.errorCount.incrementAndGet()
        }
        
        // Update system metrics for imports
        if (operationName.contains("import", ignoreCase = true)) {
            systemMetrics.totalImports.incrementAndGet()
            updateAverageImportTime(duration)
        }
    }
    
    private fun updateAverageImportTime(duration: Long) {
        val totalImports = systemMetrics.totalImports.get()
        if (totalImports > 0) {
            val currentAverage = systemMetrics.averageImportTime.get()
            val newAverage = ((currentAverage * (totalImports - 1)) + duration) / totalImports
            systemMetrics.averageImportTime.set(newAverage)
        }
    }
    
    fun recordTransactionCount(count: Int) {
        systemMetrics.totalTransactions.addAndGet(count.toLong())
    }
    
    fun recordClientCount(count: Int) {
        systemMetrics.totalClients.addAndGet(count.toLong())
    }
    
    fun getOperationMetrics(operationName: String): Map<String, Any>? {
        val metrics = operationMetrics[operationName] ?: return null
        
        val totalCalls = metrics.totalCalls.get()
        val totalTime = metrics.totalTime.get()
        val averageTime = if (totalCalls > 0) totalTime / totalCalls else 0
        
        return mapOf(
            "operationName" to operationName,
            "totalCalls" to totalCalls,
            "totalTime" to totalTime,
            "averageTime" to averageTime,
            "minTime" to if (metrics.minTime.get() == Long.MAX_VALUE) 0 else metrics.minTime.get(),
            "maxTime" to metrics.maxTime.get(),
            "errorCount" to metrics.errorCount.get(),
            "successRate" to if (totalCalls > 0) {
                ((totalCalls - metrics.errorCount.get()) * 100.0 / totalCalls).toInt()
            } else 100
        )
    }
    
    fun getAllOperationMetrics(): Map<String, Any> {
        val operations = operationMetrics.keys.mapNotNull { operationName ->
            getOperationMetrics(operationName)
        }
        
        return mapOf(
            "operations" to operations,
            "totalOperations" to operations.size,
            "systemMetrics" to getSystemMetrics()
        )
    }
    
    fun getSystemMetrics(): Map<String, Any> {
        val uptime = System.currentTimeMillis() - systemMetrics.systemUptime
        
        return mapOf(
            "totalImports" to systemMetrics.totalImports.get(),
            "totalTransactions" to systemMetrics.totalTransactions.get(),
            "totalClients" to systemMetrics.totalClients.get(),
            "averageImportTime" to systemMetrics.averageImportTime.get(),
            "systemUptime" to uptime,
            "systemUptimeFormatted" to formatDuration(uptime)
        )
    }
    
    fun getPerformanceSummary(): Map<String, Any> {
        val allMetrics = getAllOperationMetrics()
        val systemMetrics = getSystemMetrics()
        
        // Calculate overall performance score
        val operations = allMetrics["operations"] as List<Map<String, Any>>
        val averageSuccessRate = if (operations.isNotEmpty()) {
            operations.map { it["successRate"] as Int }.average()
        } else 100.0
        
        val performanceScore = when {
            averageSuccessRate >= 95 -> "EXCELLENT"
            averageSuccessRate >= 85 -> "GOOD"
            averageSuccessRate >= 70 -> "FAIR"
            else -> "POOR"
        }
        
        return mapOf(
            "performanceScore" to performanceScore,
            "averageSuccessRate" to averageSuccessRate.toInt(),
            "systemMetrics" to systemMetrics,
            "operationMetrics" to allMetrics,
            "recommendations" to generateRecommendations(operations, systemMetrics)
        )
    }
    
    private fun generateRecommendations(operations: List<Map<String, Any>>, systemMetrics: Map<String, Any>): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Check for slow operations
        operations.forEach { operation ->
            val averageTime = operation["averageTime"] as Long
            val operationName = operation["operationName"] as String
            
            when {
                averageTime > 10000 -> recommendations.add("$operationName is very slow (${averageTime}ms). Consider optimization.")
                averageTime > 5000 -> recommendations.add("$operationName is slow (${averageTime}ms). Monitor performance.")
            }
        }
        
        // Check for high error rates
        operations.forEach { operation ->
            val successRate = operation["successRate"] as Int
            val operationName = operation["operationName"] as String
            
            if (successRate < 90) {
                recommendations.add("$operationName has high error rate (${100 - successRate}%). Check error handling.")
            }
        }
        
        // Check system load
        val totalImports = systemMetrics["totalImports"] as Long
        val averageImportTime = systemMetrics["averageImportTime"] as Long
        
        if (totalImports > 100 && averageImportTime > 5000) {
            recommendations.add("High import volume detected. Consider implementing caching or database optimization.")
        }
        
        return recommendations
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    fun resetMetrics() {
        operationMetrics.clear()
        systemMetrics.totalImports.set(0)
        systemMetrics.totalTransactions.set(0)
        systemMetrics.totalClients.set(0)
        systemMetrics.averageImportTime.set(0)
    }
}
