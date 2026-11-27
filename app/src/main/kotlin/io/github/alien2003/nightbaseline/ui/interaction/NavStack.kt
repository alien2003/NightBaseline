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
    NbStackHost(
        stack = stack,
        key = { it.id },
        onBack = { navController.popBackStack() },
        modifier = modifier,
        id = "pages",
        saveable = false,
        onSettled = { navigator.settleAll() },
    ) { entry ->
        val destination = entry.destination as? NbPageNavigator.Destination
        if (destination != null) entry.LocalOwnersProvider(holder) { destination.content(entry) }
    }
}
