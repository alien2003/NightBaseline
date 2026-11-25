package io.github.alien2003.nightbaseline.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.SourceDiscovery
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.ui.settings.SourceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The first run (`screens.md` 7.5, `states.md` 7.3): Health Connect check and source discovery (step 3), the Health
 * Connect permission request (step 4, launched by the route with
 * `PermissionController.createRequestPermissionResultContract()` and [permissionsToRequest]), the notification prompt
 * (step 5, API 33+), then the first-run history read ([io.github.alien2003.nightbaseline.work.process.HistoryBackfill])
 * and, on "Done", the daily chain. Step, selection and the denial count live in the [SavedStateHandle], so returning
 * from Health Connect or a recreation keeps the step (`layouts.md` 8.1).
 */
@Suppress("TooManyFunctions") // One function per action of the six steps.
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val handle: SavedStateHandle,
        private val data: AppData,
    ) : ViewModel() {
        private val state =
            MutableStateFlow(
                OnboardingState(
                    step =
                        handle.get<String>(KEY_STEP)?.let { s -> OnboardingStep.entries.firstOrNull { it.name == s } }
                            ?: OnboardingStep.Welcome,
                    selectedSource = handle.get<String>(KEY_SOURCE),
                ),
            )
        private var requestable: Set<String> = HcPermissions.ALL
        private var denials: Int = handle.get<Int>(KEY_DENIALS) ?: 0
        private var sleepGranted: Boolean = false

        /** The screen state. */
        val ui: StateFlow<OnboardingState> = state.asStateFlow()

        init {
            if (state.value.step >= OnboardingStep.Source) checkAgain()
            if (state.value.step == OnboardingStep.Ready) readHistory()
        }

        /** The permissions the Health Connect request asks for (feature-gated ones only where they exist). */
        fun permissionsToRequest(): Set<String> = requestable

        /**
         * Before sleep may be read no source can be listed except the installed default: "Check again" on an empty
         * source step then asks for the permissions first.
         */
        fun needsPermissionsToList(): Boolean =
            state.value.healthConnect == HealthConnectStatus.Available && state.value.sources.isEmpty() && !sleepGranted

        /** Whether the permission step must open the Health Connect app (the dialog is no longer shown). */
        fun mustOpenHealthConnect(): Boolean = state.value.permission == PermissionStatus.DeniedAgain

        /** "Get started", "Next". */
        fun next() {
            when (state.value.step) {
                OnboardingStep.Welcome -> go(OnboardingStep.HowItWorks)
                OnboardingStep.HowItWorks -> go(OnboardingStep.Source).also { checkAgain() }
                OnboardingStep.Source -> useSource()
                OnboardingStep.Permissions -> toNotifications()
                OnboardingStep.Notifications -> go(OnboardingStep.Ready).also { readHistory() }
                OnboardingStep.Ready -> Unit
            }
        }

        /** "Back"; false at the first step. */
        fun back(): Boolean {
            val step = state.value.step
            if (step == OnboardingStep.Welcome || step == OnboardingStep.Ready) return false
            go(OnboardingStep.entries[step.ordinal - 1])
            return true
        }

        /** A source row. */
        fun select(option: SourceOption) {
            state.update { it.copy(selectedSource = option.packageName) }
            handle[KEY_SOURCE] = option.packageName
        }

        /** "Use {source}": stored, then the permission step. */
        fun useSource() {
            val selected = state.value.selectedSource ?: return
            viewModelScope.launch {
                data.settingsRepository.setSourcePackage(selected)
                go(OnboardingStep.Permissions)
                val granted = withContext(data.io) { runCatching { data.reader.grantedPermissions() }.getOrDefault(emptySet()) }
                if (HcPermissions.missingRequired(granted).isEmpty()) state.update { it.copy(permission = statusOf(granted)) }
            }
        }

        /** Health Connect availability and the source list, again. */
        fun checkAgain() {
            state.update { it.copy(healthConnect = HealthConnectStatus.Checking) }
            viewModelScope.launch {
                val availability = withContext(data.io) { data.reader.availability() }
                val status =
                    when (availability) {
                        HcAvailability.AVAILABLE -> HealthConnectStatus.Available
                        HcAvailability.UPDATE_REQUIRED -> HealthConnectStatus.NeedsUpdate
                        HcAvailability.NOT_SUPPORTED -> HealthConnectStatus.Unavailable
                    }
                if (status == HealthConnectStatus.Available) {
                    val features =
                        withContext(data.io) {
                            HcFeature.entries.filterTo(
                                mutableSetOf(),
                            ) { f -> runCatching { data.reader.isFeatureAvailable(f) }.getOrDefault(false) }
                        }
                    requestable = HcPermissions.requestable(features)
                    sleepGranted =
                        HcPermissions.READ_SLEEP in
                        withContext(data.io) { runCatching { data.reader.grantedPermissions() }.getOrDefault(emptySet()) }
                }
                val sources = if (status == HealthConnectStatus.Available) SourceDiscovery.discover(data) else emptyList()
                val current = state.value.selectedSource ?: data.settings.first().sourcePackage
                val selected = sources.firstOrNull { it.packageName == current } ?: sources.firstOrNull()
                state.update { it.copy(healthConnect = status, sources = sources, selectedSource = selected?.packageName) }
                selected?.let { handle[KEY_SOURCE] = it.packageName }
            }
        }

        /** The result of the Health Connect permission request. */
        fun onPermissionsResult(granted: Set<String>) {
            viewModelScope.launch {
                data.refresh()
                val all = withContext(data.io) { runCatching { data.reader.grantedPermissions() }.getOrDefault(granted) } + granted
                val core = HcPermissions.missingRequired(all).isEmpty()
                if (!core) {
                    denials++
                    handle[KEY_DENIALS] = denials
                    state.update { it.copy(permission = if (denials >= 2) PermissionStatus.DeniedAgain else PermissionStatus.Denied) }
                    return@launch
                }
                sleepGranted = true
                state.update { it.copy(permission = statusOf(all)) }
                // Now sleep can be read: the real list of sources. If the chosen app wrote nothing, pick again.
                val sources = SourceDiscovery.discover(data)
                val selected = state.value.selectedSource
                state.update { it.copy(sources = sources) }
                val chosenWroteSleep = sources.any { it.packageName == selected && it.lastSleep != null }
                if (!chosenWroteSleep && sources.any { it.lastSleep != null }) {
                    go(OnboardingStep.Source)
                }
            }
        }

        private fun statusOf(granted: Set<String>): PermissionStatus =
            if ((requestable - granted).isEmpty()) PermissionStatus.Granted else PermissionStatus.Partial

        private fun toNotifications() {
            go(OnboardingStep.Notifications)
            val access = data.platform.notificationAccess()
            if (access == NotificationAccess.Allowed || data.platform.sdkInt < NOTIFICATION_PERMISSION_SDK) {
                state.update { it.copy(notifications = NotificationAccess.Allowed) }
            }
        }

        /** The result of the notification prompt (API 33+). */
        fun onNotificationsResult(granted: Boolean) {
            data.platform.markNotificationsAsked()
            state.update { it.copy(notifications = if (granted) NotificationAccess.Allowed else NotificationAccess.Blocked) }
            viewModelScope.launch { data.refresh() }
            go(OnboardingStep.Ready)
            readHistory()
        }

        /** "Not now". */
        fun skipNotifications() {
            go(OnboardingStep.Ready)
            readHistory()
        }

        /** "Done": onboarding finished, the first check runs now and the daily chain continues from it. */
        fun done(onFinished: () -> Unit) {
            viewModelScope.launch {
                data.settingsRepository.setOnboardingDone(true)
                data.refresh()
                data.checkNow()
                onFinished()
            }
        }

        /** Reads the source's past nights (values never leave the database) and counts the valid ones. */
        private fun readHistory() {
            viewModelScope.launch {
                withContext(data.io) {
                    try {
                        data.backfill.runIfNeeded()
                    } catch (_: HcError) {
                        // Rate limit or a service error: the daily check reads the history later.
                    }
                }
                val source = data.settings.first().sourcePackage
                val valid = data.nightRepository.since(source, FIRST_DATE).count { it.summary.valid }
                state.update { it.copy(pastNights = valid) }
            }
        }

        private fun go(step: OnboardingStep) {
            state.update { it.copy(step = step) }
            handle[KEY_STEP] = step.name
        }

        private companion object {
            const val KEY_STEP = "step"
            const val KEY_SOURCE = "onboarding_source"
            const val KEY_DENIALS = "onboarding_denials"
            const val NOTIFICATION_PERMISSION_SDK = 33
            const val FIRST_DATE = "0000-01-01"
        }
    }
