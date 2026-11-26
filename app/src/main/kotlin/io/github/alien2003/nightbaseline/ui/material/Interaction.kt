package io.github.alien2003.nightbaseline.ui.material

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateLayer
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.motion.NB_PRESS_SCALE_LARGE
import io.github.alien2003.nightbaseline.ui.motion.NB_PRESS_SCALE_SMALL
import io.github.alien2003.nightbaseline.ui.motion.NbMotionScheme
import io.github.alien2003.nightbaseline.ui.motion.NbSprings
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
 * Performance (one of these sits on every list row): the feedback lives in two `Modifier.Node`s that read no state
 * in composition, so a press, hover or focus change only redraws (or updates the scale layer) and never recomposes
 * the row. Their animations are created on the first interaction, so idle rows hold no `Animatable` and run nothing
 * but the interaction collector; elements that do not shrink get no layer.
 *
 * The caller keeps the element at least 48 x 48 dp (`target.min`).
 *
 * @param onGlass the element sits on a glass surface (glow instead of the grey highlight, brighter rim on hover).
 * @param pressScale false for rows inside a grouped card: they only light up.
 * @param glow touch-following light also on a solid element (round shell buttons, period thumb, primary button).
 * @param forced draws a state without real input (component catalog only).
 */
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
    val motion = NbTheme.motion
    val colors = NbTheme.colors
    val look =
        PressLook(
            highlight = colors.statePressed,
            glow = colors.glowNeutral,
            rimHover = colors.glassRimTop,
            ring = colors.focusRing,
        )
    val feedback =
        PressFeedbackElement(
            source = source,
            shape = shape,
            look = look,
            motion = motion,
            onGlass = onGlass,
            row = !pressScale,
            glow = glow,
            forced = forced,
        )
    return this
        .then(if (pressScale && !motion.reduced) PressScaleElement(source, motion) else Modifier)
        .then(feedback)
        .pointerHoverIcon(PointerIcon.Hand)
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

/** The colours of [nbClickable]'s feedback. */
private data class PressLook(
    val highlight: Color,
    val glow: Color,
    val rimHover: Color,
    val ring: Color,
)

/**
 * Counts the press animations [nbClickable] and [NbPressIndication] have created, so a test can check that idle
 * elements (rows that are only scrolled past, never touched) create none.
 */
@VisibleForTesting
internal object PressAnimationProbe {
    private val created = AtomicInteger()

    /** Press, highlight and glow animations created since the process started. */
    val animationsCreated: Int get() = created.get()

    fun onCreated() {
        created.incrementAndGet()
    }
}

private fun newPressAnimatable(initial: Float): Animatable<Float, AnimationVector1D> {
    PressAnimationProbe.onCreated()
    return Animatable(initial)
}

/** Counts the presses of one interaction source (several fingers may press at once). */
private class PressCounter {
    var presses = 0
        private set

    /** Applies [interaction]; true when the pressed state changed. */
    fun apply(interaction: Interaction): Boolean {
        val before = presses > 0
        when (interaction) {
            is PressInteraction.Press -> presses++
            is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
            else -> return false
        }
        return before != presses > 0
    }

    fun reset() {
        presses = 0
    }
}

private data class PressScaleElement(
    val source: InteractionSource,
    val motion: NbMotionScheme,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(source, motion)

    override fun update(node: PressScaleNode) = node.update(source, motion)

    override fun InspectorInfo.inspectableProperties() {
        name = "nbPressScale"
    }
}

/**
 * The press shrink: places the element in a layer whose scale follows a spring that exists only from the first press
 * on. The layer block reads the animated value, so a running press only updates the layer (no relayout, no
 * recomposition).
 */
private class PressScaleNode(
    private var source: InteractionSource,
    private var motion: NbMotionScheme,
) : Modifier.Node(),
    LayoutModifierNode {
    private var scale: Animatable<Float, AnimationVector1D>? = null
    private val presses = PressCounter()
    private var collector: Job? = null
    private var size = IntSize.Zero
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val s = (scale?.value ?: 1f).coerceAtMost(1f)
        scaleX = s
        scaleY = s
    }

    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() {
        collect()
    }

    override fun onDetach() {
        presses.reset()
        collector = null
        if (scale != null) {
            // A recycled row must not keep a half-finished shrink.
            scale = null
            invalidateLayer()
        }
    }

    fun update(
        source: InteractionSource,
        motion: NbMotionScheme,
    ) {
        this.motion = motion
        if (source != this.source) {
            this.source = source
            presses.reset()
            collector?.cancel()
            if (isAttached) collect()
        }
    }

    private fun collect() {
        collector =
            coroutineScope.launch {
                source.interactions.collect { interaction ->
                    if (presses.apply(interaction)) animate(pressed = presses.presses > 0)
                }
            }
    }

    private fun animate(pressed: Boolean) {
        val target = if (pressed) nbPressScale(size, requireDensity()) else 1f
        val spec = (if (pressed) motion.pressIn<Float>(target >= WIDE_PRESS_THRESHOLD) else motion.pressOut()) ?: return
        val anim =
            scale ?: newPressAnimatable(1f).also {
                scale = it
                // The layer block has read no state so far; from now on it reads the animation's value.
                invalidateLayer()
            }
        // A new target cancels the running animation and keeps its velocity (the release carries the press over).
        coroutineScope.launch { anim.animateTo(target, spec) }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        size = IntSize(placeable.width, placeable.height)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layerBlock) }
    }
}

private data class PressFeedbackElement(
    val source: InteractionSource,
    val shape: Shape,
    val look: PressLook,
    val motion: NbMotionScheme,
    val onGlass: Boolean,
    val row: Boolean,
    val glow: Boolean,
    val forced: ForcedInteraction?,
) : ModifierNodeElement<PressFeedbackNode>() {
    override fun create(): PressFeedbackNode = PressFeedbackNode(this)

    override fun update(node: PressFeedbackNode) = node.update(this)

    override fun InspectorInfo.inspectableProperties() {
        name = "nbPressFeedback"
    }
}

/**
 * Highlight, glow, glass hover rim and focus ring of [nbClickable], drawn under and over the content. Interactions
 * are collected here (no composition state), animations start on the first interaction, and the glow follows the
 * finger by observing pointer events (never consumed), redrawing only while it is visible.
 */
private class PressFeedbackNode(
    private var spec: PressFeedbackElement,
) : Modifier.Node(),
    DrawModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private val presses = PressCounter()
    private var hovers = 0
    private var focused = false
    private var collector: Job? = null
    private var highlight: Animatable<Float, AnimationVector1D>? = null
    private var highlightTarget = 0f
    private var glowLevel: Animatable<Float, AnimationVector1D>? = null
    private var glowCenter = Offset.Unspecified
    private val glowBrush = GlowBrushCache()
    private val outline = OutlineCache()

    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() {
        collect()
        syncHighlight(animate = false)
    }

    override fun onDetach() {
        presses.reset()
        hovers = 0
        focused = false
        collector = null
        highlight = null
        highlightTarget = 0f
        glowLevel = null
        glowCenter = Offset.Unspecified
    }

    fun update(next: PressFeedbackElement) {
        val previous = spec
        spec = next
        if (next.source != previous.source) {
            presses.reset()
            hovers = 0
            focused = false
            collector?.cancel()
            if (isAttached) collect()
        }
        if (isAttached) syncHighlight(animate = true)
        invalidateDraw()
    }

    private fun collect() {
        collector =
            coroutineScope.launch {
                spec.source.interactions.collect { interaction -> onInteraction(interaction) }
            }
    }

    private fun onInteraction(interaction: Interaction) {
        when (interaction) {
            is PressInteraction -> {
                presses.apply(interaction)
                if (spec.glow) glowFor(interaction)
            }

            is HoverInteraction.Enter -> {
                hovers++
                invalidateDraw()
            }

            is HoverInteraction.Exit -> {
                hovers = (hovers - 1).coerceAtLeast(0)
                invalidateDraw()
            }

            is FocusInteraction.Focus -> {
                focused = true
                invalidateDraw()
            }

            is FocusInteraction.Unfocus -> {
                focused = false
                invalidateDraw()
            }

            else -> {
                return
            }
        }
        syncHighlight(animate = true)
    }

    private val hovered: Boolean get() = hovers > 0 || spec.forced == ForcedInteraction.Hover

    private fun syncHighlight(animate: Boolean) {
        val target =
            when {
                spec.glow -> 0f
                presses.presses > 0 || spec.forced == ForcedInteraction.Pressed -> 1f
                hovered -> HOVER_FRACTION
                else -> 0f
            }
        if (target == highlightTarget) return
        highlightTarget = target
        val anim =
            highlight ?: newPressAnimatable(if (animate) 0f else target).also {
                // A forced look (catalog) is there from the first frame, like animateFloatAsState's initial value.
                highlight = it
                invalidateDraw()
            }
        if (anim.value == target) return
        val animation = if (target > 0f) spec.motion.stateLayer<Float>(spec.row) else spec.motion.stateLayerOut(spec.row)
        coroutineScope.launch { anim.animateTo(target, animation) }
    }

    private fun glowFor(interaction: PressInteraction) {
        when (interaction) {
            is PressInteraction.Press -> {
                glowCenter = interaction.pressPosition
                val anim =
                    glowLevel ?: newPressAnimatable(0f).also {
                        glowLevel = it
                        invalidateDraw()
                    }
                val animation = spec.motion.glowIn<Float>()
                // Reduced motion: no spread animation, the light is simply there while pressed.
                coroutineScope.launch { if (animation != null) anim.animateTo(1f, animation) else anim.snapTo(1f) }
            }

            is PressInteraction.Release, is PressInteraction.Cancel -> {
                val anim = glowLevel ?: return
                coroutineScope.launch { anim.animateTo(0f, spec.motion.glowOut()) }
            }
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (!spec.glow || pass != PointerEventPass.Initial) return
        // The light follows the finger; events are only observed, never consumed.
        val change = pointerEvent.changes.firstOrNull()?.takeIf { it.pressed } ?: return
        glowCenter = change.position
        if ((glowLevel?.value ?: 0f) > 0f) invalidateDraw()
    }

    override fun onCancelPointerInput() = Unit

    override fun ContentDrawScope.draw() {
        drawUnderContent()
        drawContent()
        val keyboard = focused && currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard
        if (keyboard || spec.forced == ForcedInteraction.Focus) drawFocusRing(spec.shape, spec.look.ring)
    }

    private fun glowAlpha(forced: Boolean): Float =
        when {
            !spec.glow -> 0f
            forced -> 1f
            else -> glowLevel?.value ?: 0f
        }

    /** Glow, highlight and glass hover rim, under the content; nothing at all while the element is idle. */
    private fun ContentDrawScope.drawUnderContent() {
        val s = spec
        val forcedGlow = s.forced == ForcedInteraction.Pressed && s.glow
        val glowAlpha = glowAlpha(forcedGlow)
        val level = highlight?.value ?: 0f
        val rim = s.onGlass && hovered
        if (glowAlpha <= 0f && level <= 0f && !rim) return
        val shapeOutline = outline.get(s.shape, size, layoutDirection, this)
        if (glowAlpha > 0f) {
            val c = if (forcedGlow || glowCenter == Offset.Unspecified) center else glowCenter
            val radius = min(NbDimens.glowRadius.toPx(), max(size.width, size.height))
            drawOutline(shapeOutline, glowBrush.get(s.look.glow, c, radius), alpha = glowAlpha)
        }
        if (level > 0f) drawOutline(shapeOutline, s.look.highlight, alpha = level)
        if (rim) {
            // state.glassHover: a brighter rim instead of a grey layer.
            drawOutline(shapeOutline, s.look.rimHover, style = Stroke(width = 1.5f * density), alpha = 1f)
        }
    }
}

private fun DrawScope.drawFocusRing(
    shape: Shape,
    color: Color,
) {
    val inset = (NbDimens.focusRingOffset + NbDimens.focusRingWidth / 2).toPx()
    val ringSize = Size(size.width + 2 * inset, size.height + 2 * inset)
    val ring = shape.createOutline(ringSize, layoutDirection, this)
    translate(-inset, -inset) {
        drawOutline(ring, color, style = Stroke(width = NbDimens.focusRingWidth.toPx()))
    }
}

/**
 * The radial light of a press, reused while its colour, centre and radius stay the same: a brush keeps its shader for
 * one size, so a fading glow draws without creating a gradient and a shader per frame.
 */
internal class GlowBrushCache {
    private var color = Color.Unspecified
    private var center = Offset.Unspecified
    private var radius = Float.NaN
    private var brush: Brush? = null

    fun get(
        color: Color,
        center: Offset,
        radius: Float,
    ): Brush {
        val cached = brush
        val same = color == this.color && center == this.center && radius == this.radius
        if (cached != null && same) return cached
        this.color = color
        this.center = center
        this.radius = radius
        return Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius).also { brush = it }
    }
}

/** The outline of a shape, recreated only when the shape, the size, the direction or the density change. */
internal class OutlineCache {
    private var shape: Shape? = null
    private var size = Size.Unspecified
    private var direction: LayoutDirection? = null
    private var density = Float.NaN
    private var outline: Outline? = null

    fun get(
        shape: Shape,
        size: Size,
        direction: LayoutDirection,
        density: Density,
    ): Outline {
        val cached = outline
        val sameGeometry = shape == this.shape && size == this.size && direction == this.direction
        if (cached != null && sameGeometry && density.density == this.density) return cached
        this.shape = shape
        this.size = size
        this.direction = direction
        this.density = density.density
        return shape.createOutline(size, direction, density).also { outline = it }
    }
}

/**
 * The app-wide default indication (`LocalIndication`, `tokens.md` 2.8): ColorOS has no ripple. A `clickable` that
 * does not use [nbClickable] gets the pressed highlight (`color.state.pressed`, under the content that follows it in
 * the modifier chain, over the bounds) and the hover highlight; nothing else. The animation is created on the first
 * interaction, so an idle element holds only its interaction collector.
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
        private var level: Animatable<Float, AnimationVector1D>? = null
        private var target = 0f
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
                        else -> return@collect
                    }
                    val next =
                        when {
                            presses > 0 -> 1f
                            hovers > 0 -> HOVER_FRACTION
                            else -> 0f
                        }
                    if (next == target) return@collect
                    target = next
                    val anim = level ?: newPressAnimatable(0f).also { level = it }
                    launch {
                        val spec = if (next > anim.value) NbSprings.HighlightIn else NbSprings.HighlightOut
                        anim.animateTo(next, spec.spec()) { invalidateDraw() }
                    }
                }
            }
        }

        override fun onDetach() {
            level = null
            target = 0f
            presses = 0
            hovers = 0
        }

        override fun ContentDrawScope.draw() {
            val value = level?.value ?: 0f
            if (value > 0f) drawRect(color, alpha = value)
            drawContent()
        }
    }
}
