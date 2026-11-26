package io.github.alien2003.nightbaseline.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.TitleColumn
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.withEnd
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.shellToolbarHeight

/**
 * A top-level or pushed page inside the app shell ([AppScaffold]) with the shell top bar (`coloros-conformance.md`
 * C1–C7): on top-level Compact pages the large title sits below the 52 dp toolbar and scrolls away with the content;
 * pushed pages get the round back button and the small title. 48 dp toolbar in short windows. Transparent at rest,
 * progressive blur once [scroll] moves content under it. No top bar in `Glance` and `Tabletop` (the page draws its own
 * title row there, so nothing interactive sits in the tabletop top half). Pushed pages hide the bottom bar.
 *
 * [content] receives the padding that clears the rail, the bar, the status bar and the measured top bar (the bar
 * grows with the font scale, so its resting height is measured instead of assumed).
 */
@Composable
internal fun ShellPage(
    layout: LayoutSpec,
    tab: TopLevel,
    title: String,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    onSelectTab: (TopLevel) -> Unit = {},
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    column: TitleColumn? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val showBar = layout.mode != LayoutMode.Glance && layout.mode != LayoutMode.Tabletop
    val large = layout.mode == LayoutMode.Compact && onBack == null
    val short = layout.mode == LayoutMode.CompactShort || layout.mode == LayoutMode.WideShort
    val barHeight = shellToolbarHeight(short, large)
    var restingHeight by remember { mutableStateOf(0.dp) }
    AppScaffold(
        layout = layout,
        selected = tab,
        onSelect = onSelectTab,
        modifier = modifier,
        showBar = onBack == null,
        overlay = overlay,
        topBar = { padding ->
            if (showBar) {
                Box(
                    Modifier
                        .padding(start = padding.calculateStartPadding(direction))
                        .onSizeChanged { size ->
                            if (scroll.value == 0) {
                                restingHeight = with(density) { size.height.toDp() - statusBar }.coerceAtLeast(0.dp)
                            }
                        },
                ) {
                    ShellTopBar(
                        title = title,
                        subtitle = subtitle,
                        large = large,
                        height = barHeight,
                        scrollOffset = { with(density) { scroll.value.toDp() } },
                        onBack = onBack,
                        column = column?.withEnd(padding.calculateEndPadding(direction)),
                    )
                }
            }
        },
    ) { padding ->
        val resting = if (restingHeight > 0.dp) restingHeight else barHeight
        val top = if (showBar) statusBar + resting + NbSpace.s3 else statusBar + NbSpace.s3
        content(
            PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = top,
                bottom = padding.calculateBottomPadding() + NbSpace.s5,
            ),
        )
    }
}
