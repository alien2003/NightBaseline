@file:Suppress("TooManyFunctions") // The navigator, the host and the framework's page calls.

package io.github.alien2003.nightbaseline.ui.interaction

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.Navigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.navigation.get
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * The navigator of the app's pages (ADR 0011): navigation-compose keeps the back stack, the entries' lifecycles,
 * ViewModels and saved state; this navigator only records which entries are in a transition, and [NbNavStack] draws
 * them with [NbStackHost] (the Page kind) instead of NavHost, whose commit restarts every pop from rest. A popped
 * entry stays alive until the host has settled and dropped it (ADR 0011 R3); a pushed one is resumed only then, so a
 * second tap during a push does not push twice.
 */
@Navigator.Name(NbPageNavigator.NAME)
class NbPageNavigator : Navigator<NbPageNavigator.Destination>() {
    /** One page: its content for an entry. */
    class Destination(
        navigator: NbPageNavigator,
        internal val content: @Composable (NavBackStackEntry) -> Unit,
    ) : NavDestination(navigator)

    /** The pages in the stack. */
    internal val backStack get() = state.backStack

    /** The state machine of the host that draws the pages, and its layout side (set by [NbNavStack]). */
    internal var machine: NbTransition? = null
    internal var side: Int = 1

    /**
     * Applies the controller's stack to the pages' state machine at once (outside composition): a navigation made
     * through the framework's calls ([nbPush], [nbGo], [nbReset], [nbPopTo], [nbPop]) is an event of its input, so its
     * first frame is the next display frame and a Back right after it goes back from what it opened.
     */
    @Suppress("ReturnCount") // Batching, no machine, nothing changed.
    internal fun sync() {
        if (batching > 0) return
        val m = machine ?: return
        val keys = backStack.value.map { it.id }
        if (keys.isEmpty() || keys == m.state.stack) return
        m.dispatch(stackGo(m.state.stack, keys, side))
    }

    /** Nesting of [nbBatch]: the stack is applied to the machine once, when the outermost batch ends. */
    internal var batching = 0

    /** Entries whose change has not settled yet. */
    internal val inTransition get() = state.transitionsInProgress

    override fun createDestination(): Destination = Destination(this) {}

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?,
    ) {
        entries.forEach { state.pushWithTransition(it) }
    }

    override fun popBackStack(
        popUpTo: NavBackStackEntry,
        savedState: Boolean,
    ) {
        state.popWithTransition(popUpTo, savedState)
    }

    override fun onSaveState(): Bundle? = null

    /** Every pending change has settled. */
    internal fun settleAll() {
        inTransition.value.forEach { state.markTransitionComplete(it) }
    }

    companion object {
        /** The navigator name. */
        const val NAME: String = "nb-page"
    }
}

/** Builds a page destination for the typed route [route]. */
class NbPageBuilder(
    navigator: NbPageNavigator,
    route: KClass<*>,
    typeMap: Map<KType, NavType<*>>,
    private val content: @Composable (NavBackStackEntry) -> Unit,
) : NavDestinationBuilder<NbPageNavigator.Destination>(navigator, route, typeMap) {
    override fun instantiateDestination(): NbPageNavigator.Destination = NbPageNavigator.Destination(navigator as NbPageNavigator, content)
}

/** A page of route [T] in an [NbNavStack] graph. */
inline fun <reified T : Any> NavGraphBuilder.page(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    destination(NbPageBuilder(provider[NbPageNavigator::class], T::class, emptyMap(), content))
}

/** The [NbPageNavigator] of [this] controller, added on first use. */
fun NavHostController.pageNavigator(): NbPageNavigator {
    val existing = runCatching { navigatorProvider.getNavigator(NbPageNavigator::class.java) }.getOrNull()
    return existing ?: NbPageNavigator().also { navigatorProvider.addNavigator(it) }
}

/** A navigation controller with the framework's page navigator (ADR 0011). */
@Composable
fun rememberNbNavController(): NavHostController {
    val controller = rememberNavController()
    remember(controller) { controller.pageNavigator() }
    return controller
}

/**
 * The app's page stack on a navigation-compose controller, drawn by [NbStackHost]: every push and pop of every route
 * runs the Page kind; Back pops the controller. Deep links stay manual (the caller navigates).
 */
@Composable
fun NbNavStack(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = checkNotNull(LocalViewModelStoreOwner.current) { "NbNavStack needs a ViewModelStoreOwner" }
    navController.setLifecycleOwner(lifecycleOwner)
    navController.setViewModelStore(store.viewModelStore)
    val navigator = remember(navController) { navController.pageNavigator() }
    // Rebuilt when the builder changes (it captures the layout and the callbacks), as NavHost does; the controller
    // moves its entries onto the new destinations.
    val graph =
        remember(navController, startDestination, builder) { navController.createGraph(startDestination, null, emptyMap(), builder) }
    navController.graph = graph
    val stack by navigator.backStack.collectAsState()
    val holder = rememberSaveableStateHolder()
    StackHost(
        stack = stack,
        key = { it.id },
        // The machine has gone back already (one event, ADR 0011 N2); the controller follows at once, and its
        // entries stay in transition (the leaving page alive, the page below not resumed) until the motion settles.
        onBack = { navController.popBackStack() },
        modifier = modifier,
        id = "pages",
        backEnabled = true,
        saveable = false,
        onSettled = { navigator.settleAll() },
        machine = { machine, side ->
            navigator.machine = machine
            navigator.side = side
        },
    ) { entry ->
        val destination = entry.destination as? NbPageNavigator.Destination
        // The entry's lifecycle is capped at CREATED while the pages move (round 6 H2): nothing restarts in a commit.
        if (destination != null) entry.LocalOwnersProvider(holder) { NbCappedLifecycle { destination.content(entry) } }
    }
}

/*
 * The app's page navigation (ADR 0011 N7): the only calls that change the page stack. Each one changes the
 * controller and applies the result to the pages' state machine in the same input event. Direct calls of the
 * controller's navigate and popBackStack are forbidden outside this package (tools/interaction-forbidden.txt).
 */

/**
 * Pushes [route] from a tap. There is no lifecycle gate (round 6, M1): input during a motion follows the one rule of
 * every host, the machine catches it (a tap on the entering page pushes from it at once, on the same path). A tap only
 * reaches the logical top (the leaving layer blocks input), so a double tap on a row cannot push twice.
 */
fun <T : Any> NavHostController.nbPush(route: T) = nbGo(route)

/**
 * Runs the navigations of [block] (a deep link: back to a section, then a page over it) as one change of the pages'
 * machine (round 6 H1): the controller's stack after the block is applied once, so one motion runs instead of two.
 */
fun NavHostController.nbBatch(block: NavHostController.() -> Unit) {
    val navigator = pageNavigator()
    navigator.batching++
    try {
        block()
    } finally {
        navigator.batching--
    }
    navigator.sync()
}

/** Pushes [route] whatever runs (a deep link, a fold that re-projects a pane as a page). */
fun <T : Any> NavHostController.nbGo(route: T) {
    navigate(route)
    pageNavigator().sync()
}

/** Replaces the whole stack with [route] (onboarding done, onboarding restarted). */
fun <T : Any> NavHostController.nbReset(route: T) {
    navigate(route) { popUpTo(0) { inclusive = true } }
    pageNavigator().sync()
}

/** Pops back to the topmost entry of route [T] (kept); true when one was found. */
inline fun <reified T : Any> NavHostController.nbPopTo(): Boolean = nbPopTo(T::class)

/** [nbPopTo] for [route]. */
fun <T : Any> NavHostController.nbPopTo(route: KClass<T>): Boolean {
    val popped = popBackStack(route, inclusive = false)
    pageNavigator().sync()
    return popped
}

/**
 * Pops the top page without a Back (a layout change that folds a page into a pane, inside [nbReproject]). A Back of
 * the person goes through the router ([rememberNbBack]), never here.
 */
fun NavHostController.nbPop(): Boolean {
    val popped = popBackStack()
    pageNavigator().sync()
    return popped
}
