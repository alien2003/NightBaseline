// detekt treats only src/test as tests; this is the test source set of the debug variant (CatalogFrame is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.nav.AppNavHost
import io.github.alien2003.nightbaseline.nav.LocalViewModelFactory
import io.github.alien2003.nightbaseline.nav.SystemActions
import io.github.alien2003.nightbaseline.nav.toTop
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.MODAL_PANEL_TAG
import io.github.alien2003.nightbaseline.ui.components.MODAL_SCRIM_TAG
import io.github.alien2003.nightbaseline.ui.components.ModalKind
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.metric.infoModalKind
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Motion frames (review round 5) on the manually driven clock, glass on, light and dark:
 * - the range explainer opened from Home at 0, ~50 and 100 % of its open motion (phone: the sheet on `spring.sheet`;
 *   owner-book: the centred dialog), checked for one panel and one scrim, scrim over the whole window (bar included);
 * - on `owner-outer` the Settings > Sensitivity push and pop at fixed times (the pushed page is opaque, the leaving
 *   page of a pop slides off on top).
 * Output: `frames-{sheet|push|pop}-{device}-{theme}-{step}.png` in build/screenshots; look at them after a motion
 * change.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class SheetFramesScreenshotsTest(
    private val device: Device,
    private val dark: Boolean,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val outputDir = File(System.getProperty("nightbaseline.screenshots.dir") ?: "build/screenshots")
    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity
    private lateinit var defaultLocale: Locale
    private lateinit var defaultZone: TimeZone
    private val theme get() = if (dark) "dark" else "light"

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        val orientation = if (device.widthDp > device.heightDp) "land" else "port"
        RuntimeEnvironment.setQualifiers("en-rUS-w${device.widthDp}dp-h${device.heightDp}dp-$orientation-${device.dpi}dpi")
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultZone)
    }

    private fun launch() {
        val layout = resolveLayout(LayoutInputs(device.widthDp.toFloat(), device.heightDp.toFloat(), verticalFold = device.verticalFold))
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides TestApp().factory) {
                CatalogFrame(CatalogConfig(dark = dark, glass = true), Modifier.fillMaxSize()) {
                    AppNavHost(layout, true, SystemActions.None, newestNight = SampleData.LAST_WAKE.toString(), navController = nav)
                }
            }
        }
        idle()
    }

    @Test
    fun explainerOpenFrames() {
        if (device.id == OUTER) return
        launch()
        val kind =
            infoModalKind(
                resolveLayout(LayoutInputs(device.widthDp.toFloat(), device.heightDp.toFloat(), verticalFold = device.verticalFold)),
            )
        compose.mainClock.autoAdvance = false
        compose
            .onAllNodes(hasText("What this means") and hasClickAction())
            .onFirst()
            .performScrollTo()
            .performClick()
        // 0 %: the first frame; ~50 %: the critically damped spring.sheet (250) or the 250 ms dialog tween half way.
        val half = if (kind == ModalKind.Sheet) SHEET_HALF_MS else DIALOG_HALF_MS
        var t = 0L
        for ((step, at) in listOf("0" to FRAME_MS, "50" to half, "100" to SETTLED_MS)) {
            compose.mainClock.advanceTimeBy(at - t)
            t = at
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("one panel at $step %", 1, compose.onAllNodesWithTag(MODAL_PANEL_TAG).fetchSemanticsNodes().size)
            val scrims = compose.onAllNodesWithTag(MODAL_SCRIM_TAG).fetchSemanticsNodes()
            assertEquals("one scrim at $step %", 1, scrims.size)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("the scrim covers the window (bar included)", scrims[0].boundsInRoot == root)
            write("frames-sheet-${device.id}-$theme-$step", compose.onRoot().captureToImage().asAndroidBitmap())
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun settingsPushPopFrames() {
        if (device.id != OUTER) return
        launch()
        compose.runOnIdle { nav.toTop(TopLevel.Settings) }
        idle()
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
        frames("push")
        activity.onBackPressedDispatcher.onBackPressed()
        frames("pop")
        compose.mainClock.autoAdvance = true
    }

    private fun frames(name: String) {
        var t = 0L
        for (at in listOf(FRAME_MS, 100L, 200L, SETTLED_MS)) {
            compose.mainClock.advanceTimeBy(at - t)
            t = at
            shadowOf(Looper.getMainLooper()).idle()
            write("frames-$name-${device.id}-$theme-${at}ms", compose.onRoot().captureToImage().asAndroidBitmap())
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun write(
        name: String,
        bitmap: Bitmap,
    ) {
        outputDir.mkdirs()
        File(outputDir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    /** The matrix. */
    companion object {
        private const val OUTER = "owner-outer"
        private const val FRAME_MS = 16L
        private const val SHEET_HALF_MS = 106L
        private const val DIALOG_HALF_MS = 112L
        private const val SETTLED_MS = 1500L

        /** Phone and owner-book for the sheet, owner-outer for the Settings pages. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> =
            listOf(
                Device("phone", 412, 915, 420),
                Device("owner-book", 692, 763, 520, verticalFold = FoldInfo(346f, separating = true)),
                Device(OUTER, 482, 1107, 378),
            ).flatMap { d -> listOf(false, true).map { arrayOf<Any>(d, it) } }
    }
}
