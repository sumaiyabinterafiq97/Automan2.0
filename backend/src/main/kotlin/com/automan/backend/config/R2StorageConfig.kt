package com.automan.backend.config

import com.automan.backend.service.media.MediaStorageService
import com.automan.backend.service.media.R2MediaStorageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
@EnableConfigurationProperties(MediaStorageProperties::class)
class R2StorageConfig {
    @Bean
    @ConditionalOnProperty(prefix = "automan.media.r2", name = ["enabled"], havingValue = "true")
    fun r2S3Client(properties: MediaStorageProperties): S3Client {
        require(properties.isConfigured()) {
            "automan.media.r2.enabled=true but R2 credentials or bucket settings are incomplete"
        }
        return S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint.trim()))
            .region(Region.of("auto"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.accessKeyId.trim(),
                        properties.secretAccessKey.trim(),
                    ),
                ),
            )
            .forcePathStyle(true)
            .build()
    }

    @Bean
    @ConditionalOnProperty(prefix = "automan.media.r2", name = ["enabled"], havingValue = "true")
    fun r2MediaStorageService(
        s3Client: S3Client,
        properties: MediaStorageProperties,
    ): MediaStorageService {
        require(properties.isConfigured()) {
            "automan.media.r2.enabled=true but R2 credentials or bucket settings are incomplete"
        }
        return R2MediaStorageService(s3Client, properties)
    }
}
