package com.automan.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "automan.media.r2")
data class MediaStorageProperties(
    val enabled: Boolean = false,
    val accountId: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucketName: String = "",
    val endpoint: String = "",
    val presignedUrlTtlSeconds: Long = 3600,
    val maxFileSizeBytes: Long = 5L * 1024L * 1024L,
    val maxFilesPerPurchase: Int = 20,
) {
    fun isConfigured(): Boolean =
        enabled &&
            accountId.isNotBlank() &&
            accessKeyId.isNotBlank() &&
            secretAccessKey.isNotBlank() &&
            bucketName.isNotBlank() &&
            endpoint.isNotBlank()
}
