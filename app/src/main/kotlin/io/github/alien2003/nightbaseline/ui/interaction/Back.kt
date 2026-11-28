package io.github.alien2003.nightbaseline.ui.interaction

import android.os.SystemClock
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Something Back can close (ADR 0011 R4): a stack level, a section, a modal. The router hands one gesture to one
 * target, chosen at the gesture's start: the deepest enabled target in the active branch of the composition.
 */
interface NbBackTarget {
    /** Nesting depth: deeper targets win; modals are above every page. */
    val depth: Int

    /** True while this target can go back now (reads snapshot state). */
    fun enabled(): Boolean

    /** A gesture starts from [edge] ([BackEventCompat.EDGE_LEFT] or [BackEventCompat.EDGE_RIGHT]) at [touchX]. */
    fun start(
        edge: Int,
        touchX: Float,
    )

    /** The finger moved ([move]: the platform's progress, the touch position and the event time). */
    fun progress(move: NbBackMove)

    /** The gesture was cancelled. */
    fun cancel()

    /** The gesture was committed. */
    fun commit()

    /** Back without a gesture (button, Esc, Alt+Left, 3-button navigation, TalkBack, a gesture under reduced motion). */
    fun back()

    /** Drops a running change at once (a layout change, ADR 0011 R6); the rest of a running gesture does nothing. */
    fun abort() {}
}

/** One move of a back gesture. */
@Immutable
class NbBackMove(
    /** The platform's back progress, 0..1 (shaped by the system: not proportional to the finger on Android 14+). */
    val progress: Float,
    /** The finger's x in window px (NaN when the platform gives none: keyboard, accessibility). */
    val touchX: Float,
    /** Event time, ms. */
    val timeMillis: Long,
)

/**
 * The finger of one back gesture over a host (ADR 0011 rule "the page stays under the finger"): the travel is the
 * touch position's distance from where the gesture started, as a share of the host's width, so the page follows the
 * finger exactly whatever shape the platform gives its progress (Android 14+ makes it non-linear, with distance
 * thresholds and smoothing). Without a touch position the platform progress is used as it is.
 */
internal class NbBackFinger {
    private var x0 = Float.NaN
    private var dir = 1

    fun start(
        edge: Int,
        touchX: Float,
    ) {
        x0 = touchX
        dir = if (edge == BackEventCompat.EDGE_RIGHT) -1 else 1
    }

    /** The finger's travel share in 0..1 over a host [width] px wide. */
    fun share(
        move: NbBackMove,
        width: Float,
    ): Float {
        val x = move.touchX
        if (x.isNaN() || x0.isNaN() || width <= 0f) return move.progress.coerceIn(0f, 1f)
        return (dir * (x - x0) / width).coerceIn(0f, 1f)
    }
}

/** The branch of the composition a target lives in: its depth and whether that branch is the one shown. */
@Immutable
class NbBackScope(
    /** Depth of this branch. */
    val depth: Int,
    /** True while this branch is the shown one (read outside composition). */
    val active: () -> Boolean,
)

/** The current branch; the root is depth 0 and always active. */
val LocalNbBackScope = staticCompositionLocalOf { NbBackScope(0, { true }) }

/** Depth of modal targets: above every page. */
internal const val MODAL_DEPTH: Int = 10_000

/**
 * The one back router of the app (ADR 0011 R4). Every host registers its [NbBackTarget]; the root handler is enabled
 * only while some target is ([hasTarget]), so at the root nothing takes Back and the system's back-to-home plays.
 */
@Stable
class NbBackRouter {
    private val targets = mutableStateListOf<NbBackTarget>()

    /** True while a target can take Back (reads snapshot state). */
    val hasTarget: Boolean get() = targets.any { it.enabled() }

    /** The target a Back now goes to: the deepest enabled one, the latest registered on a tie. */
    fun top(): NbBackTarget? {
        var best: NbBackTarget? = null
        for (t in targets) if (t.enabled() && (best == null || t.depth >= best.depth)) best = t
        return best
    }

    /** Back without a gesture through the same targets (Esc, Alt+Left); false when nothing took it. */
    fun back(): Boolean {
        val target = top() ?: return false
        target.back()
        return true
    }

    /** Drops every running change (a fold or unfold during a gesture). */
    fun abort() {
        targets.toList().forEach { it.abort() }
    }

    /** Nesting of [nbReproject] calls on this app's tree (never global: another window's hosts are not affected). */
    internal var reprojectDepth: Int = 0

    /** True while this app re-projects its state after a layout change: its hosts snap the changes they see. */
    val reprojecting: Boolean get() = reprojectDepth > 0

    private val steps = mutableStateListOf<NbStepTarget>()

    /**
     * A decoded key (ADR 0011 one key path): Back goes to [back]; a pager step (arrows in the reading direction,
     * Page Up / Down) to the deepest shown sequence ([NbStepKeys]). False when nothing took it.
     *
     * One routing rule for every navigation key: it goes to the deepest enabled target, and an open modal is deeper
     * than every page, so while one is open ([modalOpen]) a step key reaches nothing behind it, exactly as Back closes
     * the modal instead of popping the page (review: the arrows used to step the night behind an open sheet).
     */
    fun key(intent: NbKeyIntent): Boolean {
        if (intent == NbKeyIntent.Back) return back()
        val delta = intent.stepDelta()
        var best: NbStepTarget? = null
        if (delta != null && !modalOpen()) for (t in steps) if (t.enabled() && (best == null || t.depth >= best.depth)) best = t
        return delta != null && best?.step?.invoke(if (delta < 0) -1 else 1) == true
    }

    /** True while an enabled modal (a sheet or dialog of the shown branch) is open. */
    fun modalOpen(): Boolean = targets.any { it.depth >= MODAL_DEPTH && it.enabled() }

    internal fun addStep(target: NbStepTarget) {
        steps += target
    }

    internal fun removeStep(target: NbStepTarget) {
        steps -= target
    }

    /** The release velocity of the last step swipe, for the Pager change it makes ([NbStepHandoff]). */
    internal val stepHandoff = NbStepHandoff()

    /** Tests: the targets that could take Back now (one per shown branch level). */
    internal fun enabledTargets(): Int = targets.count { it.enabled() }

    /** Runs whenever a target is added or removed (the root handler updates its enabled state at once). */
    internal var onTargetsChanged: (() -> Unit)? = null

    internal fun add(target: NbBackTarget) {
        targets += target
        onTargetsChanged?.invoke()
    }

    internal fun remove(target: NbBackTarget) {
        targets -= target
        onTargetsChanged?.invoke()
    }
}

/**
 * A step swipe's release speed, handed to the Pager change the step makes (the swipe and the pager are apart). Written
 * by the swipe (an input event) and consumed after the composition that set the change up was applied: the
 * composition only reads it ([peek]), so a discarded or repeated composition never loses it (review V4).
 */
internal class NbStepHandoff {
    private var speed = 0f
    private var at = Long.MIN_VALUE

    /** A step swipe released at [speedPx] px/s and makes a step now: its Pager change starts with that speed. */
    fun hand(speedPx: Float) {
        speed = speedPx
        at = NbClock.nowNanos()
    }

    /**
     * The hand-over of the step swipe that caused the Pager change starting now, as (its id, its speed in px/s), or
     * null when there is none or it came longer than [NbPhysics.ENTER_WAIT_MS] ago (the change has another cause). A
     * pure read: call it in composition and [consume] the id once that composition is applied.
     */
    fun peek(): Pair<Long, Float>? {
        val fresh = at != Long.MIN_VALUE && NbClock.nowNanos() - at <= NbPhysics.ENTER_WAIT_MS * NANOS_PER_MS
        return if (fresh) at to speed else null
    }

    /** The hand-over [id] (from [peek]) was used: it is cleared, unless a newer swipe replaced it. */
    fun consume(id: Long) {
        if (at != id) return
        speed = 0f
        at = Long.MIN_VALUE
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** The router of the app; provided by [NbInteractionRoot]. */
val LocalNbBackRouter = staticCompositionLocalOf<NbBackRouter?> { null }

/** Registers [target] with the app's router while the caller is composed. */
@Composable
internal fun RegisterBackTarget(target: NbBackTarget) {
    val router = LocalNbBackRouter.current ?: return
    DisposableEffect(router, target) {
        router.add(target)
        onDispose { router.remove(target) }
    }
}

/**
 * The root back handler: one callback on the activity's dispatcher for the whole app, enabled only while some target
 * can take Back. A gesture is handed to the router's top target at its start event (with the touch position where it
 * started, so the page follows the finger's travel from there); the release instant is the first of commit and
 * cancel. Under reduced motion a gesture shows no preview and goes back at once.
 */
@Composable
internal fun NbBackRoot(router: NbBackRouter) {
    val reduced by rememberUpdatedState(LocalNbMotion.current.reduced)
    val enabled = router.hasTarget
    val owner = LocalOnBackPressedDispatcherOwner.current ?: return
    val callback = remember(router) { RootBackCallback(router) { reduced } }
    SideEffect { callback.isEnabled = enabled }
    // A target registered or removed takes effect at once, not one composition of the root later: a Back right
    // after a modal opened (within a frame of its click, P1) reached nothing while the handler was still disabled.
    DisposableEffect(router, callback) {
        router.onTargetsChanged = { callback.isEnabled = router.hasTarget }
        onDispose { router.onTargetsChanged = null }
    }
    DisposableEffect(owner, callback) {
        owner.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

/** The one back callback of the app (ADR 0011 R4): routes start, moves, cancel and commit to one target. */
private class RootBackCallback(
    private val router: NbBackRouter,
    private val reduced: () -> Boolean,
) : OnBackPressedCallback(false) {
    private var target: NbBackTarget? = null

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        target = if (reduced()) null else router.top()?.also { it.start(backEvent.swipeEdge, backEvent.touchX) }
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        target?.progress(NbBackMove(backEvent.progress, backEvent.touchX, eventTime(backEvent)))
    }

    override fun handleOnBackCancelled() {
        val t = target
        target = null
        t?.cancel()
    }

    override fun handleOnBackPressed() {
        val t = target
        target = null
        if (t != null) t.commit() else router.back()
    }
}

/** The event's frame time where the platform gives one, else the receipt time. */
private fun eventTime(event: BackEventCompat): Long {
    val frame = runCatching { event.frameTimeMillis }.getOrDefault(0L)
    return if (frame > 0L) frame else SystemClock.uptimeMillis()
}

/**
 * A Back button's action: the router's Back (the same path as Esc, Alt+Left, 3-button Back and the gesture: one event
 * into the deepest host's state machine, ADR 0011 N7), or [fallback] when no target takes it. Every Back control of
 * the app uses it (the shell's round Back button and the settings back row do so themselves).
 */
@Composable
fun rememberNbBack(fallback: () -> Unit): () -> Unit {
    val router = LocalNbBackRouter.current
    val current by rememberUpdatedState(fallback)
    return remember(router) { { if (router?.back() != true) current() } }
}

/** A child branch of [LocalNbBackScope] that is active while [active] says so. */
@Composable
internal fun rememberChildScope(active: () -> Boolean): NbBackScope {
    val parent = LocalNbBackScope.current
    val current by rememberUpdatedState(active)
    return remember(parent) { NbBackScope(parent.depth + 1) { parent.active() && current() } }
}
