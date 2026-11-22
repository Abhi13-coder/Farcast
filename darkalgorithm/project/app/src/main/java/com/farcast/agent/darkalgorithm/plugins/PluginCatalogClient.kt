package com.farcast.agent.darkalgorithm.plugins

import android.content.Context
import android.os.Build
import com.farcast.agent.darkalgorithm.features.FarcastHttp
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.Json

/**
 * Catalogue source: a `catalogue.json` file in a GitHub repo (or any raw
 * URL), matching Section 4's "Use GitHub Releases and repository-hosted
 * assets as the initial plugin distribution mechanism." This is the
 * "browse the marketplace" read path; PluginInstaller handles the actual
 * per-plugin download/verify/install.
 */
class PluginCatalogClient(private val context: Context, private val catalogueUrl: String) {

    private val json = Json { ignoreUnknownKeys = true }

    data class CatalogueEntry(val manifest: PluginManifest, val compatible: Boolean, val incompatibilityReason: String?)

    suspend fun fetchCatalogue(caseIdForTorIsolation: String = "plugin-catalogue"): List<CatalogueEntry> {
        val client = TorEnforcedHttpClient.forBranch(context, caseIdForTorIsolation, "plugins")
        val text = FarcastHttp.getText(client, catalogueUrl)
        val catalogue = json.decodeFromString(PluginCatalogue.serializer(), text)
        return catalogue.plugins.map { manifest ->
            val (compatible, reason) = checkCompatibility(manifest)
            CatalogueEntry(manifest, compatible, reason)
        }
    }

    /** supportedAndroidVersions lists a floor (minSdk), not a ceiling — newer
     *  OS versions aren't assumed incompatible just for being newer. */
    private fun checkCompatibility(manifest: PluginManifest): Pair<Boolean, String?> {
        val minSdk = manifest.supportedAndroidVersions.minOrNull() ?: 0
        if (Build.VERSION.SDK_INT < minSdk) {
            return false to "needs Android API $minSdk+, this device is API ${Build.VERSION.SDK_INT}"
        }
        val deviceAbis = Build.SUPPORTED_ABIS.toSet()
        if (manifest.supportedCpuArchitectures.none { it in deviceAbis }) {
            return false to "no matching CPU ABI — plugin supports ${manifest.supportedCpuArchitectures}, device has $deviceAbis"
        }
        return true to null
    }
}
