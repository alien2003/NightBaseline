package io.github.alien2003.nightbaseline.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.alien2003.nightbaseline.ui.components.BarClearance
import io.github.alien2003.nightbaseline.ui.components.FloatingNavBar
import io.github.alien2003.nightbaseline.ui.components.FloatingNavRail
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.LocalLayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.material.LocalHazeState
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * The app shell (`layouts.md` 4): the solid window background, the content as the glass backdrop
 * (`Modifier.hazeSource`), and on top of it the floating glass navigation of [layout] (bar centred above the
 * navigation-bar inset + 12 dp, or rail at the start edge + 12 dp) plus the screen's [topBar]. The shell components
 * are drawn outside the backdrop so they blur the content and not themselves. [topBar] receives the same padding as
 * [content] so it starts after the rail.
 *
 * @param content receives the padding that keeps it clear of the rail and the bar (`content.bottomClearance`).
 */
@Composable
fun AppScaffold(
    layout: LayoutSpec,
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable BoxScope.(PaddingValues) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val hazeState = rememberHazeState()
    val direction = LocalLayoutDirection.current
    val system = WindowInsets.navigationBars.asPaddingValues()
    val rail = layout.navigation !in setOf(NavigationKind.Bar, NavigationKind.None)
    val density = LocalDensity.current
    var railWidth by remember { mutableStateOf(0.dp) }
    val padding =
        PaddingValues(
            // The rail grows with its labels at large font, so the content starts after its measured width.
            start = if (rail) maxOf(NbDimens.railArea, railWidth + NbDimens.railOffset * 2) else 0.dp,
            bottom =
                if (layout.navigation ==
                    NavigationKind.Bar
                ) {
                    BarClearance + system.calculateBottomPadding()
                } else {
                    system.calculateBottomPadding()
                },
            end = system.calculateEndPadding(direction),
        )
    CompositionLocalProvider(LocalHazeState provides hazeState, LocalLayoutSpec provides layout) {
        Box(modifier.fillMaxSize().background(NbTheme.colors.background)) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) { content(padding) }
            topBar(padding)
            when (layout.navigation) {
                NavigationKind.Bar -> {
                    // In Book posture the bar is centred in the start pane, never across the fold (layouts.md 4.1).
                    val paneWidth = layout.verticalFold?.let { (it.positionDp - it.occludedDp / 2).dp - NbDimens.hingeClearance }
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .then(if (paneWidth != null) Modifier.width(paneWidth) else Modifier.fillMaxSize())
                            .navigationBarsPadding()
                            .padding(bottom = NbDimens.barOffset),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        FloatingNavBar(selected, onSelect)
                    }
                }

                NavigationKind.Rail, NavigationKind.RailIcons, NavigationKind.RailLabelled -> {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(start = NbDimens.railOffset),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        FloatingNavRail(
                            selected,
                            onSelect,
                            modifier = Modifier.onSizeChanged { railWidth = with(density) { it.width.toDp() } },
                            showLabels = layout.navigation != NavigationKind.RailIcons,
                            wide = layout.navigation == NavigationKind.RailLabelled,
                        )
                    }
                }

                NavigationKind.None -> {}
            }
            overlay()
        }
    }
}
