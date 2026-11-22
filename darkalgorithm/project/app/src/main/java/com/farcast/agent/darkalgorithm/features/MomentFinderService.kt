package com.farcast.agent.darkalgorithm.features

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import com.farcast.agent.darkalgorithm.vision.Detection
import com.farcast.agent.darkalgorithm.vision.OnnxYoloEngine
import com.farcast.agent.darkalgorithm.vision.YoloDetector
import java.io.File

/**
 * Moment Finder: "run YOLO once, search the labels forever." A webcam
 * snapshot, a frame grabbed from somewhere, any Lead with an image attached
 * gets auto-labeled, and every label becomes a searchable entry — "show me
 * every moment a 'truck' showed up in this case" across however many images
 * have been processed, without re-running detection each time.
 *
 * This is the thing that makes PUBLIC CAMS (#p-cam) actually useful for
 * investigation rather than just a gallery: pull a cam snapshot -> auto-label
 * it -> later, search "person" and get back every cam frame across the whole
 * case where one showed up, each linked back to its originating Lead.
 */
class MomentFinderService(context: Context) {
    private val db = Room.databaseBuilder(context, CaseDatabase::class.java, "farcast_case.db")
        // NOTE: a real release needs a proper Migration(1,2) here once this
        // schema ships to users with existing databases — fallback is fine
        // for development, not for an upgrade that would silently wipe rows.
        .fallbackToDestructiveMigration()
        .build()

    private val engine = OnnxYoloEngine(context)
    private var detector: YoloDetector? = null

    /** Call once at app start (or lazily on first use) with the bundled/
     *  downloaded model file. See OnnxYoloEngine's header for the model-
     *  licensing note before picking which .onnx file to ship. */
    fun loadModel(modelFile: File): OnnxYoloEngine.LoadResult {
        val result = engine.load(modelFile)
        if (result is OnnxYoloEngine.LoadResult.Success) detector = YoloDetector(engine)
        return result
    }

    data class Moment(val label: String, val confidence: Float, val leadId: String, val imagePath: String, val detectedAtEpochMs: Long)

    /** Runs detection on one image file and indexes every detection found —
     *  this is the "auto label" step. Returns what it found so a caller can
     *  show immediate feedback, in addition to it being persisted for search. */
    suspend fun processImage(caseId: String, leadId: String, imageFile: File): List<Detection> {
        val d = detector ?: error("call loadModel() first")
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return emptyList()
        val detections = d.detect(bitmap)
        bitmap.recycle()

        val now = System.currentTimeMillis()
        db.detectionDao().insertAll(detections.map { det ->
            DetectionRow(
                caseId = caseId, leadId = leadId, label = det.label, confidence = det.confidence,
                boxX = det.box.x, boxY = det.box.y, boxW = det.box.w, boxH = det.box.h,
                imagePath = imageFile.absolutePath, detectedAtEpochMs = now
            )
        })
        return detections
    }

    /** The actual "Moment Finder" search: type a label, get every moment it
     *  appeared in this case, newest first. */
    suspend fun findMoments(caseId: String, label: String): List<Moment> =
        db.detectionDao().byLabel(caseId, label.lowercase()).map {
            Moment(it.label, it.confidence, it.leadId, it.imagePath, it.detectedAtEpochMs)
        }

    /** What labels are even searchable in this case — populates a search
     *  suggestion/autocomplete list in the UI. */
    suspend fun availableLabels(caseId: String): List<LabelCount> = db.detectionDao().labelCounts(caseId)

    fun release() = engine.release()
}
