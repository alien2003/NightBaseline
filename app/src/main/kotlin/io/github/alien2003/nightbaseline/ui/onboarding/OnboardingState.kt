package io.github.alien2003.nightbaseline.ui.onboarding

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.ui.settings.SourceOption

/** The six steps of the first run (`screens.md` 7.5, `states.md` 7.3). */
enum class OnboardingStep(
    /** Number shown in "Step n of 6". */
    val number: Int,
) {
    /** `O-WELCOME`: what the app does, the full disclaimer. */
    Welcome(1),

    /** `O-HOW`. */
    HowItWorks(2),

    /** `O-SOURCE` / `O-NOSOURCE` / `O-HC-*`: Health Connect check and source picker. */
    Source(3),

    /** `O-PERM` and its denied / partial variants. */
    Permissions(4),

    /** `O-NOTIF`. */
    Notifications(5),

    /** `O-READY-HISTORY` / `O-READY-LEARN`. */
    Ready(6),
    ;

    /** Steps in total. */
    companion object {
        /** "of 6". */
        const val COUNT: Int = 6
    }
}

/** Health Connect availability (`HealthConnectClient.getSdkStatus()`), checked on the source step. */
enum class HealthConnectStatus {
    /** The check is running. */
    Checking,

    /** Available. */
    Available,

    /** `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` (`O-HC-UPDATE`). */
    NeedsUpdate,

    /** `SDK_UNAVAILABLE` (`O-HC-NONE`). */
    Unavailable,
}

/** Where the permission step is. */
enum class PermissionStatus {
    /** Not requested yet (`O-PERM`). */
    NotRequested,

    /** Sleep or heart rate denied after the request (`O-PERM-DENIED`, "Try again"). */
    Denied,

    /** Denied twice: Health Connect no longer shows the dialog, so the action opens its app permissions. */
    DeniedAgain,

    /** Core granted, some optional not (`O-PERM-PARTIAL`). */
    Partial,

    /** Everything granted. */
    Granted,
}

/** A "Check again" that changed nothing, said in words so the tap visibly did something. */
enum class SourceRecheck {
    /** Health Connect is still missing or still needs an update. */
    HealthConnectStillMissing,

    /** Health Connect works, but still no app wrote sleep to it. */
    StillNoSources,
}

/** What the first run shows. Stateless input of [OnboardingScreen]; the ViewModel builds it later. */
@Immutable
data class OnboardingState(
    /** Current step. */
    val step: OnboardingStep = OnboardingStep.Welcome,
    /** Health Connect availability. */
    val healthConnect: HealthConnectStatus = HealthConnectStatus.Checking,
    /** Sources that wrote sleep to Health Connect (or have access, before permissions). */
    val sources: List<SourceOption> = emptyList(),
    /** Package of the selected source. */
    val selectedSource: String? = null,
    /** Permission step. */
    val permission: PermissionStatus = PermissionStatus.NotRequested,
    /** Notification permission. */
    val notifications: NotificationAccess = NotificationAccess.NotAsked,
    /** Valid past nights found by the history read (ready step). */
    val pastNights: Int = 0,
    /**
     * Requestable optional permissions (measurements, background, past data) left off after the request: which rows
     * of the permission step show "off" and what the partial note names.
     */
    val missingOptional: Set<String> = emptySet(),
    /**
     * Health Connect works, but sleep may not be read yet, so no source can be listed (`O-SOURCE-ACCESS`): the step
     * asks for access instead of claiming there is no sleep data.
     */
    val sourcesNeedAccess: Boolean = false,
    /** What the last "Check again" found when nothing changed (a calm line under the step's text), or null. */
    val recheck: SourceRecheck? = null,
) {
    /** The selected source option, if any. */
    val selected: SourceOption? get() = sources.firstOrNull { it.packageName == selectedSource }

    /** Enough past nights for a range right away (`O-READY-HISTORY`), otherwise learning starts (`O-READY-LEARN`). */
    val rangeReady: Boolean get() = pastNights >= LEARNING_NIGHTS

    /** Constants. */
    companion object {
        /** Nights of the learning period. */
        const val LEARNING_NIGHTS: Int = 7
    }
}

/** Callbacks of [OnboardingScreen]. */
@Immutable
data class OnboardingActions(
    /** Next step ("Get started", "Next"). */
    val onNext: () -> Unit = {},
    /** Previous step. */
    val onBack: () -> Unit = {},
    /** Select a source row. */
    val onSelectSource: (SourceOption) -> Unit = {},
    /** "Use {source}". */
    val onUseSource: () -> Unit = {},
    /** Run the Health Connect check / source query again. */
    val onCheckAgain: () -> Unit = {},
    /** Open the Health Connect app (permissions). */
    val onOpenHealthConnect: () -> Unit = {},
    /** Open the store page to install or update Health Connect. */
    val onInstallHealthConnect: () -> Unit = {},
    /** Open About (Health Connect not available). */
    val onAbout: () -> Unit = {},
    /** Launch the Health Connect permission request. */
    val onRequestPermissions: () -> Unit = {},
    /** Launch the notification permission request (API 33+). */
    val onAllowNotifications: () -> Unit = {},
    /** "Not now" on the notification step. */
    val onSkipNotifications: () -> Unit = {},
    /** "Done": finish and open Home. */
    val onDone: () -> Unit = {},
)

/** The buttons of a step: optional Back (bottom start), optional secondary text action, the primary action. */
internal data class StepButtons(
    val primary: String?,
    val onPrimary: () -> Unit,
    val primaryEnabled: Boolean = true,
    val secondary: String? = null,
    val onSecondary: () -> Unit = {},
    val back: Boolean = false,
)
