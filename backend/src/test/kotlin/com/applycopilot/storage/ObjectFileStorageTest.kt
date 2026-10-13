package com.applycopilot.storage

import com.applycopilot.config.ObjectStorageConfig
import org.testcontainers.containers.MinIOContainer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Runs [ObjectFileStorage] against MinIO, an S3-compatible server, as a stand-in for Cloudflare R2. */
class ObjectFileStorageTest {
    @Test
    fun putGetDelete() {
        MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z").use { minio ->
            minio.start()
            val config = ObjectStorageConfig(minio.s3URL, "uploads", minio.userName, minio.password, region = "us-east-1")
            ObjectFileStorage(config).use { storage ->
                createBucket(minio, "uploads")
                val bytes = "%PDF-1.4 resume".toByteArray()
                storage.put("u1/resume.pdf", bytes)
                assertContentEquals(bytes, storage.get("u1/resume.pdf"))
                storage.delete("u1/resume.pdf")
                assertNull(storage.get("u1/resume.pdf"))
                storage.delete("u1/missing.pdf") // deleting what isn't there is fine
                assertFailsWith<IllegalArgumentException> { storage.put("../escape", bytes) }
            }
        }
    }

    private fun createBucket(minio: MinIOContainer, name: String) {
        software.amazon.awssdk.services.s3.S3Client.builder()
            .endpointOverride(java.net.URI.create(minio.s3URL))
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .credentialsProvider(
                software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(minio.userName, minio.password),
                ),
            )
            .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().build())
            .build()
            .use { it.createBucket { b -> b.bucket(name) } }
    }
}
