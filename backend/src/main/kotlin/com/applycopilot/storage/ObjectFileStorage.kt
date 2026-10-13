package com.applycopilot.storage

import com.applycopilot.config.ObjectStorageConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

/**
 * Files in an S3-compatible bucket (Cloudflare R2 in production). Objects are private: the API reads them with
 * its own keys, and nothing is ever served from the bucket directly.
 */
class ObjectFileStorage(config: ObjectStorageConfig) : FileStorage, AutoCloseable {
    private val bucket = config.bucket
    private val s3: S3Client = S3Client.builder()
        .endpointOverride(URI.create(config.endpoint))
        .region(Region.of(config.region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.accessKeyId, config.secretAccessKey)))
        // Path-style addressing works on R2, MinIO and AWS alike; the bucket name stays out of the hostname.
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .httpClient(UrlConnectionHttpClient.builder().build())
        .build()

    private fun check(key: String) = require(keyPattern.matches(key)) { "Invalid storage key" }

    override fun put(key: String, bytes: ByteArray) {
        check(key)
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(bytes))
    }

    override fun get(key: String): ByteArray? {
        check(key)
        return try {
            s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()
        } catch (e: NoSuchKeyException) {
            null
        }
    }

    override fun delete(key: String) {
        check(key)
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }

    override fun close() = s3.close()

    private companion object {
        val keyPattern = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_.-]+)*$")
    }
}
