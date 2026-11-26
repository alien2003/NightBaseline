package io.github.alien2003.nightbaseline.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.alien2003.nightbaseline.ui.shell.BackPreview
import io.github.alien2003.nightbaseline.ui.shell.BackPreviewHandler
import io.github.alien2003.nightbaseline.ui.shell.BackSettle
import io.github.alien2003.nightbaseline.ui.shell.ReleaseBackPreviewOnDispose
import io.github.alien2003.nightbaseline.ui.shell.backPreviewPage

/**
 * The bridge between the system back gesture and the NavHost's own predictive back (ADR 0009). NavHost seeks its
 * pop transition straight from the gesture progress, which would slide the page over the whole width under the
 * finger; the canon (`coloros-conformance.md` P3) is a preview instead: the page scales, rounds its corners and moves
 * a little, while the page underneath waits in its covered pose. So the NavHost listens to a private
 * [NavigationEventDispatcher] fed only by [input]: a gesture on the window reaches [BackPreviewHandler] (which draws
 * the preview), and the NavHost only learns that a gesture started (progress 0: both pages composed, the underlying
 * one at the start of `predictivePopEnter`), was cancelled (after the page is back at rest) or committed (the pop
 * plays the rest of the predictive transitions on `spring.page`).
 */
@Stable
internal class NavBackBridge(
    /** The window's dispatcher, given back to the destinations so their own Back handlers stay on it. */
    val outer: NavigationEventDispatcherOwner,
) : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher: NavigationEventDispatcher = NavigationEventDispatcher()

    /** Feeds [navigationEventDispatcher]. */
    val input: DirectNavigationEventInput = DirectNavigationEventInput()
}

/** The window's [NavigationEventDispatcherOwner], provided inside every destination by [BackPreviewDestination]. */
internal val LocalNavBackBridge = staticCompositionLocalOf<NavBackBridge?> { null }

/**
 * Hosts [content] (the NavHost) on a [NavBackBridge] and registers the preview handler for the NavHost's pops: enabled
 * only while there is an entry to pop, so at the root the window has no Back handler from here.
 */
@Composable
internal fun NavBackPreviewHost(
    navController: NavHostController,
    preview: BackPreview,
    content: @Composable () -> Unit,
) {
    val outer = checkNotNull(LocalNavigationEventDispatcherOwner.current) { "NavHost needs a NavigationEventDispatcherOwner" }
    val bridge = remember(outer) { NavBackBridge(outer) }
    DisposableEffect(bridge) {
        bridge.navigationEventDispatcher.addInput(bridge.input)
        onDispose {
            bridge.navigationEventDispatcher.removeInput(bridge.input)
            bridge.navigationEventDispatcher.dispose()
        }
    }
    val stack by navController.currentBackStack.collectAsStateWithLifecycle()
    val pages = stack.filter { it.destination.navigatorName == COMPOSE_NAVIGATOR }
    val top = pages.lastOrNull()
    BackPreviewHandler(
        preview = preview,
        enabled = pages.size > 1,
        key = top?.id ?: Unit,
        settle = BackSettle.Leave,
        onStarted = { edge -> bridge.input.backStarted(NavigationEvent(swipeEdge = edge, progress = 0f)) },
        onCancelled = { bridge.input.backCancelled() },
        onBack = { bridge.input.backCompleted() },
    )
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides bridge, LocalNavBackBridge provides bridge) {
        content()
    }
}

/**
 * One NavHost destination: back on the window's dispatcher (its own Back handlers and sheets keep working as
 * before) and drawn with the preview transform of its entry.
 */
@Composable
internal fun BackPreviewDestination(
    entry: NavBackStackEntry,
    preview: BackPreview,
    content: @Composable () -> Unit,
) {
    val bridge = LocalNavBackBridge.current
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().backPreviewPage(preview, entry.id)) { content() }
        ReleaseBackPreviewOnDispose(preview, entry.id)
    }
    if (bridge == null) {
        body()
    } else {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides bridge.outer, content = body)
    }
}

/** `ComposeNavigator.NAME`. */
private const val COMPOSE_NAVIGATOR = "composable"
