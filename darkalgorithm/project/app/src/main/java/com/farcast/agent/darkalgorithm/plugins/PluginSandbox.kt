package com.farcast.agent.darkalgorithm.plugins

import org.mozilla.javascript.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Honest framing, since this is the part where "sandboxed" needs to mean
 * something specific: VS Code does NOT actually sandbox extensions — they
 * run with full Node.js access in the same process, and the marketplace's
 * real safety mechanism is manual review + reputation, not technical
 * isolation (which is exactly why malicious VS Code extensions keep
 * happening). This class aims to do better than that, concretely:
 *
 *  - DenyAllClassShutter: Rhino's documented hook for blocking ALL Java
 *    class access from script code (java.lang.Runtime, java.io.File,
 *    reflection, everything) — returns false unconditionally, and the
 *    Packages/java/org/com/etc. top-level globals Rhino normally injects
 *    are deleted from scope on top of that, so there's no LiveConnect
 *    bridge out of the interpreter at all.
 *  - No fetch/network global is ever injected into script scope. A script
 *    cannot make its own network call, full stop — the host fetches
 *    (PluginManifest.declaredFetchUrlTemplate) and hands the script already-
 *    fetched JSON to transform. This closes off exfiltration and SSRF as
 *    attack surfaces regardless of anything else.
 *  - Hard instruction-count ceiling (via a custom ContextFactory overriding
 *    observeInstructionCount, Rhino's own documented mechanism for this)
 *    AND a wall-clock deadline checked at the same checkpoint — two
 *    independent reasons a runaway/malicious loop gets killed, not one.
 *
 * RESIDUAL RISK, stated plainly rather than overclaimed: no from-memory
 * implementation of a JS sandbox should be presented as bulletproof. JS
 * engines have had real VM-escape bugs before, in heavily-audited engines
 * with full security teams behind them. What's built here is genuine
 * defense in depth — denied Java access, no network capability, hard
 * resource limits — not a guarantee against every possible interpreter bug.
 * A plugin catalogue built on this should still have a manual review step
 * before a SCRIPT plugin goes live, the same real safety net VS Code's
 * marketplace actually relies on — just layered on top of a sandbox that
 * VS Code doesn't have, instead of instead of one.
 */
object PluginSandbox {

    class ScriptTimeoutException(message: String) : RuntimeException(message)
    class ScriptRuntimeException(message: String, cause: Throwable?) : RuntimeException(message, cause)

    private object DenyAllClassShutter : ClassShutter {
        override fun visibleToScripts(fullClassName: String): Boolean = false
    }

    private class LimitedContextFactory(private val maxInstructions: Long, private val deadlineNanos: AtomicLong) : ContextFactory() {
        private val totalInstructions = AtomicLong(0)
        override fun makeContext(): Context {
            val cx = super.makeContext()
            cx.optimizationLevel = -1 // required for the instruction observer to fire at all (interpreted mode only)
            cx.instructionObserverThreshold = 10_000
            return cx
        }
        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            if (System.nanoTime() > deadlineNanos.get()) {
                throw ScriptTimeoutException("script exceeded its wall-clock time limit")
            }
            if (totalInstructions.addAndGet(instructionCount.toLong()) > maxInstructions) {
                throw ScriptTimeoutException("script exceeded its instruction budget ($maxInstructions)")
            }
        }
    }

    data class SandboxResult(val outputJson: String?, val error: String?)

    /**
     * Runs `scriptSource`'s `process(input)` function against `inputJson`,
     * returns whatever it returns, JSON-stringified via Rhino's built-in
     * JSON object (no custom serialization path that could itself become an
     * attack surface). Bounded by both maxInstructions and timeoutMs —
     * whichever is hit first ends it.
     */
    suspend fun runTransform(
        scriptSource: String,
        inputJson: String,
        timeoutMs: Long = 3_000,
        maxInstructions: Long = 50_000_000
    ): SandboxResult = withContext(Dispatchers.Default) {
        val deadline = AtomicLong(System.nanoTime() + timeoutMs * 1_000_000)
        val factory = LimitedContextFactory(maxInstructions, deadline)

        // Honest limitation: withTimeout() cancels at coroutine SUSPENSION
        // points, and the Rhino call below is one long blocking (non-
        // suspending) call with none inside it — so withTimeout alone cannot
        // forcibly stop a stuck script mid-execution; its real job here is
        // making sure THIS FUNCTION returns to its caller within a bounded
        // time even in that edge case. The actual enforcement that stops the
        // script itself is observeInstructionCount's deadline check above,
        // which Rhino's interpreter calls periodically during execution —
        // that's what kills a runaway loop, not withTimeout. If a script
        // somehow runs long between two instruction-observer callbacks, the
        // underlying thread can still run to completion in the background
        // after this function has already returned a timeout result to the
        // caller — a real edge case, not fully closed, worth knowing before
        // trusting this for a script source you don't also trust somewhat.
        try {
            withTimeout(timeoutMs + 500) {
                val cx = factory.enterContext()
                try {
                    cx.setClassShutter(DenyAllClassShutter)
                    val scope: Scriptable = cx.initStandardObjects()
                    // Belt-and-suspenders on top of ClassShutter: remove the
                    // LiveConnect entry points entirely rather than rely only
                    // on them failing closed.
                    for (name in listOf("Packages", "java", "org", "com", "edu", "net", "JavaAdapter", "JavaImporter")) {
                        scope.delete(name)
                    }

                    // JSON.parse, not eval-style string embedding — the input
                    // came from an external API response (host-fetched per
                    // the plugin's declaredFetchUrlTemplate), so it gets
                    // parsed as DATA, never evaluated as JS, even inside the
                    // sandbox. Closes off a response that's crafted to look
                    // like executable JS rather than plain JSON.
                    val jsonGlobal = ScriptableObject.getProperty(scope, "JSON") as Scriptable
                    val parseFn = ScriptableObject.getProperty(jsonGlobal, "parse") as Function
                    val inputValue = parseFn.call(cx, scope, jsonGlobal, arrayOf(inputJson))
                    ScriptableObject.putProperty(scope, "input", inputValue)

                    cx.evaluateString(scope, scriptSource, "plugin-script", 1, null)
                    val processFn = scope.get("process", scope)
                    if (processFn !is Function) {
                        return@withTimeout SandboxResult(null, "script did not define a top-level process(input) function")
                    }
                    val result = processFn.call(cx, scope, scope, arrayOf(inputValue))
                    val stringify = ScriptableObject.getProperty(jsonGlobal, "stringify") as Function
                    val jsonText = stringify.call(cx, scope, jsonGlobal, arrayOf(result)) as String
                    SandboxResult(jsonText, null)
                } finally {
                    Context.exit()
                }
            }
        } catch (e: ScriptTimeoutException) {
            SandboxResult(null, "timed out: ${e.message}")
        } catch (e: TimeoutCancellationException) {
            SandboxResult(null, "timed out (coroutine-level backstop)")
        } catch (e: Exception) {
            SandboxResult(null, "script error: ${e.message}")
        }
    }
}
