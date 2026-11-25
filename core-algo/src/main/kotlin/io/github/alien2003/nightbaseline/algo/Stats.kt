package io.github.alien2003.nightbaseline.algo

import kotlin.math.abs
import kotlin.math.floor

/** Small robust-statistics toolkit (no dependencies). */
object Stats {
    /** Scale factor that makes the MAD a consistent estimator of the standard deviation for normal data. */
    const val MAD_TO_SD: Double = 1.4826

    private const val MEDIAN_QUANTILE = 0.5

    /** Median of [values]; NaN when empty. */
    fun median(values: List<Double>): Double = quantile(values, MEDIAN_QUANTILE)

    /** Linear-interpolated quantile (type 7, as numpy's default) of [values]; NaN when empty. */
    fun quantile(
        values: List<Double>,
        q: Double,
    ): Double {
        require(q in 0.0..1.0) { "q must be in [0, 1]" }
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val pos = q * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** Mean; NaN when empty. */
    fun mean(values: List<Double>): Double = if (values.isEmpty()) Double.NaN else values.sum() / values.size

    /** Median absolute deviation (unscaled); NaN when empty. */
    fun mad(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val m = median(values)
        return median(values.map { abs(it - m) })
    }

    /** Robust standard deviation: MAD · 1.4826. */
    fun robustSd(values: List<Double>): Double = mad(values) * MAD_TO_SD

    /** Mean after dropping the lowest and highest [share] of the sorted values; NaN when empty. */
    fun trimmedMean(
        values: List<Double>,
        share: Double,
    ): Double {
        require(share in 0.0..<MEDIAN_QUANTILE) { "share must be in [0, 0.5)" }
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val cut = (sorted.size * share).toInt()
        return mean(sorted.subList(cut, sorted.size - cut))
    }
}
