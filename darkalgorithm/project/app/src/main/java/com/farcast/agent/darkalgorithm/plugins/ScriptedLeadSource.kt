package com.farcast.agent.darkalgorithm.plugins

import android.content.Context
import com.farcast.agent.darkalgorithm.*
import com.farcast.agent.darkalgorithm.features.FarcastHttp
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/**
 * The SCRIPT plugin kind's LeadSource. Division of responsibility is the
 * whole security model here, worth restating at the call site:
 *  - THIS CLASS performs the one network fetch, to the one URL the plugin's
 *    manifest declared (shown to the user before install) — never a URL the
 *    script itself chooses at runtime.
 *  - PluginSandbox runs the script against that already-fetched JSON and
 *    gets back plain data — an array of objects — which this class then
 *    validates into real Leads. A script returning something that isn't a
 *    plain array of {branch, summary, rawData} objects just produces zero
 *    leads, not a crash and not a trusted pass-through.
 */
class ScriptedLeadSource(
    private val context: Context,
    private val manifest: PluginManifest,
    private val scriptFile: File,
    private val queryParam: String,
    private val paramPlaceholder: String = "{query}"
) : LeadSource {

    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val urlTemplate = manifest.declaredFetchUrlTemplate
            ?: return emptyList() // no declared fetch target — nothing for this plugin to do
        val client = TorEnforcedHttpClient.forBranch(context, ctx.caseFile.caseId, "plugin-${manifest.id}")
        val url = urlTemplate.replace(paramPlaceholder, queryParam)
        val rawJson = runCatching { FarcastHttp.getText(client, url) }.getOrElse { return emptyList() }

        val result = PluginSandbox.runTransform(scriptFile.readText(), rawJson)
        val outputJson = result.outputJson ?: return emptyList() // script errored/timed out — fail soft, log result.error upstream if wanted

        val parsed = runCatching { Json.parseToJsonElement(outputJson) }.getOrNull() ?: return emptyList()
        val items = (parsed as? JsonArray) ?: return emptyList()

        return items.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val rawData = obj["rawData"]?.jsonObject?.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: "" } ?: emptyMap()
            Lead(
                id = UUID.randomUUID().toString(),
                caseId = ctx.caseFile.caseId,
                branch = manifest.category,
                summary = summary,
                sourceIds = listOf("src-plugin-${manifest.id}"),
                createdAtEpochMs = System.currentTimeMillis(),
                rawData = rawData,
                category = LeadCategory.PUBLIC_RECORD
            )
        }
    }
}
