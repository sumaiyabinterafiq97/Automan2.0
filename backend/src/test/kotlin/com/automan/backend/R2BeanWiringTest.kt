package com.automan.backend

import com.automan.backend.service.media.MediaStorageService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import software.amazon.awssdk.services.s3.S3Client

@SpringBootTest
@TestPropertySource(
    properties = [
        "R2_ENABLED=true",
        "R2_ACCOUNT_ID=test-account",
        "R2_ACCESS_KEY_ID=test-key",
        "R2_SECRET_ACCESS_KEY=test-secret",
        "R2_BUCKET_NAME=test-bucket",
        "R2_ENDPOINT=https://test-account.r2.cloudflarestorage.com",
        "spring.flyway.enabled=false",
    ],
)
class R2BeanWiringTest {
    @Autowired
    private lateinit var s3ClientProvider: ObjectProvider<S3Client>

    @Autowired
    private lateinit var mediaStorageProvider: ObjectProvider<MediaStorageService>

    @Test
    fun `R2 beans are registered when media storage is configured`() {
        assertNotNull(s3ClientProvider.ifAvailable, "S3Client bean should exist")
        assertNotNull(mediaStorageProvider.ifAvailable, "MediaStorageService bean should exist")
    }
}
