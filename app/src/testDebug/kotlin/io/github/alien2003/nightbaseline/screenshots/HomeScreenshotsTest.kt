// detekt treats only src/test as tests; this is the test source set of the debug variant (CatalogFrame is debug-only).
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
import io.github.alien2003.nightbaseline.ui.home.HomeSample
import io.github.alien2003.nightbaseline.ui.home.HomeSamples
import io.github.alien2003.nightbaseline.ui.home.HomeScreen
import io.github.alien2003.nightbaseline.ui.home.SCREEN_TOP_BAR_TAG
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.night.NightDetailSamples
import io.github.alien2003.nightbaseline.ui.night.NightDetailScreen
import io.github.alien2003.nightbaseline.ui.night.NightSample
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
import androidx.compose.ui.geometry.Rect as ComposeRect

/** A device of [HomeScreenshotsTest]: a [Device] of the shared matrix plus an optional tabletop fold. */
data class ScreenDevice(
    /** Window, density and vertical fold. */
    val device: Device,
    /** Synthetic horizontal fold (tabletop) injected through LayoutInputs. */
    val horizontalFold: FoldInfo? = null,
) {
    override fun toString(): String = device.id
}

/**
 * Renders Home and Night detail from synthetic data ([HomeSamples], [NightDetailSamples]) on the JVM with the harness
 * of [CatalogScreenshotsTest] (Robolectric native graphics, hardware PixelCopy so the glass blur renders):
 * - key states x {light, dark} x {font 1.0, 2.0} x {phone, flip-cover-square, owner-book, tablet-land, owner-tabletop},
 *   glass shell, plus the solid fallback of `S-DEV` for both screens;
 * - every state of `states.md` on phone and flip-cover-square at font 1.0 in both themes;
 * - grayscale variants on the phone (`principles.md` 4).
 *
 * A screen taller than the window is scrolled and stitched below the fixed top bar (and the tabletop top half) and
 * above the floating bar, so every block appears once. Every render checks: no unfilled `%1$s` placeholder, no clipped
 * text, every clickable node at least 48 x 48 dp, and no clickable node inside a separating fold +- 12 dp.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class HomeScreenshotsTest(
    private val screen: ScreenDevice,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val device get() = screen.device
    private val outputDir = File(System.getProperty(OUTPUT_PROPERTY) ?: "build/screenshots")
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
        val everyState = fontScale == 1f && device.id in setOf("phone", "flip-cover-square")
        for (dark in listOf(false, true)) {
            val homeSamples = if (everyState) HomeSample.entries else KEY_HOME
            homeSamples.forEach { home(it, CatalogConfig(dark = dark, glass = true)) }
            home(HomeSample.Deviations, CatalogConfig(dark = dark, glass = false))
            val nightSamples = if (everyState) NightSample.entries else KEY_NIGHT
            nightSamples.forEach { night(it, CatalogConfig(dark = dark, glass = true)) }
            night(NightSample.Deviations, CatalogConfig(dark = dark, glass = false))
        }
        if (device.id == "phone" && fontScale == 1f) {
            val gray = CatalogConfig(grayscale = true, glass = false)
            listOf(HomeSample.SomeOne, HomeSample.Deviations, HomeSample.L3Short, HomeSample.AllWithinPartial).forEach {
                home(it, gray, "grayscale")
            }
            night(NightSample.SomeOne, gray, "grayscale")
        }
        activity.finish()
    }

    private fun layout(): LayoutSpec =
        resolveLayout(
            LayoutInputs(
                device.widthDp.toFloat(),
                device.heightDp.toFloat(),
                fontScale = fontScale,
                verticalFold = device.verticalFold,
                horizontalFold = screen.horizontalFold,
            ),
        )

    private fun home(
        sample: HomeSample,
        config: CatalogConfig,
        variant: String? = null,
    ) {
        val layout = layout()
        val scroll = ScrollState(0)
        val state = HomeSamples.state(sample)
        capture(name("home", sample.id, config, variant), layout, scroll) {
            CatalogFrame(config, Modifier.fillMaxSize()) { HomeScreen(state, layout, scrollState = scroll) }
        }
    }

    private fun night(
        sample: NightSample,
        config: CatalogConfig,
        variant: String? = null,
    ) {
        val layout = layout()
        val scroll = ScrollState(0)
        val state = NightDetailSamples.state(sample)
        capture(name("night", sample.id, config, variant), layout, scroll) {
            CatalogFrame(config, Modifier.fillMaxSize()) { NightDetailScreen(state, layout, scrollState = scroll) }
        }
    }

    private fun name(
        screenId: String,
        state: String,
        config: CatalogConfig,
        variant: String?,
    ): String {
        val theme = if (config.dark) "dark" else "light"
        val mode = if (config.glass) "glass" else "solid"
        val font = "font%.1f".format(Locale.US, fontScale)
        return listOfNotNull(screenId, state, device.id, variant ?: theme, mode, font).joinToString("-")
    }

    /**
     * Shows [content], checks it, and writes the whole screen: the window at rest, then the rows between the fixed
     * top (bar, tabletop top half) and the floating bar of each scrolled capture, and the last capture to its bottom.
     */
    private fun capture(
        name: String,
        layout: LayoutSpec,
        scroll: ScrollState,
        content: @Composable () -> Unit,
    ) {
        show(content)
        checkNodes(name, layout)
        val first = compose.onRoot().captureToImage().asAndroidBitmap()
        val max = scroll.maxValue
        assertTrue("$name: the screen does not use the hoisted scroll state", max != Int.MAX_VALUE)
        if (max == 0) {
            write(name, first)
            return
        }
        val height = first.height
        val bottom = bottomOverlay(layout, height)
        // Only the scrolled pane grows; the other panes, the rail and the window background stay as at rest.
        val pane =
            nodes()
                .firstOrNull { node ->
                    node.config
                        .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                        ?.maxValue
                        ?.invoke()
                        ?.toInt() ==
                        max
                }?.boundsInRoot
        val left = pane?.left?.toInt()?.coerceAtLeast(0) ?: 0
        val right = pane?.right?.toInt()?.coerceAtMost(first.width) ?: first.width
        val out = Bitmap.createBitmap(first.width, height + max, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(first.getPixel(first.width - 1, 1))
        canvas.drawBitmap(first, 0f, 0f, null)
        var position = 0
        var top = topOverlay(layout)
        while (position < max) {
            val target = (position + maxOf(height - top - bottom, height / 4)).coerceAtMost(max)
            compose.runOnIdle { runBlocking { scroll.scrollTo(target) } }
            idle()
            val chunk = compose.onRoot().captureToImage().asAndroidBitmap()
            val at = scroll.value
            top = topOverlay(layout)
            val last = at >= max
            val end = if (last) height else height - bottom
            val src = Rect(left, top, right, end)
            val dst = Rect(left, at + top, right, at + end)
            canvas.drawBitmap(chunk, src, dst, null)
            if (last || at <= position) break
            position = at
        }
        write(name, out)
    }

    /** Pixels at the top that do not scroll: the top bar, and in tabletop the top half down to the fold + 12 dp. */
    private fun topOverlay(layout: LayoutSpec): Int {
        val density = density()
        val bar =
            compose
                .onAllNodes(hasTestTag(SCREEN_TOP_BAR_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.boundsInRoot
                ?.bottom ?: 0f
        val fold =
            layout.horizontalFold?.takeIf { layout.mode == LayoutMode.Tabletop }?.let { (it.positionDp + CLEARANCE_DP) * density } ?: 0f
        return maxOf(bar, fold).toInt()
    }

    /** Pixels at the bottom covered by the floating bar (its tabs plus the pill padding). */
    private fun bottomOverlay(
        layout: LayoutSpec,
        height: Int,
    ): Int {
        val density = density()
        val tabsTop =
            nodes()
                .filter { layout.navigation == NavigationKind.Bar && it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                .map { it.boundsInRoot.top }
                .filter { it > height - BAR_AREA_DP * density }
                .minOrNull()
        return tabsTop?.let { (height - it + BAR_PADDING_DP * density).toInt().coerceIn(0, height / 2) } ?: 0
    }

    private fun density(): Float =
        RuntimeEnvironment
            .getApplication()
            .resources.displayMetrics.density

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

    private fun nodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.descendants() }

    /**
     * The layout assertions of `layouts.md` 11: no unfilled placeholder, no clipped text, clickable nodes >= 48 dp,
     * no clickable node within a separating fold +- 12 dp.
     */
    private fun checkNodes(
        where: String,
        layout: LayoutSpec,
    ) {
        val nodes = nodes()
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
        checkControls(where, layout, nodes.filter { it.config.getOrNull(SemanticsActions.OnClick) != null })
    }

    /** Clickable nodes: each >= 48 dp, none twice (e.g. from a measuring pass), none within a fold +- 12 dp. */
    private fun checkControls(
        where: String,
        layout: LayoutSpec,
        clickable: List<SemanticsNode>,
    ) {
        val density = density()
        val small =
            clickable
                .filter { minOf(it.size.width, it.size.height) < (TARGET_DP - 0.5f) * density }
                .map { "${it.label()} ${it.size}" }
        assertEquals("$where: targets under 48 dp", emptyList<String>(), small)
        val twice =
            clickable
                .groupBy { it.label() }
                .filterValues { it.size > 1 }
                .keys
                .toList()
        assertEquals("$where: duplicated controls", emptyList<String>(), twice)
        val folds =
            listOfNotNull(
                layout.verticalFold?.let { f ->
                    val c = f.positionDp * density
                    val half = (f.occludedDp / 2 + CLEARANCE_DP) * density
                    ComposeRect(c - half, Float.NEGATIVE_INFINITY, c + half, Float.POSITIVE_INFINITY)
                },
                layout.horizontalFold?.let { f ->
                    val c = f.positionDp * density
                    val half = (f.occludedDp / 2 + CLEARANCE_DP) * density
                    ComposeRect(Float.NEGATIVE_INFINITY, c - half, Float.POSITIVE_INFINITY, c + half)
                },
            )
        val onFold =
            clickable
                .filter { node ->
                    val bounds =
                        ComposeRect(
                            node.positionInRoot,
                            androidx.compose.ui.geometry
                                .Size(node.size.width.toFloat(), node.size.height.toFloat()),
                        )
                    // 1 px of tolerance for the rounding of dp to pixels.
                    folds.any { it.overlaps(bounds.deflate(1f)) }
                }.map { it.label() }
        assertEquals("$where: controls on the fold", emptyList<String>(), onFold)
    }

    private fun SemanticsNode.label(): String =
        (
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        ).joinToString(" ")
            .ifEmpty { "node $id" }

    /** Cut vertically or ellipsized (see [CatalogScreenshotsTest]). */
    private fun TextLayoutResult.isClipped(): Boolean = didOverflowHeight || (0 until lineCount).any { isLineEllipsized(it) }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    /** The matrix. */
    companion object {
        private const val OUTPUT_PROPERTY = "nightbaseline.screenshots.dir"
        private const val PNG_QUALITY = 100
        private const val TARGET_DP = 48f
        private const val CLEARANCE_DP = 12f
        private const val BAR_AREA_DP = 160f
        private const val BAR_PADDING_DP = 12f
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** Home states rendered on every device and font scale. */
        val KEY_HOME: List<HomeSample> =
            listOf(
                HomeSample.Deviations,
                HomeSample.SomeOne,
                HomeSample.AllWithinPartial,
                HomeSample.L3Short,
                HomeSample.Short,
                HomeSample.Permission,
                HomeSample.Loading,
            )

        /** Night detail states rendered on every device and font scale. */
        val KEY_NIGHT: List<NightSample> =
            listOf(NightSample.Deviations, NightSample.SomeOne, NightSample.Short, NightSample.Learning, NightSample.NoData)

        /** The shared devices plus the owner's phone half-opened on a table (`layouts.md` 11 `owner-tabletop`). */
        val DEVICES: List<ScreenDevice> =
            CatalogScreenshotsTest.DEVICES.map { ScreenDevice(it) } +
                ScreenDevice(Device("owner-tabletop", 763, 692, 520), horizontalFold = FoldInfo(346f, separating = true))

        /** Parameters: device x font scale. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun parameters(): List<Array<Any>> = DEVICES.flatMap { d -> CatalogScreenshotsTest.FONT_SCALES.map { arrayOf<Any>(d, it) } }
    }
}
