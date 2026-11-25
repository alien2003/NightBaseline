package io.github.alien2003.nightbaseline.ui.material

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import io.github.alien2003.nightbaseline.ui.motion.NB_PRESS_SCALE
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/** `state.hover` alpha of `text.primary` over solid elements. */
const val STATE_HOVER_ALPHA: Float = 0.08f

/** `state.pressed` alpha. */
const val STATE_PRESSED_ALPHA: Float = 0.12f

/** `state.dragged` alpha. */
const val STATE_DRAGGED_ALPHA: Float = 0.16f

/** Forced interaction looks for the component catalog (hover, pressed, focus shown without a pointer or keyboard). */
enum class ForcedInteraction {
    /** Hover state layer. */
    Hover,

    /** Pressed state layer (and the glow on glass). */
    Pressed,

    /** Keyboard focus ring. */
    Focus,
}

/**
 * Clickable with NightBaseline's interaction tokens (`tokens.md` 2.8, 9.5, 10): a hover layer (`state.hover`) or, on
 * glass, a brighter rim; a pressed layer (`state.pressed`) or, on glass, the `accentGlow` light under the finger; press
 * scale 0.97 with `spring.press` (none under reduced motion); a 2 dp `focus.ring` 2 dp outside [shape], only for
 * keyboard / D-pad focus; and the hand pointer on hover.
 *
 * The caller keeps the element at least 48 x 48 dp (`target.min`).
 *
 * @param onGlass the element sits on a glass surface (no grey state layer there).
 * @param forced draws a state without real input (component catalog only).
 */
@Suppress("CyclomaticComplexMethod") // One modifier composing all interaction tokens of tokens.md 2.8, 9.5, 10.
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
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val motion = NbTheme.motion
    val colors = NbTheme.colors
    val isPressed = pressed || forced == ForcedInteraction.Pressed
    val isHovered = hovered || forced == ForcedInteraction.Hover
    val showRing = (focused && keyboard) || forced == ForcedInteraction.Focus
    val pressSpec = motion.press<Float>()
    val scale by animateFloatAsState(
        targetValue = if (pressed && pressScale && pressSpec != null) NB_PRESS_SCALE else 1f,
        animationSpec = pressSpec ?: snap(),
        label = "pressScale",
    )
    val layerAlpha by animateFloatAsState(
        targetValue =
            when {
                onGlass -> 0f
                isPressed -> STATE_PRESSED_ALPHA
                isHovered -> STATE_HOVER_ALPHA
                else -> 0f
            },
        animationSpec = motion.stateLayer(),
        label = "stateLayer",
    )
    var glowCenter by remember { mutableStateOf<Offset?>(null) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(source, onGlass) {
        if (!onGlass) return@LaunchedEffect
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    glowCenter = interaction.pressPosition
                    motion.glowIn<Float>()?.let { glow.animateTo(1f, it) }
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    glow.animateTo(0f, motion.glowOut())
                }
            }
        }
    }
    val ringColor = colors.focusRing
    val layerColor = colors.textPrimary
    val glowColor = colors.accentGlow
    val rimHover = colors.glassRimTop
    val forcedGlow = forced == ForcedInteraction.Pressed && onGlass
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val glowAlpha = if (forcedGlow) 1f else glow.value
            if (onGlass && glowAlpha > 0f) {
                val center = if (forcedGlow) Offset(size.width / 2f, size.height / 2f) else glowCenter ?: center
                val radius = NbDimens.glowRadius.toPx()
                drawOutline(
                    outline,
                    Brush.radialGradient(listOf(glowColor, Color.Transparent), center = center, radius = radius),
                    alpha = glowAlpha,
                )
            }
            if (onGlass && isHovered) {
                // state.glassHover: a brighter rim instead of a grey layer.
                drawOutline(outline, rimHover, style = Stroke(width = 1.5f * density), alpha = 1f)
            }
            drawContent()
            if (layerAlpha > 0f) drawOutline(outline, layerColor, alpha = layerAlpha)
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
