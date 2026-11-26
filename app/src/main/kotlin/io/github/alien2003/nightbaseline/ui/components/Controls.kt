package io.github.alien2003.nightbaseline.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** `spring.selection` of the conformance plan: critically damped, 247 (tab underline). */
private const val SELECTION_STIFFNESS = 247f

/** `spring.switch`: the knob overshoots a little (0.7 / 247). */
private const val SWITCH_DAMPING = 0.7f

/** The knob stretches to x1.3 of its width while it travels. */
private const val SWITCH_STRETCH = 0.3f

/** Peak of the stretch along the travel (34.7 %). */
private const val SWITCH_STRETCH_PEAK = 0.347f

/** Disabled switch and radio draw at this alpha (the position still carries the state). */
private const val DISABLED_ALPHA = 0.3f
private const val DISABLED_KNOB_ALPHA = 0.6f

/** Radio fill: 300 ms with a 1.08 pulse. */
private const val RADIO_FILL_MS = 300
private const val RADIO_PULSE_MS = 200
private const val RADIO_PULSE = 1.08f

/**
 * A ColorOS-style switch (`coloros-conformance.md` L1): a 44 x 24 dp pill, an 18 dp white knob 3 dp inside, no
 * outline; the track is the accent when on and grey when off. The knob moves on `spring.switch` (0.7 / 247) and
 * stretches toward its destination mid-travel; under reduced motion it snaps. Purely visual: the containing row is the
 * touch target and carries the switch semantics (`Role.Switch`, on / off), so this node is cleared.
 */
@Composable
fun NbSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val controls = NbTheme.controls
    val reduced = NbTheme.motion.reduced
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = SWITCH_DAMPING, stiffness = SELECTION_STIFFNESS),
        label = "switchKnob",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .size(NbControlDimens.switchWidth, NbControlDimens.switchHeight)
            .clearAndSetSemantics {},
    ) {
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        val fraction = position.coerceIn(0f, 1f)
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(lerp(controls.switchOff, controls.accent, fraction), cornerRadius = radius, alpha = alpha)
        val knob = NbControlDimens.switchKnob.toPx()
        val inset = (size.height - knob) / 2
        val travel = size.width - 2 * inset - knob
        // Stretch peaks early in the travel and is drawn toward the destination.
        val progress = if (checked) position else 1f - position
        val bump = if (progress < SWITCH_STRETCH_PEAK) progress / SWITCH_STRETCH_PEAK else (1f - progress) / (1f - SWITCH_STRETCH_PEAK)
        val stretch = knob * SWITCH_STRETCH * bump.coerceIn(0f, 1f)
        val x = inset + travel * position
        val left = if (checked) x else x - stretch
        val start = if (rtl) size.width - left - knob - stretch else left
        drawRoundRect(
            controls.onFill,
            topLeft = Offset(start.coerceIn(0f, size.width - knob), inset),
            size = Size(knob + stretch, knob),
            cornerRadius = CornerRadius(knob / 2),
            alpha = if (enabled) 1f else DISABLED_KNOB_ALPHA,
        )
    }
}

/**
 * A ColorOS-style radio (`coloros-conformance.md` L2): a 24 dp box; unchecked a 20 dp ring of 1.3 dp in [radioRing]
 * (3:1 on the card), checked a filled accent disc with a white centre dot that grows in over 300 ms with a 1.08 pulse
 * (`easing.flow`; snaps under reduced motion). Always placed trailing in its row; the row carries the radio semantics.
 */
@Composable
fun NbRadio(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val controls = NbTheme.controls
    val reduced = NbTheme.motion.reduced
    val fill = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected, reduced) {
        val target = if (selected) 1f else 0f
        if (reduced) {
            fill.snapTo(target)
        } else if (selected) {
            fill.animateTo(target, radioFillSpec())
        } else {
            fill.animateTo(target, tween(RADIO_FILL_MS / 2, easing = NbEasing.Exit))
        }
    }
    Canvas(modifier.size(NbControlDimens.radioBox).clearAndSetSemantics {}) {
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        val disc = NbControlDimens.radioDisc.toPx() / 2
        val stroke = NbControlDimens.radioStroke.toPx()
        val f = fill.value
        if (f < 1f) {
            drawCircle(controls.radioRing, radius = disc - stroke / 2, style = Stroke(stroke), alpha = alpha * (1f - min(f, 1f)))
        }
        if (f > 0f) {
            scale(f) {
                drawCircle(controls.accent, radius = disc, alpha = alpha)
                drawCircle(controls.onFill, radius = NbControlDimens.radioDot.toPx() / 2, alpha = alpha)
            }
        }
    }
}

private fun radioFillSpec(): FiniteAnimationSpec<Float> =
    keyframes {
        durationMillis = RADIO_FILL_MS
        RADIO_PULSE at RADIO_PULSE_MS using NbEasing.Flow
        1f at RADIO_FILL_MS
    }

/**
 * The small disclosure chevron of a list row (`coloros-conformance.md` L3): a 12 x 24 dp box with a ~5 x 10 dp glyph
 * in a 1.3 dp stroke, decorative (`color.chevron`), mirrored in RTL.
 */
@Composable
fun NbChevron(
    modifier: Modifier = Modifier,
    color: Color = NbTheme.controls.chevron,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(NbControlDimens.chevronWidth, NbControlDimens.chevronHeight).clearAndSetSemantics {}) {
        val w = 5.dp.toPx()
        val h = 10.dp.toPx()
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        val path =
            Path().apply {
                if (rtl) {
                    moveTo(left + w, top)
                    lineTo(left, top + h / 2)
                    lineTo(left + w, top + h)
                } else {
                    moveTo(left, top)
                    lineTo(left + w, top + h / 2)
                    lineTo(left, top + h)
                }
            }
        drawPath(path, color, style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The 1 px hairline between rows of a grouped card (`coloros-conformance.md` S5): [hairline] colour, inset [start]
 * (16 dp, or 16 + leading icon + 16) and [end] (16 dp).
 */
@Composable
fun NbHairline(
    modifier: Modifier = Modifier,
    start: Dp = NbControlDimens.rowPaddingHorizontal,
    end: Dp = NbControlDimens.rowPaddingHorizontal,
) {
    val color = NbTheme.controls.hairline
    val px = with(LocalDensity.current) { max(1f, 0.33.dp.toPx()).toDp() }
    Box(
        modifier
            .padding(start = start, end = end)
            .fillMaxWidth()
            .height(px)
            .background(color),
    )
}

/** Haptic for a switch flip (`coloros-conformance.md` M4): `TOGGLE_ON` / `TOGGLE_OFF` on API 34+, else `CLOCK_TICK`. */
@Composable
fun rememberToggleHaptic(): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { on: Boolean ->
            val constant =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            view.performHapticFeedback(constant)
        }
    }
}

/**
 * A ColorOS tab row (`coloros-conformance.md` L6): a 48 dp row with a centred group of text tabs (label + 12 dp each
 * side); the selected label is 500 `text.primary`, the others 400 tertiary; a 2 dp square-ended underline as wide as
 * the selected label, in the label colour (not the accent), slides on `spring.selection` (snaps under reduced motion).
 * Selection is carried by weight, colour and the underline. Each tab is a >= 48 dp `Role.Tab` target.
 */
@Composable
fun NbTabRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = NbTheme.controls
    val reduced = NbTheme.motion.reduced
    val tabs = remember { TabBounds() }
    val target = tabs.bounds[selected]
    val spec: FiniteAnimationSpec<Float> = if (reduced) snap() else spring(stiffness = SELECTION_STIFFNESS)
    val left by animateFloatAsState(target?.first ?: 0f, spec, label = "tabLeft")
    val width by animateFloatAsState(target?.second ?: 0f, spec, label = "tabWidth")
    val indicator = controls.tabSelected
    Row(
        modifier
            .sizeIn(minHeight = NbControlDimens.tabRowHeight)
            .selectableGroup()
            .onGloballyPositioned { tabs.row(it) }
            .drawBehind {
                if (target != null && width > 0f) {
                    val h = NbControlDimens.tabIndicator.toPx()
                    drawRect(indicator, topLeft = Offset(left, size.height - h), size = Size(width, h))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbControlDimens.tabRowHeight)
                    .nbClickable(onClick = { onSelect(i) }, shape = NbShapes.s, role = Role.Tab, pressScale = false)
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = NbControlDimens.tabPadding),
                contentAlignment = Alignment.Center,
            ) {
                NbText(
                    label,
                    style = NbControlType.label.copy(fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal),
                    color = if (isSelected) controls.tabSelected else controls.tabUnselected,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.onGloballyPositioned { tabs.label(i, it) },
                )
            }
        }
    }
}

/** Start and width of every tab label inside the tab row (row coordinates), for the sliding underline. */
private class TabBounds {
    /** Label index -> (left, width) in px. */
    val bounds = mutableStateMapOf<Int, Pair<Float, Float>>()
    private val labels = mutableMapOf<Int, LayoutCoordinates>()
    private var row: LayoutCoordinates? = null

    fun row(coordinates: LayoutCoordinates) {
        row = coordinates
        measure()
    }

    fun label(
        index: Int,
        coordinates: LayoutCoordinates,
    ) {
        labels[index] = coordinates
        measure()
    }

    private fun measure() {
        val parent = row?.takeIf { it.isAttached } ?: return
        labels.filterValues { it.isAttached }.forEach { (i, c) ->
            val box = parent.localBoundingBoxOf(c, clipBounds = false)
            val value = box.left to box.width
            if (bounds[i] != value) bounds[i] = value
        }
    }
}

/**
 * A ColorOS chip tab (`coloros-conformance.md` L7): a 36 dp pill inside a 48 dp target, 16 dp padding, no outline and
 * no check glyph; unselected a quiet neutral fill with a secondary label, selected a brighter fill with a 1 dp rim
 * and a 700 label (weight + fill, never colour alone). Single choice: `Role.RadioButton` with `selected`.
 */
@Composable
fun NbChipTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = NbTheme.controls
    Box(
        modifier
            .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbDimens.targetMin)
            .nbClickable(onClick = onClick, shape = NbShapes.pill, role = Role.RadioButton)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .sizeIn(minHeight = NbControlDimens.chipHeight)
                .background(if (selected) controls.chipSelected else controls.chip, NbShapes.pill)
                .then(if (selected) Modifier.border(1.dp, controls.chipSelectedRim, NbShapes.pill) else Modifier)
                .padding(horizontal = NbControlDimens.chipPadding, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            NbText(
                label,
                style = NbControlType.label.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) controls.chipSelectedLabel else controls.summary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A ColorOS choice row (`coloros-conformance.md` L9, Settings > Display "Light mode / Dark mode"): equal columns,
 * each an illustration ([art], decorative), a 14 sp label and an [NbRadio] below it, split by 1 px vertical hairlines;
 * the selected label is the accent at 500 (with the filled radio, never colour alone). Each column is one >= 48 dp
 * radio target. When the labels do not fit (large font, narrow window) it falls back to trailing-radio rows.
 */
@Composable
fun NbChoiceRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    art: @Composable (index: Int) -> Unit = {},
) {
    val controls = NbTheme.controls
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perColumn = maxWidth / labels.size.coerceAtLeast(1)
        if (perColumn < CHOICE_COLUMN_MIN * density.fontScale || density.fontScale >= CHOICE_MAX_FONT) {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                labels.forEachIndexed { i, label ->
                    SettingsRow(label, onClick = { onSelect(i) }, trailing = SettingsTrailing.Radio(i == selected), showDivider = i > 0)
                }
            }
            return@BoxWithConstraints
        }
        val hairline = with(density) { 1f.toDp() }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            labels.forEachIndexed { i, label ->
                if (i > 0) {
                    Box(
                        Modifier
                            .padding(vertical = NbControlDimens.rowPaddingHorizontal)
                            .width(hairline)
                            .fillMaxHeight()
                            .background(controls.hairline),
                    )
                }
                val isSelected = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .sizeIn(minHeight = NbDimens.targetMin)
                        .nbClickable(onClick = { onSelect(i) }, shape = RectangleShape, role = Role.RadioButton, pressScale = false)
                        .semantics(mergeDescendants = true) { this.selected = isSelected }
                        .padding(vertical = NbControlDimens.rowPaddingHorizontal, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.clearAndSetSemantics {}) { art(i) }
                    NbText(
                        label,
                        style = NbControlType.label.copy(fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal),
                        color = if (isSelected) controls.accentText else controls.title,
                        textAlign = TextAlign.Center,
                    )
                    NbRadio(isSelected)
                }
            }
        }
    }
}

/** A choice column needs this much width per 1.0 font scale. */
private val CHOICE_COLUMN_MIN = 96.dp

/** From this font scale the choice row always uses rows. */
private const val CHOICE_MAX_FONT = 1.5f
