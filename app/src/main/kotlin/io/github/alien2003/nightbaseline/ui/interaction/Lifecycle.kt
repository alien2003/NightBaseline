package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The lifecycle cap of one layer of a stack host (ADR 0011 round 6, H2): while the host moves, every page but the one
 * it showed at rest when the motion began is held at most CREATED, whatever its navigation entry does (Navigation
 * raises the revealed entry CREATED -> STARTED at the commit of a pop), and released once the host is at rest. So the
 * flows a page collects with its lifecycle and its STARTED effects never restart in a commit frame or while the page
 * moves: a page shows the state it has, and what arrives meanwhile is drawn after the motion.
 */
internal class NbLayerCap(
    private val transition: NbTransition,
    private val key: Any,
) {
    /**
     * The motion this layer was first composed in (an entering page of a push: it has no state yet and must load
     * it, so it is never capped in the motion that brings it), or -1 when it was composed at rest.
     */
    private val bornIn: Int = if (transition.running) transition.motionSerial else -1

    /** True while this layer is held at CREATED. */
    fun capped(): Boolean = transition.running && key != transition.restTop && bornIn != transition.motionSerial

    /** Runs [listener] whenever the host starts moving or comes to rest; returns its removal. */
    fun watch(listener: () -> Unit): () -> Unit = transition.onMoving { listener() }
}

/** The cap of the layer composed here (null outside a stack host). */
internal val LocalNbLayerCap = staticCompositionLocalOf<NbLayerCap?> { null }

/** A [LifecycleOwner] whose state is `min(source, CREATED)` while [capped] says so, else the source's. */
internal class NbCappedLifecycleOwner(
    private val source: LifecycleOwner,
    private val capped: () -> Boolean,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val observer = LifecycleEventObserver { _, _ -> sync() }

    /** Follows the source and the cap now. */
    fun sync() {
        val now = registry.currentState
        if (now == Lifecycle.State.DESTROYED) return
        val s = source.lifecycle.currentState
        val target = if (capped() && s.isAtLeast(Lifecycle.State.STARTED)) Lifecycle.State.CREATED else s
        // A registry that was never created cannot be destroyed; it simply stays as it is.
        if (target == Lifecycle.State.DESTROYED && now == Lifecycle.State.INITIALIZED) return
        if (target != now) registry.currentState = target
    }

    fun attach() {
        source.lifecycle.addObserver(observer)
        sync()
    }

    fun detach() {
        source.lifecycle.removeObserver(observer)
    }
}

/**
 * Provides [content] a [LocalLifecycleOwner] capped by the stack layer it is composed in ([NbLayerCap]); outside a
 * stack host it changes nothing. The app's pages wrap their entry's content in it ([NbNavStack]).
 */
@Composable
internal fun NbCappedLifecycle(content: @Composable () -> Unit) {
    val cap = LocalNbLayerCap.current
    if (cap == null) {
        content()
        return
    }
    val source = LocalLifecycleOwner.current
    val owner = remember(source, cap) { NbCappedLifecycleOwner(source) { cap.capped() } }
    DisposableEffect(owner) {
        owner.attach()
        val unwatch = cap.watch { owner.sync() }
        onDispose {
            unwatch()
            owner.detach()
        }
    }
    CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
}
