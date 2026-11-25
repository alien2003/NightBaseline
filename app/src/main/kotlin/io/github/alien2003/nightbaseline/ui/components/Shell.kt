package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.material.shellSurface
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * The top bar (`screens.md` 4, `layouts.md` 4.3). Transparent over the solid window background while content rests;
 * `material.glass.topBar` (progressive blur, tint) once content scrolls under it, with the tint at full strength
 * after [NbDimens.topBarTintDistance], before content reaches the title. The title row itself gets an opaque
 * `solid.topBar` backing at the same time, so text never sits directly on blur (only the status-bar strip shows the
 * progressive glass). Content in any pane registered through [rememberPaneScrollState] counts as scrolled under. In
 * the fallback the fill is the window background plus a `separator` hairline. Under reduced motion the tint switches
 * at the threshold without a fade.
 *
 * @param scrolledUnder how far content has moved under the bar's bottom edge (0 at rest).
 * @param large the large-title variant of top-level screens (`type.largeTitle` at rest, collapsing to `type.title`).
 * @param collapse 0 (large title at rest) .. 1 (collapsed) for the large variant.
 * @param height the bar height of the layout mode when not large (56, 64 or 48 dp).
 */
@Composable
fun ShellTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrolledUnder: Dp = 0.dp,
    large: Boolean = false,
    collapse: Float = 0f,
    height: Dp = NbDimens.topBarCollapsed,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = NbTheme.colors
    val motion = NbTheme.motion
    // Any pane that scrolls on its own (second pane, supporting pane) also counts as content under the bar.
    val paneScroll = LocalPaneScrolls.current?.maxScroll ?: 0
    val under = maxOf(scrolledUnder, with(LocalDensity.current) { paneScroll.toDp() })
    val fraction = (under / NbDimens.topBarTintDistance).coerceIn(0f, 1f)
    val target = if (motion.reduced) (if (fraction > 0f) 1f else 0f) else fraction
    val strength by animateFloatAsState(target, motion.crossfade(), label = "topBarTint")
    // The title row's solid backing follows the scroll offset directly (no fade lag): it is opaque before content
    // can reach the title, so text never sits directly on blur (principles.md, tokens.md 7.1).
    val backing = maxOf(strength, target)
    val barHeight = if (large) lerp(NbDimens.topBarLarge, NbDimens.topBarCollapsed, collapse) else height
    val titleStyle =
        if (large) lerp(NbTheme.typography.largeTitle, NbTheme.typography.title, collapse) else NbTheme.typography.title
    Column(
        modifier
            .fillMaxWidth()
            .shellSurface(ShellMaterial.TopBar, RectangleShape, strength = strength),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.solidTopBar.copy(alpha = backing))
                .sizeIn(minHeight = barHeight)
                .padding(horizontal = NbSpace.s2),
            verticalAlignment = if (large && collapse < 1f) Alignment.Bottom else Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                NbIconButton(
                    NbIcons.Back,
                    contentDescription = stringResource(R.string.a11y_back),
                    onClick = onBack,
                    tint = colors.onGlassPrimary,
                    onGlass = true,
                )
            } else {
                Spacer(Modifier.width(NbSpace.s3))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = NbSpace.s3, vertical = NbSpace.s3),
            ) {
                Text(title, style = titleStyle, color = colors.onGlassPrimary, modifier = Modifier.semantics { heading() })
                subtitle?.let { Text(it, style = NbTheme.typography.bodySmall, color = colors.onGlassSecondary) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        if (strength > 0f && !NbTheme.effects.glass) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator.copy(alpha = strength)),
            )
        }
    }
}

/**
 * Scroll states of panes that scroll on their own beside the main column (two-pane detail, supporting and book
 * panes). [AppScaffold][io.github.alien2003.nightbaseline.ui.shell.AppScaffold] provides one; [ShellTopBar] treats
 * the furthest scrolled pane as content under the bar.
 */
class PaneScrolls {
    private val states = mutableStateListOf<ScrollState>()

    /** The largest scroll offset of the registered panes, px. */
    val maxScroll: Int get() = states.maxOfOrNull { it.value } ?: 0

    internal fun add(state: ScrollState) {
        states += state
    }

    internal fun remove(state: ScrollState) {
        states -= state
    }
}

/** The [PaneScrolls] of the current shell, null outside it. */
val LocalPaneScrolls = staticCompositionLocalOf<PaneScrolls?> { null }

/** A saved [ScrollState] for a pane beside the main column, registered with the shell's [PaneScrolls]. */
@Composable
fun rememberPaneScrollState(): ScrollState {
    val state = rememberScrollState()
    val panes = LocalPaneScrolls.current
    DisposableEffect(panes, state) {
        panes?.add(state)
        onDispose { panes?.remove(state) }
    }
    return state
}

/**
 * The glass bottom sheet (`screens.md` 4): `material.glass.sheet` (solid `solid.sheet` in the fallback), top corners
 * `shape.sheet` 32 dp, a rim, `elevation.overlay`, a drag handle and a labelled close action for TalkBack (and Esc).
 * Content cards inside it are solid `surface.1` with `text.*`; text placed directly on the tint uses `onGlass.*`.
 * Drawn in place (inside the window, so the blur sees the content behind it); [GlassScrim] dims the rest.
 */
@Composable
fun GlassSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NbTheme.colors
    val close = stringResource(R.string.a11y_close)
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.dialogMaxWidthWide)
            .shellSurface(ShellMaterial.Sheet, NbShapes.sheet)
            .semantics {
                paneTitle = title
                dismiss(close) {
                    onClose()
                    true
                }
            }.padding(horizontal = NbSpace.s6),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        // Drag handle: a 32 x 4 dp pill inside a 48 dp target.
        Box(
            Modifier
                .fillMaxWidth()
                .height(NbDimens.targetMin)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(NbDimens.sheetHandleWidth)
                    .height(NbDimens.sheetHandleHeight)
                    .background(colors.onGlassSecondary, NbShapes.pill),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = NbTheme.typography.title,
                color = colors.onGlassPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            NbIconButton(NbIcons.Close, contentDescription = close, onClick = onClose, tint = colors.onGlassPrimary, onGlass = true)
        }
        content()
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.height(NbSpace.s5))
    }
}

/**
 * The centred glass dialog (`screens.md` 4): `material.glass.dialog` (solid in the fallback), `shape.dialog` 32 dp,
 * max 480 dp (560 dp with [wide] for sheet content), title, body text on the tint in `onGlass.*`, and the buttons at
 * the end. Never placed across a fold (the caller positions it).
 */
@Composable
fun GlassDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val colors = NbTheme.colors
    Column(
        modifier
            .widthIn(max = if (wide) NbDimens.dialogMaxWidthWide else NbDimens.dialogMaxWidth)
            .shellSurface(ShellMaterial.Dialog, NbShapes.dialog)
            .semantics { paneTitle = title }
            .padding(NbSpace.s7),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Text(title, style = NbTheme.typography.title, color = colors.onGlassPrimary, modifier = Modifier.semantics { heading() })
        Text(body, style = NbTheme.typography.body, color = colors.onGlassSecondary)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s3, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbTextButton(dismiss, onClick = onDismiss)
            PrimaryButton(confirm, onClick = onConfirm)
        }
    }
}

/** `color.scrim` over the content behind a sheet or dialog; a tap outside closes it (TalkBack uses the close action). */
@Composable
fun BoxScope.GlassScrim(onDismiss: () -> Unit) {
    val close = stringResource(R.string.a11y_close)
    Box(
        Modifier
            .matchParentSize()
            .background(NbTheme.colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .semantics { contentDescription = close },
    )
}

/** Hosts [content] above a scrim in a full-size box (sheets at the bottom, dialogs centred). */
@Composable
fun OverlayHost(
    onDismiss: () -> Unit,
    alignment: Alignment,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = alignment) {
        GlassScrim(onDismiss)
        content()
    }
}
