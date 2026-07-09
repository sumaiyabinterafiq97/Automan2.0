package com.automan.backend.service.media

import com.automan.backend.config.MediaStorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.InputStream
import java.time.Duration

@Service
@ConditionalOnBean(S3Client::class)
class R2MediaStorageService(
    private val s3Client: S3Client,
    private val properties: MediaStorageProperties,
) : MediaStorageService {

    private val presigner: S3Presigner by lazy {
        S3Presigner.builder()
            .endpointOverride(s3Client.serviceClientConfiguration().endpointOverride().orElseThrow())
            .region(s3Client.serviceClientConfiguration().region())
            .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
            .build()
    }

    override fun upload(fileKey: String, contentType: String, inputStream: InputStream, contentLength: Long) {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucketName)
            .key(fileKey)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength))
    }

    override fun delete(fileKey: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucketName)
                .key(fileKey)
                .build(),
        )
    }

    override fun presignedGetUrl(fileKey: String, ttl: Duration): String {
        val getRequest = GetObjectRequest.builder()
            .bucket(properties.bucketName)
            .key(fileKey)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(getRequest)
            .build()
        return presigner.presignGetObject(presignRequest).url().toString()
    }
}
