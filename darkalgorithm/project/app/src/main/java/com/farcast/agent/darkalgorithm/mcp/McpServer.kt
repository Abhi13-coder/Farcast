package com.farcast.agent.darkalgorithm.mcp

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Embedded MCP server so an AI assistant (Claude Desktop/Code, or any MCP
 * client) can connect to FARCAST directly.
 *
 * Implements the MCP spec's dated revision 2025-11-25 (JSON-RPC 2.0 over a
 * single HTTP POST endpoint, stateful: initialize -> Mcp-Session-Id -> tools/
 * list -> tools/call). I'm targeting that revision specifically because it's
 * the current FINAL spec; there's a newer 2026-07-28 release candidate that
 * drops the session handshake for a stateless header-based call convention
 * (Mcp-Method/Mcp-Name headers instead of an initialize round-trip) — worth
 * revisiting once that's finalized and clients broadly support it, but
 * building against an RC now risks working against a spec that still moves.
 *
 * Binding: 127.0.0.1 ONLY by default. Reaching it from a desktop MCP client
 * means `adb forward tcp:8765 tcp:8765` — deliberately a developer-tool step,
 * not something that works out of the box over the network. LAN binding is a
 * separate opt-in (McpBindScope.LOCAL_NETWORK) with its own explicit warning
 * surfaced in Settings; this class takes bindScope as a parameter rather than
 * defaulting to it.
 *
 * Every request except the health check requires `Authorization: Bearer <token>`
 * matching McpAuth's current pairing token. No token issued = server answers
 * 503 to everything but the health check, full stop.
 */
class McpServer(
    private val repo: McpTools.CaseRepository,
    private val bindScope: McpBindScope = McpBindScope.LOOPBACK_ONLY,
    port: Int = 8765
) : NanoHTTPD(
    if (bindScope == McpBindScope.LOOPBACK_ONLY) "127.0.0.1" else null,
    port
) {
    private val sessions = mutableSetOf<String>()
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri == "/healthz") {
            return ok("""{"status":"ok","authRequired":${McpAuth.isEnabled()}}""")
        }

        if (!McpAuth.isEnabled()) {
            return error(Response.Status.SERVICE_UNAVAILABLE, "AI access is disabled in Settings.")
        }
        val authHeader = session.headers["authorization"]
        val token = authHeader?.removePrefix("Bearer ")?.trim()
        if (!McpAuth.isValid(token)) {
            return error(Response.Status.UNAUTHORIZED, "invalid or missing pairing token")
        }

        if (session.method != Method.POST) {
            return error(Response.Status.METHOD_NOT_ALLOWED, "POST only")
        }

        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val rawBody = bodyMap["postData"] ?: return error(Response.Status.BAD_REQUEST, "empty body")
        val request = runCatching { json.parseToJsonElement(rawBody).jsonObject }
            .getOrElse { return error(Response.Status.BAD_REQUEST, "malformed JSON-RPC") }

        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.content
            ?: return rpcError(id, -32600, "missing method")

        return when (method) {
            "initialize" -> {
                val sessionId = UUID.randomUUID().toString()
                sessions += sessionId
                val result = buildJsonObject {
                    put("protocolVersion", "2025-11-25")
                    put("serverInfo", buildJsonObject {
                        put("name", "farcast-dark-algorithm")
                        put("version", "0.1.0")
                    })
                    put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
                }
                ok(rpcResult(id, result).toString()).also {
                    it.addHeader("Mcp-Session-Id", sessionId)
                }
            }
            "tools/list" -> {
                if (!sessionValid(session)) return rpcError(id, -32001, "missing/invalid Mcp-Session-Id")
                ok(rpcResult(id, buildJsonObject { put("tools", McpTools.toolCatalogue()) }).toString())
            }
            "tools/call" -> {
                if (!sessionValid(session)) return rpcError(id, -32001, "missing/invalid Mcp-Session-Id")
                val params = request["params"]?.jsonObject
                    ?: return rpcError(id, -32602, "missing params")
                val toolName = params["name"]?.jsonPrimitive?.content
                    ?: return rpcError(id, -32602, "missing tool name")
                val args = params["arguments"]?.jsonObject ?: buildJsonObject {}
                val result = runBlocking { McpTools.call(toolName, args, repo) }
                ok(rpcResult(id, buildJsonObject {
                    put("content", buildJsonArray {
                        addJsonObject { put("type", "text"); put("text", result.toString()) }
                    })
                }).toString())
            }
            else -> rpcError(id, -32601, "method not found: $method")
        }
    }

    private fun sessionValid(session: IHTTPSession): Boolean =
        session.headers["mcp-session-id"] in sessions

    private fun rpcResult(id: JsonElement?, result: JsonElement) = buildJsonObject {
        put("jsonrpc", "2.0"); id?.let { put("id", it) }; put("result", result)
    }

    private fun rpcError(id: JsonElement?, code: Int, message: String): Response {
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); id?.let { put("id", it) }
            put("error", buildJsonObject { put("code", code); put("message", message) })
        }
        return ok(body.toString())
    }

    private fun ok(body: String) =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)

    private fun error(status: Response.Status, message: String) =
        newFixedLengthResponse(status, "application/json", """{"error":"$message"}""")
}
