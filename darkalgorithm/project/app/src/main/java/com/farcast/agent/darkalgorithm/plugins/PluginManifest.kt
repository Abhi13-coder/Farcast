package com.farcast.agent.darkalgorithm.plugins

import kotlinx.serialization.Serializable

/**
 * Section 4's manifest fields, plus `kind` and `extensionPoint`, which are
 * what make DarkMixer actually behave differently per installed plugin.
 *
 * `kind` is the safety boundary. Every value here is DATA interpreted by
 * code already in the app — nothing here causes a downloaded file to be
 * executed as code:
 *  - MODEL: a .onnx file, loaded by the existing OnnxYoloEngine (same
 *    generic loader used for the built-in YOLO model — a plugin model
 *    isn't special-cased, it's just another file path).
 *  - DECLARATIVE_SOURCE: a JSON rule set describing a new OSINT branch —
 *    URL template + field-extraction paths — interpreted by
 *    DeclarativeLeadSource. No code, just data a fixed interpreter reads.
 *  - SCORING_WEIGHTS: a JSON map of PhoenixLite estimator weights for a
 *    given branch — adjusts prioritization, doesn't add new capabilities.
 */
@Serializable
enum class PluginKind {
    MODEL, DECLARATIVE_SOURCE, SCORING_WEIGHTS,
    /** Real scripted logic — not just URL-template + field-path mapping.
     *  Runs in PluginSandbox's locked-down Rhino interpreter: no Java
     *  reflection, no filesystem, no network calls from inside the script
     *  itself. The host fetches data (declaredFetchUrlTemplate below) and
     *  hands the script that JSON to transform; the script returns leads as
     *  plain data, it never gets a capability to do anything but compute. */
    SCRIPT
}

@Serializable
enum class ExtensionPoint { SOURCE, SCORER_WEIGHTS, VISION_MODEL }

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val author: String,
    val category: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val minFarcastVersion: String,
    val supportedAndroidVersions: List<Int>,       // minSdk values this plugin supports
    val supportedCpuArchitectures: List<String>,   // "arm64-v8a", "armeabi-v7a", ...
    val requiredRuntime: String,                   // "onnxruntime", "none", ...
    val kind: PluginKind,
    val extensionPoint: ExtensionPoint,
    val permissions: List<String> = emptyList(),   // declared network/data scopes this plugin needs, shown to the user before install
    val modelInputSpec: String? = null,            // only meaningful for MODEL plugins
    val modelOutputSpec: String? = null,
    val declaredFetchUrlTemplate: String? = null   // SCRIPT/DECLARATIVE_SOURCE: the ONE url the host will ever
                                                    // fetch on this plugin's behalf — shown to the user before
                                                    // install as exactly what network access this plugin gets
)

@Serializable
data class PluginCatalogue(val plugins: List<PluginManifest>)
