package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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

    /** Start velocity (p / s) of the next hide, from a fling or a released gesture (snapshot state, V4). */
    internal var releaseVelocity: Float by mutableFloatStateOf(0f)

    /**
     * [NbClock.nowNanos] of the last open or close request, or the frame a released gesture last drew: the time origin
     * of its run (ADR 0011 R7). Set for every open and every close, whoever asks for it (review V1: a close by the
     * caller, without [dismiss], ran from the open request seconds earlier and snapped shut).
     */
    internal var requestAt: Long? by mutableStateOf(null)

    /** True from a [dismiss] until the host composes the close it asked for (its origin is then already set). */
    internal var dismissing: Boolean by mutableStateOf(false)

    /** The branch of the composition the modal was opened in (its Back is enabled only while that branch is shown). */
    internal var branch: NbBackScope = NbBackScope(0) { true }

    /** Width of the host (the window) in px: the travel of a back gesture over the modal. */
    internal var hostWidth: Float = 0f

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
        dismissFrom(velocity, NbClock.nowNanos())
    }

    /** [dismiss] whose hide continues a motion last drawn at [origin] (a released back gesture, R7). */
    internal fun dismissFrom(
        velocity: Float,
        origin: Long,
    ) {
        if (!target) return
        releaseVelocity = velocity
        requestAt = origin
        dismissing = true
        onDismiss()
    }

    /**
     * Opens (or continues opening) the panel. Rule R8: a panel opening from hidden waits, at p = 0 (invisible), until
     * it has been measured (its content composed and laid out), at most [NbPhysics.ENTER_WAIT_MS] after the request.
     * Its first frame of motion is at most one display frame into the spring (R7, [NbPlayClock]), so the cost of its
     * first composition never shows as a jump of the motion, waited or not (review V2, V2-3). Reopened while it hides,
     * it continues from (p, v).
     */
    internal suspend fun show() {
        job?.cancel()
        var origin = requestAt
        val v = driver.v
        if (driver.p == 0f && panelHeight <= 0f && !reduced) {
            val asked = origin ?: NbClock.nowNanos()
            val budget = (NbPhysics.ENTER_WAIT_MS * NANOS_PER_MS - (NbClock.nowNanos() - asked)).coerceAtLeast(0L)
            var first = Long.MIN_VALUE
            while (true) {
                val frame = withFrameNanos { it }
                if (first == Long.MIN_VALUE) first = frame
                if (panelHeight > 0f || frame - first >= budget) break
            }
            origin = NbClock.nowNanos()
        } else if (v != 0f) {
            origin = driver.continueOrigin()
        }
        driver.run(1f, NbRelease.startVelocity(driver.p, v, 1f, NbPhysics.Nav, visibleTarget = true), origin)
    }

    internal suspend fun hide() {
        job?.cancel()
        val v = releaseVelocity
        releaseVelocity = 0f
        dismissing = false
        driver.run(0f, NbRelease.startVelocity(driver.p, v, 0f, NbPhysics.Nav, visibleTarget = false), requestAt)
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
            val at = NbClock.nowNanos()
            job =
                scope?.launch(start = CoroutineStart.UNDISPATCHED) {
                    driver.run(1f, NbRelease.startVelocity(driver.p, perSecond, 1f, NbPhysics.Nav, visibleTarget = true), at)
                }
        }
    }

    private var gestureFrom = 1f
    private var gestureOn = false
    private val finger = NbBackFinger()

    /**
     * The modal's Back target (depth above every page). Enabled only while the modal is wanted open **and** the branch
     * it was opened in is the shown one: a modal left open in a parked page or a hidden section never takes Back from
     * the page that is shown (review: it used to, as it ignored its branch, unlike every other target).
     */
    internal val backTarget: NbBackTarget =
        object : NbBackTarget {
            override val depth: Int = MODAL_DEPTH

            override fun enabled(): Boolean = target && branch.active()

            override fun start(
                edge: Int,
                touchX: Float,
            ) {
                job?.cancel()
                driver.resetTracking()
                finger.start(edge, touchX)
                gestureFrom = driver.p
                gestureOn = true
            }

            override fun progress(move: NbBackMove) {
                if (!gestureOn || !target || reduced) return
                // The panel's hide share moves by exactly the finger's travel share g over the window, from wherever
                // the panel is (review V5: p0 (1 - g) moved a panel caught mid-show by p0 g only), the same law as a
                // page (ADR 0011: every back gesture follows the finger's travel, never the platform's progress).
                val g = finger.share(move, hostWidth)
                val next = 1f - NbGestureMap.linear(g, 1f - gestureFrom)
                scope?.launch(start = CoroutineStart.UNDISPATCHED) { driver.track(next, move.timeMillis, finger = g, slope = -1f) }
            }

            override fun cancel() {
                if (!gestureOn) return
                gestureOn = false
                val at = NbClock.nowNanos()
                job = scope?.launch(start = CoroutineStart.UNDISPATCHED) { driver.release(1f, visibleTarget = true, at = at) }
            }

            override fun commit() {
                if (!gestureOn) {
                    dismiss()
                    return
                }
                gestureOn = false
                val at = NbClock.nowNanos()
                // The hide continues the finger's motion (R7), not a new start at the dismiss request.
                dismissFrom(driver.releaseVelocity(at), driver.releaseOrigin(at))
            }

            override fun back() {
                dismiss()
            }

            // A fold or unfold during the gesture (R6): the gesture is void and the panel is at rest where it was
            // asked to be (review V12: the modal kept the gesture running while every page dropped its own).
            override fun abort() {
                if (!gestureOn) return
                gestureOn = false
                job?.cancel()
                driver.resetTracking()
                scope?.launch(start = CoroutineStart.UNDISPATCHED) { driver.snapTo(if (target) 1f else 0f) }
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

        private const val NANOS_PER_MS = 1_000_000L
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
@Suppress("CyclomaticComplexMethod") // One host: open and close requests, their origins, the panel and the scrim.
@Composable
fun <T : Any> ModalHost(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ModalKind = ModalKind.Sheet,
    prewarm: T? = null,
    content: @Composable ModalScope.(T) -> Unit,
) {
    if (prewarm != null) Prewarm(kind) { content(prewarm) }
    val reduced = LocalNbMotion.current.reduced
    val scope = rememberCoroutineScope()
    val dismissNow by rememberUpdatedState(onDismiss)
    val state = remember { ModalState(kind, reduced, 0f, scope).also { it.onDismiss = { dismissNow() } } }
    // Snapshot state only in composition (V4): a discarded composition rolls these writes back.
    if (value != null) {
        state.kind = kind
        state.reduced = reduced
    }
    val retained = remember { RetainedValue<T>() }
    if (value != null) retained.value = value
    val wanted = value != null
    // Every open and every close takes its origin now, unless a recent dismiss already gave the close its own (V1).
    val asked = state.requestAt
    val fresh = state.dismissing && asked != null && NbClock.nowNanos() - asked <= NbPhysics.ENTER_WAIT_MS * MODAL_NANOS_PER_MS
    if (wanted != state.target && (wanted || !fresh)) state.requestAt = NbClock.nowNanos()
    if (!wanted && state.target) state.dismissing = false
    state.target = wanted
    val branch = LocalNbBackScope.current
    SideEffect { state.branch = branch }
    LaunchedEffect(state, wanted) {
        if (wanted) {
            state.show()
        } else {
            state.hide()
            retained.value = null
            // The next open measures its panel again (R8).
            state.panelHeight = 0f
        }
    }
    val present by remember(state) { derivedStateOf { state.target || state.p > 0f } }
    val shown = value ?: retained.value
    if (!present || shown == null) return
    if (wanted) RegisterBackTarget(state.backTarget)
    val modalScope = remember(state) { ModalScope(state) }
    ModalLayer(modifier, state, wanted) { modalScope.content(shown) }
}

/** The scrim and the panel of a modal (shared by the host and its prewarm, so the prewarm runs the same code). */
@Composable
private fun ModalLayer(
    modifier: Modifier,
    state: ModalState,
    wanted: Boolean,
    content: @Composable () -> Unit,
) {
    val close = stringResource(R.string.a11y_close)
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(modifier.fillMaxSize().onSizeChanged { state.hostWidth = it.width.toFloat() }, contentAlignment = state.kind.alignment) {
            Box(
                Modifier
                    .matchParentSize()
                    .testTag(MODAL_SCRIM_TAG)
                    .graphicsLayer { alpha = state.p.coerceIn(0f, 1f) }
                    .background(NbTheme.colors.scrim)
                    .then(
                        if (wanted) {
                            // The framework's tap (tap slop, the click path of every pressable; review P1-1).
                            Modifier.nbTap { state.dismiss() }.semantics { contentDescription = close }
                        } else {
                            Modifier
                        },
                    ),
            )
            content()
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
                    NbFrame(
                        "modal",
                        state.kind.kind,
                        NbPhysics.Nav,
                        p,
                        MODAL_SHOWN,
                        MODAL_HIDDEN,
                        pose,
                        NbLayerPose.Rest,
                        false,
                        size.width,
                    ),
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

/** The last non-null value, kept for the exit animation (snapshot state: a discarded composition rolls it back). */
private class RetainedValue<T : Any> {
    var value: T? by mutableStateOf(null)
}

/**
 * Composes, measures and draws [content] once, shortly after the host first appears, invisibly (alpha 1/100 for
 * [PREWARM_FRAMES] frames, no semantics, no input), then drops it (review R3-5: the first open of a sheet after
 * launch stalled while its content was composed and its glass rendered for the first time). The first real open then
 * runs on warm code, resources and render paths.
 */
@Composable
private fun Prewarm(
    kind: ModalKind,
    content: @Composable ModalScope.() -> Unit,
) {
    val reduced = LocalNbMotion.current.reduced
    val phase = remember { PrewarmPhase() }
    LaunchedEffect(phase) {
        repeat(PREWARM_AFTER_FRAMES) { withFrameNanos { } }
        phase.on = true
        repeat(PREWARM_FRAMES) { withFrameNanos { } }
        phase.on = false
    }
    if (!phase.on || reduced) return
    val scope = remember(kind) { ModalScope(ModalState(kind, reduced = false, initial = 1f, scope = null)) }
    ModalLayer(
        Modifier
            .graphicsLayer { alpha = PREWARM_ALPHA }
            .clearAndSetSemantics {}
            .blockInput { true },
        scope.state,
        wanted = true,
    ) { scope.content() }
}

private class PrewarmPhase {
    var on: Boolean by mutableStateOf(false)
}

/** Frames after the host appears before the prewarm (the launch's own frames come first). */
private const val PREWARM_AFTER_FRAMES = 12

/** Frames the prewarm is drawn. */
private const val PREWARM_FRAMES = 2

/** The prewarm's alpha: drawn (an alpha of 0 is skipped by the renderer), imperceptible. */
private const val PREWARM_ALPHA = 0.01f

/** A panel drawn at rest outside a host (component catalog, previews). */
@Composable
fun StaticModal(
    kind: ModalKind = ModalKind.Sheet,
    content: @Composable ModalScope.() -> Unit,
) {
    val scope = remember(kind) { ModalScope(ModalState(kind, reduced = false, initial = 1f, scope = null)) }
    scope.content()
}

/** ns per ms. */
private const val MODAL_NANOS_PER_MS = 1_000_000L
