package io.github.alien2003.nightbaseline.work.notify

import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.MetricStatus
import io.github.alien2003.nightbaseline.algo.NightEvaluation

/** A night result notification (docs/design/states.md §6.1). */
data class NightNotice(
    /** Wake date (ISO), opened on tap. */
    val date: String,
    /** Deviating metrics above the usual range, in display order. */
    val higher: List<MetricId>,
    /** Deviating metrics below the usual range, in display order. */
    val lower: List<MetricId> = emptyList(),
) {
    /** Every deviating metric, in display order. */
    val metrics: List<MetricId> get() = higher + lower

    /** Constructors. */
    companion object {
        /** The notice of [evaluation]: its deviating metrics split by direction. */
        fun of(evaluation: NightEvaluation): NightNotice {
            val deviating = evaluation.metrics.filter { it.deviates }
            return NightNotice(
                evaluation.date,
                higher = deviating.filter { it.status == MetricStatus.ABOVE }.map { it.metric },
                lower = deviating.filter { it.status == MetricStatus.BELOW }.map { it.metric },
            )
        }
    }
}

/** Shows night result notifications. */
fun interface NightNotifier {
    /** Shows the notification of [notice], replacing an older night's; false when notifications are blocked. */
    fun show(notice: NightNotice): Boolean
}

/** A data-access problem found by the daily run (docs/design/states.md §6.2). */
enum class DataProblem(
    /** Notification tag and `nightbaseline://fix/{segment}` deep-link target (null: open the app). */
    val fixSegment: String?,
) {
    /** E-PERM: a required Health Connect permission is missing. */
    PERMISSION("permission"),

    /** B-BG: the morning check runs in the background without READ_HEALTH_DATA_IN_BACKGROUND. */
    BACKGROUND("permission"),

    /** E-HC-UPDATE: Health Connect is unavailable or must be updated. */
    HEALTH_CONNECT("health-connect"),

    /** Reads kept failing (rate limit, service errors) after the retries of one run. */
    READ_FAILED(null),
}

/** Shows data-access problems on the low-importance `data_access` channel. */
fun interface ProblemNotifier {
    /**
     * Makes [current] the only shown problem: posts it if it is new (once per problem, not again while it lasts) and
     * cancels every other one (resolved). Null cancels all.
     */
    fun update(current: DataProblem?)
}
