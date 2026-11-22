package com.farcast.agent.darkalgorithm.mcp

import com.farcast.agent.darkalgorithm.*
import kotlinx.serialization.json.*

/**
 * Tool definitions exposed over MCP (JSON Schema 2020-12 input schemas, per
 * spec). Every tool here operates on CaseFile data that already passed
 * SafetyGate inside DarkMixer — the MCP server doesn't get a separate,
 * looser code path to case data than the app's own UI does.
 *
 * Deliberately read/execute-only, no "delete_case" or "edit_evidence" tool:
 * an AI assistant connected this way can help analyze and run passes, not
 * silently rewrite the case record.
 */
object McpTools {

    /** A running set of cases DarkMixer is managing, keyed by caseId. In the
     *  real app this is backed by the same SQLite case DB the UI reads from. */
    interface CaseRepository {
        fun listCases(): List<CaseFile>
        fun getCase(caseId: String): CaseFile?
        suspend fun runPass(caseId: String): CaseFile?
    }

    fun toolCatalogue(): JsonArray = buildJsonArray {
        addJsonObject {
            put("name", "list_cases")
            put("description", "List case IDs and titles DARK ALGORITHM is tracking.")
            put("inputSchema", buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) })
        }
        addJsonObject {
            put("name", "get_case")
            put("description", "Get a case's leads, sources, and evidence as structured JSON.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    putJsonObject("caseId") { put("type", "string") }
                })
                put("required", buildJsonArray { add("caseId") })
            })
        }
        addJsonObject {
            put("name", "run_dark_algorithm_pass")
            put("description", "Run one DarkMixer pipeline pass on a case (Source->Hydrator->Filter->Scorer->Selector) and return newly selected leads.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    putJsonObject("caseId") { put("type", "string") }
                })
                put("required", buildJsonArray { add("caseId") })
            })
        }
        addJsonObject {
            put("name", "export_case")
            put("description", "Export a full case as the same JSON format Section 3.D's JSON export produces.")
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    putJsonObject("caseId") { put("type", "string") }
                })
                put("required", buildJsonArray { add("caseId") })
            })
        }
    }

    suspend fun call(name: String, arguments: JsonObject, repo: CaseRepository): JsonElement = when (name) {
        "list_cases" -> buildJsonArray {
            repo.listCases().forEach { c -> addJsonObject { put("caseId", c.caseId); put("title", c.title) } }
        }
        "get_case" -> {
            val caseId = arguments["caseId"]?.jsonPrimitive?.content
                ?: return errorResult("caseId is required")
            val case = repo.getCase(caseId) ?: return errorResult("no such case: $caseId")
            Json.parseToJsonElement(DarkMixer.exportCase(case))
        }
        "run_dark_algorithm_pass" -> {
            val caseId = arguments["caseId"]?.jsonPrimitive?.content
                ?: return errorResult("caseId is required")
            val result = repo.runPass(caseId) ?: return errorResult("no such case: $caseId")
            buildJsonObject {
                put("caseId", result.caseId)
                put("selectedLeadCount", result.leads.count { it.status == LeadStatus.SELECTED })
            }
        }
        "export_case" -> {
            val caseId = arguments["caseId"]?.jsonPrimitive?.content
                ?: return errorResult("caseId is required")
            val case = repo.getCase(caseId) ?: return errorResult("no such case: $caseId")
            buildJsonObject { put("json", DarkMixer.exportCase(case)) }
        }
        else -> errorResult("unknown tool: $name")
    }

    private fun errorResult(message: String): JsonElement = buildJsonObject {
        put("isError", true)
        put("message", message)
    }
}
