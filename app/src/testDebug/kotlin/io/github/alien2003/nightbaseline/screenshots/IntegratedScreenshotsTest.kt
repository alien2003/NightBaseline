// detekt treats only src/test as tests; this is the test source set of the debug variant (CatalogFrame is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber", "LongMethod")

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
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.nav.AppNavHost
import io.github.alien2003.nightbaseline.nav.LocalViewModelFactory
import io.github.alien2003.nightbaseline.nav.SystemActions
import io.github.alien2003.nightbaseline.nav.toTop
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
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
import androidx.compose.ui.geometry.Rect as ComposeRect

/**
 * The integrated app (navigation graph, ViewModels, fake repositories filled with the synthetic [SampleData] nights):
 * every top-level destination x {phone, flip-cover-square, owner-book, owner-tabletop, tablet-land, tablet-portrait} x
 * {light, dark} x {font 1.0, 2.0} x {glass, solid}, captured at rest (the per-area tests stitch whole screens). Each
 * render checks: no unfilled placeholder, no clipped text, and no clickable node within a separating fold +- 12 dp.
 * Output: `integrated-{destination}-{device}-{theme}-{glass|solid}-font{scale}.png` in build/screenshots.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class IntegratedScreenshotsTest(
    private val screen: ScreenDevice,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val device get() = screen.device
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
        val orientation = if (device.widthDp > device.heightDp) "land" else "port"
        RuntimeEnvironment.setQualifiers("en-rUS-w${device.widthDp}dp-h${device.heightDp}dp-$orientation-${device.dpi}dpi")
        RuntimeEnvironment.setFontScale(fontScale)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun renderMatrix() {
        val app = TestApp()
        val layout =
            resolveLayout(
                LayoutInputs(
                    device.widthDp.toFloat(),
                    device.heightDp.toFloat(),
                    fontScale = fontScale,
                    verticalFold = device.verticalFold,
                    horizontalFold = screen.horizontalFold,
                ),
            )
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
        var count = 0
        for (dark in listOf(false, true)) {
            for (glass in listOf(true, false)) {
                compose.runOnIdle { config.value = CatalogConfig(dark = dark, glass = glass) }
                idle()
                for (tab in TopLevel.entries) {
                    compose.runOnIdle { nav.toTop(tab) }
                    idle()
                    val name =
                        listOf(
                            "integrated",
                            tab.name.lowercase(Locale.US),
                            device.id,
                            if (dark) "dark" else "light",
                            if (glass) "glass" else "solid",
                            "font%.1f".format(Locale.US, fontScale),
                        ).joinToString("-")
                    check(name, layout.verticalFold, layout.horizontalFold)
                    write(name, compose.onRoot().captureToImage().asAndroidBitmap())
                    count++
                }
            }
        }
        assertEquals(12, count)
        activity.finish()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun nodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.descendants() }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    private fun check(
        where: String,
        vertical: FoldInfo?,
        horizontal: FoldInfo?,
    ) {
        val nodes = nodes()
        val texts =
            nodes.flatMap { n ->
                n.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .map { it.text }
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
                    ?.takeIf { r ->
                        r.didOverflowHeight || (0 until r.lineCount).any { r.isLineEllipsized(it) }
                    }?.layoutInput
                    ?.text
                    ?.text
            }
        assertEquals("$where: clipped text", emptyList<String>(), clipped)
        val density =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        val folds =
            listOfNotNull(
                vertical?.let { f ->
                    val c = f.positionDp * density
                    val half = (f.occludedDp / 2 + CLEARANCE_DP) * density
                    ComposeRect(c - half, Float.NEGATIVE_INFINITY, c + half, Float.POSITIVE_INFINITY)
                },
                horizontal?.let { f ->
                    val c = f.positionDp * density
                    val half = (f.occludedDp / 2 + CLEARANCE_DP) * density
                    ComposeRect(Float.NEGATIVE_INFINITY, c - half, Float.POSITIVE_INFINITY, c + half)
                },
            )
        val onFold =
            nodes
                .filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
                .filter { node ->
                    val bounds =
                        ComposeRect(
                            node.positionInRoot,
                            androidx.compose.ui.geometry
                                .Size(node.size.width.toFloat(), node.size.height.toFloat()),
                        )
                    folds.any { it.overlaps(bounds.deflate(1f)) }
                }.map { n ->
                    n.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .joinToString { it.text } + " " + n.boundsInRoot
                }
        assertEquals("$where: controls on the fold", emptyList<String>(), onFold)
    }

    private fun write(
        name: String,
        bitmap: Bitmap,
    ) {
        outputDir.mkdirs()
        val file = File(outputDir, "$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    /** The matrix. */
    companion object {
        private const val CLEARANCE_DP = 12f
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** The six windows of the integrated matrix. */
        val DEVICES: List<ScreenDevice> =
            listOf(
                ScreenDevice(Device("phone", 412, 915, 420)),
                ScreenDevice(Device("flip-cover-square", 340, 352, 320)),
                ScreenDevice(Device("owner-book", 692, 763, 520, verticalFold = FoldInfo(346f, separating = true))),
                ScreenDevice(Device("owner-tabletop", 763, 692, 520), horizontalFold = FoldInfo(346f, separating = true)),
                ScreenDevice(Device("tablet-land", 1280, 800, 320)),
                ScreenDevice(Device("tablet-portrait", 800, 1280, 320)),
            )

        /** Parameters: device x font scale. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun parameters(): List<Array<Any>> = DEVICES.flatMap { d -> listOf(1f, 2f).map { arrayOf<Any>(d, it) } }
    }
}
