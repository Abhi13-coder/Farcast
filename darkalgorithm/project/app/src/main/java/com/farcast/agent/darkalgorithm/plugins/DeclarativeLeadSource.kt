package com.farcast.agent.darkalgorithm.plugins

import android.content.Context
import com.farcast.agent.darkalgorithm.*
import com.farcast.agent.darkalgorithm.features.FarcastHttp
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/**
 * The actual rule format a DECLARATIVE_SOURCE plugin ships as its "model"
 * file (despite PluginManifest.kind naming it after MODEL/DECLARATIVE_SOURCE
 * generically, this is the JSON schema for the latter). No executable code
 * anywhere in this file or the one it reads — `jsonPath` is a tiny fixed
 * dotted-path reader (see below), not a scripting language.
 */
@Serializable
data class DeclarativeSourceRule(
    val branchName: String,              // e.g. "data-breach-check"
    val urlTemplate: String,             // "{placeholder}" substituted from the caller-supplied param
    val summaryTemplate: String,         // "{fieldName} found in {otherFieldName}" — placeholders are fieldExtraction's KEYS, not JSON paths
    val resultArrayPath: String? = null, // dotted path to an array in the response, if results are a list
    val fieldExtraction: Map<String, String> // name -> dotted JSON path, resolved per result item
)

class DeclarativeLeadSource(
    private val context: Context,
    private val ruleFile: File,
    private val param: String,           // the single input this run's query is about (handle/domain/ip/etc.)
    private val paramPlaceholder: String = "{query}"
) : LeadSource {

    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val rule = Json { ignoreUnknownKeys = true }
            .decodeFromString(DeclarativeSourceRule.serializer(), ruleFile.readText())

        val client = TorEnforcedHttpClient.forBranch(context, ctx.caseFile.caseId, rule.branchName)
        val url = rule.urlTemplate.replace(paramPlaceholder, param)
        val root = FarcastHttp.getJson(client, url)

        val items: List<JsonElement> = rule.resultArrayPath?.let { path -> resolvePath(root, path)?.jsonArray }
            ?: listOf(root)

        return items.mapNotNull { item ->
            val fields = rule.fieldExtraction.mapValues { (_, path) -> resolvePath(item, path)?.jsonPrimitive?.contentOrNull ?: "" }
            if (fields.values.all { it.isBlank() }) return@mapNotNull null
            val summary = substituteTemplate(rule.summaryTemplate, fields)
            Lead(
                id = UUID.randomUUID().toString(), caseId = ctx.caseFile.caseId, branch = rule.branchName,
                summary = summary, sourceIds = listOf("src-plugin-${rule.branchName}"),
                createdAtEpochMs = System.currentTimeMillis(), rawData = fields,
                category = LeadCategory.PUBLIC_RECORD
            )
        }
    }

    /** Tiny fixed dotted-path resolver — "a.b.c" walks JsonObjects, "a.0.b" walks
     *  into a JsonArray index. This is the entire "interpreter": no branching,
     *  no loops a plugin author controls, no function calls — just field access. */
    private fun resolvePath(root: JsonElement, path: String): JsonElement? {
        var current: JsonElement? = root
        for (segment in path.split(".")) {
            current = when {
                current == null -> return null
                segment.toIntOrNull() != null -> current.jsonArray.getOrNull(segment.toInt())
                else -> current.jsonObject[segment]
            }
        }
        return current
    }

    private fun substituteTemplate(template: String, fields: Map<String, String>): String {
        var result = template
        fields.forEach { (k, v) -> result = result.replace("{$k}", v) }
        return result
    }
}
