package com.farcast.agent.darkalgorithm.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Thin GET/POST helper every FeatureService below uses. All calls go through
 *  whatever OkHttpClient is passed in — in practice always one from
 *  TorEnforcedHttpClient.forBranch(), so every feature stays Tor-routed with
 *  its own circuit, same as the DomainRdapSource/PersonaUsernameSource stubs
 *  already did. */
object FarcastHttp {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getText(client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} for $url")
                resp.body?.string() ?: ""
            }
        }

    suspend fun getJson(client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        json.parseToJsonElement(getText(client, url, headers))

    /** Returns null instead of throwing on non-2xx — several panels (GitHub/Reddit/
     *  GitLab/etc. "quick 7" checks) treat a 404 as a normal "not found" result. */
    suspend fun getJsonOrNull(client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): JsonElement? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.string()?.let { json.parseToJsonElement(it) }
                }
            } catch (_: Exception) { null }
        }

    suspend fun postJson(client: OkHttpClient, url: String, bodyJson: String, headers: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val body = bodyJson.toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).post(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                json.parseToJsonElement(text)
            }
        }

    suspend fun postForm(client: OkHttpClient, url: String, fields: Map<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val formBuilder = FormBody.Builder()
            fields.forEach { (k, v) -> formBuilder.add(k, v) }
            val req = Request.Builder().url(url).post(formBuilder.build()).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                json.parseToJsonElement(text)
            }
        }

    /** HEAD-ish existence probe used by the username-OSINT panel: does the status
     *  code / final URL / response body match what a site's check definition expects.
     *  Mirrors sherlockCheck()/maigretCheck() in the website 1:1. */
    suspend fun probe(client: OkHttpClient, url: String): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                ProbeResult(statusCode = resp.code, finalUrl = resp.request.url.toString(), body = resp.body?.string() ?: "")
            }
        } catch (e: Exception) {
            ProbeResult(statusCode = -1, finalUrl = url, body = "", blocked = true)
        }
    }

    data class ProbeResult(val statusCode: Int, val finalUrl: String, val body: String, val blocked: Boolean = false)
}
