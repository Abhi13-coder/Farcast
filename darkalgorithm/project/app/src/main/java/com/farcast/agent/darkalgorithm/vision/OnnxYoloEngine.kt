package com.farcast.agent.darkalgorithm.vision

import ai.onnxruntime.*
import android.content.Context
import java.io.File
import java.nio.FloatBuffer

/**
 * Section 3.B — ONNX Runtime integration. Uses the official Microsoft
 * artifact (com.microsoft.onnxruntime:onnxruntime-android, MIT license,
 * confirmed current release 1.27.0 as of this writing — verified against
 * Maven Central rather than assumed).
 *
 * LICENSING NOTE ON THE MODEL ITSELF (separate from ONNX Runtime, which is
 * MIT and unencumbered): Section 3.C asks for "one general-purpose YOLO
 * model." The easiest current export is Ultralytics' YOLOv8/YOLO11, shipped
 * under AGPL-3.0 — compatible with FARCAST being open source (confirmed),
 * but AGPL still means the actual source FARCAST ships under needs to be
 * AGPL-compatible and the weights' provenance/license noted in-repo, not
 * silently bundled with no attribution. A permissively-licensed alternative
 * (e.g. YOLOX, Apache-2.0) remains worth knowing about even without the
 * closed-source pressure, uses the same engine/decode shape below with
 * different tensor names — swap MODEL_INPUT_NAME/MODEL_OUTPUT_NAME and
 * confirm the output layout matches YoloDetector's assumptions.
 *
 * Lifecycle: one OrtSession per loaded model, explicitly closed in
 * release() — never left running in the background per Section 6's "avoid
 * permanently loaded AI models."
 */
class OnnxYoloEngine(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    var inputSize: Int = 640
        private set

    sealed class LoadResult {
        data object Success : LoadResult()
        data class Incompatible(val reason: String) : LoadResult()
        data class Error(val message: String) : LoadResult()
    }

    /** Model compatibility validation: confirm it has exactly one image input
     *  of the expected [1,3,H,W] shape before committing to it, rather than
     *  finding out at inference time with a cryptic native crash. */
    fun load(modelFile: File, useNnapi: Boolean = true): LoadResult {
        return try {
            val e = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setMemoryPatternOptimization(true)  // memory-conscious: reuse buffers across runs
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                if (useNnapi) {
                    try { addNnapi() } catch (_: Throwable) { /* fall back to CPU silently — "optional hardware acceleration" */ }
                }
            }
            val s = e.createSession(modelFile.absolutePath, options)

            val inputInfo = s.inputInfo.values.firstOrNull()?.info as? TensorInfo
                ?: return LoadResult.Incompatible("model has no tensor input").also { s.close() }
            val shape = inputInfo.shape
            if (shape.size != 4 || shape[1] != 3L) {
                s.close()
                return LoadResult.Incompatible("expected [1,3,H,W] input, got ${shape.joinToString()}")
            }
            inputSize = if (shape[2] > 0) shape[2].toInt() else 640  // dynamic axis falls back to 640

            env = e; session = s
            LoadResult.Success
        } catch (t: Throwable) {
            LoadResult.Error(t.message ?: "unknown ONNX load failure")
        }
    }

    /** Raw forward pass. Caller (YoloDetector) owns pre/post-processing —
     *  this class's job ends at "pixels in, raw tensor out," matching
     *  Section 3.B's separation of model lifecycle from pre/post-processing. */
    fun runRaw(chwFloats: FloatArray): OrtSession.Result {
        val s = session ?: error("call load() first")
        val e = env ?: error("call load() first")
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        OnnxTensor.createTensor(e, FloatBuffer.wrap(chwFloats), shape).use { tensor ->
            val inputName = s.inputNames.first()
            return s.run(mapOf(inputName to tensor))
        }
    }

    val isLoaded: Boolean get() = session != null

    /** Explicit release — called when a plugin/model is swapped or the
     *  feature using it closes, not left to GC. */
    fun release() {
        session?.close(); session = null
        env = null
    }
}
