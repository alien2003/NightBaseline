package io.github.alien2003.nightbaseline.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.app.AccessStatus
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.NightPresenter
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.settings.SourceNames
import io.github.alien2003.nightbaseline.ui.shell.PerfProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Everything the night mapping of History depends on (its cache key; Home also depends on the time and maps anew). */
private data class MapKey(
    val settings: AppSettings,
    val access: AccessStatus?,
    val records: List<NightRecord>,
    val zone: ZoneId,
)

/** View options of History, kept in the [SavedStateHandle] (`layouts.md` 8.1). */
private data class HistoryOptions(
    val view: HistoryView,
    val filter: HistoryFilter,
    val month: YearMonth?,
    val selected: LocalDate?,
)

/**
 * History: every stored night (an earlier source's nights stay listed after a source change), newest first, the
 * List / Calendar view, the filter, the visible month and the selected night (saved state), and the access banner of
 * `states.md` 5 (`E-PERM`, `E-HC-UPDATE`; `E-HC-NONE` shows `H-EMPTY`).
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val handle: SavedStateHandle,
        private val data: AppData,
    ) : ViewModel() {
        private val options =
            MutableStateFlow(
                HistoryOptions(
                    view = handle.get<String>(KEY_VIEW)?.let { v -> HistoryView.entries.firstOrNull { it.name == v } } ?: HistoryView.List,
                    filter =
                        handle.get<String>(KEY_FILTER)?.let { f ->
                            HistoryFilter.entries.firstOrNull { it.name == f }
                        } ?: HistoryFilter.All,
                    month = handle.get<String>(KEY_MONTH)?.let(YearMonth::parse),
                    selected = handle.get<String>(KEY_SELECTED)?.let(LocalDate::parse),
                ),
            )

        /** The last inputs and their nights: a re-subscription (screen back after the stop timeout) maps nothing. */
        @Volatile
        private var last: Pair<MapKey, List<NightUi>>? = null

        private val nights =
            combine(data.settings, data.access.status, data.allNights) { settings, access, all ->
                val zone = data.zoneId()
                val key = MapKey(settings, access, all, zone)
                val cached = last?.takeIf { it.first == key }?.second
                val list =
                    cached ?: run {
                        PerfProbe.hit(MAP_PROBE)
                        val records = NightPresenter.mergeSources(all, settings.sourcePackage)
                        NightPresenter.nights(records, settings.algoParams(), zone, access).also { last = key to it }
                    }
                Triple(list, access, settings)
            }

        /** The screen state ([HistoryUiState.loading] until the first read). */
        val ui: StateFlow<HistoryUiState> =
            combine(nights, options) { (list, access, settings), o ->
                val today = data.now().atZone(data.zoneId()).toLocalDate()
                val problem = NightPresenter.problemState(access)
                HistoryUiState(
                    nights = if (problem == NightState.HealthConnectNone) emptyList() else list,
                    source = SourceNames.display(access?.sourceLabel, settings.sourcePackage),
                    today = today,
                    view = o.view,
                    filter = o.filter,
                    month = o.month ?: YearMonth.from(list.firstOrNull()?.wakeDate ?: today),
                    selected = o.selected,
                    loading = false,
                    access =
                        when (problem) {
                            NightState.Permission -> HistoryAccess.Permission
                            NightState.HealthConnectUpdate -> HistoryAccess.HealthConnectUpdate
                            else -> null
                        },
                )
            }.flowOn(data.compute)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    HistoryUiState(emptyList(), "", LocalDate.now(data.clock), loading = true),
                )

        /** The stored nights alone (for the Night detail pane of two-pane History). */
        val list: StateFlow<List<NightUi>> =
            nights
                .map { it.first }
                .flowOn(
                    data.compute,
                ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** List / Calendar. */
        fun setView(view: HistoryView) = update { copy(view = view) }.also { handle[KEY_VIEW] = view.name }

        /** Filter chip. */
        fun setFilter(filter: HistoryFilter) = update { copy(filter = filter) }.also { handle[KEY_FILTER] = filter.name }

        /** Calendar month. */
        fun setMonth(month: YearMonth) = update { copy(month = month) }.also { handle[KEY_MONTH] = month.toString() }

        /** Selected night (detail pane, tabletop top half, calendar reflow). */
        fun select(date: LocalDate) = update { copy(selected = date) }.also { handle[KEY_SELECTED] = date.toString() }

        private fun update(change: HistoryOptions.() -> HistoryOptions) {
            options.value = options.value.change()
        }

        /** Keys and constants. */
        companion object {
            /** [PerfProbe] name of one mapping of the stored nights. */
            const val MAP_PROBE: String = "HistoryViewModel.nights"

            private const val KEY_VIEW = "history_view"
            private const val KEY_FILTER = "history_filter"
            private const val KEY_MONTH = "history_month"
            private const val KEY_SELECTED = "history_selected"
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
