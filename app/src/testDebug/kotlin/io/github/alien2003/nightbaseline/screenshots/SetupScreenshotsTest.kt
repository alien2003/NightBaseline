// detekt treats only src/test as tests; this is the test source set of the debug variant (CatalogFrame is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.learning.LearningSamples
import io.github.alien2003.nightbaseline.ui.learning.LearningScreen
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingSamples
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingScreen
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.settings.SettingsSamples
import io.github.alien2003.nightbaseline.ui.settings.SettingsScreen
import io.github.alien2003.nightbaseline.ui.settings.SettingsState
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

/** A window of the setup matrix: [Device] plus an optional horizontal (tabletop) fold. */
data class SetupDevice(
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
 * Renders the setup screens (onboarding, learning period, settings) from synthetic sample states with the catalog
 * harness (Robolectric native graphics, hardware PixelCopy so glass renders): phone, flip-cover-square (Glance),
 * owner-book (separating vertical fold), owner-tabletop (separating horizontal fold) and tablet-land, at font 1.0 and
 * 2.0, light and dark, plus Russian at font 2.0 on the phone and the cover screen. Settings and the learning period
 * show shell surfaces (top bar, bar or rail, dialog), so they render in glass and in the solid fallback; onboarding
 * is outside the shell (solid only). Every onboarding state renders on the phone and the cover screen, the key states
 * everywhere. Scrolling screens are captured as a filmstrip of window-sized frames.
 *
 * Assertions on every render: no unfilled `%1$s` placeholder, no clipped or ellipsized text, every interactive node
 * at least 48 x 48 dp, no interactive node within 12 dp of a separating fold, and nothing interactive in the tabletop
 * top half (`layouts.md` 6 and 11).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class SetupScreenshotsTest(
    private val device: SetupDevice,
    private val fontScale: Float,
    private val language: String,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val outputDir = File(System.getProperty(OUTPUT_PROPERTY) ?: "build/screenshots")
    private val shot = mutableStateOf<(@Composable () -> Unit)?>(null)
    private lateinit var defaultLocale: Locale
    private lateinit var defaultZone: TimeZone
    private val density get() = device.dpi / 160f

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultZone = TimeZone.getDefault()
        Locale.setDefault(if (language == "ru") Locale.forLanguageTag("ru-RU") else Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val orientation = if (device.widthDp > device.heightDp) "land" else "port"
        val region = if (language == "ru") "ru-rRU" else "en-rUS"
        RuntimeEnvironment.setQualifiers("$region-w${device.widthDp}dp-h${device.heightDp}dp-$orientation-${device.dpi}dpi")
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
        val layout =
            resolveLayout(
                LayoutInputs(
                    device.widthDp.toFloat(),
                    device.heightDp.toFloat(),
                    fontScale = fontScale,
                    verticalFold = device.verticalFold,
                    horizontalFold = device.horizontalFold,
                ),
            )
        val suffix = "${device.id}-${"font%.1f".format(Locale.US, fontScale)}" + if (language == "en") "" else "-$language"
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            onboarding(layout, dark, theme, suffix)
            LearningSamples.all.forEach { (id, state) ->
                effects(id == "l3").forEach { glass ->
                    capture("learning-$id-$theme-${mode(glass)}-$suffix", CatalogConfig(dark, glass), layout) { scroll ->
                        LearningScreen(state, layout, scroll = scroll)
                    }
                }
            }
            settingsStates().forEach { (id, state) ->
                effects(id == "main" || id == "source-change").forEach { glass ->
                    capture("settings-$id-$theme-${mode(glass)}-$suffix", CatalogConfig(dark, glass), layout) { scroll ->
                        SettingsScreen(state, layout, scroll = scroll)
                    }
                }
            }
        }
        activity.finish()
    }

    private fun onboarding(
        layout: LayoutSpec,
        dark: Boolean,
        theme: String,
        suffix: String,
    ) {
        val everyState = device.id == "phone" || device.id == "flip-cover-square"
        OnboardingSamples.all
            .filterKeys { everyState || it in OnboardingSamples.key }
            .forEach { (id, state) ->
                capture("onboarding-$id-$theme-$suffix", CatalogConfig(dark, glass = false), layout) { scroll ->
                    OnboardingScreen(state, layout, scroll = scroll)
                }
            }
    }

    /** Glass and solid for the states that show off the shell; solid only for the rest of the shell screens. */
    private fun effects(both: Boolean): List<Boolean> = if (both) listOf(true, false) else listOf(false)

    private fun settingsStates(): Map<String, SettingsState> =
        linkedMapOf(
            "main" to SettingsSamples.default,
            "blocked" to SettingsSamples.blocked,
            "fresh" to SettingsSamples.fresh,
            "sensitivity" to SettingsSamples.sensitivityFewer,
            "source-change" to SettingsSamples.sourceChange,
            "about" to SettingsSamples.page(SettingsPage.About),
            "licenses" to SettingsSamples.page(SettingsPage.Licenses),
            "privacy" to SettingsSamples.page(SettingsPage.Privacy),
        )

    private fun mode(glass: Boolean) = if (glass) "glass" else "solid"

    /** Shows [content] with its main scroll state, checks it, and writes a filmstrip of window-sized frames. */
    private fun capture(
        name: String,
        config: CatalogConfig,
        layout: LayoutSpec,
        content: @Composable (ScrollState) -> Unit,
    ) {
        val scroll = ScrollState(0)
        show { CatalogFrame(config, Modifier.fillMaxSize()) { content(scroll) } }
        checkTexts(name)
        checkTargets(name, layout)
        val frames = mutableListOf(compose.onRoot().captureToImage().asAndroidBitmap())
        val step = frames.first().height * 3 / 4
        while (scroll.value < scroll.maxValue && frames.size < MAX_FRAMES) {
            compose.runOnIdle { runBlocking { scroll.scrollTo(scroll.value + step) } }
            idle()
            frames += compose.onRoot().captureToImage().asAndroidBitmap()
        }
        write(name, stack(frames))
    }

    /** Frames one below the other with a thin grey separator. */
    private fun stack(frames: List<Bitmap>): Bitmap {
        if (frames.size == 1) return frames.first()
        val width = frames.first().width
        val out = Bitmap.createBitmap(width, frames.sumOf { it.height } + SEPARATOR * (frames.size - 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.GRAY)
        var y = 0
        frames.forEach {
            canvas.drawBitmap(it, 0f, y.toFloat(), null)
            y += it.height + SEPARATOR
        }
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

    private fun nodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.descendants() }

    /** No text shows an unfilled format placeholder, and no text is clipped (same rules as CatalogScreenshotsTest). */
    private fun checkTexts(where: String) {
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
    }

    /**
     * Every clickable node is at least 48 x 48 dp; none lies within the hinge clearance of a separating fold, and in
     * tabletop none lies in the top half. The scrim behind a dialog (it covers the window) is exempt.
     */
    private fun checkTargets(
        where: String,
        layout: LayoutSpec,
    ) {
        val root = compose.onRoot().fetchSemanticsNode().size
        val min = 48 * density - 1
        // 1 px tolerance: dp edges are rounded to whole pixels.
        val clearance = 12 * density - 1
        val clickable =
            nodes().filter {
                it.config.contains(
                    SemanticsActions.OnClick,
                ) && !(it.size.width >= root.width && it.size.height >= root.height)
            }
        val small = clickable.filter { it.size.width < min || it.size.height < min }.map { "${it.label()} ${it.size}" }
        assertEquals("$where: targets under 48 dp", emptyList<String>(), small)
        val visible = clickable.map { it to it.boundsInRoot }.filter { (_, b) -> b.width > 0 && b.height > 0 }
        layout.verticalFold?.let { fold ->
            val x = fold.positionDp * density
            val band = Rect(x - clearance, 0f, x + clearance, root.height.toFloat())
            val hits = visible.filter { (_, b) -> b.overlaps(band) }.map { it.first.label() }
            assertEquals("$where: interactive nodes across the vertical fold", emptyList<String>(), hits)
        }
        layout.horizontalFold?.let { fold ->
            val limit = fold.positionDp * density + clearance
            val hits = visible.filter { (_, b) -> b.top < limit }.map { it.first.label() }
            assertEquals("$where: interactive nodes in the tabletop top half", emptyList<String>(), hits)
        }
    }

    private fun SemanticsNode.label(): String =
        (
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                children.flatMap { c ->
                    c.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .map { it.text }
                }
        ).joinToString(" ").ifEmpty { "node $id" }

    /** Cut vertically or ellipsized (see CatalogScreenshotsTest for why `hasVisualOverflow` is not used). */
    private fun TextLayoutResult.isClipped(): Boolean = didOverflowHeight || (0 until lineCount).any { isLineEllipsized(it) }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    /** The matrix. */
    companion object {
        private const val OUTPUT_PROPERTY = "nightbaseline.screenshots.dir"
        private const val PNG_QUALITY = 100
        private const val MAX_FRAMES = 6
        private const val SEPARATOR = 6
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** Devices of `layouts.md` 11 for the setup screens. */
        val DEVICES: List<SetupDevice> =
            listOf(
                SetupDevice("phone", 412, 915, 420),
                SetupDevice("flip-cover-square", 340, 352, 320),
                SetupDevice("owner-book", 692, 763, 520, verticalFold = FoldInfo(346f, separating = true)),
                SetupDevice("owner-tabletop", 763, 692, 520, horizontalFold = FoldInfo(346f, separating = true)),
                SetupDevice("tablet-land", 1280, 800, 320),
            )

        /** Parameters: device x font scale in English, plus Russian at 200 % on the phone and the cover screen. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1} {2}")
        fun parameters(): List<Array<Any>> {
            val all =
                DEVICES.flatMap { d -> listOf(1f, 2f).map { arrayOf<Any>(d, it, "en") } } +
                    DEVICES.filter { it.id == "phone" || it.id == "flip-cover-square" }.map { arrayOf<Any>(it, 2f, "ru") }
            val only = System.getenv("SETUP_ONLY")?.split(',')?.toSet() ?: return all
            return all.filter { "${it[0]}-${it[1]}-${it[2]}" in only }
        }
    }
}
