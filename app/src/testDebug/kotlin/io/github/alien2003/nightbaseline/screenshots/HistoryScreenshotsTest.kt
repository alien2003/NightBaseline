// detekt treats only src/test as tests; this is the test source set of the debug variant (the catalog is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.ui.history.HistoryAccess
import io.github.alien2003.nightbaseline.ui.history.HistoryFilter
import io.github.alien2003.nightbaseline.ui.history.HistorySamples
import io.github.alien2003.nightbaseline.ui.history.HistoryScreen
import io.github.alien2003.nightbaseline.ui.history.HistoryUiState
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
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

/** A window of the History matrix: a [Device] of `layouts.md` 11, plus a horizontal fold for tabletop windows. */
data class HistoryWindow(
    /** Device (id, size, density, vertical fold). */
    val device: Device,
    /** Synthetic horizontal fold (tabletop). */
    val horizontalFold: FoldInfo? = null,
) {
    override fun toString(): String = device.id
}

/**
 * Renders the History screen (synthetic [HistorySamples] only) in every History state of `states.md` 5 and 7.2 on the
 * windows phone, flip-cover-square, owner-book (vertical separating fold), tablet-land, plus owner-inner (Medium) and
 * owner-tabletop (horizontal separating fold), at font 1.0 and 2.0, light and dark; the feed and the calendar also in
 * glass and the solid fallback (the shell is the only glass). Each window is captured at rest and, when the feed
 * scrolls, stitched from window-sized steps down to the end, so rows under the floating bar are checked too.
 *
 * Every step asserts what [CatalogScreenshotsTest] asserts (no unfilled `%1$s`, no clipped or ellipsized text), plus
 * that every clickable node is at least 48 x 48 dp and that no clickable node lies within 12 dp of a separating fold.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class HistoryScreenshotsTest(
    private val window: HistoryWindow,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val device get() = window.device
    private val outputDir = File(System.getProperty(OUTPUT_PROPERTY) ?: "build/screenshots", "history")
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
        assertEquals("font scale applied", fontScale, activity.resources.configuration.fontScale, 0.01f)
        val font = "font%.1f".format(Locale.US, fontScale)
        val renders =
            listOf(false, true).flatMap { dark ->
                STATES.flatMap { (name, state) ->
                    val effects = if (name in BOTH_EFFECTS) listOf(true, false) else listOf(true)
                    effects.map { glass -> Triple(name, state, CatalogConfig(dark = dark, glass = glass)) }
                }
            }
        renders.forEach { (name, state, config) ->
            val theme = if (config.dark) "dark" else "light"
            val mode = if (config.glass) "glass" else "solid"
            capture("history-$name-${device.id}-$theme-$mode-$font", config, state())
        }
        activity.finish()
    }

    /** Shows History in [state], checks every scroll step and writes the stitched window. */
    private fun capture(
        name: String,
        config: CatalogConfig,
        state: HistoryUiState,
    ) {
        val layout =
            resolveLayout(
                LayoutInputs(
                    device.widthDp.toFloat(),
                    device.heightDp.toFloat(),
                    fontScale = fontScale,
                    verticalFold = device.verticalFold,
                    horizontalFold = window.horizontalFold,
                ),
            )
        val list = LazyListState()
        show { CatalogFrame(config, Modifier.fillMaxSize()) { HistoryScreen(state, layout, listState = list) } }
        val first = compose.onRoot().captureToImage().asAndroidBitmap()
        val chunks = mutableListOf(first)
        write(name, first)
        checkNodes("$name@0")
        // Scroll the feed a window at a time to its end (at most a few steps: the rest repeats the same rows).
        var step = 1
        while (list.canScrollForward && step <= MAX_STEPS) {
            compose.runOnIdle { runBlocking { list.scrollBy(first.height * SCROLL_SHARE) } }
            idle()
            checkNodes("$name@$step")
            chunks += compose.onRoot().captureToImage().asAndroidBitmap()
            step++
        }
        write(name, stitch(chunks))
    }

    private fun stitch(chunks: List<Bitmap>): Bitmap {
        if (chunks.size == 1) return chunks.first()
        val width = chunks.first().width
        val gap = 8
        val out = Bitmap.createBitmap(width * chunks.size + gap * (chunks.size - 1), chunks.first().height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.MAGENTA)
        chunks.forEachIndexed { i, chunk -> canvas.drawBitmap(chunk, (i * (width + gap)).toFloat(), 0f, null) }
        return out
    }

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

    /** Text and target assertions of `layouts.md` 11 for the nodes on screen now. */
    private fun checkNodes(where: String) {
        val nodes = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.descendants() }
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
        val density =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        val viewport = nodes.first().boundsInRoot
        val targets = nodes.filter { it.config.contains(SemanticsActions.OnClick) && !it.config.contains(SemanticsProperties.Disabled) }
        // The node's own size, not its bounds clipped by a scrolling container or the window edge.
        val small =
            targets
                .filter { it.size.width / density < MIN_TARGET_DP - 0.5f || it.size.height / density < MIN_TARGET_DP - 0.5f }
                .map { "${label(it)} ${it.size.width / density}x${it.size.height / density} dp" }
        assertEquals("$where: targets under 48 dp", emptyList<String>(), small)
        val folds = foldAreas(density, viewport)
        val onFold = targets.filter { t -> folds.any { it.overlaps(t.boundsInRoot) } }.map { label(it) }
        assertEquals("$where: targets on the fold", emptyList<String>(), onFold)
    }

    /** The separating folds plus 12 dp on each side, in px (1 px less for pixel snapping of edges on the line). */
    private fun foldAreas(
        density: Float,
        viewport: Rect,
    ): List<Rect> =
        listOfNotNull(
            device.verticalFold?.takeIf { it.separating }?.let { f ->
                Rect(
                    (f.positionDp - CLEARANCE_DP) * density + SNAP_PX,
                    0f,
                    (f.positionDp + CLEARANCE_DP) * density - SNAP_PX,
                    viewport.bottom,
                )
            },
            window.horizontalFold?.takeIf { it.separating }?.let { f ->
                Rect(
                    0f,
                    (f.positionDp - CLEARANCE_DP) * density + SNAP_PX,
                    viewport.right,
                    (f.positionDp + CLEARANCE_DP) * density - SNAP_PX,
                )
            },
        )

    private fun label(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: node.config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString { it.text }
            ?: node.children
                .flatMap { c -> c.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                .joinToString { it.text }

    /** Cut vertically or ellipsized (as in [CatalogScreenshotsTest]). */
    private fun TextLayoutResult.isClipped(): Boolean = didOverflowHeight || (0 until lineCount).any { isLineEllipsized(it) }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    /** The matrix. */
    companion object {
        private const val OUTPUT_PROPERTY = "nightbaseline.screenshots.dir"
        private const val PNG_QUALITY = 100
        private const val MAX_STEPS = 3
        private const val SCROLL_SHARE = 0.8f
        private const val MIN_TARGET_DP = 48f
        private const val CLEARANCE_DP = 12f

        /** Layout snaps edges to whole pixels: an edge exactly on the clearance line may land up to 1 px inside it. */
        private const val SNAP_PX = 1f
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** States of `states.md` 5 and 7.2 that History shows (name -> builder). */
        private val STATES: List<Pair<String, () -> HistoryUiState>> =
            listOf(
                "feed" to { HistorySamples.feed() },
                "calendar" to { HistorySamples.calendar() },
                "calendar-previous" to { HistorySamples.previousMonth() },
                "moved" to { HistorySamples.filtered(HistoryFilter.Moved) },
                "learning" to { HistorySamples.learning() },
                "filter-empty" to { HistorySamples.filterEmpty() },
                "empty" to { HistorySamples.empty() },
                "loading" to { HistorySamples.loading() },
                "perm" to { HistorySamples.access(HistoryAccess.Permission) },
                "hc-update" to { HistorySamples.access(HistoryAccess.HealthConnectUpdate) },
            )

        /** States rendered in glass and in the solid fallback. */
        private val BOTH_EFFECTS = setOf("feed", "calendar")

        /** `layouts.md` 11: phone, flip cover, owner book, tablet landscape, plus owner inner (Medium) and tabletop. */
        val WINDOWS: List<HistoryWindow> =
            CatalogScreenshotsTest.DEVICES.map { HistoryWindow(it) } +
                listOf(
                    HistoryWindow(Device("owner-inner", 692, 763, 520)),
                    HistoryWindow(Device("owner-tabletop", 763, 692, 520), horizontalFold = FoldInfo(346f, separating = true)),
                )

        /** Parameters: window x font scale. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun parameters(): List<Array<Any>> = WINDOWS.flatMap { w -> CatalogScreenshotsTest.FONT_SCALES.map { arrayOf<Any>(w, it) } }
    }
}
