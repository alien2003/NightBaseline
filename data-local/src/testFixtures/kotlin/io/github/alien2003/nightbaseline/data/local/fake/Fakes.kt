package io.github.alien2003.nightbaseline.data.local.fake

import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.NightRepository
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.RunLogLine
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.data.local.settings.GlassMode
import io.github.alien2003.nightbaseline.data.local.settings.SensitivityLevel
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [NightRepository] with the same ordering as the Room one. */
class InMemoryNightRepository : NightRepository {
    private val rows = MutableStateFlow<Map<Pair<String, String>, NightRecord>>(emptyMap())

    /** Every upsert, in order. */
    val upserts: MutableList<NightRecord> = mutableListOf()

    /** Snapshot of every stored night, oldest first. */
    val all: List<NightRecord> get() = rows.value.values.sortedBy { it.date }

    override suspend fun get(
        sourcePackage: String,
        date: String,
    ): NightRecord? = rows.value[sourcePackage to date]

    override suspend fun since(
        sourcePackage: String,
        fromDate: String,
    ): List<NightRecord> = all.filter { it.sourcePackage == sourcePackage && it.date >= fromDate }

    override suspend fun upsert(record: NightRecord) {
        upserts += record
        rows.update { it + ((record.sourcePackage to record.date) to record) }
    }

    override suspend fun hasAny(sourcePackage: String): Boolean = rows.value.keys.any { it.first == sourcePackage }

    override fun observe(sourcePackage: String): Flow<List<NightRecord>> =
        rows.map { m -> m.values.filter { it.sourcePackage == sourcePackage }.sortedByDescending { it.date } }
}

/** In-memory [RunLog] (lines in insertion order in [lines]). */
class InMemoryRunLog(
    private val now: () -> Long = { 0L },
) : RunLog {
    /** Every line, oldest first. */
    val lines: MutableList<RunLogLine> = mutableListOf()

    override suspend fun log(
        kind: RunLogKind,
        message: String,
    ) {
        lines += RunLogLine(now(), kind, message)
    }

    override suspend fun recent(limit: Int): List<RunLogLine> = lines.asReversed().take(limit)
}

/** In-memory [SettingsRepository]. */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    /** Current value. */
    val current: StateFlow<AppSettings> = state.asStateFlow()

    override val settings: Flow<AppSettings> = state

    override suspend fun setSourcePackage(packageName: String) = state.update { it.copy(sourcePackage = packageName) }

    override suspend fun setNotificationsEnabled(enabled: Boolean) = state.update { it.copy(notificationsEnabled = enabled) }

    override suspend fun setSensitivity(level: SensitivityLevel) = state.update { it.copy(sensitivity = level) }

    override suspend fun setOnboardingDone(done: Boolean) = state.update { it.copy(onboardingDone = done) }

    override suspend fun setGlassMode(mode: GlassMode) = state.update { it.copy(glassMode = mode) }
}
