package io.github.alien2003.nightbaseline.seeder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * EMULATOR-ONLY seeder UI. Driven by intents (tools/emu/seed.sh):
 * `am start -n <pkg>/.SeedActivity --es scenario <name> [--ei nights <n>] [--es now <iso>] [--ei end_days_ago <n>]`,
 * `--es action append_tail` (late upload of the last `late_upload` seed) or `--es scenario wipe`.
 * Every run first deletes what the seeder wrote before, so a scenario always replaces the previous one. The result is
 * logged as one `SEED_RESULT ...` line under the tag [TAG], ending with ` run=<id>` when the intent has
 * `--es run_id <id>` ([SeedResult]).
 */
class SeedActivity : ComponentActivity() {
    private lateinit var log: TextView
    private var running = false

    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            line("granted ${granted.size}/${PERMISSIONS.size}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (PADDING_DP * resources.displayMetrics.density).toInt()
        log = TextView(this).apply { setPadding(pad, pad, pad, pad) }
        val grant =
            Button(this).apply {
                text = "Grant permissions"
                setOnClickListener { requestPermissions.launch(PERMISSIONS) }
            }
        val normal =
            Button(this).apply {
                text = "Seed normal"
                setOnClickListener { run(Intent().putExtra(EXTRA_SCENARIO, Scenario.NORMAL.key)) }
            }
        val wipe =
            Button(this).apply {
                text = "Wipe seeded data"
                setOnClickListener { run(Intent().putExtra(EXTRA_ACTION, ACTION_WIPE)) }
            }
        setContentView(
            ScrollView(this).apply {
                addView(
                    LinearLayout(this@SeedActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(pad, pad * TOP_PADDING_FACTOR, pad, pad)
                        addView(TextView(context).apply { text = "SYNTHETIC data for emulators only. Never use on a real phone." })
                        addView(grant)
                        addView(normal)
                        addView(wipe)
                        addView(log)
                    },
                )
            },
        )
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.hasExtra(EXTRA_SCENARIO) || intent.hasExtra(EXTRA_ACTION)) run(intent)
    }

    private fun run(intent: Intent) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val result = { text: String -> line(SeedResult.line(text, runId)) }
        if (running) {
            result("error busy")
            return
        }
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            result("error health-connect-unavailable")
            return
        }
        val client = HealthConnectClient.getOrCreate(this)
        running = true
        lifecycleScope.launch {
            try {
                val missing = PERMISSIONS - client.permissionController.getGrantedPermissions()
                check(missing.isEmpty()) { "missing permissions: ${missing.joinToString { it.substringAfterLast('.') }}" }
                execute(SeedWriter(client, ::line), intent, result)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception, // Any failure is reported to the script.
            ) {
                result("error ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                running = false
            }
        }
    }

    private suspend fun execute(
        writer: SeedWriter,
        intent: Intent,
        result: (String) -> Unit,
    ) {
        val scenarioKey = intent.getStringExtra(EXTRA_SCENARIO)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: if (scenarioKey == ACTION_WIPE) ACTION_WIPE else ACTION_SEED
        when (action) {
            ACTION_WIPE -> {
                writer.wipe()
                prefs().edit().clear().apply()
                result("ok wipe")
            }

            ACTION_APPEND_TAIL -> {
                val p = prefs()
                val scenario = Scenario.of(p.getString(EXTRA_SCENARIO, null) ?: "")
                checkNotNull(scenario) { "no previous seed to append to" }
                val nights = p.getInt(EXTRA_NIGHTS, 0)
                val plan = SeedPlanner.plan(scenario, nights, p.getLong(KEY_NOW_MS, 0), zone(), p.getInt(EXTRA_END_DAYS_AGO, 0))
                check(plan.tail.isNotEmpty()) { "scenario ${scenario.key} has no late tail" }
                val n = writer.appendTail(plan)
                result("ok append_tail scenario=${scenario.key} records=$n")
            }

            ACTION_SEED -> {
                val scenario = Scenario.of(scenarioKey ?: "")
                checkNotNull(scenario) { "unknown scenario '$scenarioKey' (one of ${Scenario.entries.joinToString { it.key }}, wipe)" }
                val nights = intent.getIntExtra(EXTRA_NIGHTS, scenario.defaultNights)
                val endDaysAgo = intent.getIntExtra(EXTRA_END_DAYS_AGO, 0)
                val nowMs = parseNow(intent.getStringExtra(EXTRA_NOW))
                val plan = SeedPlanner.plan(scenario, nights, nowMs, zone(), endDaysAgo)
                writer.wipe()
                val n = writer.seed(plan)
                prefs()
                    .edit()
                    .putString(EXTRA_SCENARIO, scenario.key)
                    .putInt(EXTRA_NIGHTS, nights)
                    .putLong(KEY_NOW_MS, nowMs)
                    .putInt(EXTRA_END_DAYS_AGO, endDaysAgo)
                    .apply()
                val lastEndMs = plan.sessions.lastOrNull()?.endMs
                val last = lastEndMs?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDateTime() }
                // last_session_end_s (epoch seconds) lets seed.sh compare the night's end with the emulator clock.
                result(
                    "ok seed scenario=${scenario.key} nights=$nights records=$n sessions=${plan.sessions.size} " +
                        "last_wake_date=${plan.lastWakeDate} last_session_end=$last " +
                        "last_session_end_s=${lastEndMs?.div(MS_PER_S)} tail=${plan.tail.values.sumOf { it.size }}",
                )
            }

            else -> {
                error("unknown action '$action' (seed, append_tail, wipe)")
            }
        }
    }

    /** `now` as an ISO instant (`2026-11-26T08:00:00Z`) or local date-time (`2026-11-26T08:00`); default: the clock. */
    private fun parseNow(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            LocalDateTime
                .parse(value)
                .atZone(zone())
                .toInstant()
                .toEpochMilli()
        }
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun prefs() = getSharedPreferences("last_seed", Context.MODE_PRIVATE)

    private fun line(text: String) {
        Log.i(TAG, text)
        runOnUiThread { log.append(text + "\n") }
    }

    private companion object {
        const val TAG = "NbSeeder"
        const val PADDING_DP = 16
        const val TOP_PADDING_FACTOR = 3
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NIGHTS = "nights"
        const val EXTRA_NOW = "now"
        const val EXTRA_END_DAYS_AGO = "end_days_ago"
        const val EXTRA_RUN_ID = "run_id"
        const val MS_PER_S = 1000L
        const val KEY_NOW_MS = "now_ms"
        const val ACTION_SEED = "seed"
        const val ACTION_WIPE = "wipe"
        const val ACTION_APPEND_TAIL = "append_tail"

        private const val P = "android.permission.health."
        val PERMISSIONS: Set<String> =
            listOf("SLEEP", "HEART_RATE", "OXYGEN_SATURATION", "RESPIRATORY_RATE", "HEART_RATE_VARIABILITY", "SKIN_TEMPERATURE", "STEPS")
                .flatMap { listOf("${P}WRITE_$it", "${P}READ_$it") }
                .toSet()
    }
}
