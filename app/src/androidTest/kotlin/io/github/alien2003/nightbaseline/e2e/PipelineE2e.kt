package io.github.alien2003.nightbaseline.e2e

import android.app.NotificationManager
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RoomNightRepository
import io.github.alien2003.nightbaseline.data.local.RoomRunLog
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.db.NightBaselineDatabase
import io.github.alien2003.nightbaseline.data.local.fake.FakeSettingsRepository
import io.github.alien2003.nightbaseline.e2e.safety.E2eHealth
import io.github.alien2003.nightbaseline.e2e.safety.ReadBudget
import io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule
import io.github.alien2003.nightbaseline.work.notify.NightNotice
import io.github.alien2003.nightbaseline.work.notify.NightNotifier
import io.github.alien2003.nightbaseline.work.process.NightProcessor
import io.github.alien2003.nightbaseline.work.process.NightReevaluator
import io.github.alien2003.nightbaseline.work.process.ProcessOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.time.Clock

/**
 * The daily check (NightProcessor, which runs the Evaluator) over the REAL Health Connect data of the configured
 * source, with nothing stored and nothing notified: the reads go through the app's own read-only reader (budgeted),
 * the nights and the run log into an in-memory Room database of the app's own database class (the app's database file
 * is never opened), the settings are an in-memory copy of the app's, and the notifier is a fake that posts nothing.
 * The outcome must be an evaluation or a valid waiting state; Blocked fails. Reports counts and outcome kinds only.
 */
@RunWith(AndroidJUnit4::class)
class PipelineE2e {
    @get:Rule
    val status = E2eStatusRule()

    private val budget = ReadBudget.fromArgs(DEFAULT_BUDGET)

    /** Posts nothing ("notifications blocked"), so no notification can come from the test. */
    private class FakeNotifier : NightNotifier {
        override fun show(notice: NightNotice): Boolean = false
    }

    @Test
    fun processorEvaluatesTheRealSourceInMemory() {
        val context = E2e.app
        val notifications = context.getSystemService(NotificationManager::class.java)
        val shownBefore = notifications.activeNotifications.size
        val db = Room.inMemoryDatabaseBuilder(context, NightBaselineDatabase::class.java).build()
        try {
            val clock = Clock.systemUTC()
            val settings = FakeSettingsRepository(E2eHealth.settingsSnapshot())
            val nights = RoomNightRepository(db.nightDao())
            val runLog = RoomRunLog(db.runLogDao(), clock)
            val notifier = FakeNotifier()
            val processor =
                processorOf(
                    listOf(settings, E2eHealth.reader(budget), nights, notifier, runLog, clock, ZoneSource.SYSTEM) +
                        NightReevaluator(settings, nights, runLog, clock),
                )
            val started = System.nanoTime()
            val outcome = runBlocking { processor.process() }
            status.count("processMs", (System.nanoTime() - started) / NANOS_PER_MS)
            status.count("readCalls", budget.spent.toLong())
            status.count("readBudget", budget.limit.toLong())
            status.count("permissionQueries", budget.metaCalls.toLong())
            status.flag("evaluated", outcome is ProcessOutcome.Evaluated)
            status.flag("waiting", outcome is ProcessOutcome.Retry)
            status.flag("nothingNew", outcome is ProcessOutcome.NothingNew)
            val stored = runBlocking { nights.since(E2eHealth.settingsSnapshot().sourcePackage, "0000-01-01") }
            status.count("nightsStored", stored.size.toLong())
            status.count("nightsLearning", stored.count { it.evaluation.status == NightStatus.LEARNING }.toLong())
            status.count("runLogLines", runBlocking { runLog.recent(RUN_LOG_LINES) }.size.toLong())
            E2e.progress("outcome: ${outcome.javaClass.simpleName}, nights stored in memory: ${stored.size}, reads: ${budget.spent}")
            checkOutcome(outcome, stored)
            assertTrue("every stored night must have an evaluation of its own date", stored.all { it.evaluation.date == it.date })
            assertEquals("notifications the app shows", shownBefore, notifications.activeNotifications.size)
        } finally {
            db.close()
        }
    }

    /**
     * The app's NightProcessor with [collaborators], matched to its constructor parameters by type. A collaborator
     * the suite does not know (an interface added to the processor later, such as a value-free decision trace) gets a
     * no-op implementation, so the suite keeps compiling and running against the processor as it is.
     */
    private fun processorOf(collaborators: List<Any>): NightProcessor {
        val constructor = NightProcessor::class.java.constructors.single()
        val args =
            constructor.parameterTypes.map { type ->
                collaborators.firstOrNull { type.isInstance(it) } ?: noOp(type)
            }
        return constructor.newInstance(*args.toTypedArray()) as NightProcessor
    }

    private fun noOp(type: Class<*>): Any {
        check(type.isInterface) { "NightProcessor needs a ${type.name} the suite cannot provide" }
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when {
                method.name == "toString" && method.parameterCount == 0 -> {
                    "no-op ${type.simpleName}"
                }

                method.name == "hashCode" && method.parameterCount == 0 -> {
                    System.identityHashCode(proxy)
                }

                method.name == "equals" && method.parameterCount == 1 -> {
                    proxy === args?.get(0)
                }

                else -> {
                    check(method.returnType == Void.TYPE) { "no-op ${type.simpleName}.${method.name} cannot return a value" }
                    null
                }
            }
        }
    }

    /** An evaluation, a valid waiting state or nothing new; Blocked fails. */
    private fun checkOutcome(
        outcome: ProcessOutcome,
        stored: List<NightRecord>,
    ) {
        when (outcome) {
            is ProcessOutcome.Evaluated -> {
                val record = stored.firstOrNull { it.date == outcome.date }
                assertTrue("the evaluated night is not in the in-memory database", record != null)
                assertEquals("the evaluated night's status", outcome.status, record?.evaluation?.status)
                assertTrue("a notification was reported as shown", !outcome.notified)
            }

            is ProcessOutcome.Retry -> {
                assertTrue("a retry must lie in the future", outcome.atMs > System.currentTimeMillis())
            }

            is ProcessOutcome.NothingNew -> {
                assertTrue("re-evaluated nights must be stored", outcome.reevaluated.all { d -> stored.any { it.date == d } })
            }

            is ProcessOutcome.Blocked -> {
                fail("the check was blocked: ${outcome.reason}")
            }
        }
    }

    private companion object {
        const val DEFAULT_BUDGET = 600
        const val NANOS_PER_MS = 1_000_000L
        const val RUN_LOG_LINES = 100
    }
}
