package io.github.alien2003.nightbaseline.ui.material

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.motion.NB_PRESS_SCALE_LARGE
import io.github.alien2003.nightbaseline.ui.motion.NB_PRESS_SCALE_SMALL
import io.github.alien2003.nightbaseline.ui.motion.NbSprings
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Retired `state.hover` alpha of `text.primary` (kept for the callers until the sweep); hover is 2/3 of `state.pressed`. */
const val STATE_HOVER_ALPHA: Float = 0.08f

/** Retired `state.pressed` alpha over content (kept for the callers until the sweep); see `color.state.pressed`. */
const val STATE_PRESSED_ALPHA: Float = 0.12f

/** `state.dragged` alpha. */
const val STATE_DRAGGED_ALPHA: Float = 0.16f

/** Hover highlight strength relative to the pressed highlight. */
private const val HOVER_FRACTION: Float = 2f / 3f

/** Forced interaction looks for the component catalog (hover, pressed, focus shown without a pointer or keyboard). */
enum class ForcedInteraction {
    /** Hover highlight. */
    Hover,

    /** Pressed highlight (and the glow on glass). */
    Pressed,

    /** Keyboard focus ring. */
    Focus,
}

/**
 * Press scale for an element of [size] (`tokens.md` 9.5): 0.96 for small controls (side <= 40 dp) up to 0.98 for
 * large or wide ones (side >= 150 dp, or aspect >= 4, such as the bar and full-width buttons). Never above 1.0.
 */
fun nbPressScale(
    size: IntSize,
    density: Density,
): Float {
    if (size.width <= 0 || size.height <= 0) return NB_PRESS_SCALE_SMALL
    val side = with(density) { sqrt(size.width.toFloat() * size.height).toDp() }
    val bySize = ((side - SMALL_SIDE) / (LARGE_SIDE - SMALL_SIDE)).coerceIn(0f, 1f)
    val aspect = max(size.width, size.height).toFloat() / min(size.width, size.height)
    val byAspect = ((aspect - 1f) / (WIDE_ASPECT - 1f)).coerceIn(0f, 1f)
    return NB_PRESS_SCALE_SMALL + (NB_PRESS_SCALE_LARGE - NB_PRESS_SCALE_SMALL) * max(bySize, byAspect)
}

private val SMALL_SIDE = 40.dp
private val LARGE_SIDE = 150.dp
private const val WIDE_ASPECT = 4f
private const val WIDE_PRESS_THRESHOLD = 0.97f

/**
 * Clickable with NightBaseline's interaction tokens (`tokens.md` 2.8, 9.5, 10), the ColorOS press language:
 *
 * - press shrink to 0.96-0.98 by size ([nbPressScale]) on `spring.pressIn`, released on `spring.pressOut` with the
 *   press velocity carried over and the scale clamped at 1.0 (none under reduced motion);
 * - a pressed highlight (`color.state.pressed`) drawn **under** the content, clipped to [shape], so labels keep
 *   their colour; hover shows two thirds of it; rows ([pressScale] false) use the slower row springs;
 * - on glass ([onGlass]) or with [glow]: a touch-following light (`color.glow.neutral`, radius up to 140 dp,
 *   clipped to the element) that appears in 110 ms, follows the finger and fades in 480 ms, instead of the grey
 *   highlight;
 * - a 2 dp `focus.ring` 2 dp outside [shape], only for keyboard / D-pad focus, and the hand pointer on hover.
 *
 * The caller keeps the element at least 48 x 48 dp (`target.min`).
 *
 * @param onGlass the element sits on a glass surface (glow instead of the grey highlight, brighter rim on hover).
 * @param pressScale false for rows inside a grouped card: they only light up.
 * @param glow touch-following light also on a solid element (round shell buttons, period thumb, primary button).
 * @param forced draws a state without real input (component catalog only).
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One modifier composing all interaction tokens of tokens.md.
@Composable
fun Modifier.nbClickable(
    onClick: () -> Unit,
    shape: Shape,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    onGlass: Boolean = false,
    pressScale: Boolean = true,
    forced: ForcedInteraction? = null,
    interactionSource: MutableInteractionSource? = null,
    glow: Boolean = onGlass,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val localDensity = LocalDensity.current
    val motion = NbTheme.motion
    val colors = NbTheme.colors
    val isPressed = pressed || forced == ForcedInteraction.Pressed
    val isHovered = hovered || forced == ForcedInteraction.Hover
    val showRing = (focused && keyboard) || forced == ForcedInteraction.Focus
    var elementSize by remember { mutableStateOf(IntSize.Zero) }
    val target = if (pressed && pressScale) nbPressScale(elementSize, localDensity) else 1f
    val wide = target >= WIDE_PRESS_THRESHOLD
    val scaleSpec = if (pressed) motion.pressIn<Float>(wide) else motion.pressOut()
    val scale by animateFloatAsState(
        targetValue = if (scaleSpec == null) 1f else target,
        animationSpec = scaleSpec ?: snap(),
        label = "pressScale",
    )
    val highlightTarget =
        when {
            glow -> 0f
            isPressed -> 1f
            isHovered -> HOVER_FRACTION
            else -> 0f
        }
    val highlight by animateFloatAsState(
        targetValue = highlightTarget,
        animationSpec = if (highlightTarget > 0f) motion.stateLayer(row = !pressScale) else motion.stateLayerOut(row = !pressScale),
        label = "highlight",
    )
    var glowCenter by remember { mutableStateOf<Offset?>(null) }
    val glowLevel = remember { Animatable(0f) }
    LaunchedEffect(source, glow) {
        if (!glow) return@LaunchedEffect
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    glowCenter = interaction.pressPosition
                    val spec = motion.glowIn<Float>()
                    // Reduced motion: no spread animation, the light is simply there while pressed.
                    launch { if (spec != null) glowLevel.animateTo(1f, spec) else glowLevel.snapTo(1f) }
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    launch { glowLevel.animateTo(0f, motion.glowOut()) }
                }
            }
        }
    }
    val ringColor = colors.focusRing
    val highlightColor = colors.statePressed
    val glowColor = colors.glowNeutral
    val rimHover = colors.glassRimTop
    val forcedGlow = forced == ForcedInteraction.Pressed && glow
    return this
        .onSizeChanged { elementSize = it }
        .graphicsLayer {
            val s = scale.coerceAtMost(1f)
            scaleX = s
            scaleY = s
        }.then(
            if (glow) {
                Modifier.pointerInput(Unit) {
                    // The light follows the finger; events are only observed, never consumed.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes
                                .firstOrNull()
                                ?.takeIf { it.pressed }
                                ?.let { glowCenter = it.position }
                        }
                    }
                }
            } else {
                Modifier
            },
        ).drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val glowAlpha = if (forcedGlow) 1f else glowLevel.value
            if (glow && glowAlpha > 0f) {
                val center = if (forcedGlow) Offset(size.width / 2f, size.height / 2f) else glowCenter ?: center
                val radius = min(NbDimens.glowRadius.toPx(), max(size.width, size.height))
                drawOutline(
                    outline,
                    Brush.radialGradient(listOf(glowColor, glowColor.copy(alpha = 0f)), center = center, radius = radius),
                    alpha = glowAlpha,
                )
            }
            if (highlight > 0f) drawOutline(outline, highlightColor, alpha = highlight)
            if (onGlass && isHovered) {
                // state.glassHover: a brighter rim instead of a grey layer.
                drawOutline(outline, rimHover, style = Stroke(width = 1.5f * density), alpha = 1f)
            }
            drawContent()
            if (showRing) {
                val inset = (NbDimens.focusRingOffset + NbDimens.focusRingWidth / 2).toPx()
                val ringSize = Size(size.width + 2 * inset, size.height + 2 * inset)
                val ring = shape.createOutline(ringSize, layoutDirection, this)
                translate(-inset, -inset) {
                    drawOutline(ring, ringColor, style = Stroke(width = NbDimens.focusRingWidth.toPx()))
                }
            }
        }.pointerHoverIcon(PointerIcon.Hand)
        .hoverable(source, enabled)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/**
 * The app-wide default indication (`LocalIndication`, `tokens.md` 2.8): ColorOS has no ripple. A `clickable` that
 * does not use [nbClickable] gets the pressed highlight (`color.state.pressed`, under the content that follows it in
 * the modifier chain, over the bounds) and the hover highlight; nothing else.
 */
class NbPressIndication(
    private val color: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = Node(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is NbPressIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()

    private class Node(
        private val source: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(),
        DrawModifierNode {
        private val level = Animatable(0f)
        private var presses = 0
        private var hovers = 0

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses++
                        is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                        is HoverInteraction.Enter -> hovers++
                        is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                    }
                    val target =
                        when {
                            presses > 0 -> 1f
                            hovers > 0 -> HOVER_FRACTION
                            else -> 0f
                        }
                    launch {
                        val spec =
                            if (target > level.value) {
                                NbSprings.HighlightIn
                            } else {
                                NbSprings.HighlightOut
                            }
                        level.animateTo(target, spec.spec()) { invalidateDraw() }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            if (level.value > 0f) drawRect(color, alpha = level.value)
            drawContent()
        }
    }
}
