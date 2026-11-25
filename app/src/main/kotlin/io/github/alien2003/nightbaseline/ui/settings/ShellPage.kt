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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace

/**
 * A top-level or pushed page inside the app shell ([AppScaffold]) with the shell top bar of `layouts.md` 4.3: a
 * large collapsing title on top-level Compact pages, 48 dp in short windows, 64 dp otherwise, transparent at rest and
 * glass once [scroll] moves content under it. No top bar in `Glance` and `Tabletop` (the page draws its own title row
 * there, so nothing interactive sits in the tabletop top half).
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
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val showBar = layout.mode != LayoutMode.Glance && layout.mode != LayoutMode.Tabletop
    val large = layout.mode == LayoutMode.Compact && onBack == null
    val barHeight = topBarHeight(layout.mode, large)
    val scrollDp = with(density) { scroll.value.toDp() }
    val collapseRange = NbDimens.topBarLarge - NbDimens.topBarCollapsed
    val collapse = if (large) (scrollDp / collapseRange).coerceIn(0f, 1f) else 1f
    val scrolledUnder = if (large) (scrollDp - collapseRange).coerceAtLeast(0.dp) else scrollDp
    var restingHeight by remember { mutableStateOf(0.dp) }
    AppScaffold(
        layout = layout,
        selected = tab,
        onSelect = onSelectTab,
        modifier = modifier,
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
                        collapse = collapse,
                        height = barHeight,
                        scrolledUnder = scrolledUnder,
                        onBack = onBack,
                    )
                }
            }
        },
    ) { padding ->
        val top = if (showBar) statusBar + maxOf(barHeight, restingHeight) + NbSpace.s3 else statusBar + NbSpace.s3
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

/** Top bar height per mode (`layouts.md` 4.3). */
private fun topBarHeight(
    mode: LayoutMode,
    large: Boolean,
): Dp =
    when {
        large -> NbDimens.topBarLarge
        mode == LayoutMode.Compact -> NbDimens.topBarCollapsed
        mode == LayoutMode.CompactShort || mode == LayoutMode.WideShort -> NbDimens.topBarShort
        else -> NbDimens.topBarMedium
    }
