package io.github.alien2003.nightbaseline.ui.night

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.NightPresenter
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.settings.SourceNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Night detail (`night/{date}`): the stored night of that date (the configured source first, an earlier source's
 * night otherwise), its stored neighbours for previous / next, and the banners Night detail shows. The date lives in
 * the [SavedStateHandle] (route argument, updated by previous / next), so it survives recreation.
 */
@HiltViewModel
class NightDetailViewModel
    @Inject
    constructor(
        private val handle: SavedStateHandle,
        private val data: AppData,
    ) : ViewModel() {
        private val date = MutableStateFlow(handle.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: LocalDate.now(data.clock))
        private val section =
            handle.get<String>(KEY_SECTION)?.let { s -> TopLevel.entries.firstOrNull { it.name == s } } ?: TopLevel.History

        /** The screen state; null until the first read. */
        val ui: StateFlow<NightDetailState?> =
            combine(date, data.settings, data.access.status, data.allNights, data.recentRuns) { d, settings, access, all, runs ->
                val records = NightPresenter.mergeSources(all, settings.sourcePackage)
                val nights = NightPresenter.nights(records, settings.algoParams(), data.zoneId(), access)
                val night = nights.firstOrNull { it.wakeDate == d } ?: NightPresenter.placeholder(d, NightState.NoData)
                val older = nights.firstOrNull { it.wakeDate < d }?.wakeDate
                val newer = nights.lastOrNull { it.wakeDate > d }?.wakeDate
                val banner = NightPresenter.resolveHome(data.now(), data.zoneId(), settings, access, nights, false, runs).banner
                NightDetailState(
                    night = night,
                    source = SourceNames.display(access?.sourceLabel, settings.sourcePackage),
                    previous = older,
                    next = newer,
                    banner = banner,
                    section = section,
                )
            }.flowOn(data.compute)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** The shown night's date. */
        val shownDate: StateFlow<LocalDate> get() = date

        /** Moves to another night (previous / next, Left / Right, swipe). */
        fun show(night: LocalDate) {
            date.value = night
            handle[KEY_DATE] = night.toString()
        }

        /** Dismisses a dismissible banner. */
        fun dismissBanner(kind: BannerKind) {
            viewModelScope.launch {
                if (kind == BannerKind.NotificationsOff) {
                    data.settingsRepository.setNotificationBannerDismissedAt(data.now().toEpochMilli())
                }
            }
        }

        /** Saved-state keys (the route's argument names). */
        companion object {
            /** `NightRoute.date`. */
            const val KEY_DATE: String = "date"

            /** `NightRoute.section`. */
            const val KEY_SECTION: String = "section"

            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
