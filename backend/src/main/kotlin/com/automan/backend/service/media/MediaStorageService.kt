package com.automan.backend.service.media

import java.io.InputStream
import java.time.Duration

interface MediaStorageService {
    fun upload(fileKey: String, contentType: String, inputStream: InputStream, contentLength: Long)

    fun delete(fileKey: String)

    fun presignedGetUrl(fileKey: String, ttl: Duration): String
}
