// detekt treats only src/test as tests; this is the test source set of the debug variant (the catalog is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.catalog.CatalogPage
import io.github.alien2003.nightbaseline.catalog.CatalogPageContent
import io.github.alien2003.nightbaseline.ui.home.HomeScreen
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.sample.SampleData
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

/** A window of the screenshot matrix (`layouts.md` 11). */
data class Device(
    /** Id from `layouts.md` 11. */
    val id: String,
    /** Width in dp. */
    val widthDp: Int,
    /** Height in dp. */
    val heightDp: Int,
    /** Density in dpi. */
    val dpi: Int,
    /** Synthetic fold injected through LayoutInputs (Robolectric cannot produce postures). */
    val verticalFold: FoldInfo? = null,
) {
    override fun toString(): String = id
}

/**
 * Renders the component catalog pages and the placeholder Home from synthetic data on the JVM (Robolectric native
 * graphics, offline android-all jar, hardware PixelCopy so the Haze blur renders, see [HazeBlurProbeTest]) for the
 * windows phone, flip-cover-square, owner-book (vertical separating fold) and tablet-land at font 1.0 and 2.0, in light
 * and dark. Glass and the solid fallback are rendered for the pages that contain glass (Shell, Home); the other pages
 * hold only solid surfaces, so a fallback copy would be identical. Pages taller than the window are scrolled and
 * stitched. Grayscale variants of Metrics, Nights and Home on the phone check that statuses stay distinguishable.
 * Every render also checks that no text shows an unfilled `%1$s` placeholder and that no text is clipped.
 *
 * Output: build/screenshots (every run) or docs/screenshots with -PrecordScreenshots (app/build.gradle.kts).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class CatalogScreenshotsTest(
    private val device: Device,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

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
        val font = "font%.1f".format(Locale.US, fontScale)
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            CatalogPage.entries.forEach { page ->
                val effects = if (page == CatalogPage.Shell) listOf(true, false) else listOf(false)
                effects.forEach { glass ->
                    val config = CatalogConfig(dark = dark, glass = glass)
                    val name =
                        listOfNotNull(
                            page.name.lowercase(),
                            device.id,
                            theme,
                            if (page ==
                                CatalogPage.Shell
                            ) {
                                mode(glass)
                            } else {
                                null
                            },
                            font,
                        )
                    capturePage(name.joinToString("-"), config, page)
                }
            }
            listOf(true, false).forEach { glass ->
                captureHome("home-${device.id}-$theme-${mode(glass)}-$font", CatalogConfig(dark, glass))
            }
        }
        if (device.id == "phone" && fontScale == 1f) {
            val gray = CatalogConfig(grayscale = true, glass = false)
            capturePage("metrics-phone-grayscale-$font", gray, CatalogPage.Metrics)
            capturePage("nights-phone-grayscale-$font", gray, CatalogPage.Nights)
            captureHome("home-phone-grayscale-moved-$font", gray, SampleData.night(NightState.Moved))
            captureHome("home-phone-learning-$font", CatalogConfig(glass = true), SampleData.night(NightState.Learning))
        }
        activity.finish()
    }

    private fun mode(glass: Boolean) = if (glass) "glass" else "solid"

    /** Shows [page] in a scrolling column and writes the whole page (stitched from window-sized captures). */
    private fun capturePage(
        name: String,
        config: CatalogConfig,
        page: CatalogPage,
    ) {
        val scroll = ScrollState(0)
        show {
            CatalogFrame(config, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) { CatalogPageContent(page) }
            }
        }
        checkTexts(name)
        val viewport = compose.onRoot().captureToImage().asAndroidBitmap()
        val total = viewport.height + scroll.maxValue
        val out = Bitmap.createBitmap(viewport.width, total, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        var offset = 0
        while (true) {
            compose.runOnIdle { runBlocking { scroll.scrollTo(offset) } }
            idle()
            val chunk = compose.onRoot().captureToImage().asAndroidBitmap()
            canvas.drawBitmap(chunk, 0f, scroll.value.toFloat(), null)
            if (scroll.value >= scroll.maxValue) break
            offset = scroll.value + viewport.height
        }
        write(name, out)
    }

    /** Shows the placeholder Home for this window (fold injected through LayoutInputs) and writes the window. */
    private fun captureHome(
        name: String,
        config: CatalogConfig,
        night: io.github.alien2003.nightbaseline.ui.model.NightUi = SampleData.lastNight,
    ) {
        val layout =
            resolveLayout(
                LayoutInputs(
                    device.widthDp.toFloat(),
                    device.heightDp.toFloat(),
                    fontScale = fontScale,
                    verticalFold = device.verticalFold,
                ),
            )
        show { CatalogFrame(config, Modifier.fillMaxSize()) { HomeScreen(SampleData.home(night), layout) } }
        checkTexts(name)
        write(name, compose.onRoot().captureToImage().asAndroidBitmap())
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

    /** No text shows an unfilled format placeholder, and no text is clipped (`layouts.md` 11 layout assertions). */
    private fun checkTexts(where: String) {
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
    }

    /**
     * Cut vertically or ellipsized. (`hasVisualOverflow` is not used: Compose reports a width overflow for every
     * wrap-content text whose paragraph was laid out at the full constraint width.)
     */
    private fun TextLayoutResult.isClipped(): Boolean = didOverflowHeight || (0 until lineCount).any { isLineEllipsized(it) }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    /** The matrix. */
    companion object {
        private const val OUTPUT_PROPERTY = "nightbaseline.screenshots.dir"
        private const val PNG_QUALITY = 100
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** Devices of `layouts.md` 11: phone compact, flip cover small, book unfolded (separating fold), tablet landscape. */
        val DEVICES: List<Device> =
            listOf(
                Device("phone", 412, 915, 420),
                Device("flip-cover-square", 340, 352, 320),
                Device("owner-book", 692, 763, 520, verticalFold = FoldInfo(346f, separating = true)),
                Device("tablet-land", 1280, 800, 320),
            )

        /** Font scales: default and the largest (200 %). */
        val FONT_SCALES: List<Float> = listOf(1f, 2f)

        /** Parameters: device x font scale. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun parameters(): List<Array<Any>> = DEVICES.flatMap { d -> FONT_SCALES.map { arrayOf<Any>(d, it) } }
    }
}
