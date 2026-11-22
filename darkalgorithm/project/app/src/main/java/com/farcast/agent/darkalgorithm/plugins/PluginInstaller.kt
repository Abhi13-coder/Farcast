package com.farcast.agent.darkalgorithm.plugins

import android.content.Context
import java.io.File

/**
 * This is the whole "one tap install / delete" contract: a UI button calls
 * install(manifest) or uninstall(id), nothing else. Everything between
 * "user tapped install" and "plugin is usable" happens here: compatibility
 * re-check, download, checksum verify, registry write.
 */
class PluginInstaller(
    private val context: Context,
    private val downloader: PluginDownloader,
    private val registry: PluginRegistry
) {
    sealed class InstallOutcome {
        data object Installed : InstallOutcome()
        data class Incompatible(val reason: String) : InstallOutcome()
        data class ChecksumFailed(val expected: String, val actual: String) : InstallOutcome()
        data class Failed(val message: String) : InstallOutcome()
    }

    private val pluginDir: File get() = File(context.filesDir, "plugins").also { it.mkdirs() }

    suspend fun install(entry: PluginCatalogClient.CatalogueEntry): InstallOutcome {
        if (!entry.compatible) return InstallOutcome.Incompatible(entry.incompatibilityReason ?: "incompatible")

        return when (val result = downloader.download(entry.manifest, pluginDir)) {
            is PluginDownloader.DownloadResult.Success -> {
                registry.record(entry.manifest, result.file.absolutePath)
                InstallOutcome.Installed
            }
            is PluginDownloader.DownloadResult.ChecksumMismatch ->
                InstallOutcome.ChecksumFailed(result.expected, result.actual)
            is PluginDownloader.DownloadResult.Error -> InstallOutcome.Failed(result.message)
        }
    }

    suspend fun uninstall(pluginId: String) {
        val installed = registry.listInstalled(onlyEnabled = false).find { it.manifest.id == pluginId }
        installed?.let { File(it.localFilePath).delete() }
        registry.remove(pluginId)
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) = registry.setEnabled(pluginId, enabled)
}
