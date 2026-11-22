package com.farcast.agent.darkalgorithm.plugins

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class PluginDownloader(private val context: Context) {

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class ChecksumMismatch(val expected: String, val actual: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    /** Downloads to a staging file, verifies SHA-256 BEFORE it's moved anywhere
     *  the app treats as "installed" — a failed hash means the download is
     *  deleted, not quarantined-and-trusted-anyway. */
    suspend fun download(manifest: PluginManifest, destDir: File): DownloadResult {
        val client = TorEnforcedHttpClient.forBranch(context, "plugin-install", "plugins")
        val staging = File(destDir, "${manifest.id}.download")
        try {
            val request = Request.Builder().url(manifest.downloadUrl).build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body ?: return DownloadResult.Error("empty response body")
                staging.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
        } catch (e: Exception) {
            staging.delete()
            return DownloadResult.Error(e.message ?: "download failed")
        }

        val actualHash = sha256(staging)
        if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
            staging.delete()
            return DownloadResult.ChecksumMismatch(manifest.sha256, actualHash)
        }

        val finalFile = File(destDir, "${manifest.id}-${manifest.version}.bin")
        staging.renameTo(finalFile)
        return DownloadResult.Success(finalFile)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
