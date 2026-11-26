package io.github.alien2003.nightbaseline.e2e

import android.app.Activity
import android.app.KeyguardManager
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.alien2003.nightbaseline.MainActivity
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.e2e.safety.E2eHealth
import io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule
import io.github.alien2003.nightbaseline.e2e.support.E2eWorkManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.FileOutputStream

/**
 * The installed app's own UI (MainActivity with the app's Hilt graph and data), read-only: it presses only the three
 * navigation tabs, nothing else (no "Check now", no setting, no source change). If onboarding is not done on the
 * phone, it only checks that onboarding shows. Otherwise Home -> History -> Settings -> Home, each with its key text.
 * Any crash of the app fails it. Screenshots go to `e2e/ui/` of the app's external files directory (they show the
 * owner's real screens: they stay on the owner's computer, in the git-ignored tools/device-test/out/).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UiE2e {
    private val status = E2eStatusRule()

    // The v1 rule, like PulseStages' tour: the app's own coroutines run as in production.
    @Suppress("DEPRECATION")
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(status).around(compose)

    private var screenshots = 0

    @Test
    fun walksHomeHistorySettings() {
        awaitUnlockedScreen()
        val onboarded = E2eHealth.settingsSnapshot().onboardingDone
        status.flag("onboardingDone", onboarded)
        val intent = Intent(E2e.app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val activity: Activity = E2e.instrumentation.startActivitySync(intent)
        try {
            compose.waitForIdle()
            if (!onboarded) {
                awaitAnyText("onboarding", ONBOARDING_TEXTS)
                screenshot("onboarding")
            } else {
                awaitTab(R.string.nav_home)
                awaitPageText(R.string.home_title)
                screenshot("home")
                openTab(R.string.nav_history)
                awaitPageText(R.string.history_title)
                screenshot("history")
                openTab(R.string.nav_settings)
                awaitPageText(R.string.settings_title)
                screenshot("settings")
                openTab(R.string.nav_home)
                awaitPageText(R.string.home_title)
            }
            assertFalse("MainActivity finished during the walk", activity.isFinishing)
            assertTrue("MainActivity is not resumed", resumed(activity))
        } finally {
            status.count("screenshots", screenshots.toLong())
            runCatching { E2e.instrumentation.runOnMainSync { activity.finish() } }
        }
        // Nothing the screens did ran a worker of the app (this process's WorkManager is the in-memory one).
        assertTrue("workers of the app were asked for: ${E2eWorkManager.refusedWorkers()}", E2eWorkManager.refusedWorkers().isEmpty())
    }

    private fun text(
        @StringRes id: Int,
    ): String = E2e.app.getString(id)

    private fun tab(label: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and (hasText(label) or hasContentDescription(label))

    private fun notTab(): SemanticsMatcher = !SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun awaitTab(
        @StringRes id: Int,
    ) {
        val label = text(id)
        compose.waitUntilAtLeastOneExists(tab(label), TIMEOUT_MS)
    }

    /** Presses the navigation tab [id]: the only click of the test. */
    private fun openTab(
        @StringRes id: Int,
    ) {
        val label = text(id)
        compose.waitUntilAtLeastOneExists(tab(label), TIMEOUT_MS)
        compose.onAllNodes(tab(label))[0].performClick()
        compose.waitForIdle()
        compose.onAllNodes(tab(label))[0].assertIsSelected()
        status.flag("tab:${E2e.app.resources.getResourceEntryName(id)}", true)
    }

    /** Waits for [id] as a page text (not a tab label). */
    private fun awaitPageText(
        @StringRes id: Int,
    ) {
        compose.waitUntilAtLeastOneExists(hasText(text(id)) and notTab(), TIMEOUT_MS)
        status.flag("text:${E2e.app.resources.getResourceEntryName(id)}", true)
    }

    private fun awaitAnyText(
        what: String,
        ids: List<Int>,
    ) {
        val labels = ids.map(::text)
        val matcher = labels.map { hasText(it) }.reduce { a, b -> a or b }
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
        status.flag("text:$what", true)
    }

    private fun resumed(activity: Activity): Boolean {
        var stage: Stage? = null
        E2e.instrumentation.runOnMainSync { stage = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity) }
        return stage == Stage.RESUMED
    }

    /** A screenshot of the whole screen into `e2e/ui/`; `-e e2eScreenshots 0` turns them off. */
    private fun screenshot(name: String) {
        if (E2e.intArg(ARG_SCREENSHOTS, 1) == 0) return
        compose.waitForIdle()
        E2e.instrumentation.waitForIdleSync()
        val bitmap = E2e.instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()
        if (bitmap == null) {
            E2e.progress("screenshot $name: not available")
            return
        }
        screenshots++
        val file = E2e.file("ui/%02d-%s.png".format(screenshots, name))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
    }

    /** Returns once the screen is on and unlocked; the suite never unlocks the phone, it asks and waits. */
    private fun awaitUnlockedScreen() {
        val power = E2e.app.getSystemService(PowerManager::class.java)
        val keyguard = E2e.app.getSystemService(KeyguardManager::class.java)
        val timeoutMs = E2e.intArg(ARG_UNLOCK_TIMEOUT_SEC, DEFAULT_UNLOCK_TIMEOUT_SEC).coerceAtLeast(0) * SECOND_MS
        val started = SystemClock.elapsedRealtime()
        var announced = false
        while ((!power.isInteractive || keyguard.isKeyguardLocked) && SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (!announced) E2e.progress("the phone is locked or its screen is off: UNLOCK IT NOW and leave it on")
            announced = true
            SystemClock.sleep(POLL_MS)
        }
        assertTrue("the screen is off: unlock the phone and keep it on", power.isInteractive)
        assertFalse("the phone is locked: unlock it and leave it on", keyguard.isKeyguardLocked)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val PNG_QUALITY = 100
        const val ARG_SCREENSHOTS = "e2eScreenshots"
        const val ARG_UNLOCK_TIMEOUT_SEC = "e2eUnlockTimeoutSec"
        const val DEFAULT_UNLOCK_TIMEOUT_SEC = 120
        const val POLL_MS = 2_000L
        const val SECOND_MS = 1_000L

        /** Texts of which at least one shows on every onboarding step. */
        val ONBOARDING_TEXTS: List<Int> =
            listOf(
                R.string.onb_start,
                R.string.action_next,
                R.string.onb_source_title,
                R.string.onb_nosource_title,
                R.string.onb_perm_allow,
                R.string.state_hc_none_title,
                R.string.state_hc_update_title,
                R.string.action_done,
            )
    }
}
