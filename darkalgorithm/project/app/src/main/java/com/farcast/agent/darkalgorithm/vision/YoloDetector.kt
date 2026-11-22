package com.farcast.agent.darkalgorithm.vision

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.min

data class BoundingBox(val x: Float, val y: Float, val w: Float, val h: Float) // normalized 0..1, center-based
data class Detection(val label: String, val confidence: Float, val box: BoundingBox)

/**
 * Decodes the common anchor-free single-output YOLO export layout:
 * output shape [1, 4+numClasses, numAnchors] (e.g. [1,84,8400] for 80 COCO
 * classes @ 640 input) — this is the layout Ultralytics' own `export(format=
 * "onnx")` produces, and what most current YOLO-family ONNX exports use.
 * If a specific export uses a different head (extra objectness channel,
 * [1,numAnchors,85] transposed, multi-scale outputs), this decode will
 * silently produce garbage boxes rather than throw — always sanity-check a
 * freshly wired model's output shape in SessionOptions logging before
 * trusting its detections (Section 3.B's "inference status reporting").
 */
class YoloDetector(
    private val engine: OnnxYoloEngine,
    private val labels: List<String> = YoloLabels.COCO_80,
    var confidenceThreshold: Float = 0.45f,
    var iouThreshold: Float = 0.45f
) {
    data class PreprocessResult(val chw: FloatArray, val scale: Float, val padX: Float, val padY: Float)

    /** Letterbox resize: scale to fit inputSize x inputSize preserving aspect
     *  ratio, pad the rest — standard YOLO preprocessing, needed so decode's
     *  box coordinates can be mapped back to the original image correctly. */
    private fun letterbox(bitmap: Bitmap, size: Int): Pair<Bitmap, PreprocessResult> {
        val scale = min(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val newW = (bitmap.width * scale).toInt()
        val newH = (bitmap.height * scale).toInt()
        val padX = (size - newW) / 2f
        val padY = (size - newH) / 2f

        val resized = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(resized)
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114)) // standard YOLO pad color
        val m = Matrix().apply { setScale(scale, scale); postTranslate(padX, padY) }
        canvas.drawBitmap(bitmap, m, null)
        return resized to PreprocessResult(FloatArray(0), scale, padX, padY)
    }

    private fun toChw(bitmap: Bitmap): FloatArray {
        val size = bitmap.width
        val chw = FloatArray(3 * size * size)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f                     // R plane
            chw[size * size + i] = ((p shr 8) and 0xFF) / 255f        // G plane
            chw[2 * size * size + i] = (p and 0xFF) / 255f            // B plane
        }
        return chw
    }

    fun detect(original: Bitmap): List<Detection> {
        require(engine.isLoaded) { "OnnxYoloEngine.load() must succeed before detect()" }
        val (letterboxed, meta) = letterbox(original, engine.inputSize)
        val chw = toChw(letterboxed)

        val result = engine.runRaw(chw)
        val output = result[0].value as Array<*>   // [1, 4+numClasses, numAnchors]
        @Suppress("UNCHECKED_CAST")
        val tensor2d = (output[0] as Array<FloatArray>)  // [4+numClasses][numAnchors]
        result.close()

        val numAnchors = tensor2d[0].size
        val numClasses = labels.size
        val raw = mutableListOf<Detection>()

        for (a in 0 until numAnchors) {
            var bestClass = -1; var bestScore = 0f
            for (c in 0 until numClasses) {
                val score = tensor2d[4 + c][a]
                if (score > bestScore) { bestScore = score; bestClass = c }
            }
            if (bestScore < confidenceThreshold || bestClass < 0) continue

            // cx, cy, w, h in letterboxed-image pixel space -> undo letterbox -> normalize to original image
            val cx = (tensor2d[0][a] - meta.padX) / meta.scale
            val cy = (tensor2d[1][a] - meta.padY) / meta.scale
            val w = tensor2d[2][a] / meta.scale
            val h = tensor2d[3][a] / meta.scale

            raw += Detection(
                label = labels[bestClass],
                confidence = bestScore,
                box = BoundingBox(cx / original.width, cy / original.height, w / original.width, h / original.height)
            )
        }
        return nms(raw)
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept += best
            sorted.removeAll { iou(best.box, it.box) > iouThreshold && it.label == best.label }
        }
        return kept
    }

    private fun iou(a: BoundingBox, b: BoundingBox): Float {
        val ax1 = a.x - a.w / 2; val ax2 = a.x + a.w / 2
        val ay1 = a.y - a.h / 2; val ay2 = a.y + a.h / 2
        val bx1 = b.x - b.w / 2; val bx2 = b.x + b.w / 2
        val by1 = b.y - b.h / 2; val by2 = b.y + b.h / 2
        val interX = max(0f, min(ax2, bx2) - max(ax1, bx1))
        val interY = max(0f, min(ay2, by2) - max(ay1, by1))
        val inter = interX * interY
        val union = a.w * a.h + b.w * b.h - inter
        return if (union <= 0f) 0f else inter / union
    }
}
