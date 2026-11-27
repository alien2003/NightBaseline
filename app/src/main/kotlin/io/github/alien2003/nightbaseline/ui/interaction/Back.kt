package io.github.alien2003.nightbaseline.ui.interaction

import android.os.SystemClock
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException

/**
 * Something Back can close (ADR 0011 R4): a stack level, a section, a modal. The router hands one gesture to one
 * target, chosen at the gesture's start: the deepest enabled target in the active branch of the composition.
 */
interface NbBackTarget {
    /** Nesting depth: deeper targets win; modals are above every page. */
    val depth: Int

    /** True while this target can go back now (reads snapshot state). */
    fun enabled(): Boolean

    /** A gesture starts from [edge] ([BackEventCompat.EDGE_LEFT] or [BackEventCompat.EDGE_RIGHT]). */
    fun start(edge: Int)

    /** The finger moved to [fraction] (0..1 of the platform's back progress) at [timeMillis]. */
    fun progress(
        fraction: Float,
        timeMillis: Long,
    )

    /** The gesture was cancelled. */
    fun cancel()

    /** The gesture was committed. */
    fun commit()

    /** Back without a gesture (button, Esc, Alt+Left, 3-button navigation, TalkBack, a gesture under reduced motion). */
    fun back()

    /** Drops a running change at once (a layout change, ADR 0011 R6); the rest of a running gesture does nothing. */
    fun abort() {}
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

    internal fun add(target: NbBackTarget) {
        targets += target
    }

    internal fun remove(target: NbBackTarget) {
        targets -= target
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
 * The root back handler: one `PredictiveBackHandler` for the whole app. The release instant is the first of
 * commit and cancel; later progress events are ignored. Under reduced motion a gesture shows no preview and goes back
 * at once.
 */
@Composable
internal fun NbBackRoot(router: NbBackRouter) {
    val reduced by rememberUpdatedState(LocalNbMotion.current.reduced)
    val enabled = router.hasTarget
    PredictiveBackHandler(enabled) { events ->
        var target: NbBackTarget? = null
        try {
            events.collect { event ->
                if (reduced) return@collect
                val t = target ?: router.top()?.also { it.start(event.swipeEdge) }
                target = t
                t?.progress(event.progress, eventTime(event))
            }
        } catch (e: CancellationException) {
            target?.cancel()
            throw e
        }
        val t = target
        if (t != null) t.commit() else router.top()?.back()
    }
}

/** The event's frame time where the platform gives one, else the receipt time. */
private fun eventTime(event: BackEventCompat): Long {
    val frame = runCatching { event.frameTimeMillis }.getOrDefault(0L)
    return if (frame > 0L) frame else SystemClock.uptimeMillis()
}

/** A child branch of [LocalNbBackScope] that is active while [active] says so. */
@Composable
internal fun rememberChildScope(active: () -> Boolean): NbBackScope {
    val parent = LocalNbBackScope.current
    val current by rememberUpdatedState(active)
    return remember(parent) { NbBackScope(parent.depth + 1) { parent.active() && current() } }
}
