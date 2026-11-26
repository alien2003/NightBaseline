package io.github.alien2003.nightbaseline.work.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.work.R
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wording of the night result notification (docs/design/states.md §6.1), from resources (en, ru): which
 * measurements moved outside the usual range, never values, never a diagnosis.
 */
object NoticeText {
    /** Names shown before "and {n} more". */
    const val MAX_LISTED: Int = 3

    /**
     * Display name of [metric], the same full name the app's cards, summary and History use (one name per
     * measurement); unknown (future) metrics get a generic name.
     */
    fun metricNameRes(metric: MetricId): Int =
        when (metric) {
            MetricId.SLEEPING_HR -> R.string.metric_sleeping_hr
            MetricId.RESPIRATORY_RATE -> R.string.metric_respiratory_rate
            MetricId.SPO2 -> R.string.metric_spo2
            MetricId.SLEEP_DURATION -> R.string.metric_sleep_duration
            MetricId.HRV -> R.string.metric_hrv
            MetricId.SKIN_TEMPERATURE -> R.string.metric_skin_temperature
            else -> R.string.metric_other
        }

    /** Short display name of [metric] (compact surfaces); unknown (future) metrics get a generic name. */
    fun metricShortNameRes(metric: MetricId): Int =
        when (metric) {
            MetricId.SLEEPING_HR -> R.string.metric_sleeping_hr_short
            MetricId.RESPIRATORY_RATE -> R.string.metric_respiratory_rate_short
            MetricId.SPO2 -> R.string.metric_spo2_short
            MetricId.SLEEP_DURATION -> R.string.metric_sleep_duration_short
            MetricId.HRV -> R.string.metric_hrv_short
            MetricId.SKIN_TEMPERATURE -> R.string.metric_skin_temperature_short
            else -> R.string.metric_other_short
        }

    /** `notif_dev_title`: "{k} measurements moved outside your usual range". */
    fun title(
        res: Resources,
        notice: NightNotice,
    ): String {
        val k = notice.metrics.size
        return res.getQuantityString(R.plurals.notif_dev_title, k, k)
    }

    /** `notif_dev_body`: "{metric list}. {night label}.". */
    fun body(
        res: Resources,
        notice: NightNotice,
        locale: Locale,
    ): String = res.getString(R.string.notif_dev_body, list(res, notice.metrics, locale), nightLabel(res, notice.date, locale))

    /** `notif_dev_body_long`: higher and lower groups (empty ones omitted), then the disclaimer. */
    fun bodyLong(
        res: Resources,
        notice: NightNotice,
    ): String {
        val locale = res.configuration.locales[0]
        val higher =
            if (notice.higher.isEmpty()) {
                ""
            } else {
                res.getString(
                    R.string.notif_higher_prefix,
                    list(res, notice.higher, locale, inline = true),
                )
            }
        val lower =
            if (notice.lower.isEmpty()) "" else res.getString(R.string.notif_lower_prefix, list(res, notice.lower, locale, inline = true))
        return res.getString(R.string.notif_dev_body_long, higher, lower)
    }

    /**
     * Names: "A", "A and B", "A, B and C", "A, B, C and {n} more", written as inside a sentence ([InlineNames];
     * [inline] when the list follows other words, as after "Lower than usual:").
     */
    fun list(
        res: Resources,
        metrics: List<MetricId>,
        locale: Locale = res.configuration.locales[0],
        inline: Boolean = false,
    ): String {
        val names = InlineNames.of(metrics.map { res.getString(metricNameRes(it)) }, locale, inline)
        val shown = names.take(MAX_LISTED)
        val rest = names.size - shown.size
        return when {
            shown.isEmpty() -> ""
            rest > 0 -> shown.joinToString(", ") + " " + res.getQuantityString(R.plurals.notif_list_more, rest, rest)
            shown.size == 1 -> shown[0]
            else -> res.getString(R.string.notif_list_and, shown.dropLast(1).joinToString(", "), shown.last())
        }
    }

    /** `night_label` of the night that ends on [wakeDate], the same label the app's screens show ([NightLabel]). */
    fun nightLabel(
        res: Resources,
        wakeDate: String,
        locale: Locale,
    ): String = NightLabel.format(res, LocalDate.parse(wakeDate), locale)
}

/**
 * The notification tap contract. The design's deep links (`nightbaseline://night/{date}`,
 * `nightbaseline://fix/{problem}`, docs/design/screens.md §3) are sent as the data of an `ACTION_VIEW` intent aimed
 * at this app: explicitly at its launcher activity when there is one (the single activity, so no exported browsable
 * filter is needed), otherwise limited to the package. `:work` thus never references `:app`. The night date is also
 * put into [EXTRA_NIGHT_DATE]; the activity validates it with [nightDateOf].
 */
object NightIntents {
    /** Extra with the night date (ISO `yyyy-MM-dd`). */
    const val EXTRA_NIGHT_DATE: String = "night_date"

    /** Deep-link scheme. */
    const val SCHEME: String = "nightbaseline"

    /** Brings the app's task to the front instead of stacking a second activity on it. */
    const val FLAGS: Int = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

    /** `nightbaseline://night/{date}`. */
    fun nightUri(date: String): String = "$SCHEME://night/$date"

    /** `nightbaseline://fix/{segment}`. */
    fun fixUri(segment: String): String = "$SCHEME://fix/$segment"

    /** The intent that opens [uri] (null: just the app) in [context]'s app. */
    fun open(
        context: Context,
        uri: String?,
    ): Intent {
        val intent = Intent(Intent.ACTION_VIEW).setPackage(context.packageName).addFlags(FLAGS)
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component
            ?.let { intent.component = it }
        uri?.let { intent.data = Uri.parse(it) }
        return intent
    }

    /** The night date carried by an intent extra or deep-link segment, or null when not an ISO date. */
    fun nightDateOf(value: String?): String? =
        value?.takeIf {
            try {
                LocalDate.parse(it)
                true
            } catch (_: DateTimeParseException) {
                false
            }
        }
}

/** Whether the app may post notifications at all (POST_NOTIFICATIONS on 33+, app-level switch). */
private fun notificationsAllowed(context: Context): Boolean {
    val granted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Posts [notification] unless notifications or its channel are blocked; false when nothing was posted. */
private fun post(
    context: Context,
    channelId: String,
    tag: String,
    id: Int,
    notification: Notification,
): Boolean {
    val manager = NotificationManagerCompat.from(context)
    val blocked =
        !notificationsAllowed(context) || manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
    return !blocked &&
        try {
            manager.notify(tag, id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
}

/**
 * Night result notifications on [CHANNEL_ID] (default importance): fixed id [NOTIFICATION_ID] and tag [TAG], so a
 * newer night replaces the older one; no grouping, no actions. The lock screen gets a public version without names
 * or counts.
 */
@Singleton
class AndroidNightNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : NightNotifier {
        override fun show(notice: NightNotice): Boolean {
            Channels.ensure(context)
            return post(context, CHANNEL_ID, TAG, NOTIFICATION_ID, build(notice))
        }

        /** The notification of [notice]. */
        fun build(notice: NightNotice): Notification {
            val res = context.resources
            val locale = res.configuration.locales[0] ?: Locale.getDefault()
            val body = NoticeText.body(res, notice, locale)
            val tap =
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    NightIntents.open(context, NightIntents.nightUri(notice.date)).putExtra(NightIntents.EXTRA_NIGHT_DATE, notice.date),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val public =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_night)
                    .setContentTitle(res.getString(R.string.notif_public_title))
                    .setContentText(res.getString(R.string.notif_public_body))
                    .build()
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_night)
                .setContentTitle(NoticeText.title(res, notice))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n${NoticeText.bodyLong(res, notice)}"))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .build()
        }

        /** Constants (docs/design/states.md §6.1). */
        companion object {
            /** Channel id. */
            const val CHANNEL_ID: String = "night_results"

            /** Fixed notification id. */
            const val NOTIFICATION_ID: Int = 1001

            /** Fixed tag. */
            const val TAG: String = "night"

            private const val REQUEST_CODE = 1001
        }
    }

/**
 * Data-access problem notifications on [CHANNEL_ID] (low importance), id [NOTIFICATION_ID], tag = problem name.
 * Which problems are shown is remembered in private preferences so that a problem is posted once (not again every
 * day while it lasts, even if swiped away) and cancelled when resolved.
 */
@Singleton
class AndroidProblemNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ProblemNotifier {
        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun update(current: DataProblem?) {
            val posted = prefs.getStringSet(KEY_POSTED, emptySet()).orEmpty()
            val manager = NotificationManagerCompat.from(context)
            posted.filter { it != current?.name }.forEach { manager.cancel(it, NOTIFICATION_ID) }
            val keep = mutableSetOf<String>()
            if (current != null) {
                val shown =
                    current.name in posted ||
                        run {
                            Channels.ensure(context)
                            post(context, CHANNEL_ID, current.name, NOTIFICATION_ID, build(current))
                        }
                if (shown) keep += current.name
            }
            prefs.edit().putStringSet(KEY_POSTED, keep).apply()
        }

        /** The notification of [problem]. */
        fun build(problem: DataProblem): Notification {
            val (title, body) =
                when (problem) {
                    DataProblem.PERMISSION -> R.string.notif_perm_title to R.string.notif_perm_body
                    DataProblem.BACKGROUND -> R.string.notif_bg_title to R.string.notif_bg_body
                    DataProblem.HEALTH_CONNECT -> R.string.notif_hc_title to R.string.notif_hc_body
                    DataProblem.READ_FAILED -> R.string.notif_read_failed_title to R.string.notif_read_failed_body
                }
            val tap =
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_BASE + problem.ordinal,
                    NightIntents.open(context, problem.fixSegment?.let(NightIntents::fixUri)),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val text = context.getString(body)
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_night)
                .setContentTitle(context.getString(title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                // The texts contain no health data, so the lock screen may show them as they are.
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }

        /** Constants (docs/design/states.md §6.2). */
        companion object {
            /** Channel id. */
            const val CHANNEL_ID: String = "data_access"

            /** Notification id (the tag is the problem). */
            const val NOTIFICATION_ID: Int = 2001

            private const val PREFS = "data_access_notifications"
            private const val KEY_POSTED = "posted"
            private const val REQUEST_CODE_BASE = 2001
        }
    }

/**
 * The notification channels, created lazily by the notifiers and once by the app on start (an importance cannot be
 * raised later, so each starts at its final level).
 */
object Channels {
    /** Creates both channels if missing. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(AndroidNightNotifier.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    AndroidNightNotifier.CHANNEL_ID,
                    context.getString(R.string.channel_night_results_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_night_results_desc) },
            )
        }
        if (manager.getNotificationChannel(AndroidProblemNotifier.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    AndroidProblemNotifier.CHANNEL_ID,
                    context.getString(R.string.channel_data_access_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.channel_data_access_desc) },
            )
        }
    }
}
