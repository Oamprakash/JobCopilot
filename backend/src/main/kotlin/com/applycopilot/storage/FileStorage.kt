package com.applycopilot.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/** Where uploaded resumes live: local disk, or an S3-compatible bucket such as Cloudflare R2 ([ObjectFileStorage]). */
interface FileStorage {
    fun put(key: String, bytes: ByteArray)
    fun get(key: String): ByteArray?
    fun delete(key: String)
}

class LocalFileStorage(root: String) : FileStorage {
    private val root: Path = Paths.get(root).toAbsolutePath().normalize()

    init {
        Files.createDirectories(this.root)
    }

    private fun resolve(key: String): Path {
        require(keyPattern.matches(key)) { "Invalid storage key" }
        val path = root.resolve(key).normalize()
        require(path.startsWith(root)) { "Invalid storage key" }
        return path
    }

    override fun put(key: String, bytes: ByteArray) {
        val path = resolve(key)
        Files.createDirectories(path.parent)
        // Write to a temp file first so a crash never leaves a half-written resume behind.
        val tmp = Files.createTempFile(path.parent, ".upload", ".tmp")
        Files.write(tmp, bytes)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun get(key: String): ByteArray? = resolve(key).let { if (Files.exists(it)) Files.readAllBytes(it) else null }

    override fun delete(key: String) {
        Files.deleteIfExists(resolve(key))
    }

    private companion object {
        val keyPattern = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_.-]+)*$")
    }
}
