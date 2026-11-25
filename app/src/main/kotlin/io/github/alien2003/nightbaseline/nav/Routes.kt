package io.github.alien2003.nightbaseline.nav

import io.github.alien2003.nightbaseline.ui.components.TopLevel
import kotlinx.serialization.Serializable

/** Typed destinations of the navigation graph (`screens.md` 2). Arguments are strings so they survive process death. */
@Serializable
data object HomeRoute

/** History. */
@Serializable
data object HistoryRoute

/** Settings; [page] is a `SettingsPage` name (`settings/source`, `settings/sensitivity`, ...). */
@Serializable
data class SettingsRoute(
    /** Sub-page. */
    val page: String = "Main",
)

/** `night/{date}`: one stored night; [section] is the top-level section it was opened from. */
@Serializable
data class NightRoute(
    /** Wake date, ISO. */
    val date: String,
    /** `TopLevel` name selected in the bar or rail. */
    val section: String = TopLevel.History.name,
)

/** `metric/{metricId}?date&period`: Metric detail of [metric] anchored at [date] (null: last night). */
@Serializable
data class MetricRoute(
    /** `MetricId.key`. */
    val metric: String,
    /** Anchor wake date, ISO, or null for the newest night. */
    val date: String? = null,
    /** `TopLevel` name selected in the bar or rail. */
    val section: String = TopLevel.Home.name,
)

/** `onboarding/{step}`: the first run (outside the shell); [step] is an `OnboardingStep` name. */
@Serializable
data class OnboardingRoute(
    /** First step shown. */
    val step: String = "Welcome",
)
