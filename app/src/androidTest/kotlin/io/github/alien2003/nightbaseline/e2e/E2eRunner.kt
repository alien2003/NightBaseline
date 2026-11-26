package io.github.alien2003.nightbaseline.e2e

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import io.github.alien2003.nightbaseline.e2e.support.E2eWorkManager

/**
 * The instrumentation of the installed app (`testInstrumentationRunner`): AndroidJUnitRunner, but the in-memory
 * WorkManager of [E2eWorkManager] is installed first. Android calls `Instrumentation.onCreate` after it created the
 * app's Application object and before it calls the app's `Application.onCreate`, so the app's start-up code
 * (`NightBaselineApp`: `scheduler.ensureScheduled()`) already finds that WorkManager and the app's real one is never
 * initialized in a test process: no worker of the app runs here, nothing is handed to JobScheduler.
 */
class E2eRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        check(targetContext.packageName == E2e.APP_PACKAGE) { "E2eRunner instruments ${E2e.APP_PACKAGE} only" }
        E2eWorkManager.install(targetContext)
        super.onCreate(arguments)
    }
}

/**
 * The self-instrumentation of the test package (declared in src/androidTest/AndroidManifest.xml): the process is the
 * test package's own, so its Health Connect calls are made as the test package, with the test package's permissions.
 * Only WriteDeleteE2e runs here. No code of the app starts in this process (its Application is the platform's).
 */
class SelfE2eRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        check(targetContext.packageName == E2e.TEST_PACKAGE) { "SelfE2eRunner instruments ${E2e.TEST_PACKAGE} only" }
        super.onCreate(arguments)
    }
}
