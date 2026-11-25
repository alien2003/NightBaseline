// Synthetic sample values for previews and screenshot tests.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.onboarding

import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.ui.settings.SettingsSamples

/**
 * Every onboarding state of `states.md` 7.3 built from synthetic sample data (the sources of [SettingsSamples]; no
 * real app, no health data), keyed by a stable id used in screenshot file names.
 */
object OnboardingSamples {
    private val base =
        OnboardingState(
            healthConnect = HealthConnectStatus.Available,
            sources = SettingsSamples.sources,
            selectedSource = SettingsSamples.sources.first().packageName,
        )

    /** id -> state, in flow order. */
    val all: Map<String, OnboardingState> =
        linkedMapOf(
            "welcome" to base.copy(step = OnboardingStep.Welcome),
            "how" to base.copy(step = OnboardingStep.HowItWorks),
            "source-checking" to
                base.copy(step = OnboardingStep.Source, healthConnect = HealthConnectStatus.Checking, sources = emptyList()),
            "source" to base.copy(step = OnboardingStep.Source),
            "source-none" to base.copy(step = OnboardingStep.Source, sources = emptyList(), selectedSource = null),
            "hc-update" to base.copy(step = OnboardingStep.Source, healthConnect = HealthConnectStatus.NeedsUpdate, sources = emptyList()),
            "hc-none" to base.copy(step = OnboardingStep.Source, healthConnect = HealthConnectStatus.Unavailable, sources = emptyList()),
            "perm" to base.copy(step = OnboardingStep.Permissions),
            "perm-denied" to base.copy(step = OnboardingStep.Permissions, permission = PermissionStatus.Denied),
            "perm-denied-again" to base.copy(step = OnboardingStep.Permissions, permission = PermissionStatus.DeniedAgain),
            "perm-partial" to base.copy(step = OnboardingStep.Permissions, permission = PermissionStatus.Partial),
            "notif" to base.copy(step = OnboardingStep.Notifications),
            "notif-denied" to base.copy(step = OnboardingStep.Notifications, notifications = NotificationAccess.Blocked),
            "ready-history" to base.copy(step = OnboardingStep.Ready, pastNights = 42),
            "ready-learn" to base.copy(step = OnboardingStep.Ready, pastNights = 3),
        )

    /** The states rendered on every device and font scale; the others render on the phone and the cover screen. */
    val key: Set<String> = setOf("welcome", "source", "perm-denied", "ready-learn")
}
