// detekt treats only src/test as tests; this is the test source set of the debug variant (the catalog is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.metric.METRIC_TOP_BAR_TAG
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailSamples
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailScreen
import io.github.alien2003.nightbaseline.ui.metric.MetricSample
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import kotlinx.coroutines.runBlocking
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
import kotlin.math.roundToInt

/** A window of the Metric detail matrix; folds are injected through LayoutInputs (Robolectric has no postures). */
data class MetricDevice(
    /** Id from `layouts.md` 11. */
    val id: String,
    /** Width in dp. */
    val widthDp: Int,
    /** Height in dp. */
    val heightDp: Int,
    /** Density in dpi. */
    val dpi: Int,
    /** Synthetic vertical fold (book). */
    val verticalFold: FoldInfo? = null,
    /** Synthetic horizontal fold (tabletop). */
    val horizontalFold: FoldInfo? = null,
) {
    override fun toString(): String = id
}

/**
 * Renders Metric detail (`screens.md` 7.3) from synthetic data on the JVM (same harness as [CatalogScreenshotsTest]:
 * Robolectric native graphics, hardware PixelCopy so the glass renders) for phone, flip-cover-square, owner-book,
 * owner-tabletop and tablet-land at font 1.0 and 2.0, light and dark. Every state of [MetricSample] renders on the
 * phone; the other windows render the key states. Glass and the solid fallback are rendered for the states where the
 * shell matters (the default state and the explainer sheet / dialog). Single-scroll layouts are stitched into one
 * tall image (top bar once at the top, the floating bar once at the bottom); two-region layouts (book, tabletop)
 * are written at the top and at the end of their main scroll. Every render checks that no text shows an unfilled
 * placeholder, no text is clipped, every clickable node is at least 48 x 48 dp, and in tabletop no clickable node
 * sits above the fold.
 *
 * Output: build/screenshots/metric (or docs/screenshots/metric with -PrecordScreenshots).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class MetricScreenshotsTest(
    private val device: MetricDevice,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val outputDir = File(System.getProperty(OUTPUT_PROPERTY) ?: "build/screenshots", "metric")
    private val shot = mutableStateOf<(@Composable () -> Unit)?>(null)
    private lateinit var defaultLocale: Locale
    private lateinit var defaultZone: TimeZone

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
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
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { shot.value?.invoke() }
        val font = "font%.1f".format(Locale.US, fontScale)
        val layout =
            resolveLayout(
                LayoutInputs(
                    device.widthDp.toFloat(),
                    device.heightDp.toFloat(),
                    fontScale = fontScale,
                    verticalFold = device.verticalFold,
                    horizontalFold = device.horizontalFold,
                ),
            ).let {
                // Metric detail is a pushed route: on phones the app hides the bar there (AppNavHost forDetail).
                if (it.navigation == NavigationKind.Bar) it.copy(navigation = NavigationKind.None) else it
            }
        val samples = if (device.id == "phone") MetricSample.entries else KEY_SAMPLES
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            samples.forEach { sample ->
                val effects = if (sample in SHELL_SAMPLES) listOf(true, false) else listOf(false)
                effects.forEach { glass ->
                    val name =
                        listOfNotNull(
                            "metric",
                            sample.name.lowercase(),
                            device.id,
                            theme,
                            (if (glass) "glass" else "solid").takeIf { sample in SHELL_SAMPLES },
                            font,
                        ).joinToString("-")
                    capture(name, CatalogConfig(dark = dark, glass = glass), layout, sample)
                }
            }
        }
        if (device.id == "phone" && fontScale == 1f) {
            val gray = CatalogConfig(grayscale = true, glass = false)
            listOf(MetricSample.Higher, MetricSample.Learning, MetricSample.NoData, MetricSample.SideIgnored).forEach {
                capture("metric-${it.name.lowercase()}-phone-grayscale-$font", gray, layout, it)
            }
            ChartPeriod.entries.forEach { period ->
                capture(
                    "metric-period${period.nights}-phone-light-$font",
                    CatalogConfig(glass = false),
                    layout,
                    MetricSample.Higher,
                    period,
                )
            }
        }
        activity.finish()
    }

    private fun capture(
        name: String,
        config: CatalogConfig,
        layout: LayoutSpec,
        sample: MetricSample,
        period: ChartPeriod? = null,
    ) {
        val scroll = ScrollState(0)
        val base = MetricDetailSamples.state(sample)
        val state = period?.let { base.copy(period = it, selected = null) } ?: base
        show { CatalogFrame(config, Modifier.fillMaxSize()) { MetricDetailScreen(state, layout, scroll = scroll) } }
        checkTexts(name)
        checkTargets(name, layout)
        val split = layout.mode == LayoutMode.Book || layout.mode == LayoutMode.Tabletop || state.explainerOpen
        if (split) {
            write(name, compose.onRoot().captureToImage().asAndroidBitmap())
            if (scroll.maxValue > 0 && !state.explainerOpen) {
                compose.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
                idle()
                write("$name-end", compose.onRoot().captureToImage().asAndroidBitmap())
            }
        } else {
            write(name, stitched(scroll))
            if (sample == MetricSample.Higher && scroll.maxValue > 0) {
                // Content part-way under the top bar: the title row must stay on its opaque backing.
                val px = device.dpi / 160f
                compose.runOnIdle { runBlocking { scroll.scrollTo((SCROLLED_DP * px).roundToInt().coerceAtMost(scroll.maxValue)) } }
                idle()
                write("$name-scrolled", compose.onRoot().captureToImage().asAndroidBitmap())
            }
        }
    }

    /**
     * The whole scrolling column as one image: the first window down to the floating bar, then content-only slices
     * between the top bar and the bar, then the last window from below the top bar (so both shell parts appear once).
     */
    private fun stitched(scroll: ScrollState): Bitmap {
        val first = compose.onRoot().captureToImage().asAndroidBitmap()
        if (scroll.maxValue == 0) return first
        val height = first.height
        val topBar =
            compose
                .onAllNodes(hasTestTag(METRIC_TOP_BAR_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.boundsInRoot
                ?.bottom
                // Below the top bar's darkening / blur fade, so a slice never repeats faded content over the next.
                ?.let { (it + TOP_FADE_DP * device.dpi / 160f).roundToInt() } ?: 0
        val px = device.dpi / 160f
        val history = navHistoryTab()
        // A floating bar sits at the bottom; otherwise the History tab belongs to the rail at the start edge.
        val isBar = history != null && history.bottom > height - BAR_ZONE_DP * px
        val bar = if (history != null && isBar) (history.top - 20 * px).roundToInt() else height
        val railEnd = if (history != null && !isBar) (history.right + 16 * px).roundToInt().coerceAtMost(first.width) else 0
        val band = (bar - topBar).coerceAtLeast(1)
        val out = Bitmap.createBitmap(first.width, height + scroll.maxValue, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(first.getPixel(first.width - 1, height / 2))
        canvas.drawBitmap(first, Rect(0, 0, first.width, bar), Rect(0, 0, first.width, bar), null)
        var offset = band
        while (true) {
            compose.runOnIdle { runBlocking { scroll.scrollTo(offset.coerceAtMost(scroll.maxValue)) } }
            idle()
            val chunk = compose.onRoot().captureToImage().asAndroidBitmap()
            val y = scroll.value
            val end = if (y >= scroll.maxValue) height else bar
            canvas.drawBitmap(chunk, Rect(railEnd, topBar, chunk.width, end), Rect(railEnd, y + topBar, chunk.width, y + end), null)
            if (y >= scroll.maxValue) break
            offset = y + band
        }
        return out
    }

    /** Bounds of the navigation's "History" tab (bar or rail), or null in Glance. */
    private fun navHistoryTab(): androidx.compose.ui.geometry.Rect? =
        compose
            .onAllNodes(isRoot())
            .fetchSemanticsNodes()
            .flatMap { it.descendants() }
            .firstOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .any { it.text == NAV_HISTORY }
            }?.boundsInRoot

    private fun show(content: @Composable () -> Unit) {
        compose.runOnIdle { shot.value = null }
        idle()
        compose.runOnIdle { shot.value = content }
        idle()
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
        val file = File(outputDir, "$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
        assertTrue("$file was not written", file.length() > 0)
    }

    private fun allNodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.descendants() }

    /** No text shows an unfilled format placeholder, and no text is clipped (`layouts.md` 11 layout assertions). */
    private fun checkTexts(where: String) {
        val nodes = allNodes()
        val texts =
            nodes.flatMap { node ->
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    listOfNotNull(node.config.getOrNull(SemanticsProperties.StateDescription))
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
                    ?.takeIf { it.isClipped() }
                    ?.layoutInput
                    ?.text
                    ?.text
            }
        assertEquals("$where: clipped text", emptyList<String>(), clipped)
    }

    /** Every clickable node is >= 48 x 48 dp; in tabletop nothing clickable is above the fold (+ 12 dp). */
    private fun checkTargets(
        where: String,
        layout: LayoutSpec,
    ) {
        val px = device.dpi / 160f
        val min = 48 * px - 1
        val clickable = allNodes().filter { it.config.contains(SemanticsActions.OnClick) && !it.boundsInRoot.isEmpty }
        // Scrim and sheets are full-size; only real controls are checked.
        val small = clickable.filter { it.size.width < min || it.size.height < min }
        assertEquals("$where: targets under 48 dp", emptyList<String>(), small.map { describe(it) })
        val fold = layout.horizontalFold
        if (layout.mode == LayoutMode.Tabletop && fold != null) {
            val limit = (fold.positionDp - 12) * px
            val above = clickable.filter { it.boundsInRoot.bottom > 0 && it.boundsInRoot.top < limit && !isScrim(it) }
            assertEquals("$where: interactive above the fold", emptyList<String>(), above.map { describe(it) })
        }
    }

    private fun isScrim(node: SemanticsNode): Boolean = node.boundsInRoot.width >= device.widthDp * device.dpi / 160f - 1

    private fun describe(node: SemanticsNode): String =
        (
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.firstOrNull()
                    ?.text
                ?: "node ${node.id}"
        ) + " ${node.boundsInRoot}"

    /** Cut vertically or ellipsized (see [CatalogScreenshotsTest]). */
    private fun TextLayoutResult.isClipped(): Boolean = didOverflowHeight || (0 until lineCount).any { isLineEllipsized(it) }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    /** The matrix. */
    companion object {
        private const val OUTPUT_PROPERTY = "nightbaseline.screenshots.dir"
        private const val PNG_QUALITY = 100
        private const val NAV_HISTORY = "History"
        private const val BAR_ZONE_DP = 160
        private const val TOP_FADE_DP = 28
        private const val SCROLLED_DP = 90
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** States rendered on every window. */
        val KEY_SAMPLES: List<MetricSample> =
            listOf(
                MetricSample.Higher,
                MetricSample.SideIgnored,
                MetricSample.Learning,
                MetricSample.NoData,
                MetricSample.NotProvided,
                MetricSample.NotProvidedNone,
                MetricSample.AccessOff,
                MetricSample.Explainer,
            )

        /** States rendered in glass and in the solid fallback. */
        val SHELL_SAMPLES: Set<MetricSample> = setOf(MetricSample.Higher, MetricSample.Explainer)

        /** Windows of `layouts.md` 11: phone, flip cover small, book (vertical fold), tabletop (horizontal fold), tablet. */
        val DEVICES: List<MetricDevice> =
            listOf(
                MetricDevice("phone", 412, 915, 420),
                MetricDevice("flip-cover-square", 340, 352, 320),
                MetricDevice("owner-book", 692, 763, 520, verticalFold = FoldInfo(346f, separating = true)),
                MetricDevice("owner-tabletop", 763, 692, 520, horizontalFold = FoldInfo(346f, separating = true)),
                MetricDevice("tablet-land", 1280, 800, 320),
            )

        /** Font scales: default and the largest (200 %). */
        val FONT_SCALES: List<Float> = listOf(1f, 2f)

        /** Parameters: device x font scale. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun parameters(): List<Array<Any>> = DEVICES.flatMap { d -> FONT_SCALES.map { arrayOf<Any>(d, it) } }
    }
}
