package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The light of one pressable of the press model (ADR 0011, [NbPressModel]): its strength runs to 1 while pressed and
 * back to 0 on the model's light rule ([NbPressModel.lightSpec]), every frame reported to [NbMotionProbe.press] as a
 * [NbPressSample.Glow] of [owner]. Shared by the shell controls and the primary button's touch light.
 */
internal class PressLight(
    private val scope: CoroutineScope,
    private val owner: Any,
) {
    val glow = Animatable(0f)
    private var job: Job? = null

    fun to(
        pressed: Boolean,
        motion: NbMotionConfig,
        snap: Boolean,
    ) {
        job?.cancel()
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                glow.pressTo(NbPressModel.lightTarget(pressed), NbPressModel.lightSpec(motion, pressed, snap), NbPressSample.Glow, owner)
            }
    }
}

/**
 * State of the shell press feedback: scale, glow strength and the glow centre, driven by the press interactions of
 * [source] (and of any source the element [follows][FollowPresses]). The visuals ([shellPressScale],
 * [shellPressGlow]) are separate modifiers so a 48 dp target can drive the light on its 40 dp visible circle. The
 * pressed scale is the press model's scale for the size of the element that shrinks ([nbPressScale] of the element
 * [shellPressScale] is on), so a shell control and a clickable of the same size press the same way.
 */
@Stable
class ShellPressState internal constructor(
    internal val scope: CoroutineScope,
    /** The interaction source of the element's clickable. */
    val source: MutableInteractionSource,
) {
    internal val scale = NbPressModel.newScale()
    private val light = PressLight(scope, this)
    internal val glow: Animatable<Float, AnimationVector1D> get() = light.glow
    internal var center by mutableStateOf<Offset?>(null)
    private val presses = HashSet<PressInteraction.Press>()
    private var scaleJob: Job? = null

    /** The press model's scale for the shrinking element's measured size ([shellPressScale] keeps it current). */
    internal var pressedScale: Float = NbPhysics.PRESS_SCALE_SMALL

    /** Applies a press interaction (ADR 0011: the same press node rules as every other control). */
    internal fun onInteraction(
        interaction: Interaction,
        motion: NbMotionConfig,
    ) {
        val before = presses.isNotEmpty()
        when (interaction) {
            is PressInteraction.Press -> {
                presses += interaction
                center = interaction.pressPosition
            }

            is PressInteraction.Release -> {
                presses -= interaction.press
            }

            is PressInteraction.Cancel -> {
                presses -= interaction.press
            }
        }
        val pressed = presses.isNotEmpty()
        if (pressed != before) animate(pressed, motion, snap = false)
    }

    /** The app left the foreground while pressed: the press ends ([snap] when the activity is no longer visible). */
    internal fun clear(
        motion: NbMotionConfig,
        snap: Boolean,
    ) {
        if (presses.isEmpty()) return
        presses.clear()
        animate(false, motion, snap)
    }

    /** One press model (ADR 0011, [NbPressModel]): the same springs and light rule as every other pressable. */
    private fun animate(
        pressed: Boolean,
        motion: NbMotionConfig,
        snap: Boolean,
    ) {
        light.to(pressed, motion, snap)
        scaleJob?.cancel()
        val target = if (pressed) pressedScale else 1f
        val spec = NbPressModel.scaleSpec(motion, pressed, pressedScale) ?: return
        scaleJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                scale.pressTo(target, if (snap) null else spec, NbPressSample.Scale, this@ShellPressState)
            }
    }
}

/** Remembers a [ShellPressState] with its own interaction source. */
@Composable
fun rememberShellPressState(): ShellPressState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ShellPressState(scope, MutableInteractionSource()) }
}

/**
 * Press feedback of the shell (`coloros17-glass.md` 4.4, "Living Flow" light and elastic feedback) on the framework's
 * press rules (ADR 0011): it follows the clickable's press interactions, so the press starts after the tap timeout
 * inside a scrolling container and is cancelled as soon as a scroll takes the gesture, exactly like every other
 * control. The visual shrinks to [pressedScale] on `PressIn` and returns on `PressOut` (clamped at 1.0); the glow
 * appears in 110 ms under the finger, follows it, and fades over 480 ms. Pointer events are only observed (the glow
 * centre), never consumed. Under reduced motion: no scale, a static highlight that snaps.
 */
@Composable
fun Modifier.shellPressInput(
    state: ShellPressState,
    enabled: Boolean = true,
): Modifier {
    FollowPresses(state, state.source)
    val reduced = LocalNbMotion.current.reduced
    return pointerInput(state, enabled, reduced) {
        if (!enabled || reduced) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.pressed } ?: continue
                if (state.glow.value > 0f) state.center = change.position
            }
        }
    }
}

/** Drives [state] from the press interactions of another [source] too (the items of the bar light the whole bar). */
@Composable
fun FollowPresses(
    state: ShellPressState,
    source: InteractionSource,
) {
    val motion = LocalNbMotion.current
    LaunchedEffect(state, source, motion) {
        source.interactions.collect { state.onInteraction(it, motion) }
    }
    // As every pressable: no element comes back to the foreground pressed (focus lost, paused, stopped).
    OnPressLeave(state, motion) { stopped -> state.clear(motion, snap = stopped) }
}

/**
 * The leave rule of the press model as an effect: whenever the window loses focus or the activity pauses or stops,
 * [onLeave] runs (`stopped` true once the activity is no longer visible), so no element comes back pressed.
 */
@Composable
internal fun OnPressLeave(
    key: Any,
    motion: NbMotionConfig,
    onLeave: (stopped: Boolean) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    val leave by rememberUpdatedState(onLeave)
    LaunchedEffect(key, lifecycle, window, motion) {
        val focusLost = snapshotFlow { window.isWindowFocused }.drop(1).filter { !it }.map { false }
        val paused =
            lifecycle.eventFlow
                .filter { it == Lifecycle.Event.ON_PAUSE || it == Lifecycle.Event.ON_STOP }
                .map { it == Lifecycle.Event.ON_STOP }
        merge(focusLost, paused).collect { stopped -> leave(stopped) }
    }
}

/**
 * The scale half of the press feedback; put it before the element's background so the whole element shrinks. It
 * measures the element, so the pressed scale is the press model's scale for that size ([nbPressScale]).
 */
fun Modifier.shellPressScale(state: ShellPressState): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        state.pressedScale = nbPressScale(IntSize(placeable.width, placeable.height), this)
        layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = state.scale.value
                scaleY = state.scale.value
            }
        }
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
        .nbPressableKey()
        .nbTapSlop()
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = rememberPressFirst(onClick),
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
