package com.farcast.agent.darkalgorithm

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DarkMixer — ported from Home Mixer: the orchestrator that runs one pipeline
 * pass end to end. Home Mixer exposes a gRPC ScoredPostsService; DarkMixer
 * exposes a plain suspend API because it's running inside the app process on
 * a phone, not as a separate backend service — there's no network boundary
 * to put a gRPC endpoint on.
 *
 * Section 3.D requirements this satisfies directly:
 *  - continuous background processing  -> runWorker() loop on a coroutine
 *  - multi-step task execution          -> each selected Lead can spawn child
 *                                          Leads (parentLeadId), re-entering
 *                                          the pipeline on the next tick
 *  - branch exploration                 -> LeadSource per FARCAST module
 *  - result evaluation                  -> PhoenixLite scoring
 *  - progress monitoring                -> `progress` StateFlow-free callback
 *  - task cancellation and recovery     -> AtomicBoolean checked between
 *                                          EVERY stage, not just between runs;
 *                                          a cancelled run leaves leads in
 *                                          whatever status they'd reached, so
 *                                          resuming just re-queues PENDING/
 *                                          SCORED leads on the next start()
 *  - structured output                  -> CaseFile is @Serializable end to end
 */
class DarkMixer(
    sources: List<LeadSource>,
    /** Re-evaluated at the START of every single pass, including passes inside
     *  an already-running startWorker() loop — this is what lets installing or
     *  removing a plugin take effect on the very next tick, with no restart.
     *  Defaults to always returning the constructor's fixed `sources` list, so
     *  nothing changes for callers that don't pass one. */
    private val sourcesProvider: suspend () -> List<LeadSource> = { sources },
    private val hydrators: List<LeadHydrator> = emptyList(),
    private val preFilters: List<LeadFilter> = listOf(SafetyGate),   // cheap gate, runs before scoring
    private val scorer: LeadScorer = DefaultPhoenixLite.build(),
    private val postFilters: List<LeadFilter> = listOf(SafetyGate), // same gate again, mirrors X's
                                                                     // pre-ranking + post-selection pattern
    private val selector: LeadSelector = LeadSelector { scored, limit ->
        scored.filter { it.status != LeadStatus.BLOCKED }
            .sortedByDescending { it.score ?: Double.NEGATIVE_INFINITY }
            .take(limit)
    },
    private val sideEffects: List<LeadSideEffect> = emptyList(),
    private val onProgress: (ProgressEvent) -> Unit = {}
) {
    private val cancelled = AtomicBoolean(false)
    private var job: Job? = null

    data class ProgressEvent(val stage: String, val caseId: String, val detail: String)

    /** One full pipeline pass over a case. Safe to call again later — it's
     *  idempotent over leads that are already EXPLORED/BLOCKED/DISCARDED. */
    suspend fun runOnce(caseFile: CaseFile, selectLimit: Int = 20): CaseFile = coroutineScope {
        val ctx = RunContext(caseFile) { cancelled.get() }

        onProgress(ProgressEvent("source", caseFile.caseId, "fetching candidate leads"))
        val activeSources = sourcesProvider()   // re-read every pass — see sourcesProvider's doc
        val fetched = activeSources.map { src -> async { if (ctx.cancelled()) emptyList() else src.fetch(ctx) } }
            .awaitAll().flatten()
        if (ctx.cancelled()) return@coroutineScope caseFile

        caseFile.leads += fetched.filter { f -> caseFile.leads.none { it.id == f.id } }

        val pending = caseFile.leads.filter { it.status == LeadStatus.PENDING }

        onProgress(ProgressEvent("hydrate", caseFile.caseId, "${pending.size} leads"))
        val hydrated = pending.map { lead ->
            async {
                var h = lead
                for (hydrator in hydrators) { if (ctx.cancelled()) break; h = hydrator.hydrate(h, ctx) }
                h.status = LeadStatus.HYDRATED
                h
            }
        }.awaitAll()
        if (ctx.cancelled()) return@coroutineScope caseFile

        onProgress(ProgressEvent("pre-filter", caseFile.caseId, "${hydrated.size} leads"))
        val survivedPreFilter = hydrated.filter { lead ->
            preFilters.all { f -> f.test(lead, ctx) is FilterResult.Pass }
        }

        onProgress(ProgressEvent("score", caseFile.caseId, "${survivedPreFilter.size} leads"))
        val scored = survivedPreFilter.map { lead -> async { if (ctx.cancelled()) lead else scorer.score(lead, ctx) } }
            .awaitAll()
        if (ctx.cancelled()) return@coroutineScope caseFile

        onProgress(ProgressEvent("post-filter", caseFile.caseId, "${scored.size} leads"))
        val survivedPostFilter = scored.filter { lead ->
            postFilters.all { f -> f.test(lead, ctx) is FilterResult.Pass }
        }

        val selected = selector.select(survivedPostFilter, selectLimit)
        selected.forEach { it.status = LeadStatus.SELECTED }

        onProgress(ProgressEvent("side-effects", caseFile.caseId, "${selected.size} leads"))
        for (effect in sideEffects) { if (ctx.cancelled()) break; effect.run(selected, ctx) }

        onProgress(ProgressEvent("done", caseFile.caseId, "${selected.size} selected this pass"))
        caseFile
    }

    /** Continuous background processing (Section 3.D). Call stop() to cancel + allow recovery. */
    fun startWorker(scope: CoroutineScope, caseFile: CaseFile, intervalMs: Long = 5_000) {
        cancelled.set(false)
        job = scope.launch {
            while (isActive && !cancelled.get()) {
                runOnce(caseFile)
                delay(intervalMs)
            }
        }
    }

    /** Cancellation + recovery: leads simply resume from whatever status they were left in. */
    fun stop() {
        cancelled.set(true)
        job?.cancel()
    }

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        fun importCase(jsonText: String): CaseFile = json.decodeFromString(jsonText)
        fun exportCase(caseFile: CaseFile): String = json.encodeToString(caseFile)
    }
}
