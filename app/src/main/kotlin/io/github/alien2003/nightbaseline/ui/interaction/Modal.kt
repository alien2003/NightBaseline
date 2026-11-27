package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

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

/** How a [ModalHost] places its panel; the motion is the framework kind of the same name. */
enum class ModalKind(
    /** Where the panel sits in the window. */
    val alignment: Alignment,
    /** The framework kind. */
    val kind: NbKind,
) {
    /** Bottom sheet. */
    Sheet(Alignment.BottomCenter, NbKind.Sheet),

    /** Centred dialog. */
    Dialog(Alignment.Center, NbKind.Dialog),

    /** Dialog docked at the bottom on phone widths. */
    DockedDialog(Alignment.BottomCenter, NbKind.DockedDialog),
}

/**
 * The state of one modal panel (ADR 0011, kinds Sheet / Dialog / DockedDialog): one scalar p (0 hidden, 1 at rest) on
 * the Nav spring drives the panel's pose **and** the scrim's alpha. Show, hide, a drag release, a return, a back
 * gesture and its cancel are all the same spring, continued from (p, v).
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

    /** Reduced motion: every change snaps, no drag. */
    var reduced: Boolean by mutableStateOf(reduced)
        internal set

    internal val driver = NbDriver(NbPhysics.Nav) { this.reduced }

    /** 0 = hidden, 1 = at rest; snapshot state (read in layer lambdas). */
    val p: Float get() = if (staticP >= 0f) staticP else driver.p

    private val staticP: Float = if (scope == null) initial else -1f

    /** True while the caller wants the panel open. */
    var target: Boolean by mutableStateOf(initial > 0f)
        internal set

    /** Panel height in px, measured by the panel. */
    var panelHeight: Float by mutableFloatStateOf(0f)

    internal var onDismiss: () -> Unit = {}

    /** Start velocity (p / s) of the next hide, from a fling or a released gesture. */
    internal var releaseVelocity: Float = 0f

    private var job: Job? = null
    private var dragging = false

    /** The derived phase. */
    val phase: ModalPhase
        get() {
            val v = p
            return when {
                target && v >= 1f && !driver.isRunning -> ModalPhase.Shown
                target -> ModalPhase.Showing
                v > 0f -> ModalPhase.Hiding
                else -> ModalPhase.Hidden
            }
        }

    /** Asks the caller to close (scrim, Back, drag, close button, a chosen option). Animated out by the host. */
    fun dismiss(velocity: Float = 0f) {
        if (!target) return
        releaseVelocity = velocity
        onDismiss()
    }

    internal suspend fun show() {
        job?.cancel()
        driver.run(1f, NbRelease.startVelocity(driver.p, driver.v, 1f, NbPhysics.Nav, visibleTarget = true))
    }

    internal suspend fun hide() {
        job?.cancel()
        val v = releaseVelocity
        releaseVelocity = 0f
        driver.run(0f, NbRelease.startVelocity(driver.p, v, 0f, NbPhysics.Nav, visibleTarget = false))
    }

    /** Moves the panel down by [deltaPx] (negative = up, never above rest); used by drags. Returns the consumed px. */
    internal fun dragBy(deltaPx: Float): Float {
        val height = panelHeight
        val current = driver.p
        val next = if (!target || reduced || height <= 0f) current else (current - deltaPx / height).coerceIn(0f, 1f)
        if (next != current) {
            dragging = true
            job?.cancel()
            scope?.launch(start = CoroutineStart.UNDISPATCHED) { driver.track(next, android.os.SystemClock.uptimeMillis()) }
        }
        return (current - next) * height
    }

    /**
     * Release of a drag with [velocityPx] (down positive). Decided like a scroll: the platform fling decay projects
     * where the panel would come to rest; past half its height (or a fling faster than the minimum fling velocity
     * downwards) it hides, else it returns, both on the Nav spring from (p, v).
     */
    internal fun settle(
        velocityPx: Float,
        density: Density,
    ) {
        if (!target) return
        dragging = false
        val height = panelHeight.coerceAtLeast(1f)
        val perSecond = -velocityPx / height
        val offset = (1f - driver.p) * height
        val projected = splineBasedDecay<Float>(density).calculateTargetValue(offset, velocityPx)
        val fling = velocityPx > MIN_FLING_DP * density.density
        if (projected > height / 2 || fling) {
            dismiss(perSecond)
        } else if (driver.p < 1f) {
            job =
                scope?.launch {
                    driver.run(1f, NbRelease.startVelocity(driver.p, perSecond, 1f, NbPhysics.Nav, visibleTarget = true))
                }
        }
    }

    private var gestureFrom = 1f

    /** The modal's Back target (depth above every page). */
    internal val backTarget: NbBackTarget =
        object : NbBackTarget {
            override val depth: Int = MODAL_DEPTH

            override fun enabled(): Boolean = target

            override fun start(edge: Int) {
                job?.cancel()
                driver.resetTracking()
                gestureFrom = driver.p
            }

            override fun progress(
                fraction: Float,
                timeMillis: Long,
            ) {
                if (!target || reduced) return
                // p = 1 - g: the panel follows the back gesture towards hidden (ADR 0011 Sheet / Dialog row).
                val next = (gestureFrom * (1f - fraction.coerceIn(0f, 1f))).coerceIn(0f, 1f)
                scope?.launch(start = CoroutineStart.UNDISPATCHED) { driver.track(next, timeMillis) }
            }

            override fun cancel() {
                job = scope?.launch { driver.release(1f, visibleTarget = true) }
            }

            override fun commit() {
                dismiss(driver.trackedVelocity())
            }

            override fun back() {
                dismiss()
            }
        }

    /** Content scrolling inside the panel: pulling down at the top moves the panel; pushing up restores it first. */
    internal fun nestedScroll(density: Density): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                if (available.y < 0f && driver.p < 1f && source == NestedScrollSource.UserInput) {
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
                if (!target || driver.p >= 1f) return Velocity.Zero
                settle(available.y, density)
                return available
            }
        }

    internal companion object {
        /** A downward fling faster than this (dp/s) hides the panel (the platform's minimum fling velocity, 50 dp/s x 20). */
        const val MIN_FLING_DP = 1000f
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

/** The two states of a modal in [NbFrame]s. */
private const val MODAL_SHOWN = "shown"
private const val MODAL_HIDDEN = "hidden"

/** Test tag of a [ModalHost]'s scrim. */
const val MODAL_SCRIM_TAG: String = "nb-modal-scrim"

/** Test tag of a [ModalHost]'s panel container. */
const val MODAL_PANEL_TAG: String = "nb-modal-panel"

/**
 * The one host for sheets and dialogs (`screens.md` "Sheets", ADR 0011). Open while [value] is non-null: the caller
 * owns the intent, the host owns the presentation. A request to close from inside (scrim tap, Back or the back
 * gesture through the app's router, a drag down or fling, the close button, a chosen option via [ModalScope.dismiss])
 * calls [onDismiss], the caller clears [value], and the host animates out with the last value still composed; once p
 * is back at 0 nothing of the panel or the scrim stays in the composition. Re-opening while it leaves continues from
 * where it is. The scrim's alpha is p; it takes touches only while the panel is wanted open. The overscroll is off
 * inside the panel.
 */
@Composable
fun <T : Any> ModalHost(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ModalKind = ModalKind.Sheet,
    content: @Composable ModalScope.(T) -> Unit,
) {
    val reduced = LocalNbMotion.current.reduced
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
            state.show()
        } else {
            state.hide()
            retained.value = null
        }
    }
    val present by remember(state) { derivedStateOf { state.target || state.p > 0f } }
    val shown = value ?: retained.value
    if (!present || shown == null) return
    if (wanted) RegisterBackTarget(state.backTarget)
    val close = stringResource(R.string.a11y_close)
    val modalScope = remember(state) { ModalScope(state) }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(modifier.fillMaxSize(), contentAlignment = state.kind.alignment) {
            Box(
                Modifier
                    .matchParentSize()
                    .testTag(MODAL_SCRIM_TAG)
                    .graphicsLayer { alpha = state.p.coerceIn(0f, 1f) }
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
 * The panel's own modifier, applied by the sheet and dialog surfaces right outside them: measures the panel, draws
 * its pose from the host's p ([NbPoses.modal]; reduced motion: the snap makes it binary) and, while it leaves,
 * swallows touches on the panel so an option cannot be chosen twice.
 */
fun Modifier.modalPanel(state: ModalState): Modifier =
    this
        .onSizeChanged { state.panelHeight = it.height.toFloat() }
        .graphicsLayer {
            val p = state.p
            val pose = NbPoses.modal(state.kind.kind, p, size.height)
            if (p > 0f && p < 1f) {
                NbMotionProbe.frames?.invoke(
                    NbFrame("modal", state.kind.kind, NbPhysics.Nav, p, MODAL_SHOWN, MODAL_HIDDEN, pose, NbLayerPose.Rest, false, size.width),
                )
            }
            translationY = pose.ty
            scaleX = pose.scale
            scaleY = pose.scale
            alpha = pose.alpha
        }.testTag(MODAL_PANEL_TAG)
        .blockInput { !state.target }

/** The drag handle of a sheet: follows the finger, released by [ModalState.settle]. */
@Composable
fun Modifier.sheetDrag(state: ModalState): Modifier {
    val density = LocalDensity.current
    val drag = rememberDraggableState { delta -> state.dragBy(delta) }
    return draggable(
        drag,
        Orientation.Vertical,
        enabled = !state.reduced && state.target,
        onDragStopped = { velocity -> state.settle(velocity, density) },
    )
}

/** Content scrolling inside a sheet moves the sheet at its top (velocity and distance conserved: content + sheet). */
@Composable
fun Modifier.sheetNestedScroll(state: ModalState): Modifier {
    val density = LocalDensity.current
    val nested = remember(state, density) { state.nestedScroll(density) }
    return nestedScroll(nested)
}

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
