package io.github.alien2003.nightbaseline.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbMotionScheme
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Presentation phase of a sheet or dialog in a [ModalHost]; derived, never stored, so it cannot drift. */
enum class ModalPhase {
    /** Nothing of the panel or its scrim is composed. */
    Hidden,

    /** Requested open, moving in (or dragged / pulled back while open). */
    Showing,

    /** Open and at rest. */
    Shown,

    /** Requested closed, moving out; touches already go to the screen underneath. */
    Hiding,
}

/** How a [ModalHost] places and moves its panel. */
enum class ModalKind(
    /** Where the panel sits in the window. */
    val alignment: Alignment,
) {
    /** Bottom sheet: slides up from the bottom edge on `spring.sheet`, leaves on `spring.sheetHide`. */
    Sheet(Alignment.BottomCenter),

    /** Centred dialog: scale 0.8 -> 1 with a fade. */
    Dialog(Alignment.Center),

    /** Dialog docked at the bottom on phone widths: slides up with a fade. */
    DockedDialog(Alignment.BottomCenter),
}

/**
 * The state of one modal panel (`coloros-conformance.md` G1–G3, H1–H2): a single [progress] (0 = hidden, 1 = at
 * rest) drives the panel's offset or scale **and** the scrim's alpha, so the two never run on separate timelines.
 * The open intent is the host's `value` ([target]); [phase] is derived from the intent and [progress].
 */
@Stable
class ModalState internal constructor(
    kind: ModalKind,
    reduced: Boolean,
    initial: Float,
    private val scope: CoroutineScope?,
) {
    /** Panel kind; follows the host's parameter only while open, so a leaving panel keeps its motion. */
    var kind: ModalKind by mutableStateOf(kind)
        internal set

    /** Reduced motion: fades only, no drag. */
    var reduced: Boolean by mutableStateOf(reduced)
        internal set

    /** 0 = hidden, 1 = at rest. Never above 1: the panel never moves above its resting top. */
    val progress: Animatable<Float, *> = Animatable(initial)

    /** True while the caller wants the panel open. */
    var target: Boolean by mutableStateOf(initial > 0f)
        internal set

    /** Panel height in px, measured by the panel. */
    var panelHeight: Float by mutableFloatStateOf(0f)

    /** Predictive back progress 0..1 while the system back gesture runs (scales the panel like ColorOS). */
    var backProgress: Float by mutableFloatStateOf(0f)
        internal set

    internal var onDismiss: () -> Unit = {}

    /** Initial velocity (progress units / s) for the next hide, from a fling. */
    internal var releaseVelocity: Float = 0f

    /** The derived phase. */
    val phase: ModalPhase
        get() {
            val p = progress.value
            return when {
                target && p >= 1f && !progress.isRunning -> ModalPhase.Shown
                target -> ModalPhase.Showing
                p > 0f -> ModalPhase.Hiding
                else -> ModalPhase.Hidden
            }
        }

    /** Asks the caller to close (scrim, Back, drag, close button, a chosen option). Animated out by the host. */
    fun dismiss(velocity: Float = 0f) {
        if (!target) return
        releaseVelocity = velocity
        onDismiss()
    }

    /** Moves the panel down by [deltaPx] (negative = up, never above rest); used by drags. Returns the consumed px. */
    internal fun dragBy(deltaPx: Float): Float {
        val height = panelHeight
        val current = progress.value
        val next = if (!target || reduced || height <= 0f) current else (current - deltaPx / height).coerceIn(0f, 1f)
        if (next != current) scope?.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(next) }
        return (current - next) * height
    }

    /** Release of a drag with [velocityPx] (down positive): dismiss past half the height or on a fling, else return. */
    internal fun settle(
        velocityPx: Float,
        density: Density,
    ) {
        if (!target) return
        val height = panelHeight.coerceAtLeast(1f)
        val perSecond = -velocityPx / height
        val downDp = with(density) { velocityPx.toDp().value }
        if (1f - progress.value > DISMISS_FRACTION || downDp > FLING_DISMISS_DP) {
            dismiss(perSecond)
        } else if (progress.value < 1f) {
            scope?.launch { progress.animateTo(1f, returnSpec(), perSecond) }
        }
    }

    internal fun onBackProgress(fraction: Float) {
        if (!target) return
        backProgress = fraction
        if (!reduced) scope?.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(1f - BACK_TRAVEL * fraction) }
    }

    internal fun onBackCancelled() {
        if (!target) return
        scope?.launch {
            launch { Animatable(backProgress).animateTo(0f, returnSpec()) { backProgress = value } }
            progress.animateTo(1f, returnSpec())
        }
    }

    internal fun showSpec(): FiniteAnimationSpec<Float> = motion.let { if (kind == ModalKind.Sheet) it.sheet() else it.enter() }

    internal fun hideSpec(): FiniteAnimationSpec<Float> = motion.let { if (kind == ModalKind.Sheet) it.sheetExit() else it.exit() }

    private fun returnSpec(): FiniteAnimationSpec<Float> = if (reduced) tween(NbDurations.REDUCED) else motion.sheetReturn()

    private val motion get() = NbMotionScheme(reduced)

    /** Content scrolling inside the panel: pulling down at the top moves the panel; pushing up restores it first. */
    internal fun nestedScroll(density: Density): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                if (available.y < 0f && progress.value < 1f && source == NestedScrollSource.UserInput) {
                    Offset(0f, -dragBy(available.y))
                } else {
                    Offset.Zero
                }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, -dragBy(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!target || progress.value >= 1f) return Velocity.Zero
                settle(available.y, density)
                return available
            }
        }

    internal companion object {
        /** Share of its height a sheet must be dragged down to dismiss on release (`coloros-conformance.md` G1). */
        const val DISMISS_FRACTION = 0.5f

        /** A downward fling faster than this (dp/s) dismisses. */
        const val FLING_DISMISS_DP = 1000f

        /** Share of the height the panel follows a full predictive back gesture. */
        const val BACK_TRAVEL = 0.12f

        /** Scale at a full predictive back gesture. */
        const val BACK_SCALE = 0.92f
    }
}

/** What a panel inside a [ModalHost] can use: its [state] and [dismiss]. */
@Stable
class ModalScope internal constructor(
    /** The host's state. */
    val state: ModalState,
) {
    /** Closes the panel (animated). */
    fun dismiss() = state.dismiss()
}

/** Test tag of a [ModalHost]'s scrim. */
const val MODAL_SCRIM_TAG: String = "nb-modal-scrim"

/** Test tag of a [ModalHost]'s panel container. */
const val MODAL_PANEL_TAG: String = "nb-modal-panel"

/**
 * The one host for sheets and dialogs (`screens.md` "Sheets"). Open while [value] is non-null: the caller owns the
 * intent, the host owns the presentation. A request to close from inside (scrim tap, system Back or the predictive
 * back gesture, a drag down or fling, the close button, a chosen option via [ModalScope.dismiss]) calls [onDismiss],
 * the caller clears [value], and the host animates out with the last value still composed. Once the panel is back at
 * 0 the phase is [ModalPhase.Hidden] and nothing of the panel or the scrim stays in the composition. Re-opening while
 * it leaves reverses from where it is. The scrim is drawn by the host over everything composed before it (the
 * floating bar and rail included, so they dim with it), its alpha is the panel's progress, and it takes touches only
 * while the panel is wanted open. The custom overscroll is off inside the panel.
 */
@Composable
fun <T : Any> ModalHost(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ModalKind = ModalKind.Sheet,
    content: @Composable ModalScope.(T) -> Unit,
) {
    val reduced = NbTheme.motion.reduced
    val scope = rememberCoroutineScope()
    val state = remember { ModalState(kind, reduced, 0f, scope) }
    if (value != null) {
        state.kind = kind
        state.reduced = reduced
    }
    val dismissNow by rememberUpdatedState(onDismiss)
    state.onDismiss = { dismissNow() }
    val retained = remember { RetainedValue<T>() }
    if (value != null) retained.value = value
    val wanted = value != null
    state.target = wanted
    LaunchedEffect(state, wanted) {
        if (wanted) {
            state.progress.animateTo(1f, state.showSpec())
        } else {
            val velocity = state.releaseVelocity
            state.releaseVelocity = 0f
            state.progress.animateTo(0f, state.hideSpec(), velocity)
            state.backProgress = 0f
            retained.value = null
        }
    }
    val present by remember(state) { derivedStateOf { state.target || state.progress.value > 0f } }
    val shown = value ?: retained.value
    if (!present || shown == null) return
    if (wanted) {
        PredictiveBackHandler { events ->
            try {
                events.collect { state.onBackProgress(it.progress) }
                state.dismiss()
            } catch (e: CancellationException) {
                state.onBackCancelled()
                throw e
            }
        }
    }
    val close = stringResource(R.string.a11y_close)
    val modalScope = remember(state) { ModalScope(state) }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(modifier.fillMaxSize(), contentAlignment = state.kind.alignment) {
            Box(
                Modifier
                    .matchParentSize()
                    .testTag(MODAL_SCRIM_TAG)
                    .graphicsLayer { alpha = state.progress.value.coerceIn(0f, 1f) }
                    .background(NbTheme.colors.scrim)
                    .then(
                        if (wanted) {
                            Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { state.dismiss() },
                                ).semantics { contentDescription = close }
                        } else {
                            Modifier
                        },
                    ),
            )
            modalScope.content(shown)
        }
    }
}

/**
 * The panel's own modifier, applied by [GlassSheet] / [GlassDialog] right outside their surface: measures the panel,
 * moves it from the host's single progress (sheet: offset by its height, scaled by the predictive back gesture from
 * the bottom centre; dialog: scale 0.8 -> 1 with a fade that is complete at 35 % of the motion; docked dialog: offset
 * and the same fade; reduced motion: fade only) and, while it leaves, swallows touches on the panel so an option
 * cannot be chosen twice.
 */
fun Modifier.modalPanel(state: ModalState): Modifier =
    this
        .onSizeChanged { state.panelHeight = it.height.toFloat() }
        .graphicsLayer {
            val p = state.progress.value.coerceIn(0f, 1f)
            if (state.reduced) {
                alpha = p
                return@graphicsLayer
            }
            when (state.kind) {
                ModalKind.Sheet -> {
                    translationY = (1f - p) * size.height
                    val s = 1f - (1f - ModalState.BACK_SCALE) * state.backProgress
                    scaleX = s
                    scaleY = s
                    transformOrigin = BottomCentre
                }

                ModalKind.Dialog -> {
                    alpha = (p / DIALOG_OPAQUE_AT).coerceAtMost(1f)
                    val s = DIALOG_START_SCALE + (1f - DIALOG_START_SCALE) * p
                    scaleX = s
                    scaleY = s
                }

                ModalKind.DockedDialog -> {
                    alpha = (p / DIALOG_OPAQUE_AT).coerceAtMost(1f)
                    translationY = (1f - p) * size.height
                }
            }
        }.testTag(MODAL_PANEL_TAG)
        .then(if (state.target) Modifier else SwallowTouches)

private val SwallowTouches: Modifier =
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

private const val DIALOG_START_SCALE = 0.8f

/** The sheet scales towards its bottom edge during the predictive back gesture. */
private val BottomCentre = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)

/**
 * A dialog is fully opaque from this share of its motion on (the scale and the scrim keep going): a half-transparent
 * panel would show the page through its text mid-animation.
 */
private const val DIALOG_OPAQUE_AT = 0.35f

/** The last non-null value, kept for the exit animation; plain (not state): it never drives recomposition. */
private class RetainedValue<T : Any> {
    var value: T? = null
}

/** A panel drawn at rest outside a host (component catalog, previews). */
@Composable
fun StaticModal(
    kind: ModalKind = ModalKind.Sheet,
    content: @Composable ModalScope.() -> Unit,
) {
    val scope = remember(kind) { ModalScope(ModalState(kind, reduced = false, initial = 1f, scope = null)) }
    scope.content()
}
