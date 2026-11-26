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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.RunLogLine
import io.github.alien2003.nightbaseline.nav.AppNavHost
import io.github.alien2003.nightbaseline.nav.LocalViewModelFactory
import io.github.alien2003.nightbaseline.nav.MetricRoute
import io.github.alien2003.nightbaseline.nav.OnboardingRoute
import io.github.alien2003.nightbaseline.nav.SystemActions
import io.github.alien2003.nightbaseline.nav.toTop
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * The ten README screenshots: the integrated app (navigation graph, ViewModels, fake repositories filled with the
 * synthetic [SampleData] nights, never real data) on the phone window (412 x 915 dp) at font 1.0 with the glass shell,
 * light and dark, captured at rest:
 * - `home`: Home, last night with measurements outside the usual range (the synthetic episode);
 * - `metric`: Metric detail of sleeping heart rate over 30 nights;
 * - `history`: the History feed;
 * - `onboarding`: the welcome step of the first run;
 * - `settings`: the Settings main page.
 *
 * Output: `{screen}-{light|dark}.png` in the screenshot directory: build/screenshots, or docs/screenshots (what the
 * README shows) with `-PrecordScreenshots`:
 * `./gradlew :app:testDebugUnitTest --tests '*ReadmeScreenshotsTest*' -PrecordScreenshots`.
 * Each render checks that no text shows an unfilled placeholder and no text is clipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ReadmeScreenshotsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val device = Device("phone", 412, 915, 420)
    private val outputDir = File(System.getProperty("nightbaseline.screenshots.dir") ?: "build/screenshots")
    private val config = mutableStateOf(CatalogConfig())
    private lateinit var nav: NavHostController
    private lateinit var defaultLocale: Locale
    private lateinit var defaultZone: TimeZone

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        RuntimeEnvironment.setQualifiers("en-rUS-w${device.widthDp}dp-h${device.heightDp}dp-port-${device.dpi}dpi")
        RuntimeEnvironment.setFontScale(1f)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun renderReadmeScreenshots() {
        val app = TestApp()
        // This morning's check, so that Settings shows when the source was last read.
        val checked =
            SampleData.LAST_WAKE
                .atTime(7, 42)
                .atZone(SampleData.ZONE)
                .toInstant()
                .toEpochMilli()
        app.runLog.lines += RunLogLine(checked, RunLogKind.EVALUATED, "synthetic night evaluated")
        val layout = resolveLayout(LayoutInputs(device.widthDp.toFloat(), device.heightDp.toFloat(), fontScale = 1f))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory) {
                CatalogFrame(config.value, Modifier.fillMaxSize()) {
                    AppNavHost(
                        layout = layout,
                        onboardingDone = true,
                        system = SystemActions.None,
                        newestNight = SampleData.LAST_WAKE.toString(),
                        navController = nav,
                    )
                }
            }
        }
        idle()
        val written = mutableListOf<String>()
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            compose.runOnIdle { config.value = CatalogConfig(dark = dark, glass = true) }
            idle()
            for ((screen, open) in screens()) {
                compose.runOnIdle { open(nav) }
                idle()
                val name = "$screen-$theme"
                check(name)
                write(name, compose.onRoot().captureToImage().asAndroidBitmap())
                written += name
                // Pushed screens are closed again, so that a tab's saved back stack never brings them back.
                if (screen in PUSHED) {
                    compose.runOnIdle { nav.popBackStack() }
                    idle()
                }
            }
        }
        assertEquals(SCREENS.flatMap { s -> listOf("$s-light", "$s-dark") }.sorted(), written.sorted())
        activity.finish()
    }

    /** Each README screen and how to reach it from anywhere in the graph. */
    private fun screens(): List<Pair<String, (NavHostController) -> Unit>> =
        listOf(
            "home" to { n -> n.toTop(TopLevel.Home) },
            "metric" to { n ->
                n.toTop(TopLevel.Home)
                n.navigate(MetricRoute(MetricId.SLEEPING_HR.key))
            },
            "history" to { n -> n.toTop(TopLevel.History) },
            "onboarding" to { n -> n.navigate(OnboardingRoute(OnboardingStep.Welcome.name)) },
            "settings" to { n -> n.toTop(TopLevel.Settings) },
        )

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun nodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.descendants() }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    /** No unfilled placeholder and no clipped (cut or ellipsized) text. */
    private fun check(where: String) {
        val nodes = nodes()
        val texts =
            nodes.flatMap { n ->
                n.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
        assertEquals("$where: unfilled placeholders", emptyList<String>(), texts.filter { PLACEHOLDER.containsMatchIn(it) })
        val clipped =
            nodes.mapNotNull { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(layouts)
                layouts
                    .firstOrNull()
                    ?.takeIf { r -> r.didOverflowHeight || (0 until r.lineCount).any { r.isLineEllipsized(it) } }
                    ?.layoutInput
                    ?.text
                    ?.text
            }
        assertEquals("$where: clipped text", emptyList<String>(), clipped)
    }

    private fun write(
        name: String,
        bitmap: Bitmap,
    ) {
        outputDir.mkdirs()
        val file = File(outputDir, "$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue("$file was not written", file.length() > 0)
    }

    /** Screens and patterns. */
    companion object {
        /** The README screens, in the order of its table. */
        val SCREENS: List<String> = listOf("home", "metric", "history", "onboarding", "settings")
        private val PUSHED = setOf("metric", "onboarding")
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")
    }
}
