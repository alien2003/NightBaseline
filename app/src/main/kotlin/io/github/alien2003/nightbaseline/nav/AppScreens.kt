package io.github.alien2003.nightbaseline.nav

import androidx.navigation.NavHostController
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import java.time.LocalDate
import kotlin.reflect.KClass

/** One screen of the app and how to open it with sample arguments (night [date], a metric [metric] key). */
class AppScreen(
    /** A stable name for reports. */
    val name: String,
    /**
     * The stored nights the screen needs, null for the whole sample: a state of a screen that only some data shows
     * (Home while the baseline is still learning) is a screen of its own.
     */
    val nights: Int? = null,
    /** Opens the screen on [NavHostController] from the app's start. */
    val open: NavHostController.(date: LocalDate, metric: String) -> Unit,
)

/**
 * The screen registry of the app (ADR 0011): every destination of the navigation graph with every value of its
 * page argument (each section, settings sub-page and onboarding step) and every data state that shows a screen of its
 * own (Learning), so the interaction tests enumerate the screens from here and never from a hand-written list.
 * `InteractionSweep` checks both directions: every destination of the graph is one of [routes], and every route is
 * opened by some entry of [all]; a new destination fails the tests until it is registered and opened here.
 */
object AppScreens {
    /** The route classes the registry opens. */
    val routes: Set<KClass<*>> =
        setOf(TopLevelRoute::class, SettingsRoute::class, NightRoute::class, MetricRoute::class, OnboardingRoute::class)

    /** Every screen. */
    val all: List<AppScreen> =
        buildList {
            TopLevel.entries.forEach { tab -> add(AppScreen("section ${tab.name}") { _, _ -> toTop(tab) }) }
            // Home while the baseline learns (fewer nights than the algorithm needs): the Learning screen.
            add(AppScreen("section Home learning", nights = LEARNING_NIGHTS) { _, _ -> toTop(TopLevel.Home) })
            SettingsPage.entries.filter { it != SettingsPage.Main }.forEach { page ->
                add(AppScreen("settings ${page.name}") { _, _ -> navigate(SettingsRoute(page.name)) })
            }
            add(AppScreen("night") { date, _ -> navigate(NightRoute(date.toString(), TopLevel.History.name)) })
            add(AppScreen("metric") { date, metric -> navigate(MetricRoute(metric, date.toString(), TopLevel.Home.name)) })
            OnboardingStep.entries.forEach { step ->
                add(AppScreen("onboarding ${step.name}") { _, _ -> navigate(OnboardingRoute(step.name, rerun = true)) })
            }
        }

    /** Stored nights of the Learning state: fewer than the algorithm's minimum of 7. */
    private const val LEARNING_NIGHTS = 3
}
