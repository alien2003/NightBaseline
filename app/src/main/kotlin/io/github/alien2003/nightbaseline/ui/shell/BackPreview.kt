package io.github.alien2003.nightbaseline.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.motion.NB_BACK_CORNER_DP
import io.github.alien2003.nightbaseline.ui.motion.NB_BACK_SCALE
import io.github.alien2003.nightbaseline.ui.motion.NB_BACK_TRAVEL
import io.github.alien2003.nightbaseline.ui.motion.NbSprings
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.SmoothCornerShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a committed back gesture finishes (ADR 0009). */
enum class BackSettle {
    /**
     * The back action starts a transition that takes the leaving page away (NavHost pop, Settings sub-page pop,
     * section switch): the page keeps its preview pose until it leaves, then [BackPreview.release] clears it.
     */
    Leave,

    /** The back action swaps the content in place (onboarding step, two-pane detail, catalog): the pose springs back. */
    InPlace,
}

/** Phase of a [BackPreview]. */
enum class BackPhase {
    /** No gesture; every page is at rest. */
    Idle,

    /** The finger is down: the page follows the gesture progress. */
    Dragging,

    /** Cancelled: the page returns to rest on `motion.back.cancel`. */
    Cancelling,

    /** Committed with [BackSettle.Leave]: the pose is held while the page leaves. */
    Committed,

    /** Committed with [BackSettle.InPlace]: the pose springs back on `motion.back.release`. */
    Settling,
}

/**
 * The pose of a page during a predictive back preview (`coloros-conformance.md` P3, ADR 0009), as applied in the draw
 * layer and reported to [BackPreviewProbe].
 *
 * @property progress gesture progress 0..1 carried by the page.
 * @property scale scale towards [NB_BACK_SCALE].
 * @property travel signed share of the page width the page moves with the finger (towards [NB_BACK_TRAVEL]).
 * @property cornerDp corner radius towards `shape.xl`.
 */
@Immutable
data class BackPose(
    val progress: Float,
    val scale: Float,
    val travel: Float,
    val cornerDp: Float,
) {
    /** True at rest (no transform at all). */
    val atRest: Boolean get() = progress <= 0f

    companion object {
        /** The pose at rest. */
        val Rest: BackPose = BackPose(0f, 1f, 0f, 0f)

        /** The pose at [progress] for a finger moving in [direction] (+1 right, -1 left). */
        fun of(
            progress: Float,
            direction: Int,
        ): BackPose {
            val p = progress.coerceIn(0f, 1f)
            if (p <= 0f) return Rest
            return BackPose(p, 1f - (1f - NB_BACK_SCALE) * p, direction * NB_BACK_TRAVEL * p, NB_BACK_CORNER_DP * p)
        }
    }
}

/**
 * Test hook: every evaluation of a preview layer reports its key and pose (a page's [BackPose], or the uncovered
 * section's state through [underSink]). A no-op in the app: nothing installs a sink.
 */
object BackPreviewProbe {
    /** Receives (page key, pose); set by tests only. */
    @Volatile
    var sink: ((key: Any, pose: BackPose) -> Unit)? = null

    /** Receives (section key, uncovered amount 0..1: 1 = parallax and dim of a covered page, 0 = at rest). */
    @Volatile
    var underSink: ((key: Any, under: Float) -> Unit)? = null
}

/**
 * One predictive back preview for a group of pages (ADR 0009): which page follows the gesture ([key]), the gesture
 * [progress] and the swipe [edge]. Only the draw layers of the pages read [progress], so a gesture frame never
 * recomposes page content. One gesture at a time; a new gesture during a cancel of the same page resumes from where
 * the page is, any other takes over.
 */
@Stable
class BackPreview internal constructor(
    private val scope: CoroutineScope,
) {
    /** The page that carries the pose, or null at rest. */
    var key: Any? by mutableStateOf(null)
        private set

    /** Phase of the current gesture. */
    var phase: BackPhase by mutableStateOf(BackPhase.Idle)
        private set

    /** Gesture progress carried by [key], 0 at rest. */
    val progress: Animatable<Float, *> = Animatable(0f)

    /**
     * The uncovered page's covered pose (1 = parallax and dim, 0 = at rest) for hosts that draw the page under the
     * leaving one themselves (the top-level sections); 1 during the gesture, springs to 0 on a commit.
     */
    val under: Animatable<Float, *> = Animatable(0f)

    /** Swipe edge of the current gesture ([BackEventCompat.EDGE_LEFT], [BackEventCompat.EDGE_RIGHT] or none). */
    var edge: Int by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
        private set

    private var token = 0
    private var cancelHook: (() -> Unit)? = null
    private var settleJob: Job? = null
    private var underJob: Job? = null

    /** The direction the finger moves: right from the left edge, left from the right edge. */
    val direction: Int get() = if (edge == BackEventCompat.EDGE_RIGHT) -1 else 1

    /** The pose of the page [pageKey] now (rest unless it carries the preview). Reads state: call in the draw layer. */
    fun poseOf(pageKey: Any): BackPose = if (key == pageKey) BackPose.of(progress.value, direction) else BackPose.Rest

    /** True while touches on [pageKey] are blocked (the page follows or settles from a gesture). */
    internal fun blocks(pageKey: Any): Boolean = key == pageKey && phase != BackPhase.Idle

    /**
     * Starts a gesture on [pageKey]. Returns the gesture token and whether it resumed a cancel of the same page (then
     * the host's preparation is still in place and must not run again).
     */
    internal fun begin(
        pageKey: Any,
        swipeEdge: Int,
        onCancelled: () -> Unit,
    ): Pair<Int, Boolean> {
        val resume = phase == BackPhase.Cancelling && key == pageKey
        settleJob?.cancel()
        settleJob = null
        if (!resume) finishPending()
        token++
        key = pageKey
        edge = swipeEdge
        phase = BackPhase.Dragging
        cancelHook = onCancelled
        if (!resume) {
            snap(progress, 0f)
            underJob?.cancel()
            snap(under, 1f)
        }
        return token to resume
    }

    /** The finger moved: the page follows [fraction] directly (no animation). */
    internal fun follow(
        gesture: Int,
        fraction: Float,
    ) {
        if (gesture != token || phase != BackPhase.Dragging) return
        snap(progress, fraction.coerceIn(0f, 1f))
    }

    /** The gesture was cancelled: the page returns to rest, then the host's cancel hook runs. */
    internal fun cancel(gesture: Int) {
        if (gesture != token || phase != BackPhase.Dragging) return
        phase = BackPhase.Cancelling
        settleJob =
            scope.launch {
                progress.animateTo(0f, NbSprings.BackCancel.spec())
                if (gesture == token && phase == BackPhase.Cancelling) finishPending()
            }
    }

    /**
     * The gesture was committed. Returns false when the gesture is no longer current (aborted by a layout change):
     * then the back action must not run.
     */
    internal fun commit(
        gesture: Int,
        settle: BackSettle,
    ): Boolean {
        if (gesture != token || phase != BackPhase.Dragging) return false
        cancelHook = null
        underJob?.cancel()
        underJob = scope.launch { under.animateTo(0f, NbSprings.BackRelease.spec()) }
        when (settle) {
            BackSettle.Leave -> {
                phase = BackPhase.Committed
                // Safety net: a page that never leaves (the host did not navigate) must not stay scaled.
                settleJob =
                    scope.launch {
                        delay(LEAVE_TIMEOUT_MS)
                        if (gesture == token && phase == BackPhase.Committed) {
                            phase = BackPhase.Settling
                            progress.animateTo(0f, NbSprings.BackRelease.spec())
                            if (gesture == token) reset()
                        }
                    }
            }

            BackSettle.InPlace -> {
                phase = BackPhase.Settling
                settleJob =
                    scope.launch {
                        progress.animateTo(0f, NbSprings.BackRelease.spec())
                        if (gesture == token) reset()
                    }
            }
        }
        return true
    }

    /** The committed page [pageKey] has left (disposed or hidden): its pose is cleared. */
    fun release(pageKey: Any) {
        if (key != pageKey || phase != BackPhase.Committed) return
        settleJob?.cancel()
        settleJob = null
        reset()
    }

    /**
     * Drops any gesture at once (the window changed size, fold or rotation, during the gesture): every page is at
     * rest, a pending cancel hook runs, and a later commit of that gesture does nothing.
     */
    fun abort() {
        if (phase == BackPhase.Idle) return
        token++
        settleJob?.cancel()
        settleJob = null
        underJob?.cancel()
        snap(under, 0f)
        finishPending()
    }

    private fun finishPending() {
        val hook = cancelHook
        cancelHook = null
        reset()
        hook?.invoke()
    }

    private fun reset() {
        key = null
        phase = BackPhase.Idle
        snap(progress, 0f)
    }

    private fun snap(
        animatable: Animatable<Float, *>,
        value: Float,
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { animatable.snapTo(value) }
    }

    private companion object {
        /** Longest a committed page may keep its pose without leaving (the page spring settles in about 470 ms). */
        const val LEAVE_TIMEOUT_MS = 1000L
    }
}

/** A [BackPreview] tied to the caller's composition. */
@Composable
fun rememberBackPreview(): BackPreview {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackPreview(scope) }
}

/**
 * A system Back handler that previews going back a level (ADR 0009): while the predictive back gesture runs, the
 * page [key] of [preview] follows it; a commit runs [onBack] (and the page settles as [settle] says); a cancel
 * returns the page on `motion.back.cancel` and then runs [onCancelled]. [onStarted] runs once at the start of a
 * gesture (with the swipe edge) so the host can put the page underneath in place. Back without a gesture (button,
 * keyboard, TalkBack) runs [onBack] directly; so does every gesture under reduced motion (no preview).
 *
 * Register it only where there is a level to go back to: at the root no handler is enabled, so the system's own
 * back-to-home animation plays.
 */
@Composable
fun BackPreviewHandler(
    preview: BackPreview,
    enabled: Boolean,
    key: Any,
    settle: BackSettle,
    onStarted: (edge: Int) -> Unit = {},
    onCancelled: () -> Unit = {},
    onBack: () -> Unit,
) {
    val reduced by rememberUpdatedState(NbTheme.motion.reduced)
    val currentKey by rememberUpdatedState(key)
    val started by rememberUpdatedState(onStarted)
    val cancelled by rememberUpdatedState(onCancelled)
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        var gesture = -1
        try {
            events.collect { event ->
                if (reduced) return@collect
                if (gesture < 0) {
                    val (token, resumed) = preview.begin(currentKey, event.swipeEdge) { cancelled() }
                    gesture = token
                    if (!resumed) started(event.swipeEdge)
                }
                preview.follow(gesture, event.progress)
            }
        } catch (e: CancellationException) {
            if (gesture >= 0) preview.cancel(gesture)
            throw e
        }
        if (gesture < 0 || preview.commit(gesture, settle)) back()
    }
}

/** Clears a committed preview of [key] when the caller leaves the composition (the page has left). */
@Composable
fun ReleaseBackPreviewOnDispose(
    preview: BackPreview,
    key: Any,
) {
    DisposableEffect(preview, key) { onDispose { preview.release(key) } }
}

/**
 * The preview transform of page [key] (`coloros-conformance.md` P3): towards [NB_BACK_SCALE], `shape.xl` corners and
 * [NB_BACK_TRAVEL] of the width with the finger, read in the draw layer only. At rest the layer has no transform and
 * no clip, so the page draws exactly as without it. While the page carries a gesture, touches on it are swallowed, so
 * a press under the finger is cancelled and nothing on a leaving page can be tapped.
 */
fun Modifier.backPreviewPage(
    preview: BackPreview,
    key: Any,
): Modifier =
    this
        .graphicsLayer {
            val pose = preview.poseOf(key)
            BackPreviewProbe.sink?.invoke(key, pose)
            if (pose.atRest) return@graphicsLayer
            scaleX = pose.scale
            scaleY = pose.scale
            translationX = pose.travel * size.width
            shape = SmoothCornerShape(pose.cornerDp.dp)
            clip = true
        }.pointerInput(preview, key) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (preview.blocks(key)) event.changes.forEach { it.consume() }
                }
            }
        }
