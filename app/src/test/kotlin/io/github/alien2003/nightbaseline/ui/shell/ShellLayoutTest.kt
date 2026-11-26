package io.github.alien2003.nightbaseline.ui.shell

import android.app.Application
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.alien2003.nightbaseline.ui.components.CARD_TAG
import io.github.alien2003.nightbaseline.ui.components.CalendarCell
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.components.SettingsTrailing
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.history.HistorySamples
import io.github.alien2003.nightbaseline.ui.history.HistoryScreen
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingSamples
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingScreen
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Layout rules of the shell and its rows that the emulator walkthrough found broken: the 3-button navigation backing,
 * the rail clear of a side cutout, the bottom bar at Russian font 2.0, the opaque solid top bar, the History list
 * scrolling under the collapsing large title, a settings row in a tiny window, and the calendar day labels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "en-rUS-w393dp-h851dp-port-xhdpi")
class ShellLayoutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var activity: ComponentActivity

    private fun show(
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) { content() }
            }
        }
        compose.waitForIdle()
    }

    /** Hands the composition window insets as the system would (decor not fitting system windows). */
    private fun dispatchInsets(insets: WindowInsetsCompat) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        compose.runOnIdle { ViewCompat.dispatchApplyWindowInsets(root, insets) }
        compose.waitForIdle()
    }

    private fun px(dp: Int): Int = (dp * activity.resources.displayMetrics.density).toInt()

    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun threeButtonNavigationGetsABackingAndGestureNavigationNone() {
        val layout = resolveLayout(LayoutInputs(WIDTH, HEIGHT))
        show { AppScaffold(layout, TopLevel.Home, onSelect = {}) { Box(Modifier.fillMaxSize()) } }
        dispatchInsets(navigationBar(px(THREE_BUTTON_DP)))
        val scrim = compose.onNodeWithTag(NAV_SCRIM_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the backing covers the 3-button bar: ${scrim.height} px", scrim.height >= px(THREE_BUTTON_DP))
        val bottom =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.bottom
        assertEquals("the backing ends at the window's bottom edge", bottom, scrim.bottom, 1f)
        dispatchInsets(navigationBar(px(GESTURE_DP)))
        assertTrue(
            "no backing over gesture navigation",
            compose.onAllNodesWithTag(NAV_SCRIM_TAG).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun railContentStartsAfterTheRailWithAStartCutout() {
        val layout = resolveLayout(LayoutInputs(WIDE_SHORT_W, WIDE_SHORT_H))
        assertEquals(LayoutMode.WideShort, layout.mode)
        assertEquals(NavigationKind.RailIcons, layout.navigation)
        show {
            AppScaffold(layout, TopLevel.Home, onSelect = {}) { padding ->
                Box(Modifier.fillMaxSize().padding(start = padding.calculateLeftPadding(LayoutDirection.Ltr)).testTag(CONTENT))
            }
        }
        val cutout =
            WindowInsetsCompat
                .Builder()
                .setInsets(
                    WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(px(CUTOUT_DP), 0, 0, 0),
                ).build()
        dispatchInsets(cutout)
        val railEnd = compose.onAllNodes(tabs).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        val contentStart =
            compose
                .onNodeWithTag(CONTENT)
                .fetchSemanticsNode()
                .boundsInRoot.left
        assertTrue("content starts at $contentStart px, after the rail ending at $railEnd px", contentStart >= railEnd)
    }

    @Test
    @Config(qualifiers = "ru-rRU-w393dp-h851dp-port-xhdpi")
    fun bottomBarAtRussianFont2KeepsIconsLevelAndNeverBreaksAWord() = assertBarAtFont2(WIDTH)

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h700dp-port-xhdpi")
    fun bottomBarOnANarrowPhoneAtRussianFont2UsesTheGuttersRatherThanBreakAWord() = assertBarAtFont2(NARROW_W)

    @Test
    fun bottomBarAtFont1StaysInsideTheGutters() {
        val layout = resolveLayout(LayoutInputs(WIDTH, HEIGHT))
        show { AppScaffold(layout, TopLevel.Home, onSelect = {}) { Box(Modifier.fillMaxSize()) } }
        val items = compose.onAllNodes(tabs).fetchSemanticsNodes().map { it.boundsInRoot }
        val span = items.maxOf { it.right } - items.minOf { it.left }
        val inside = px(WIDTH.toInt()) - 2 * px(GUTTER_DP)
        assertTrue("items span $span px, inside the gutters ($inside px)", span <= inside)
    }

    private fun assertBarAtFont2(width: Float) {
        val layout = resolveLayout(LayoutInputs(width, HEIGHT, fontScale = 2f))
        show(fontScale = 2f) { AppScaffold(layout, TopLevel.Home, onSelect = {}) { Box(Modifier.fillMaxSize()) } }
        val items = compose.onAllNodes(tabs).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(TopLevel.entries.size, items.size)
        assertEquals("one top line for every item", 1, items.map { it.top }.distinct().size)
        assertEquals("one height for every item", 1, items.map { it.height }.distinct().size)
        assertTrue("the bar stays on the screen", items.minOf { it.left } >= 0f && items.maxOf { it.right } <= px(width.toInt()))
        TopLevel.entries.forEach { destination ->
            val label = activity.getString(destination.label)
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layoutResult = results.single()
            for (line in 0 until layoutResult.lineCount - 1) {
                val end = layoutResult.getLineEnd(line)
                assertTrue(
                    "'$label' breaks inside a word after line $line ('${label.substring(0, end)}')",
                    label[end - 1].isWhitespace() || label.getOrNull(end)?.isWhitespace() == true,
                )
            }
        }
    }

    @Test
    fun solidTopBarIsOpaqueBackground() {
        var fill: Brush? = null
        var background = Color.Unspecified
        show {
            fill = ShellMaterial.TopBar.solidFill(ShellTokens.colors, NbTheme.colors)
            background = NbTheme.colors.background
        }
        val solid = fill as? SolidColor
        assertTrue("the solid top bar is one opaque colour, not a fading gradient: $fill", solid != null)
        assertEquals(background.copy(alpha = 1f), solid!!.value)
    }

    @Test
    @Config(qualifiers = "en-rUS-w393dp-h560dp-port-xhdpi")
    fun historyCalendarScrollsUnderTheCollapsingTitleWithoutACut() = assertCalendarNotCut(font = 1f)

    @Test
    @Config(qualifiers = "en-rUS-w393dp-h560dp-port-xhdpi")
    fun historyCalendarAtFont2ScrollsUnderTheCollapsingTitleWithoutACut() = assertCalendarNotCut(font = 2f)

    private fun assertCalendarNotCut(font: Float) {
        val layout = resolveLayout(LayoutInputs(WIDTH, CALENDAR_HEIGHT, fontScale = font))
        assertEquals(LayoutMode.Compact, layout.mode)
        val list = LazyListState()
        show(fontScale = font) { HistoryScreen(HistorySamples.calendar(), layout, listState = list) }
        val card = compose.onAllNodesWithTag(CARD_TAG)[0]
        val rest = card.getUnclippedBoundsInRoot().top
        compose.runOnIdle { runBlocking { list.scrollBy(px(SCROLL_DP).toFloat()) } }
        compose.waitForIdle()
        val whole = card.getUnclippedBoundsInRoot()
        assertTrue("font $font: the card moved up with the scroll ($rest -> ${whole.top})", whole.top < rest)
        assertEquals("font $font: the calendar card scrolled by $SCROLL_DP dp is not cut at the top", whole.top, card.getBoundsInRoot().top)
    }

    @Test
    fun settingsValueLeavesTheTitleAColumnInATinyWindow() {
        show {
            Box(Modifier.width(TINY_DP.dp)) {
                SettingsRow(
                    "Source",
                    onClick = {},
                    summary = "Read 2/8",
                    trailing = SettingsTrailing.Value("NightBaseline Seeder"),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        assertEquals("the title stays on one line", 1, lines("Source"))
        assertEquals("the summary stays on one line", 1, lines("Read 2/8"))
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h560dp-land-mdpi")
    fun onboardingCardKeepsTheButtonsInsideTheCardOnATallStep() {
        val layout = resolveLayout(LayoutInputs(TABLET_W, TABLET_H))
        show { OnboardingScreen(OnboardingSamples.all.getValue("perm-partial"), layout) }
        val card = compose.onAllNodesWithTag(CARD_TAG)[0].getUnclippedBoundsInRoot()
        val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("the step has buttons", buttons.isNotEmpty())
        val last = buttons.maxBy { it.unclippedBottom() }
        val density = activity.resources.displayMetrics.density
        assertTrue(
            "the lowest button (bottom ${last.unclippedBottom() / density} dp) is inside the card (bottom ${card.bottom})",
            last.unclippedBottom() / density <= card.bottom.value,
        )
        assertEquals("the lowest button is not clipped", last.unclippedBottom(), last.boundsInRoot.bottom, 1f)
    }

    private fun SemanticsNode.unclippedBottom(): Float = positionInRoot.y + size.height

    @Test
    fun calendarDayOutsideTheHistoryReadsAsItsDateOnly() {
        val day = LocalDate.of(2026, 9, 1)
        var expected = ""
        show {
            expected = rememberUiText().dateLong(day)
            CalendarCell(day, night = null, onClick = {}, modifier = Modifier.testTag(CELL))
        }
        val description = compose.onNodeWithTag(CELL).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(expected), description)
        assertFalse("no status for a day that is not a night", description.single().contains("No data"))
    }

    private fun lines(text: String): Int {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    private fun navigationBar(bottomPx: Int): WindowInsetsCompat =
        WindowInsetsCompat
            .Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottomPx))
            .build()

    private companion object {
        const val WIDTH = 393f
        const val HEIGHT = 851f
        const val NARROW_W = 320f
        const val WIDE_SHORT_W = 900f
        const val WIDE_SHORT_H = 400f
        const val THREE_BUTTON_DP = 48
        const val GESTURE_DP = 16
        const val CUTOUT_DP = 48
        const val GUTTER_DP = 16
        const val SCROLL_DP = 30
        const val CALENDAR_HEIGHT = 560f
        const val TABLET_W = 1280f
        const val TABLET_H = 560f
        const val TINY_DP = 188
        const val CONTENT = "content"
        const val CELL = "cell"
    }
}
