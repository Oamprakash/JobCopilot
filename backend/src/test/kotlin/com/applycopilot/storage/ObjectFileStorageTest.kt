package com.applycopilot.storage

import com.applycopilot.config.ObjectStorageConfig
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Runs [ObjectFileStorage] against Adobe's S3Mock, an S3-compatible server, as a stand-in for Cloudflare R2. */
class ObjectFileStorageTest {
    @Test
    fun putGetDelete() {
        GenericContainer("adobe/s3mock:3.11.0")
            .withExposedPorts(9090)
            .withEnv("initialBuckets", "uploads")
            .waitingFor(Wait.forHttp("/").forPort(9090))
            .use { s3 ->
                s3.start()
                val endpoint = "http://${s3.host}:${s3.getMappedPort(9090)}"
                ObjectFileStorage(ObjectStorageConfig(endpoint, "uploads", "test-key", "test-secret", region = "us-east-1")).use { storage ->
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
}
