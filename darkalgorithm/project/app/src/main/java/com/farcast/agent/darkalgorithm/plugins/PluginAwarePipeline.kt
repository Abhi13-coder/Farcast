package com.farcast.agent.darkalgorithm.plugins

import android.content.Context
import com.farcast.agent.darkalgorithm.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * This is the concrete answer to "DarkMixer should adapt to installed
 * plugins": DarkMixer's own code never changes — it's still just generic
 * over LeadSource/Scorer like it always was. What changes per install is
 * what list of LeadSources and what PhoenixLite weights get passed into it,
 * assembled fresh every time a case runs.
 */
object PluginAwarePipeline {

    /** Built-ins + every enabled DECLARATIVE_SOURCE plugin, each wired with
     *  whatever the case's current query/target param is. */
    suspend fun assembleSources(
        context: Context,
        registry: PluginRegistry,
        builtIns: List<LeadSource>,
        queryParam: String
    ): List<LeadSource> {
        val installed = registry.listInstalled()
        val declarativeSources = installed.filter { it.manifest.kind == PluginKind.DECLARATIVE_SOURCE }
            .map { plugin -> DeclarativeLeadSource(context, File(plugin.localFilePath), queryParam) }
        val scriptedSources = installed.filter { it.manifest.kind == PluginKind.SCRIPT }
            .map { plugin -> ScriptedLeadSource(context, plugin.manifest, File(plugin.localFilePath), queryParam) }
        return builtIns + declarativeSources + scriptedSources
    }

    /** Starts from PhoenixLite's built-in weights, then lets any installed
     *  SCORING_WEIGHTS plugin override individual entries — a plugin can
     *  only re-tune HOW leads get prioritized, never add new probability
     *  signals or new code paths. */
    suspend fun assembleScorerWeights(
        registry: PluginRegistry,
        baseWeights: Map<String, Double>
    ): Map<String, Double> {
        val overrides = registry.listInstalled().filter { it.manifest.kind == PluginKind.SCORING_WEIGHTS }
        val merged = baseWeights.toMutableMap()
        for (plugin in overrides) {
            val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(File(plugin.localFilePath).readText())
            json.jsonObject.forEach { (key, value) ->
                value.jsonPrimitive.doubleOrNull?.let { merged[key] = it }
            }
        }
        return merged
    }

    /** Any installed MODEL plugin that targets VISION_MODEL — swap-in point
     *  for a specialized detector alongside/instead of the built-in YOLO
     *  model. Returns the local file path OnnxYoloEngine.load() should use. */
    suspend fun activeVisionModelPath(registry: PluginRegistry, builtInModelPath: String): String =
        registry.listInstalled()
            .firstOrNull { it.manifest.kind == PluginKind.MODEL && it.manifest.extensionPoint == ExtensionPoint.VISION_MODEL }
            ?.localFilePath ?: builtInModelPath
}
