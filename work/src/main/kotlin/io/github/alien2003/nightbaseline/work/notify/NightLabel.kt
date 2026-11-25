package io.github.alien2003.nightbaseline.work.notify

import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import io.github.alien2003.nightbaseline.work.R
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/**
 * The one night label of the app (`states.md` 2, `night_label`): a night is always named by the evening before the
 * wake date and the wake date, whatever time the sleep started. Used by Home, Night detail, Metric detail, History
 * and the night result notification, so every place shows the same words.
 *
 * Arguments of `night_label`: 1 the locale's short date range ("Nov 24 – 25" in en-US, "24–25 Nov" in en-GB), 2 the
 * wake date with the month name in the locale's genitive form, 3 the evening's day alone, or with the month name when
 * the months differ; ru uses 2 and 3 ("night from the 24th to the 25th of November").
 */
object NightLabel {
    /** The label of the night that ends on [wake]. */
    fun format(
        res: Resources,
        wake: LocalDate,
        locale: Locale,
    ): String {
        val (range, wakeLong, start) = args(wake, locale)
        return res.getString(R.string.night_label, range, wakeLong, start)
    }

    /** The three `night_label` arguments for the night that ends on [wake]. */
    fun args(
        wake: LocalDate,
        locale: Locale,
    ): Triple<String, String, String> {
        val start = wake.minusDays(1)
        val interval = DateIntervalFormat.getInstance(SKELETON_SHORT, locale).apply { timeZone = TimeZone.GMT_ZONE }
        val range = interval.format(DateInterval(millis(start), millis(wake)))
        val startText = if (start.month == wake.month) start.dayOfMonth.toString() else format(start, SKELETON_LONG, locale)
        return Triple(range, format(wake, SKELETON_LONG, locale), startText)
    }

    private fun format(
        date: LocalDate,
        skeleton: String,
        locale: Locale,
    ): String = DateFormat.getInstanceForSkeleton(skeleton, locale).apply { timeZone = TimeZone.GMT_ZONE }.format(Date(millis(date)))

    private fun millis(date: LocalDate): Long = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + NOON_MS

    private const val NOON_MS = 12 * 60 * 60 * 1000L
    private const val SKELETON_SHORT = "MMMd"
    private const val SKELETON_LONG = "MMMMd"
}
