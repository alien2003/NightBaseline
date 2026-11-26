package io.github.alien2003.nightbaseline.e2e.safety

import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcChannel
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HcPointRecord
import io.github.alien2003.nightbaseline.data.hc.HcSession
import io.github.alien2003.nightbaseline.data.hc.HealthConnectGateway
import io.github.alien2003.nightbaseline.e2e.E2e
import java.util.concurrent.atomic.AtomicInteger

/**
 * A hard cap on the Health Connect read calls (`readRecords`, each page counted where the suite pages itself) one test
 * class makes through the suite's code; each class runs in its own instrumentation and the runner passes
 * `-e e2eReadBudget N`. Health Connect counts these reads against the app's quota (AOSP: 1000 per 15 minutes in the
 * background), so the whole run stays below it. Permission and feature queries are not rate-limited reads: they are
 * only counted ([metaCalls]).
 */
class ReadBudget(
    /** Calls allowed. */
    val limit: Int,
) {
    private val used = AtomicInteger(0)

    private val meta = AtomicInteger(0)

    /** Read calls made so far. */
    val spent: Int get() = used.get()

    /** Permission and feature queries made so far (not capped). */
    val metaCalls: Int get() = meta.get()

    /** Counts one permission or feature query. */
    fun countMeta() {
        meta.incrementAndGet()
    }

    /** Charges one read call; throws [BudgetExceeded] (an Error, so no `catch (e: Exception)` of the app swallows it). */
    fun charge(what: String) {
        val n = used.incrementAndGet()
        if (n > limit) throw BudgetExceeded("read budget of $limit Health Connect calls exceeded by $what")
    }

    /** Constructors. */
    companion object {
        /** Instrumentation argument: the budget of this test class. */
        const val ARG: String = "e2eReadBudget"

        /** The budget passed by the runner, or [default]. */
        fun fromArgs(default: Int): ReadBudget = ReadBudget(E2e.intArg(ARG, default).coerceAtLeast(0))
    }
}

/** Thrown when the suite would exceed its read budget. */
class BudgetExceeded(
    message: String,
) : Error(message)

/**
 * The app's [HealthConnectGateway] with every read call charged to [budget] (a gateway read that pages counts once).
 * The gateway only reads (it has no write or delete method), so everything built on it is read-only.
 */
class BudgetedGateway(
    private val delegate: HealthConnectGateway,
    private val budget: ReadBudget,
) : HealthConnectGateway {
    override fun availability(): HcAvailability = delegate.availability()

    override suspend fun grantedPermissions(): Set<String> {
        budget.countMeta()
        return delegate.grantedPermissions()
    }

    override suspend fun isFeatureAvailable(feature: HcFeature): Boolean {
        budget.countMeta()
        return delegate.isFeatureAvailable(feature)
    }

    override suspend fun readSleepSessions(
        startMs: Long,
        endMs: Long,
        origin: String?,
    ): List<HcSession> {
        budget.charge("readRecords(sleep)")
        return delegate.readSleepSessions(startMs, endMs, origin)
    }

    override suspend fun readChannel(
        channel: HcChannel,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): List<HcPointRecord> {
        budget.charge("readRecords(channel)")
        return delegate.readChannel(channel, startMs, endMs, origin)
    }

    override suspend fun hasRecords(
        channel: HcChannel,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): Boolean {
        budget.charge("readRecords(probe)")
        return delegate.hasRecords(channel, startMs, endMs, origin)
    }
}
