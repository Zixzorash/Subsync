package com.example.subsync.util

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale

object OpenSubtitlesHasher {

    private const val HASH_CHUNK_SIZE = 65536L // 64 KB

    /**
     * Computes the official 64-bit OpenSubtitles / Moviehash checksum used by
     * VLC, MPC-HC, and OpenSubtitles.org.
     *
     * It adds 64-bit integers from the first 64KB and last 64KB of the file
     * together with the file size.
     */
    fun computeMovieHash(file: File): String {
        val size = file.length()
        if (size < HASH_CHUNK_SIZE) {
            return String.format(Locale.US, "%016x", size)
        }

        var head = 0L
        var tail = 0L

        file.inputStream().use { input ->
            val headBuffer = ByteArray(HASH_CHUNK_SIZE.toInt())
            var read = 0
            while (read < headBuffer.size) {
                val r = input.read(headBuffer, read, headBuffer.size - read)
                if (r <= 0) break
                read += r
            }
            head = compute64BitSum(headBuffer, read)

            // Seek / skip to the last 64KB
            val skipBytes = size - HASH_CHUNK_SIZE
            val tailBuffer = ByteArray(HASH_CHUNK_SIZE.toInt())
            file.inputStream().use { tailInput ->
                tailInput.skip(skipBytes)
                var tailRead = 0
                while (tailRead < tailBuffer.size) {
                    val r = tailInput.read(tailBuffer, tailRead, tailBuffer.size - tailRead)
                    if (r <= 0) break
                    tailRead += r
                }
                tail = compute64BitSum(tailBuffer, tailRead)
            }
        }

        val totalHash = size + head + tail
        return String.format(Locale.US, "%016x", totalHash)
    }

    /**
     * Computes a simulated or stream-based hash when only bytes/input stream is provided.
     */
    fun computeBytesHash(bytes: ByteArray, totalSize: Long = bytes.size.toLong()): String {
        if (bytes.isEmpty()) return "0000000000000000"
        val sum = compute64BitSum(bytes, bytes.size) + totalSize
        return String.format(Locale.US, "%016x", sum)
    }

    private fun compute64BitSum(buffer: ByteArray, length: Int): Long {
        val byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0L
        while (byteBuffer.remaining() >= 8) {
            sum += byteBuffer.long
        }
        return sum
    }

    /**
     * Computes SHA-256 checksum formatted as lowercase hex string.
     */
    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
