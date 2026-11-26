package io.github.alien2003.nightbaseline.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.alien2003.nightbaseline.ui.components.FloatingNavBar
import io.github.alien2003.nightbaseline.ui.components.FloatingNavRail
import io.github.alien2003.nightbaseline.ui.components.LocalPaneScrolls
import io.github.alien2003.nightbaseline.ui.components.PaneScrolls
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.LocalLayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.material.LocalHazeState
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.motion.NbSprings
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens

/**
 * The app shell (`layouts.md` 4, `coloros-conformance.md` A4, B1, C7): the solid window background, the content as
 * the glass backdrop (`Modifier.hazeSource`), and on top of it the floating navigation of [layout] (bar centred
 * 16 dp above the screen's bottom edge with gesture navigation, or 8 dp above a 3-button navigation bar; rail 16 dp
 * from the start edge) plus the screen's [topBar]. The shell components are drawn outside the backdrop so they blur
 * the content and not themselves. [topBar] receives the same padding as [content] so it starts after the rail. The
 * padding keeps the content clear of side cutouts too, and a 3-button navigation bar gets a backing of the window
 * background ([NAV_SCRIM_TAG]); gesture navigation stays transparent.
 *
 * @param showBar false on pushed sub-pages in Compact (the bar belongs to top-level destinations only); the bar slides
 *   out and back in. The rail stays on wide windows.
 * @param content receives the padding that keeps it clear of the rail and the bar (`content.bottomClearance`).
 */
@Composable
fun AppScaffold(
    layout: LayoutSpec,
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    showBar: Boolean = true,
    topBar: @Composable BoxScope.(PaddingValues) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val hazeState = rememberHazeState()
    val direction = LocalLayoutDirection.current
    val system = WindowInsets.navigationBars.asPaddingValues()
    // The rail is offset by the start safe-drawing inset (a side cutout, a 3-button bar on the start side), so the
    // content reserves the same inset; on the end side the cutout counts as much as the navigation bar.
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safe.calculateStartPadding(direction)
    val rail = layout.navigation !in setOf(NavigationKind.Bar, NavigationKind.None)
    val bar = layout.navigation == NavigationKind.Bar && showBar
    val density = LocalDensity.current
    val motion = NbTheme.motion
    var railWidth by remember { mutableStateOf(0.dp) }
    var barHeight by remember { mutableStateOf(ShellDimens.barHeight) }
    val inset = system.calculateBottomPadding()
    // Gesture navigation: 16 dp from the screen edge, at least 4 dp above the handle; 3-button: 8 dp above the bar.
    val barBottom =
        if (inset > ShellDimens.gestureInsetMax) {
            inset + ShellDimens.barAboveButtonsInset
        } else {
            maxOf(ShellDimens.barBottomMin, inset + ShellDimens.barAboveGestureInset)
        }
    val padding =
        PaddingValues(
            // The rail grows with its labels at large font, so the content starts after its measured width.
            start = safeStart + if (rail) maxOf(NbDimens.railArea, railWidth + ShellDimens.railOffset * 2) else 0.dp,
            bottom = if (bar) barBottom + maxOf(barHeight, ShellDimens.barHeight) + ShellDimens.barContentGap else inset,
            end = maxOf(safe.calculateEndPadding(direction), system.calculateEndPadding(direction)),
        )
    val paneScrolls = remember { PaneScrolls() }
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalLayoutSpec provides layout,
        LocalPaneScrolls provides paneScrolls,
    ) {
        Box(modifier.fillMaxSize().background(NbTheme.colors.background)) {
            // Focus order (layouts.md 10.1): rail -> top bar -> panes in rail layouts, content -> bar with the bar.
            // The rail is composed first for traversal and drawn on top with zIndex (it must stay outside the
            // backdrop it blurs). Each shell part is one focus group.
            if (rail) {
                Box(
                    Modifier
                        .zIndex(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = ShellDimens.railOffset),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    FloatingNavRail(
                        selected,
                        onSelect,
                        modifier = Modifier.focusGroup().onSizeChanged { railWidth = with(density) { it.width.toDp() } },
                        showLabels = layout.navigation != NavigationKind.RailIcons,
                    )
                }
            }
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) { content(padding) }
            topBar(padding)
            // 3-button navigation: the system icons get a backing of the window background, fading in above the bar
            // (`coloros-conformance.md` D1). Gesture navigation keeps the transparent edge under the floating bar.
            if (inset > ShellDimens.gestureInsetMax) NavBarScrim(inset, Modifier.align(Alignment.BottomStart))
            if (layout.navigation == NavigationKind.Bar) {
                // In Book posture the bar is centred in the start pane, never across the fold (layouts.md 4.1).
                val paneWidth = layout.verticalFold?.let { (it.positionDp - it.occludedDp / 2).dp - NbDimens.hingeClearance }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .then(if (paneWidth != null) Modifier.width(paneWidth) else Modifier.fillMaxSize())
                        // A bar whose labels wrap at large font shares out at most the width inside the gutters.
                        .padding(start = layout.gutter + safeStart, end = layout.gutter + padding.calculateEndPadding(direction))
                        .padding(bottom = barBottom),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AnimatedVisibility(
                        visible = showBar,
                        enter =
                            if (motion.reduced) {
                                fadeIn(tween(NbDurations.REDUCED))
                            } else {
                                slideInVertically(NbSprings.Page.spec()) { it + barBottom.roundToPx(density) } +
                                    fadeIn(NbSprings.Page.spec())
                            },
                        exit =
                            if (motion.reduced) {
                                fadeOut(tween(NbDurations.REDUCED))
                            } else {
                                slideOutVertically(tween(BAR_EXIT_MS, easing = NbEasing.Exit)) { it + barBottom.roundToPx(density) } +
                                    fadeOut(tween(BAR_EXIT_MS, easing = NbEasing.Exit))
                            },
                    ) {
                        FloatingNavBar(
                            selected,
                            onSelect,
                            Modifier.focusGroup().onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                        )
                    }
                }
            }
            overlay()
        }
    }
}

/**
 * The backing behind a 3-button navigation bar of height [inset]: the window background at [NAV_SCRIM_ALPHA] under
 * the bar, fading in over [NavScrimFade] above it, so scrolled content never runs under the system icons.
 */
@Composable
private fun NavBarScrim(
    inset: Dp,
    modifier: Modifier = Modifier,
) {
    val background = NbTheme.colors.background
    val total = inset + NavScrimFade
    val solid = background.copy(alpha = NAV_SCRIM_ALPHA)
    Box(
        modifier
            .testTag(NAV_SCRIM_TAG)
            .fillMaxWidth()
            .height(total)
            .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), NavScrimFade / total to solid, 1f to solid)),
    )
}

/** Test tag of the backing behind a 3-button navigation bar. */
internal const val NAV_SCRIM_TAG: String = "nav-bar-scrim"

/** Opacity of the backing under a 3-button navigation bar. */
private const val NAV_SCRIM_ALPHA = 0.92f

/** The backing fades in over this height above a 3-button navigation bar. */
private val NavScrimFade: Dp = 16.dp

/** The bar leaves sub-pages in 200 ms with `easing.exit` (`coloros-conformance.md` C7). */
private const val BAR_EXIT_MS = 200

private fun Dp.roundToPx(density: Density): Int = with(density) { roundToPx() }
