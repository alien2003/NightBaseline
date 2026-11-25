// The synthetic feed picks fixed nights (by age in days) for each state, like SampleData's hand-picked numbers.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.history

import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import java.time.LocalDate
import java.time.YearMonth

/**
 * History states built from [SampleData] (synthetic nights evaluated by the real pipeline; never Health Connect data)
 * for the screenshot tests, the catalog and previews. States the generator does not produce on its own (short, two
 * sleeps, no data, too few readings, one moved) are placed on fixed nights by copying a sample night of that state
 * onto the date.
 */
object HistorySamples {
    /** Today in the samples (the wake date of the newest sample night). */
    val TODAY: LocalDate = SampleData.LAST_WAKE

    private const val FEED_NIGHTS = 40
    private const val LEARNING_NIGHTS = 4

    /** Night age in days -> the state placed on it, so the feed shows every row variant within the first screens. */
    private val PLACED: Map<Int, NightState> =
        mapOf(
            2 to NightState.SomeMoved,
            4 to NightState.Short,
            6 to NightState.NoData,
            9 to NightState.Ambiguous,
            12 to NightState.NotEvaluated,
            16 to NightState.SomeMoved,
            21 to NightState.Moved,
            27 to NightState.NoData,
            33 to NightState.Short,
        )

    /** The last 40 sample nights, newest first, with the placed states: the normal feed (`S-*` and `E-*` rows). */
    val feedNights: List<NightUi> by lazy {
        SampleData.nights.takeLast(FEED_NIGHTS).reversed().map { night ->
            val age = TODAY.toEpochDay() - night.wakeDate.toEpochDay()
            PLACED[age.toInt()]?.let { onDate(placed(it), night.wakeDate) } ?: night
        }
    }

    /** The list view of the normal feed. */
    fun feed(view: HistoryView = HistoryView.List): HistoryUiState =
        HistoryUiState(nights = feedNights, source = SampleData.SOURCE, today = TODAY, view = view)

    /** The calendar view of the current month with [selected] (default: the newest night). */
    fun calendar(selected: LocalDate? = null): HistoryUiState = feed(HistoryView.Calendar).copy(selected = selected)

    /** The list filtered to [filter]. */
    fun filtered(filter: HistoryFilter): HistoryUiState = feed().copy(filter = filter)

    /** `L1`..`L6`: the first nights of the learning period (dotted ring, "Learning"). */
    fun learning(): HistoryUiState {
        val base = SampleData.night(NightState.Learning)
        val nights =
            (0 until LEARNING_NIGHTS).map { age ->
                onDate(base.copy(historyNights = LEARNING_NIGHTS - 1 - age), TODAY.minusDays(age.toLong()))
            }
        return HistoryUiState(nights = nights, source = SampleData.SOURCE, today = TODAY)
    }

    /** `H-FILTER-EMPTY`: "Moved" while learning (nothing can have moved yet). */
    fun filterEmpty(): HistoryUiState = learning().copy(filter = HistoryFilter.Moved)

    /** `H-EMPTY` (also `L0` and `E-HC-NONE`). */
    fun empty(): HistoryUiState = HistoryUiState(nights = emptyList(), source = SampleData.SOURCE, today = TODAY)

    /** `H-LOADING`: skeleton rows. */
    fun loading(): HistoryUiState = empty().copy(loading = true)

    /** `E-PERM` / `E-HC-UPDATE`: rows still listed below the banner. */
    fun access(access: HistoryAccess): HistoryUiState = feed().copy(access = access)

    /** The calendar on the previous month (a month with no "today" in it). */
    fun previousMonth(): HistoryUiState = calendar().copy(month = YearMonth.from(TODAY).minusMonths(1))

    /** A sample night in [state] whose metrics match it (no comparison on nights without a result). */
    private fun placed(state: NightState): NightUi {
        val night = SampleData.night(state)
        return when (state) {
            NightState.NoData -> night.copy(metrics = emptyList())
            NightState.NotEvaluated -> night.copy(metrics = night.metrics.map { it.copy(state = MetricState.NoData, value = null) })
            NightState.Ambiguous -> night.copy(metrics = night.metrics.map { it.copy(state = MetricState.NotCompared) })
            else -> night
        }
    }

    private fun onDate(
        night: NightUi,
        wake: LocalDate,
    ): NightUi = night.copy(wakeDate = wake, startDate = wake.minusDays(1))
}
