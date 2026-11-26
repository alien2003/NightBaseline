package io.github.alien2003.nightbaseline.ui.material

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

/** Press scale of small shell controls (round buttons, dialog buttons): `coloros17-glass.md` 4.4. */
const val SHELL_PRESS_SCALE_SMALL: Float = 0.96f

/** Press scale of large shell surfaces (the bar, the rail, a sheet handle). */
const val SHELL_PRESS_SCALE_LARGE: Float = 0.98f

private const val PRESS_IN_DAMPING = 1f
private const val PRESS_IN_STIFFNESS = 632f
private const val RELEASE_DAMPING = 0.6f
private const val RELEASE_STIFFNESS = 400f

/** Release speed (dp/s) that maps to one unit of scale velocity in the rebound spring. */
private const val RELEASE_SPEED_PER_UNIT = 2000f
private const val MAX_RELEASE_VELOCITY = 3f
private const val REDUCED_GLOW = 0.6f

/**
 * State of the shell press feedback: scale, glow strength and the glow centre. Input ([shellPressInput]) and visual
 * ([shellPressVisual]) are separate modifiers so a 48 dp target can drive the light on its 40 dp visible circle.
 */
@Stable
class ShellPressState internal constructor(
    internal val scope: CoroutineScope,
) {
    internal val scale = Animatable(1f).apply { updateBounds(upperBound = 1f) }
    internal val glow = Animatable(0f)
    internal var center by mutableStateOf<Offset?>(null)
}

/** Remembers a [ShellPressState]. */
@Composable
fun rememberShellPressState(): ShellPressState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ShellPressState(scope) }
}

/**
 * Press input of the shell (`coloros17-glass.md` 4.4, "Living Flow" light and elastic feedback): on press the visual
 * shrinks to [pressedScale] on a critically damped spring and rebounds on a livelier spring whose start velocity
 * follows the finger's release speed (clamped, never above 1.0); the touch-following glow appears in 110 ms under the
 * finger, follows it, and fades over 480 ms after release ([shellPressScale] and [shellPressGlow] draw it). Observes
 * pointers without consuming them, so children stay clickable (the bar scales as a whole while its items handle the
 * taps). Under reduced motion: no scale, and the glow is a static highlight with a short fade.
 */
@Composable
fun Modifier.shellPressInput(
    state: ShellPressState,
    pressedScale: Float = SHELL_PRESS_SCALE_SMALL,
    enabled: Boolean = true,
): Modifier {
    val reduced = NbTheme.motion.reduced
    return pointerInput(state, enabled, reduced, pressedScale) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: continue
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                state.center = down.position
                state.scope.launch {
                    if (reduced) {
                        state.glow.snapTo(REDUCED_GLOW)
                    } else {
                        state.glow.animateTo(1f, tween(NbDurations.GLOW_IN, easing = NbEasing.Standard))
                    }
                }
                if (!reduced) state.scope.launch { state.scale.animateTo(pressedScale, spring(PRESS_IN_DAMPING, PRESS_IN_STIFFNESS)) }
                var up = false
                while (!up) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!reduced) state.center = change.position
                    up = event.changes.none { it.pressed }
                }
                val velocity = tracker.calculateVelocity()
                val speedDp = hypot(velocity.x, velocity.y) / density
                state.scope.launch {
                    state.glow.animateTo(
                        0f,
                        if (reduced) tween(NbDurations.REDUCED) else tween(NbDurations.GLOW_OUT, easing = NbEasing.Exit),
                    )
                }
                if (!reduced) {
                    state.scope.launch {
                        val start = (speedDp / RELEASE_SPEED_PER_UNIT).coerceAtMost(MAX_RELEASE_VELOCITY)
                        state.scale.animateTo(1f, spring(RELEASE_DAMPING, RELEASE_STIFFNESS), initialVelocity = start)
                    }
                }
            }
        }
    }
}

/** The scale half of the press feedback; put it before the element's background so the whole element shrinks. */
fun Modifier.shellPressScale(state: ShellPressState): Modifier =
    graphicsLayer {
        scaleX = state.scale.value
        scaleY = state.scale.value
    }

/**
 * The light half of the press feedback: the glow ([ShellTokens] `glow`, radius up to 140 dp, clipped to [shape])
 * drawn under the element's content, so labels and icons stay crisp on top of the light. Put it after the element's
 * background in the modifier chain. When the input element is larger (a 48 dp target around a 40 dp circle), pass
 * [inputOffset]: where this element sits inside the input element.
 *
 * @param forcedPressed catalog only: draws the pressed look without input.
 */
@Composable
fun Modifier.shellPressGlow(
    state: ShellPressState,
    shape: Shape,
    inputOffset: Dp = 0.dp,
    forcedPressed: Boolean = false,
): Modifier {
    val glowColor = ShellTokens.colors.glow
    // Outline and brush survive frames: the fading light reuses its shader instead of building one per frame.
    val outlines = remember { OutlineCache() }
    val brushes = remember { GlowBrushCache() }
    return drawWithContent {
        val alpha = if (forcedPressed) 1f else state.glow.value
        if (alpha > 0f) {
            val outline = outlines.get(shape, size, layoutDirection, this)
            val shift = inputOffset.toPx()
            val middle = Offset(size.width / 2f, size.height / 2f)
            val c = if (forcedPressed) middle else state.center?.let { it - Offset(shift, shift) } ?: middle
            val radius = min(ShellDimens.glowRadius.toPx(), maxOf(size.width, size.height))
            drawOutline(outline, brushes.get(glowColor, c, radius), alpha = alpha)
        }
        drawContent()
    }
}

/**
 * Clickable for shell controls: click semantics, hover pointer and the 2 dp keyboard-only focus ring
 * (`tokens.md` 10) around the visible shape ([shape] inset by [visualInset]). No ripple and no grey state layer: the
 * press light and shrink ([shellPressInput], [shellPressScale], [shellPressGlow]) are the feedback.
 */
@Composable
fun Modifier.shellClickable(
    onClick: () -> Unit,
    shape: Shape,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    visualInset: Dp = 0.dp,
    enabled: Boolean = true,
    forced: ForcedInteraction? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    return this
        .shellFocusRing(source, shape, visualInset, forced == ForcedInteraction.Focus)
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

/** The keyboard-only focus ring of `tokens.md` 10 around [shape] (inset by [visualInset]). */
@Composable
fun Modifier.shellFocusRing(
    source: MutableInteractionSource,
    shape: Shape,
    visualInset: Dp = 0.dp,
    forced: Boolean = false,
): Modifier {
    val focused by source.collectIsFocusedAsState()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val show = (focused && keyboard) || forced
    val color = NbTheme.colors.focusRing
    return if (!show) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawFocusRing(shape, color, visualInset.toPx())
        }
    }
}

private fun DrawScope.drawFocusRing(
    shape: Shape,
    color: Color,
    visualInset: Float,
) {
    val offset = (NbDimens.focusRingOffset + NbDimens.focusRingWidth / 2).toPx() - visualInset
    val ringSize = Size(size.width + 2 * offset, size.height + 2 * offset)
    val ring = shape.createOutline(ringSize, layoutDirection, this)
    translate(-offset, -offset) {
        drawOutline(ring, color, style = Stroke(width = NbDimens.focusRingWidth.toPx()))
    }
}
