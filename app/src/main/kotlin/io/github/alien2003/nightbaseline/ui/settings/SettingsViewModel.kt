package io.github.alien2003.nightbaseline.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.SourceDiscovery
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.data.local.settings.GlassMode
import io.github.alien2003.nightbaseline.data.local.settings.SensitivityLevel
import io.github.alien2003.nightbaseline.data.local.settings.ThemeMode
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Page, section, discovered sources and the pending source change (saved where it matters). */
private data class SettingsOptions(
    val page: SettingsPage,
    val section: SettingsSection,
    val sources: List<SourceOption> = emptyList(),
    val pending: SourceOption? = null,
)

/**
 * Settings: source (a change keeps every stored night and restarts learning for the new source, then reads its
 * history right away), notification switches and the system state, sensitivity (with the local preview over the
 * last 30 nights), theme, glass Automatic / Off, about, privacy and licenses pages.
 */
@Suppress("TooManyFunctions") // One function per setting.
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val handle: SavedStateHandle,
        private val data: AppData,
    ) : ViewModel() {
        private val options =
            MutableStateFlow(
                SettingsOptions(
                    page =
                        handle.get<String>(KEY_PAGE)?.let { p ->
                            SettingsPage.entries.firstOrNull { it.name == p }
                        } ?: SettingsPage.Main,
                    section =
                        handle.get<String>(KEY_SECTION)?.let { s -> SettingsSection.entries.firstOrNull { it.name == s } }
                            ?: SettingsSection.Source,
                ),
            )

        /**
         * The open page, synchronously: the screen and its Back handler read it from here, so a sub-page opened by a
         * tap is shown and handled by Back in the same frame (the [ui] state reaches it later, through the compute
         * dispatcher).
         */
        val page: StateFlow<SettingsPage> =
            options
                .map { it.page }
                .stateIn(viewModelScope, SharingStarted.Eagerly, options.value.page)

        init {
            if (options.value.page == SettingsPage.SourcePicker || options.value.section == SettingsSection.Source) loadSources()
        }

        /** The screen state; null until the first read. */
        val ui: StateFlow<SettingsState?> =
            combine(options, data.settings, data.access.status, data.currentNights, data.recentRuns) { o, s, access, records, runs ->
                SettingsState(
                    page = o.page,
                    section = o.section,
                    sourceLabel = access?.sourceLabel ?: s.sourcePackage,
                    sourcePackage = s.sourcePackage,
                    sources = o.sources,
                    lastRead = data.lastRead(runs),
                    notifyResults = s.notificationsEnabled,
                    notifyProblems = s.problemNotificationsEnabled,
                    notificationAccess = access?.notifications ?: NotificationAccess.Allowed,
                    sensitivity = presetOf(s.sensitivity),
                    previewNotes = preview(s, records),
                    previewNights = records.size.coerceIn(1, SettingsState.PREVIEW_NIGHTS),
                    theme = themeOf(s.theme),
                    glass = if (s.glassMode == GlassMode.OFF) GlassPreference.Off else GlassPreference.Automatic,
                    version = data.platform.versionName,
                    pendingSource = o.pending,
                )
            }.flowOn(data.compute)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Opens a page (single pane) or the detail pane content (two panes). */
        fun openPage(page: SettingsPage) {
            options.update { it.copy(page = page, section = page.section ?: it.section) }
            handle[KEY_PAGE] = page.name
            page.section?.let { handle[KEY_SECTION] = it.name }
            if (page == SettingsPage.SourcePicker) loadSources()
        }

        /** Selects a section of the two-pane list. */
        fun selectSection(section: SettingsSection) {
            options.update { it.copy(section = section) }
            handle[KEY_SECTION] = section.name
            if (section == SettingsSection.Source) loadSources()
        }

        /** Back from a sub-page: true when handled (the page changed), false at the main list. */
        fun back(): Boolean {
            val parent = options.value.page.parent() ?: return false
            openPage(parent)
            return true
        }

        /** A source was picked: the confirmation dialog when it differs from the current one. */
        fun pickSource(option: SourceOption) {
            viewModelScope.launch {
                if (option.packageName != data.settings.first().sourcePackage) options.update { it.copy(pending = option) }
            }
        }

        /** "Switch": the new source; its nights are read now and learning restarts for it. History stays. */
        fun confirmSource() {
            val pending = options.value.pending ?: return
            options.update { it.copy(pending = null) }
            viewModelScope.launch {
                data.settingsRepository.setSourcePackage(pending.packageName)
                data.refresh()
                data.checkNow()
            }
        }

        /** "Cancel". */
        fun cancelSource() = options.update { it.copy(pending = null) }

        /** "Night results" switch. */
        fun setNotifyResults(on: Boolean) = launch { data.settingsRepository.setNotificationsEnabled(on) }

        /** "Problems reading data" switch. */
        fun setNotifyProblems(on: Boolean) = launch { data.settingsRepository.setProblemNotificationsEnabled(on) }

        /** Sensitivity preset. */
        fun setSensitivity(preset: SensitivityPreset) = launch { data.settingsRepository.setSensitivity(levelOf(preset)) }

        /** Theme. */
        fun setTheme(theme: ThemeChoice) =
            launch {
                data.settingsRepository.setTheme(
                    when (theme) {
                        ThemeChoice.System -> ThemeMode.SYSTEM
                        ThemeChoice.Light -> ThemeMode.LIGHT
                        ThemeChoice.Dark -> ThemeMode.DARK
                    },
                )
            }

        /** Glass effects Automatic / Off. */
        fun setGlass(preference: GlassPreference) =
            launch { data.settingsRepository.setGlassMode(if (preference == GlassPreference.Off) GlassMode.OFF else GlassMode.AUTO) }

        /** "Set up again": onboarding restarts at the source step; nothing stored is deleted. */
        fun setupAgain() = launch { data.settingsRepository.setOnboardingDone(false) }

        /** Re-checks the system state (after the notification prompt or returning from system settings). */
        fun refresh() = launch { data.refresh() }

        private fun loadSources() {
            viewModelScope.launch {
                val found = SourceDiscovery.discover(data)
                options.update { it.copy(sources = found) }
            }
        }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        /** Mappings between the stored settings and the screen's presets. */
        companion object {
            private const val KEY_PAGE = "page"
            private const val KEY_SECTION = "settings_section"
            private const val STOP_TIMEOUT_MS = 5_000L

            /** Screen preset of a stored level (the z thresholds are the same: 3.0, 2.5, 2.0). */
            fun presetOf(level: SensitivityLevel): SensitivityPreset =
                when (level) {
                    SensitivityLevel.LOW -> SensitivityPreset.Fewer
                    SensitivityLevel.STANDARD -> SensitivityPreset.Balanced
                    SensitivityLevel.HIGH -> SensitivityPreset.More
                }

            /** Stored level of a preset. */
            fun levelOf(preset: SensitivityPreset): SensitivityLevel =
                when (preset) {
                    SensitivityPreset.Fewer -> SensitivityLevel.LOW
                    SensitivityPreset.Balanced -> SensitivityLevel.STANDARD
                    SensitivityPreset.More -> SensitivityLevel.HIGH
                }

            private fun themeOf(mode: ThemeMode): ThemeChoice =
                when (mode) {
                    ThemeMode.SYSTEM -> ThemeChoice.System
                    ThemeMode.LIGHT -> ThemeChoice.Light
                    ThemeMode.DARK -> ThemeChoice.Dark
                }

            /**
             * `sens_preview`: how many of the last 30 stored nights would have sent a note with [settings]' preset,
             * evaluated locally; null while learning (`ST-SENS-LEARNING`).
             */
            fun preview(
                settings: AppSettings,
                records: List<NightRecord>,
            ): Int? {
                val params = settings.algoParams()
                val summaries = records.map { it.summary }
                val last = records.sortedByDescending { it.date }.take(SettingsState.PREVIEW_NIGHTS)
                val evaluated = last.map { Evaluator.evaluate(it.summary, summaries, params) }
                val learning = evaluated.isEmpty() || evaluated.first().historyNights < params.minNights
                return if (learning) null else evaluated.count { it.notify }
            }
        }
    }
