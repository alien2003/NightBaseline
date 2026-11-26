package io.github.alien2003.nightbaseline.e2e

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alien2003.nightbaseline.NightBaselineApp
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The environment of the on-device end-to-end suite (tools/device-test/README.md).
 *
 * Two kinds of test process:
 * - [app]: an instrumentation of the INSTALLED app ([E2eRunner]). The test code runs in the app's process with the
 *   app's uid, so every Health Connect call is made as NightBaseline with its READ permissions. The app's classes in
 *   this process come from the test APK's unminified copy of the same sources (app/build.gradle.kts, "e2e").
 * - [self]: an instrumentation of the test package itself ([SelfE2eRunner]), only for the single test write
 *   (WriteDeleteE2e): its own process, its own data origin, its own WRITE_STEPS permission.
 *
 * Reports are JSON with counts, durations and pass / fail only: never a measurement value, a date of a night or a
 * package name in clear (see [E2eStatusRule][io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule]).
 */
object E2e {
    /** Application id of the app under test. The suite refuses to run against anything else. */
    const val APP_PACKAGE: String = "io.github.alien2003.nightbaseline"

    /** Application id of the test APK. */
    const val TEST_PACKAGE: String = "$APP_PACKAGE.test"

    /** Namespace of the clientRecordId of the suite's single test record. */
    const val TEST_NAMESPACE: String = "nb-e2e:"

    /** Logcat tag of the suite. */
    const val TAG: String = "NightBaselineE2E"

    /** Directory below the external files directory of the process's package that the runner pulls with adb. */
    const val DIR_NAME: String = "e2e"

    /** Instrumentation argument: id of this run of tools/device-test/run-device-tests.sh. */
    const val ARG_RUN_ID: String = "e2eRunId"

    /** Status code of the progress lines sent to `am instrument -r` (AndroidJUnitRunner itself uses 1, 0 and negatives). */
    private const val PROGRESS_STATUS_CODE = 2

    /** The instrumentation. */
    val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** The installed app's context; fails unless this process is an instrumentation of the app ([E2eRunner]). */
    val app: Context
        get() {
            val context = instrumentation.targetContext
            check(context.packageName == APP_PACKAGE) { "this test runs inside $APP_PACKAGE (E2eRunner), not ${context.packageName}" }
            return context
        }

    /** The test package's own context; fails unless this process is the self-instrumentation ([SelfE2eRunner]). */
    val self: Context
        get() {
            val context = instrumentation.targetContext
            check(context.packageName == TEST_PACKAGE) { "this test runs as $TEST_PACKAGE (SelfE2eRunner), not ${context.packageName}" }
            return context
        }

    /**
     * The app's Application object (the copy from the test APK, created by Android for the installed app's manifest).
     * Its injected singletons are the app's own: the suite only READS through them (settings).
     */
    val application: NightBaselineApp get() = app.applicationContext as NightBaselineApp

    /** Arguments passed with `am instrument -e`. */
    val args: Bundle get() = InstrumentationRegistry.getArguments()

    /** The id of this run ("manual" when started by hand). */
    val runId: String get() = args.getString(ARG_RUN_ID)?.takeIf { it.isNotBlank() } ?: "manual"

    /** An integer instrumentation argument, or [default]. */
    fun intArg(
        name: String,
        default: Int,
    ): Int = args.getString(name)?.trim()?.toIntOrNull() ?: default

    /**
     * `/sdcard/Android/data/<package>/files/e2e` of the process's package (the app's, or the test package's in the
     * self-instrumentation): pulled by the runner with adb and removed afterwards.
     */
    val outDir: File
        get() {
            val base =
                checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)) { "the external files directory is not available" }
            return File(base, DIR_NAME).also { it.mkdirs() }
        }

    /** [relative] inside [outDir] (parent directories are created). */
    fun file(relative: String): File = File(outDir, relative).also { it.parentFile?.mkdirs() }

    /** JSON of every report. */
    val json: Json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /** Writes [value] as JSON to [relative]. */
    inline fun <reified T> writeJson(
        relative: String,
        value: T,
    ) {
        file(relative).writeText(json.encodeToString(value))
    }

    /** Reads [relative] as [T], or null when it does not exist. */
    inline fun <reified T> readJson(relative: String): T? {
        val target = File(outDir, relative)
        return if (target.isFile) json.decodeFromString<T>(target.readText()) else null
    }

    /**
     * Logs [message] and streams it to the runner (`am instrument -r` prints it as `INSTRUMENTATION_STATUS: e2e=…`).
     * Callers pass counts and states only, never a measurement value.
     */
    fun progress(message: String) {
        Log.i(TAG, message)
        instrumentation.sendStatus(PROGRESS_STATUS_CODE, Bundle().apply { putString("e2e", message) })
    }
}
