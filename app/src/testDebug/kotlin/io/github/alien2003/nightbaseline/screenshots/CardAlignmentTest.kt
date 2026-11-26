// detekt treats only src/test as tests; this is the test source set of the debug variant (CatalogFrame is debug-only).
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import io.github.alien2003.nightbaseline.catalog.CatalogConfig
import io.github.alien2003.nightbaseline.catalog.CatalogFrame
import io.github.alien2003.nightbaseline.ui.components.CARD_TAG
import io.github.alien2003.nightbaseline.ui.history.HistoryAccess
import io.github.alien2003.nightbaseline.ui.history.HistorySamples
import io.github.alien2003.nightbaseline.ui.history.HistoryScreen
import io.github.alien2003.nightbaseline.ui.home.HomeSample
import io.github.alien2003.nightbaseline.ui.home.HomeSamples
import io.github.alien2003.nightbaseline.ui.home.HomeScreen
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.learning.LearningSamples
import io.github.alien2003.nightbaseline.ui.learning.LearningScreen
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailSamples
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailScreen
import io.github.alien2003.nightbaseline.ui.metric.MetricSample
import io.github.alien2003.nightbaseline.ui.night.NightDetailSamples
import io.github.alien2003.nightbaseline.ui.night.NightDetailScreen
import io.github.alien2003.nightbaseline.ui.night.NightSample
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingSamples
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingScreen
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.settings.SettingsSamples
import io.github.alien2003.nightbaseline.ui.settings.SettingsScreen
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
import kotlin.math.abs

/**
 * The container rule of review round 4 (owner's unfolded-screen feedback): on every screen the content cards
 * ([CARD_TAG]) of one column share one start edge and one width (within 1 dp), so no card is narrower or shifted
 * against its neighbours. Cards are grouped into columns by horizontal overlap; cards nested in another card are
 * ignored.
 * - Book (`owner-book`): every column is a single column of cards, so the rule is strict.
 * - Other wide windows (the owner's unfolded inner screen, tabletop, tablets): a column may hold a tile grid, so each
 *   card either spans the whole column or is a grid cell flush with one of its edges next to a sibling on its row.
 *
 * Covers Home, Night detail, Metric detail, History (list, calendar, access banner), Settings (main and sub-pages),
 * Learning and Onboarding. Writes the window at rest as `align-{screen}-{device}-font{scale}.png` for review.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class CardAlignmentTest(
    private val screen: ScreenDevice,
    private val fontScale: Float,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val device get() = screen.device
    private val outputDir = File(System.getProperty("nightbaseline.screenshots.dir") ?: "build/screenshots")
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
    fun cardsOfAColumnShareStartAndWidth() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { shot.value?.invoke() }
        val layout = layout()
        val failures = mutableListOf<String>()
        screens(layout).forEach { (id, content) ->
            show { CatalogFrame(CatalogConfig(dark = true, glass = false), Modifier.fillMaxSize()) { content() } }
            write("align-$id-${device.id}-font%.1f".format(Locale.US, fontScale))
            failures += check(id, layout)
        }
        activity.finish()
        assertEquals("cards out of line on ${device.id} at font $fontScale", emptyList<String>(), failures)
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

    private fun screens(layout: LayoutSpec): List<Pair<String, @Composable () -> Unit>> =
        buildList {
            listOf(HomeSample.Deviations, HomeSample.SomeOne, HomeSample.L3Short, HomeSample.Permission).forEach { sample ->
                add("home-${sample.id}" to { HomeScreen(HomeSamples.state(sample), layout, scrollState = ScrollState(0)) })
            }
            listOf(NightSample.Deviations, NightSample.Learning).forEach { sample ->
                add("night-${sample.id}" to { NightDetailScreen(NightDetailSamples.state(sample), layout, scrollState = ScrollState(0)) })
            }
            listOf(MetricSample.Within, MetricSample.Higher).forEach { sample ->
                add(
                    "metric-${sample.name.lowercase(Locale.US)}" to
                        { MetricDetailScreen(MetricDetailSamples.state(sample), layout, scroll = ScrollState(0)) },
                )
            }
            add("history-list" to { HistoryScreen(HistorySamples.feed(), layout, listState = LazyListState()) })
            add("history-calendar" to { HistoryScreen(HistorySamples.calendar(), layout, listState = LazyListState()) })
            add("history-perm" to { HistoryScreen(HistorySamples.access(HistoryAccess.Permission), layout, listState = LazyListState()) })
            linkedMapOf(
                "main" to SettingsSamples.default,
                "sensitivity" to SettingsSamples.sensitivityFewer,
                "source" to SettingsSamples.page(SettingsPage.SourcePicker),
                "about" to SettingsSamples.page(SettingsPage.About),
                "licenses" to SettingsSamples.page(SettingsPage.Licenses),
                "privacy" to SettingsSamples.page(SettingsPage.Privacy),
            ).forEach { (id, state) -> add("settings-$id" to { SettingsScreen(state, layout, scroll = ScrollState(0)) }) }
            LearningSamples.all.forEach { (id, state) -> add("learning-$id" to { LearningScreen(state, layout, scroll = ScrollState(0)) }) }
            OnboardingSamples.all
                .filterKeys { it in OnboardingSamples.key }
                .forEach { (id, state) -> add("onboarding-$id" to { OnboardingScreen(state, layout, scroll = ScrollState(0)) }) }
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

    /** The failures of one screen: every column of cards whose members disagree on start or width. */
    private fun check(
        id: String,
        layout: LayoutSpec,
    ): List<String> {
        val density =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        val all =
            compose
                .onAllNodes(hasTestTag(CARD_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot.let { r -> Rect(r.left / density, r.top / density, r.right / density, r.bottom / density) } }
                .filter { it.width > 1f && it.height > 1f }
        // Cards drawn inside another card (a summary inside a preview card, ...) are not part of the column.
        val cards = all.filter { inner -> all.none { outer -> outer != inner && outer.encloses(inner) } }
        val strict = layout.mode == LayoutMode.Book
        return columns(cards).mapNotNull { column ->
            val start = column.minOf { it.left }
            val end = column.maxOf { it.right }
            val bad =
                column.filterNot { card ->
                    val full = near(card.left, start) && near(card.right, end)
                    val cell =
                        (near(card.left, start) || near(card.right, end)) &&
                            column.any { other -> other != card && near(other.top, card.top) && !other.overlapsHorizontally(card) }
                    full || (!strict && cell)
                }
            bad.takeIf { it.isNotEmpty() }?.let {
                "$id: column ${fmt(start)}..${fmt(end)} dp has ${it.joinToString { r -> "${fmt(r.left)}..${fmt(r.right)}@${fmt(r.top)}" }}"
            }
        }
    }

    /** Groups cards whose horizontal extents overlap (more than 1 dp) into columns. */
    private fun columns(cards: List<Rect>): List<List<Rect>> {
        val groups = mutableListOf<MutableList<Rect>>()
        cards.forEach { card ->
            val touching = groups.filter { g -> g.any { it.overlapsHorizontally(card) } }
            val merged = mutableListOf(card)
            touching.forEach { merged += it }
            groups.removeAll(touching)
            groups += merged
        }
        return groups
    }

    private fun Rect.encloses(other: Rect): Boolean =
        left <= other.left + TOLERANCE_DP && right >= other.right - TOLERANCE_DP &&
            top <= other.top + TOLERANCE_DP && bottom >= other.bottom - TOLERANCE_DP

    private fun Rect.overlapsHorizontally(other: Rect): Boolean = minOf(right, other.right) - maxOf(left, other.left) > TOLERANCE_DP

    private fun near(
        a: Float,
        b: Float,
    ): Boolean = abs(a - b) <= TOLERANCE_DP

    private fun fmt(v: Float): String = "%.1f".format(Locale.US, v)

    private fun write(name: String) {
        val bitmap: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        outputDir.mkdirs()
        File(outputDir, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    /** The matrix. */
    companion object {
        private const val TOLERANCE_DP = 1f

        /**
         * The owner's book posture (strict rule) and the wide windows of the round-4 audit, including the owner's
         * unfolded inner screen as it reports itself (2248 x 2480 px at about 378 dpi: 952 x 1050 dp, Expanded).
         */
        val DEVICES: List<ScreenDevice> =
            listOf(
                ScreenDevice(Device("owner-book", 692, 763, 520, verticalFold = FoldInfo(346f, separating = true))),
                ScreenDevice(Device("owner-unfolded", 952, 1050, 378)),
                ScreenDevice(Device("owner-tabletop", 763, 692, 520), horizontalFold = FoldInfo(346f, separating = true)),
                ScreenDevice(Device("tablet-land", 1280, 800, 320)),
                ScreenDevice(Device("tablet-portrait", 800, 1280, 320)),
            )

        /** Parameters: device x font scale (1.0, and the owner's 1.35). */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} font {1}")
        fun parameters(): List<Array<Any>> = DEVICES.flatMap { d -> listOf(1f, 1.35f).map { arrayOf<Any>(d, it) } }
    }
}
