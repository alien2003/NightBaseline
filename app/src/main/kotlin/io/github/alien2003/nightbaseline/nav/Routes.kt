package io.github.alien2003.nightbaseline.nav

import io.github.alien2003.nightbaseline.ui.components.TopLevel
import kotlinx.serialization.Serializable

/**
 * Typed destinations of the navigation graph (`screens.md` 2). Arguments are strings so they survive process death.
 *
 * The top-level sections Home, History and the Settings list share this one destination ([TopLevelHost], ADR 0006);
 * the selected section is saved state of its back stack entry ([TOP_LEVEL_TAB], set by [toTop]).
 */
@Serializable
data object TopLevelRoute

/** A pushed Settings sub-page; [page] is a `SettingsPage` name (`settings/source`, `settings/about`, ...). */
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

/**
 * `metric/{metricId}?date&period`: Metric detail of [metric] anchored at [date] (null: last night). [period] and
 * [point] carry the chart period and selected night over from Home's detail pane when a fold hands it over.
 */
@Serializable
data class MetricRoute(
    /** `MetricId.key`. */
    val metric: String,
    /** Anchor wake date, ISO, or null for the newest night. */
    val date: String? = null,
    /** `TopLevel` name selected in the bar or rail. */
    val section: String = TopLevel.Home.name,
    /** `ChartPeriod` name, or null for the default period. */
    val period: String? = null,
    /** Index of the selected chart night in the period, or -1 for none. */
    val point: Int = -1,
)

/** `onboarding/{step}`: the first run (outside the shell); [step] is an `OnboardingStep` name. */
@Serializable
data class OnboardingRoute(
    /** First step shown. */
    val step: String = "Welcome",
)
