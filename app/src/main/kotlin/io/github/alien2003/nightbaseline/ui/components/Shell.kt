package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.material.extendBottom
import io.github.alien2003.nightbaseline.ui.material.fadeOutBottom
import io.github.alien2003.nightbaseline.ui.material.rememberShellPressState
import io.github.alien2003.nightbaseline.ui.material.shellClickable
import io.github.alien2003.nightbaseline.ui.material.shellPressGlow
import io.github.alien2003.nightbaseline.ui.material.shellPressInput
import io.github.alien2003.nightbaseline.ui.material.shellPressScale
import io.github.alien2003.nightbaseline.ui.material.shellRim
import io.github.alien2003.nightbaseline.ui.material.shellSurface
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.ShellShapes
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens
import kotlinx.coroutines.launch

/** `easing.title` (0.33, 0, 0.67, 1): the small toolbar title fading in over scroll. */
@Suppress("MagicNumber") // Curve control points of the token.
private val TitleEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * The top bar (`coloros-conformance.md` C1–C6, measured on ColorOS Settings and Clock). It has **no background and
 * no divider**: a 52 dp toolbar row (48 dp in short windows) below the status bar with solid convex round buttons
 * (back at the start, [actions] at the end) and the title (`type.topBarTitle` 18/24/700) at 66 dp after the back
 * button or 16 dp without it. Once content scrolls under it, a progressive blur (strong under the status bar, none
 * 16 dp below the toolbar) with a darkening gradient fades in over 10 dp of scroll; the solid fallback is a gradient
 * of the window background. Content in any pane registered through [rememberPaneScrollState] counts as scrolled under.
 *
 * With [large] (top-level pages in Compact) the large title (`type.largeTitle`) and the subtitle sit below the
 * toolbar and move up with the content, 1:1 with the scroll, until they have passed under the toolbar; the small
 * title fades into the toolbar over scroll 40 -> 72 dp. Text sizes never animate.
 *
 * @param scroll scroll offset of the page's main column. When null, [scrolledUnder] is used.
 * @param scrolledUnder how far content has moved under the bar's bottom edge (0 at rest).
 * @param height the toolbar height of the layout mode; values below 52 dp give the short toolbar.
 * @param scrollOffset the scroll offset as a function, read only where it is used (the small title's layer, the large
 *   title's layout, the derived material target), so scrolling never recomposes the bar; takes precedence over
 *   [scroll] and [scrolledUnder].
 * @param onToolbarHeight receives the measured height of the toolbar row (it grows with the font scale), so a list
 *   can start right below it.
 * @param column the page's card column: the leading element (title or back button) starts at its start edge instead
 *   of the 16 dp toolbar margin (wide single-pane and two-pane pages, review round 4 container rule).
 */
@Suppress("CyclomaticComplexMethod") // Derives the scroll-driven states of tokens C4 / C5 in one place.
@Composable
fun ShellTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrolledUnder: Dp = 0.dp,
    large: Boolean = false,
    height: Dp = NbDimens.topBarCollapsed,
    onBack: (() -> Unit)? = null,
    scroll: Dp? = null,
    scrollOffset: (() -> Dp)? = null,
    column: TitleColumn? = null,
    onToolbarHeight: (Dp) -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    val motion = NbTheme.motion
    val density = LocalDensity.current
    val total: () -> Dp = scrollOffset ?: { scroll ?: scrolledUnder }
    val blockHeight = remember { mutableStateOf(0.dp) }
    val panes = LocalPaneScrolls.current
    // Only the material's target is derived in composition; it rests at 0 or 1 outside a 10 dp window of scroll.
    val target by remember(total, large, motion.reduced, panes, density) {
        derivedStateOf {
            val scrolled = total()
            val ownUnder = if (large) (scrolled - blockHeight.value).coerceAtLeast(0.dp) else scrolled
            // Any pane that scrolls on its own (second pane, supporting pane) also counts as content under the bar.
            val paneScroll = with(density) { (panes?.maxScroll ?: 0).toDp() }
            val fraction = (maxOf(ownUnder, paneScroll) / ShellDimens.topBarTintDistance).coerceIn(0f, 1f)
            if (motion.reduced) (if (fraction > 0f) 1f else 0f) else fraction
        }
    }
    val strength by animateFloatAsState(target, motion.crossfade(), label = "topBarMaterial")
    val toolbar = if (height < ShellDimens.toolbar) height else ShellDimens.toolbar
    // Read in the title's layer only: the small title fades with the scroll without recomposing the bar.
    val smallTitle: () -> Float = {
        when {
            !large -> {
                1f
            }

            motion.reduced -> {
                if (total() >= (ShellDimens.smallTitleFadeStart + ShellDimens.smallTitleFadeEnd) / 2) 1f else 0f
            }

            else -> {
                val fade = ShellDimens.smallTitleFadeEnd - ShellDimens.smallTitleFadeStart
                TitleEasing.transform(((total() - ShellDimens.smallTitleFadeStart) / fade).coerceIn(0f, 1f))
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        Box {
            // The material: blur + darkening (glass) or a background gradient (solid), fading out 16 dp below.
            Box(
                Modifier
                    .matchParentSize()
                    .extendBottom(ShellDimens.topBarFade)
                    .fadeOutBottom(ShellDimens.topBarFade)
                    .shellSurface(ShellMaterial.TopBar, RectangleShape, strength = strength),
            )
            Column(Modifier.alignStartToColumn(column)) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(
                    Modifier
                        .fillMaxWidth()
                        // The actions stay clear of a 3-button navigation bar or a cutout at the end side (landscape).
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                        .heightIn(min = toolbar)
                        .onSizeChanged { onToolbarHeight(with(density) { it.height.toDp() }) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        Spacer(Modifier.width(ShellDimens.toolbarMargin - TargetInset))
                        ShellRoundButton(NbIcons.ArrowBack, contentDescription = stringResource(R.string.a11y_back), onClick = onBack)
                        Spacer(Modifier.width(ShellDimens.toolbarTitleGap - TargetInset))
                    } else {
                        Spacer(Modifier.width(ShellDimens.toolbarMargin))
                    }
                    ToolbarTitle(title, if (large) null else subtitle, smallTitle, large)
                    Row(
                        Modifier.padding(end = ShellDimens.toolbarMargin - TargetInset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { actions() }
                }
            }
        }
        if (large) {
            LargeTitleBlock(title, subtitle, total, Modifier.alignStartToColumn(column), onHeight = { blockHeight.value = it })
        }
    }
}

/** Test tag of the page title in the top bar (the large title where there is one). */
const val TOP_BAR_TITLE_TAG: String = "top-bar-title"

/** Half the difference between the 48 dp target and the 40 dp round button. */
private val TargetInset: Dp = (NbDimens.targetMin - ShellDimens.roundButton) / 2

@Composable
private fun RowScope.ToolbarTitle(
    title: String,
    subtitle: String?,
    alpha: () -> Float,
    large: Boolean,
) {
    val colors = NbTheme.colors
    val type = ShellTokens.type
    // At a large font the title fills the toolbar: it keeps a gap above and below, so the bar grows instead of the
    // collapsed title touching the content under it. At font 1.0 the 52 dp toolbar is unchanged.
    val vertical = if (LocalDensity.current.fontScale > 1f) ShellDimens.toolbarTitleLargeFontGap else NbDimens.focusRingWidth
    Column(
        Modifier
            .weight(1f)
            .padding(vertical = vertical)
            .graphicsLayer { this.alpha = alpha() }
            // On large-title pages the large title is the heading; the toolbar copy is decorative.
            .then(if (large) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        NbText(
            title,
            style = type.topBarTitle,
            color = colors.onGlassPrimary,
            modifier = Modifier.testTag(TOP_BAR_TITLE_TAG).semantics { heading() },
        )
        subtitle?.let { NbText(it, style = type.subtitleSmall, color = colors.textTertiary) }
    }
}

/**
 * The large title and its subtitle, laid out like the first scrolling item: it shrinks by [scroll] from the top and
 * its content moves up with it, clipped at the toolbar's bottom edge.
 */
@Composable
private fun LargeTitleBlock(
    title: String,
    subtitle: String?,
    scroll: () -> Dp,
    modifier: Modifier,
    onHeight: (Dp) -> Unit,
) {
    val colors = NbTheme.colors
    val density = LocalDensity.current
    Layout(
        content = {
            Column(
                Modifier
                    .padding(start = ShellDimens.toolbarMargin, end = ShellDimens.toolbarMargin, bottom = ShellDimens.largeTitleBottom)
                    .onSizeChanged { onHeight(with(density) { it.height.toDp() }) },
                verticalArrangement = Arrangement.spacedBy(NbDimens.focusRingWidth),
            ) {
                NbText(
                    title,
                    style = NbTheme.typography.largeTitle,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag(TOP_BAR_TITLE_TAG).semantics { heading() },
                )
                subtitle?.let { NbText(it, style = ShellTokens.type.subtitleSmall, color = colors.textTertiary) }
            }
        },
        modifier = modifier.fillMaxWidth().clipToBounds(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val shift = scroll().roundToPx().coerceIn(0, placeable.height)
        layout(constraints.maxWidth, placeable.height - shift) { placeable.place(0, -shift) }
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
 * The bottom sheet (`coloros-conformance.md` G1–G3, COUI panel-like), drawn inside a [ModalHost]: the sheet material
 * (blur with a near-opaque tint, solid in the fallback, a top highlight only, soft overlay shadow), top corners
 * `ShellDimens.sheetRadius`, the ColorOS header (a 36 x 4 dp handle, a centred `type.dialogTitle`, a round close button
 * at the end) and 16 dp side padding. Its position is the host's single progress ([modalPanel]): it slides up on
 * `spring.sheet`, follows a drag on the header (never above its resting top) and content pulled down past its top
 * (nested scroll), and on release returns on `spring.sheetReturn` or, past half its height or on a downward fling,
 * leaves on `spring.sheetHide` carrying the fling's velocity. Cards inside it are solid; text placed directly on the
 * tint uses `onGlass.*`. TalkBack and Esc use the labelled close action.
 */
@Composable
fun ModalScope.GlassSheet(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NbTheme.colors
    val shell = ShellTokens.colors
    val density = LocalDensity.current
    val close = stringResource(R.string.a11y_close)
    val modal = state
    val drag = rememberDraggableState { delta -> modal.dragBy(delta) }
    val nested = remember(modal, density) { modal.nestedScroll(density) }
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = ShellDimens.sheetTopGap)
            .fillMaxWidth()
            .widthIn(max = NbDimens.dialogMaxWidthWide)
            .modalPanel(modal)
            .shellSurface(ShellMaterial.Sheet, ShellShapes.sheet)
            .nestedScroll(nested)
            .semantics {
                paneTitle = title
                dismiss(close) {
                    modal.dismiss()
                    true
                }
            },
    ) {
        Column(
            Modifier.draggable(
                drag,
                Orientation.Vertical,
                enabled = !modal.reduced && modal.target,
                onDragStopped = { velocity -> modal.settle(velocity, density) },
            ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = ShellDimens.handleTop, bottom = ShellDimens.handleBottom)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(ShellDimens.handleWidth)
                        .height(ShellDimens.handleHeight)
                        .background(shell.sheetHandle, NbShapes.pill),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ShellDimens.sheetHeader)
                    .padding(horizontal = ShellDimens.toolbarMargin - TargetInset),
                contentAlignment = Alignment.Center,
            ) {
                NbText(
                    title,
                    style = ShellTokens.type.dialogTitle,
                    color = colors.onGlassPrimary,
                    modifier =
                        Modifier
                            .padding(horizontal = NbDimens.targetMin + NbDimens.targetSpacing)
                            .semantics { heading() },
                )
                ShellRoundButton(
                    NbIcons.Close,
                    contentDescription = close,
                    onClick = { modal.dismiss() },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Column(
            Modifier.padding(horizontal = ShellDimens.toolbarMargin),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            Spacer(Modifier.height(NbDimens.focusRingWidth))
            content()
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(ShellDimens.toolbarMargin))
        }
    }
}

/**
 * The dialog (`coloros-conformance.md` H1–H2, COUI-like): the dialog material (blur with a near-opaque tint, solid in
 * the fallback, no rim), 30 dp corners, at most 392 dp wide, 24 dp padding, a centred `type.dialogTitle`, a centred
 * message in `text.secondary` 6 dp below, and 22 dp lower two equal 44 dp pills (48 dp targets) 8 dp apart: the
 * neutral dismiss pill and the accent confirm pill (gradient, top gloss, accent glow; also when the action is
 * destructive, the wording carries the consequence). Drawn inside a [ModalHost]: on phones the caller docks it at
 * the bottom ([ModalKind.DockedDialog]: 16 dp margins, slides up with a fade); on wider windows it is centred
 * ([ModalKind.Dialog]: scale 0.8 -> 1 with a fade), both from the host's single progress. Under reduced motion it
 * only fades. Never placed across a fold (the caller positions it).
 *
 * @param wide sheet content in a dialog: up to 560 dp wide.
 */
@Composable
fun ModalScope.GlassDialog(
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
    val type = ShellTokens.type
    Column(
        modifier
            .widthIn(max = if (wide) NbDimens.dialogMaxWidthWide else ShellDimens.dialogMaxWidth)
            .fillMaxWidth()
            .modalPanel(state)
            .shellSurface(ShellMaterial.Dialog, ShellShapes.dialog)
            .semantics { paneTitle = title }
            .padding(
                start = ShellDimens.dialogButtonInset,
                end = ShellDimens.dialogButtonInset,
                top = ShellDimens.dialogPadding,
                bottom = ShellDimens.dialogButtonInset,
            ),
    ) {
        val textPadding = Modifier.fillMaxWidth().padding(horizontal = ShellDimens.dialogPadding - ShellDimens.dialogButtonInset)
        NbText(title, style = type.dialogTitle, color = colors.textPrimary, modifier = textPadding.semantics { heading() })
        Spacer(Modifier.height(ShellDimens.dialogTitleGap))
        NbText(body, style = type.dialogMessage, color = colors.textSecondary, modifier = textPadding)
        Spacer(Modifier.height(ShellDimens.dialogButtonsGap))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NbDimens.targetSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogButton(dismiss, onDismiss, primary = false, modifier = Modifier.weight(1f))
            DialogButton(confirm, onConfirm, primary = true, modifier = Modifier.weight(1f))
        }
    }
}

/** Height share of the top gloss band on an accent pill. */
private const val GLOSS_END = 0.5f

/**
 * A dialog pill: 44 dp visual in a 48 dp target; neutral, or the accent fill (gradient, top gloss, accent glow) with
 * a white label at >= 4.5:1. Local until WP3's buttons land (the sweep switches them).
 */
@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    val shell = ShellTokens.colors
    val colors = NbTheme.colors
    val press = rememberShellPressState()
    val inset = (NbDimens.targetMin - ShellDimens.dialogButton) / 2
    Box(
        modifier
            .heightIn(min = NbDimens.targetMin)
            .shellPressInput(press)
            .shellPressScale(press)
            .shellClickable(onClick = onClick, shape = NbShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        val fill =
            if (primary) {
                Modifier
                    .dropShadow(NbShapes.pill, Shadow(radius = 12.dp, color = shell.primaryGlow, offset = DpOffset(0.dp, 4.dp)))
                    .background(Brush.verticalGradient(listOf(shell.primaryFillTop, shell.primaryFillBottom)), NbShapes.pill)
                    .background(
                        Brush.verticalGradient(0f to shell.primaryGloss, GLOSS_END to Color.Transparent),
                        NbShapes.pill,
                    ).shellRim(shell.primaryGloss, 0.5.dp, Color.Transparent, 0.dp, NbShapes.pill)
            } else {
                Modifier.background(shell.secondaryFill, NbShapes.pill)
            }
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = inset)
                .then(fill)
                .shellPressGlow(press, NbShapes.pill),
        )
        NbText(
            text,
            style = ShellTokens.type.button,
            color = if (primary) Color.White else colors.textPrimary,
            modifier = Modifier.padding(horizontal = ShellDimens.toolbarMargin, vertical = inset + NbDimens.targetSpacing),
        )
    }
}
