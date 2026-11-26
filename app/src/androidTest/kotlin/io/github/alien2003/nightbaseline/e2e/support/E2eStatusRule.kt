package io.github.alien2003.nightbaseline.e2e.support

import android.os.SystemClock
import io.github.alien2003.nightbaseline.e2e.E2e
import kotlinx.serialization.Serializable
import org.junit.AssumptionViolatedException
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Writes `status/<Class>.<test>.json` for every test: PASSED / FAILED / SKIPPED, the duration and the test's
 * [metrics]. Metrics are counts, durations and flags only (the types allow nothing else), so a report never holds a
 * measurement value, a date of a night or a package name.
 *
 * @param appProcess the test runs inside the installed app (E2eRunner): the in-memory WorkManager must be installed.
 */
class E2eStatusRule(
    private val appProcess: Boolean = true,
) : TestWatcher() {
    private val counts = LinkedHashMap<String, Long>()
    private val flags = LinkedHashMap<String, Boolean>()
    private var startedAt = 0L

    /** Records a count or a duration (ms). */
    fun count(
        name: String,
        value: Long,
    ) {
        counts[name] = value
    }

    /** Records a flag. */
    fun flag(
        name: String,
        value: Boolean,
    ) {
        flags[name] = value
    }

    /**
     * One status file.
     *
     * @property test `Class.method`.
     * @property result PASSED, FAILED or SKIPPED.
     * @property durationMs how long the test took.
     * @property reason for SKIPPED: the suite's own explanation; for FAILED: the exception class only.
     * @property counts counts and durations.
     * @property flags flags.
     * @property runId the runner's run id.
     */
    @Serializable
    data class Status(
        val test: String,
        val result: String,
        val durationMs: Long,
        val reason: String? = null,
        val counts: Map<String, Long>,
        val flags: Map<String, Boolean>,
        val runId: String,
    )

    override fun starting(description: Description) {
        startedAt = SystemClock.elapsedRealtime()
        if (appProcess) E2eWorkManager.requireInstalled()
        E2e.progress("${description.testClass.simpleName}.${description.methodName}: started")
    }

    override fun succeeded(description: Description) = write(description, "PASSED", null)

    override fun failed(
        e: Throwable,
        description: Description,
    ) = write(description, "FAILED", e.javaClass.simpleName)

    override fun skipped(
        e: AssumptionViolatedException,
        description: Description,
    ) {
        // The suite's own assumption messages explain why (for example a permission that is not granted).
        E2e.progress("${description.testClass.simpleName}.${description.methodName}: SKIPPED: ${e.message}")
        write(description, "SKIPPED", e.message)
    }

    private fun write(
        description: Description,
        result: String,
        reason: String?,
    ) {
        val name = "${description.testClass.simpleName}.${description.methodName}"
        if (appProcess) {
            // Workers the app's start-up code or screens asked for never ran; their number is part of every report.
            counts["refusedWorkers"] = E2eWorkManager.refusedWorkers().size.toLong()
        }
        val status =
            Status(
                test = name,
                result = result,
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                reason = reason,
                counts = counts.toMap(),
                flags = flags.toMap(),
                runId = E2e.runId,
            )
        runCatching { E2e.writeJson("status/$name.json", status) }
        E2e.progress("$name: $result")
    }
}
