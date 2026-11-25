package io.github.alien2003.nightbaseline.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.data.local.settings.GlassMode
import io.github.alien2003.nightbaseline.data.local.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the activity root needs before any screen: the theme, glass preference, onboarding and the newest night. */
data class RootState(
    /** Onboarding finished. */
    val onboardingDone: Boolean,
    /** Theme setting. */
    val theme: ThemeMode,
    /** Glass setting. */
    val glass: GlassMode,
    /** Wake date of the newest stored night of the source (a night deep link to it opens Home). */
    val newestNight: String?,
)

/** The root of the activity: settings that shape the whole app, and the platform status refresh on resume. */
@HiltViewModel
class AppRootViewModel
    @Inject
    constructor(
        private val data: AppData,
    ) : ViewModel() {
        /** Null until the settings are read. */
        val state: StateFlow<RootState?> =
            combine(data.settings, data.currentNights) { s, nights ->
                RootState(s.onboardingDone, s.theme, s.glassMode, nights.firstOrNull()?.date)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** Re-checks Health Connect, permissions, the source and notifications (start, resume). */
        fun refresh() {
            viewModelScope.launch { data.refresh() }
        }

        /** "Check now" from anywhere (Ctrl+R). */
        fun checkNow() = data.checkNow()
    }
