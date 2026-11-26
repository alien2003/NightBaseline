package io.github.alien2003.nightbaseline.ui.model

import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.ListFormatter
import android.icu.util.TimeZone
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.work.notify.InlineNames
import io.github.alien2003.nightbaseline.work.notify.NightLabel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import io.github.alien2003.nightbaseline.work.R as WorkR

/** How one metric is named and formatted. */
@Immutable
data class MetricDisplay(
    /** Full name (`metric_<id>`). */
    @param:StringRes val name: Int,
    /** Short name (`metric_<id>_short`) for Glance windows and the widget only; sentences use [name]. */
    @param:StringRes val shortName: Int,
    /** Unit string; null for sleep duration (hours and minutes carry their own units). */
    @param:StringRes val unit: Int?,
    /** Fraction digits of values and ranges. */
    val decimals: Int,
    /** Values are minutes shown as "7 h 05 min". */
    val duration: Boolean = false,
    /**
     * Values are a change from the source's own baseline (skin temperature): shown with a sign and a real minus, the
     * value's unit says so ([valueUnit]) and ranges read "−0.5 to +0.5" instead of a dash next to a minus.
     */
    val signed: Boolean = false,
    /** Unit next to a night's value when it differs from [unit] (signed metrics: "°C vs usual"). */
    @param:StringRes val valueUnit: Int? = null,
) {
    /** Displays of the built-in metrics. */
    companion object {
        private val KNOWN: Map<MetricId, MetricDisplay> =
            mapOf(
                MetricId.SLEEPING_HR to
                    MetricDisplay(WorkR.string.metric_sleeping_hr, WorkR.string.metric_sleeping_hr_short, R.string.unit_bpm, 0),
                MetricId.RESPIRATORY_RATE to
                    MetricDisplay(
                        WorkR.string.metric_respiratory_rate,
                        WorkR.string.metric_respiratory_rate_short,
                        R.string.unit_breaths_per_min,
                        1,
                    ),
                MetricId.SPO2 to MetricDisplay(WorkR.string.metric_spo2, WorkR.string.metric_spo2_short, R.string.unit_percent, 0),
                MetricId.SLEEP_DURATION to
                    MetricDisplay(WorkR.string.metric_sleep_duration, WorkR.string.metric_sleep_duration_short, null, 0, duration = true),
                MetricId.HRV to MetricDisplay(WorkR.string.metric_hrv, WorkR.string.metric_hrv_short, R.string.unit_ms, 0),
                MetricId.SKIN_TEMPERATURE to
                    MetricDisplay(
                        WorkR.string.metric_skin_temperature,
                        WorkR.string.metric_skin_temperature_short,
                        R.string.unit_celsius,
                        1,
                        signed = true,
                        valueUnit = R.string.unit_celsius_vs_usual,
                    ),
            )

        /** Display of [metric]; an unknown (future) metric gets the heart-rate names as a safe placeholder. */
        fun of(metric: MetricId): MetricDisplay = KNOWN[metric] ?: KNOWN.getValue(MetricId.SLEEPING_HR).copy(decimals = 1)
    }
}

/** A formatted value: the number (largest text) and its unit (smaller, separate style); unit null when included. */
@Immutable
data class FormattedValue(
    /** Number, formatted per locale. */
    val number: String,
    /** Unit, or null. */
    val unit: String?,
) {
    /** "number unit" for sentences and content descriptions; never broken between the number and its unit. */
    val text: String get() = if (unit == null) number else "$number\u00A0$unit"
}

/**
 * Turns the UI model into text with the app's resources and the current locale: numbers through [NumberFormat],
 * dates through ICU skeletons, lists through [ListFormatter], counted nouns through plurals. Every text the
 * components show or announce comes from here, so screens and tests share one wording.
 */
@Suppress("TooManyFunctions") // The one wording facade of the UI: every text the components show comes from here.
class UiText(
    private val resources: Resources,
    /** Locale of numbers and dates. */
    val locale: Locale,
) {
    private val integer = NumberFormat.getIntegerInstance(locale)

    // ICU formatters are costly to build (pattern generation, data loading) and were built per call, i.e. per row and
    // per composition. One instance lives per remembered UiText (main thread only; formatters are not thread-safe).
    private val decimalFormats = HashMap<Int, NumberFormat>()
    private val percentFormat by lazy(LazyThreadSafetyMode.NONE) { NumberFormat.getPercentInstance(locale) }
    private val dateFormats = HashMap<String, DateFormat>()
    private val listFormatter by lazy(LazyThreadSafetyMode.NONE) { ListFormatter.getInstance(locale) }
    private val nightLabels = HashMap<LocalDate, String>()

    /** String resource with arguments. */
    fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = resources.getString(id, *args)

    /** Plural resource for [count]; arguments follow the plural's placeholders. */
    fun plural(
        @PluralsRes id: Int,
        count: Int,
        vararg args: Any,
    ): String = resources.getQuantityString(id, count, *args)

    /** An integer formatted for the locale. */
    fun int(value: Int): String = integer.format(value)

    /** Full metric name. */
    fun name(metric: MetricId): String = resources.getString(MetricDisplay.of(metric).name)

    /** Short metric name. */
    fun shortName(metric: MetricId): String = resources.getString(MetricDisplay.of(metric).shortName)

    /** Unit of [metric] (of ranges and differences), or null for durations. */
    fun unit(metric: MetricId): String? = MetricDisplay.of(metric).unit?.let(::unitString)

    /** Unit next to a night's value: [unit], or the signed metrics' "°C vs usual". */
    private fun valueUnit(metric: MetricId): String? = MetricDisplay.of(metric).let { it.valueUnit ?: it.unit }?.let(::unitString)

    /** A unit string; a word joiner after each slash keeps "breaths/min" on one line. */
    private fun unitString(
        @StringRes id: Int,
    ): String = resources.getString(id).replace("/", "/$WORD_JOINER")

    /** [value] of [metric] as number and unit, with [decimals] fraction digits (the metric's default when null). */
    fun value(
        metric: MetricId,
        value: Double,
        decimals: Int? = null,
    ): FormattedValue {
        val display = MetricDisplay.of(metric)
        if (display.duration) return FormattedValue(duration(value), null)
        return FormattedValue(number(metric, value, decimals ?: display.decimals), valueUnit(metric))
    }

    /** A range end or median of [metric] as "number unit" (the plain unit, signed when the metric is). */
    fun rangeEnd(
        metric: MetricId,
        value: Double,
        decimals: Int? = null,
    ): String {
        val display = MetricDisplay.of(metric)
        if (display.duration) return duration(value)
        val number = number(metric, value, decimals ?: display.decimals)
        return unit(metric)?.let { "$number$NBSP$it" } ?: number
    }

    /** A distance outside the range (always positive) as "number unit", e.g. "0.3 °C". */
    fun magnitude(
        metric: MetricId,
        value: Double,
        decimals: Int? = null,
    ): String {
        val display = MetricDisplay.of(metric)
        if (display.duration) return duration(value)
        val number = number(value, decimals ?: display.decimals)
        return unit(metric)?.let { "$number$NBSP$it" } ?: number
    }

    /** [value] of [metric] as a number only: signed ("+0.2", "−0.1", "0.0") when the metric is a change. */
    private fun number(
        metric: MetricId,
        value: Double,
        decimals: Int,
    ): String {
        if (!MetricDisplay.of(metric).signed) return number(value, decimals)
        val plain = number(abs(value), decimals)
        val zero = number(0.0, decimals)
        return when {
            plain == zero -> zero
            value < 0 -> "$MINUS$plain"
            else -> "+$plain"
        }
    }

    /** "Usual range: 52–61 bpm" (the signed metrics: "Usual range: −0.5 to +0.5 °C"). */
    fun usualRange(
        metric: MetricId,
        range: RangeUi,
        decimals: Int? = null,
    ): String {
        val low = bare(metric, range.low, decimals)
        val high = rangeEnd(metric, range.high, decimals)
        return if (MetricDisplay.of(metric).signed) {
            string(R.string.stats_range_to, low, high)
        } else {
            string(R.string.stats_range, low, high)
        }
    }

    /** "52–61 bpm" as a readout value (the signed metrics: "−0.5 to +0.5 °C"). */
    fun rangeValue(
        metric: MetricId,
        range: RangeUi,
        decimals: Int? = null,
    ): String {
        val low = bare(metric, range.low, decimals)
        val high = rangeEnd(metric, range.high, decimals)
        return if (MetricDisplay.of(metric).signed) {
            string(R.string.stats_value_range_to, low, high)
        } else {
            string(R.string.stats_value_range, low, high)
        }
    }

    /**
     * Fraction digits for [metric]'s value, range and delta: the metric's default, plus one when a value outside the
     * range would otherwise print like the range edge it passed ("58 bpm", usual range "45–58 bpm") or its distance
     * would print as 0. A value must never look inside while it is labelled outside.
     */
    fun decimals(metric: MetricUi): Int {
        val display = MetricDisplay.of(metric.metric)
        val value = metric.value
        val range = metric.range
        val edge =
            when {
                value == null || range == null || display.duration -> null
                value > range.high -> range.high
                value < range.low -> range.low
                else -> null
            }
        // Up to two extra digits; beyond that the difference is below any device's resolution.
        return if (edge == null || value == null) {
            display.decimals
        } else {
            (display.decimals..display.decimals + MAX_EXTRA_DECIMALS).firstOrNull { d -> number(value, d) != number(edge, d) }
                ?: (display.decimals + MAX_EXTRA_DECIMALS)
        }
    }

    /** [MetricUi.value] formatted with [decimals]. */
    fun value(metric: MetricUi): FormattedValue? = metric.value?.let { value(metric.metric, it, decimals(metric)) }

    /** Minutes as "7 h 05 min". */
    fun duration(minutes: Double): String {
        val total = minutes.roundToInt()
        return string(R.string.unit_hours_minutes, int(total / MINUTES_PER_HOUR), "%02d".format(locale, total % MINUTES_PER_HOUR))
    }

    /** A number with [decimals] fraction digits. */
    fun number(
        value: Double,
        decimals: Int,
    ): String =
        decimalFormats
            .getOrPut(decimals) {
                NumberFormat.getNumberInstance(locale).apply {
                    minimumFractionDigits = decimals
                    maximumFractionDigits = decimals
                }
            }.format(value)

    /** The number only (no unit), e.g. for range ends. */
    fun bare(
        metric: MetricId,
        value: Double,
        decimals: Int? = null,
    ): String = value(metric, value, decimals).number

    /** "52–61 bpm" (the signed metrics: "−0.5 to +0.5 °C"). */
    fun range(
        metric: MetricId,
        range: RangeUi,
    ): String = rangeValue(metric, range)

    /** Short status label of a metric (`status_*`). */
    fun status(
        metric: MetricUi,
        source: String,
    ): String =
        when (metric.state) {
            MetricState.Within, MetricState.SideIgnoredHigher, MetricState.SideIgnoredLower -> string(R.string.status_within)
            MetricState.Higher -> string(R.string.status_higher)
            MetricState.Lower -> string(R.string.status_lower)
            MetricState.Learning -> string(R.string.status_learning, int(metric.learnedNights))
            MetricState.NoData, MetricState.TooFew -> string(R.string.status_no_value)
            MetricState.LowCoverage -> string(R.string.status_partial)
            MetricState.NotCompared -> string(R.string.status_not_compared)
            MetricState.NotProvided -> string(R.string.status_not_provided, source)
            MetricState.AccessOff -> string(R.string.status_access_off)
            MetricState.AccessOffThen -> string(R.string.status_access_off_then)
        }

    /** The one-sentence explanation of a metric state (`detail_*`, `states.md` 4). */
    @Suppress("CyclomaticComplexMethod") // An exhaustive state -> copy table (states.md); splitting it would hide the mapping.
    fun detail(
        metric: MetricUi,
        source: String,
        minNights: Int = LEARNING_NIGHTS,
    ): String {
        val name = name(metric.metric)
        val delta = metric.delta?.let { magnitude(metric.metric, it, decimals(metric)) }
        return when (metric.state) {
            MetricState.Within -> {
                string(R.string.detail_within)
            }

            MetricState.Higher -> {
                delta?.let { string(R.string.detail_higher, it) } ?: string(R.string.status_higher)
            }

            MetricState.Lower -> {
                delta?.let { string(R.string.detail_lower, it) } ?: string(R.string.status_lower)
            }

            MetricState.SideIgnoredHigher -> {
                string(R.string.detail_side_ignored, name)
            }

            MetricState.SideIgnoredLower -> {
                string(R.string.metric_detail_side_ignored_lower, name)
            }

            MetricState.Learning -> {
                val left = minNights - metric.learnedNights
                if (left <= 0) string(R.string.detail_learning_ready) else plural(R.plurals.detail_learning, left, int(left))
            }

            MetricState.NoData -> {
                string(R.string.detail_no_data, source, name)
            }

            MetricState.TooFew -> {
                string(R.string.detail_too_few, int(metric.samples ?: 0), int(metric.minSamples ?: 0))
            }

            MetricState.LowCoverage -> {
                string(
                    R.string.detail_low_coverage,
                    percent(metric.coverage ?: 0.0),
                    percent(metric.minCoverage ?: 0.0),
                )
            }

            MetricState.NotCompared -> {
                string(R.string.detail_not_compared)
            }

            MetricState.NotProvided -> {
                string(R.string.detail_not_provided_body, source)
            }

            MetricState.AccessOff -> {
                string(R.string.detail_access_off, name)
            }

            MetricState.AccessOffThen -> {
                string(R.string.detail_access_off_then, name)
            }
        }
    }

    /** A share as a locale percentage. */
    fun percent(share: Double): String = percentFormat.format(share)

    /** TalkBack text of a metric tile (`a11y_tile*`). */
    fun tileDescription(
        metric: MetricUi,
        source: String,
    ): String {
        val name = name(metric.metric)
        val value = value(metric)?.text
        val d = decimals(metric)
        val range = metric.range
        return when {
            metric.state == MetricState.Learning && value != null -> {
                string(R.string.a11y_tile_learning, name, value, int(metric.learnedNights))
            }

            value != null && range != null && metric.state.compared -> {
                string(
                    R.string.a11y_tile,
                    name,
                    value,
                    status(metric, source),
                    rangeEnd(metric.metric, range.low, d),
                    rangeEnd(metric.metric, range.high, d),
                )
            }

            else -> {
                string(R.string.a11y_tile_missing, name, status(metric, source), detail(metric, source))
            }
        }
    }

    /** "Night of Nov 24 – 25" (en-US), "Night of 24–25 Nov" (en-GB), the evening-to-wake form in ru. */
    fun nightLabel(night: NightUi): String = nightLabel(night.wakeDate)

    /**
     * The one night label ([NightLabel], shared with the notification): always the evening before [wake] to [wake],
     * whatever time the sleep started (a sleep that began after midnight is still the night of 24–25 Nov).
     */
    fun nightLabel(wake: LocalDate): String =
        nightLabels.getOrPut(wake) {
            val (range, wakeLong, start) = NightLabel.args(wake, locale)
            string(R.string.night_label, range, wakeLong, start)
        }

    /** "Nov 25". */
    fun dateShort(date: LocalDate): String = format(date, SKELETON_SHORT)

    /** Day and month name in the locale's order, e.g. "November 25" in English (day first in Russian). */
    fun dateLong(date: LocalDate): String = format(date, SKELETON_LONG)

    /** Weekday initial for strips and the calendar header. */
    fun weekdayLetter(date: LocalDate): String = format(date, "EEEEE")

    /** "November 2026" as a heading: capitalised in every locale (CLDR writes the Russian standalone month in lower case). */
    fun month(date: LocalDate): String = format(date, "yMMMM", DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)

    private fun format(
        date: LocalDate,
        skeleton: String,
        context: DisplayContext? = null,
    ): String {
        val fmt =
            dateFormats.getOrPut("$skeleton/${context?.name.orEmpty()}") {
                DateFormat.getInstanceForSkeleton(skeleton, locale).apply {
                    timeZone = TimeZone.GMT_ZONE
                    context?.let(::setContext)
                }
            }
        return fmt.format(java.util.Date(date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + NOON_MS))
    }

    /**
     * "A, B and C" in the locale. Metric names are standalone labels with a capital letter; inside a Russian sentence
     * only the first item keeps it ("Sleeping heart rate, respiratory rate and SpO2"), [inline] lowercases the first one too
     * (after a colon). Acronyms ("SpO2", "HRV") stay as they are. English keeps its title-case names.
     */
    fun list(
        items: List<String>,
        inline: Boolean = false,
    ): String = listFormatter.format(InlineNames.of(items, locale, inline))

    /** Headline of a night (`state_*_title`). */
    @Suppress("CyclomaticComplexMethod") // An exhaustive state -> copy table (states.md); splitting it would hide the mapping.
    fun nightTitle(
        night: NightUi,
        source: String,
    ): String {
        val moved = night.movedMetrics
        return when (night.state) {
            NightState.Loading -> {
                ""
            }

            NightState.LearningStart -> {
                string(R.string.state_l0_title)
            }

            NightState.Learning -> {
                string(R.string.state_learning_title)
            }

            NightState.AllWithin -> {
                string(R.string.state_all_title)
            }

            NightState.SomeMoved -> {
                if (moved.size == 1) {
                    string(R.string.state_some_title_one, name(moved.first().metric))
                } else {
                    plural(R.plurals.state_some_title_many, night.deviating, int(night.deviating))
                }
            }

            NightState.Moved -> {
                plural(R.plurals.state_dev_title, night.deviating, int(night.deviating))
            }

            NightState.NotSynced -> {
                string(R.string.state_sync_title, source)
            }

            NightState.NoData -> {
                string(R.string.state_nodata_title)
            }

            NightState.Short -> {
                string(R.string.state_short_title)
            }

            NightState.Ambiguous -> {
                string(R.string.state_ambiguous_title)
            }

            NightState.NotEvaluated -> {
                string(R.string.state_noteval_title)
            }

            NightState.Permission -> {
                string(R.string.state_perm_title)
            }

            NightState.HealthConnectUpdate -> {
                string(R.string.state_hc_update_title)
            }

            NightState.SourceGone -> {
                string(R.string.state_source_gone_title, source)
            }

            NightState.HealthConnectNone -> {
                string(R.string.state_hc_none_title)
            }
        }
    }

    /** Body of a night (`state_*_body`). */
    @Suppress("CyclomaticComplexMethod") // An exhaustive state -> copy table (states.md); splitting it would hide the mapping.
    fun nightBody(
        night: NightUi,
        source: String,
    ): String =
        when (night.state) {
            NightState.Loading -> {
                string(R.string.state_loading_body)
            }

            NightState.LearningStart -> {
                string(R.string.state_l0_body)
            }

            NightState.Learning -> {
                val left = LEARNING_NIGHTS - night.collectedNights
                if (left <= 0) string(R.string.state_learning_ready_body) else plural(R.plurals.state_learning_body, left, int(left))
            }

            NightState.AllWithin -> {
                plural(R.plurals.state_all_body, night.compared, int(night.compared), int(night.windowNights))
            }

            NightState.SomeMoved -> {
                if (night.movedMetrics.size > 1 && night.threshold > 2) {
                    string(R.string.state_some_body_many, int(night.threshold))
                } else {
                    string(R.string.state_some_body)
                }
            }

            NightState.Moved -> {
                string(R.string.state_dev_body, list(night.movedMetrics.map { name(it.metric) }))
            }

            NightState.NotSynced -> {
                string(R.string.state_sync_body, source)
            }

            NightState.NoData -> {
                string(R.string.state_nodata_body, source)
            }

            NightState.Short -> {
                string(R.string.state_short_body, source, duration(night.asleepMinutes))
            }

            NightState.Ambiguous -> {
                string(R.string.state_ambiguous_body, source)
            }

            NightState.NotEvaluated -> {
                string(R.string.state_noteval_body, source)
            }

            NightState.Permission -> {
                string(R.string.state_perm_body)
            }

            NightState.HealthConnectUpdate -> {
                string(R.string.state_hc_update_body)
            }

            NightState.SourceGone -> {
                string(R.string.state_source_gone_body)
            }

            NightState.HealthConnectNone -> {
                string(R.string.state_hc_none_body)
            }
        }

    /** Short History label of a night (`history_status_*`). */
    fun nightStatusLabel(night: NightUi): String =
        when (night.state) {
            NightState.AllWithin -> string(R.string.history_status_all)
            NightState.SomeMoved, NightState.Moved -> plural(R.plurals.history_status_moved, night.deviating, int(night.deviating))
            NightState.Learning, NightState.LearningStart -> string(R.string.history_status_learning)
            NightState.Short -> string(R.string.history_status_short)
            NightState.Ambiguous -> string(R.string.history_status_ambiguous)
            NightState.NotEvaluated -> string(R.string.history_status_few)
            NightState.NoData, NightState.Loading -> string(R.string.history_status_nodata)
            NightState.NotSynced -> string(R.string.widget_waiting)
            NightState.Permission -> string(R.string.status_access_off)
            NightState.HealthConnectUpdate -> string(R.string.widget_update_needed)
            NightState.SourceGone -> string(R.string.widget_choose_source)
            NightState.HealthConnectNone -> string(R.string.state_hc_none_title)
        }

    /** Primary action label of a night state card, or null when the state has none. */
    fun nightAction(
        night: NightUi,
        source: String,
    ): String? =
        when (night.state) {
            NightState.LearningStart, NightState.Learning -> string(R.string.action_how_it_works)
            NightState.SomeMoved -> night.movedMetrics.singleOrNull()?.let { string(R.string.action_see_metric, name(it.metric)) }
            NightState.Moved -> string(R.string.action_what_it_means)
            NightState.NotSynced -> string(R.string.action_open_source, source)
            NightState.NoData, NightState.Short, NightState.Ambiguous, NightState.NotEvaluated -> string(R.string.action_see_history)
            NightState.Permission -> string(R.string.action_open_hc_permissions)
            NightState.HealthConnectUpdate -> string(R.string.action_open_store)
            NightState.SourceGone -> string(R.string.action_choose_source)
            NightState.HealthConnectNone -> string(R.string.action_about)
            NightState.AllWithin, NightState.Loading -> null
        }

    /** Secondary action label of a night state card, or null. */
    fun nightSecondaryAction(night: NightUi): String? =
        when (night.state) {
            NightState.NotSynced, NightState.NoData -> string(R.string.action_check_now)
            NightState.HealthConnectUpdate -> string(R.string.action_check_again)
            else -> null
        }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MAX_EXTRA_DECIMALS = 2
        const val LEARNING_NIGHTS = 7
        const val NOON_MS = 12 * 60 * 60 * 1000L
        const val SKELETON_SHORT = "MMMd"
        const val SKELETON_LONG = "MMMMd"
        const val NBSP = '\u00A0'
        const val WORD_JOINER = '\u2060'
        const val MINUS = '\u2212'
    }
}

/** [UiText] for the current resources and locale. */
@Composable
fun rememberUiText(): UiText {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) { UiText(resources, locale) }
}
