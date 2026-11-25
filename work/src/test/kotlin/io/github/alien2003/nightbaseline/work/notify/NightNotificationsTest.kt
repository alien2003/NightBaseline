package io.github.alien2003.nightbaseline.work.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.work.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

/** Notification texts (English and Russian) and the posted notifications (docs/design/states.md §6), on Robolectric. */
@RunWith(RobolectricTestRunner::class)
class NightNotificationsTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val two = NightNotice("2026-11-25", higher = listOf(MetricId.SLEEPING_HR, MetricId.RESPIRATORY_RATE))
    private val mixed = NightNotice("2026-12-01", higher = listOf(MetricId.SLEEPING_HR), lower = listOf(MetricId.SPO2))
    private val five =
        NightNotice(
            "2026-11-25",
            higher = listOf(MetricId.SLEEPING_HR, MetricId.RESPIRATORY_RATE, MetricId.SKIN_TEMPERATURE),
            lower = listOf(MetricId.SPO2, MetricId.HRV),
        )
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @Test
    fun englishCopyFollowsTheDesign() {
        val res = app.resources
        assertEquals("2 measurements moved outside your usual range", NoticeText.title(res, two))
        assertEquals("Heart rate and Breathing. Night of Nov 24–25.", NoticeText.body(res, two, Locale.ENGLISH))
        assertEquals("Heart rate, Breathing, Skin temp. and 2 more. Night of Nov 24–25.", NoticeText.body(res, five, Locale.ENGLISH))
        // Across a month boundary both months are named.
        assertEquals("Heart rate and SpO2. Night of Nov 30–Dec 1.", NoticeText.body(res, mixed, Locale.ENGLISH))
        assertEquals(
            "Higher than usual: Heart rate. Lower than usual: SpO2. This compares the night with your own recent nights and " +
                "isn't a diagnosis. Not a medical device.",
            NoticeText.bodyLong(res, mixed),
        )
        // Empty direction groups are omitted.
        assertTrue(NoticeText.bodyLong(res, two).startsWith("Higher than usual: Heart rate and Breathing. This compares"))
        assertEquals("Other", NoticeText.list(res, listOf(MetricId("future_metric"))))
    }

    @Test
    @Config(qualifiers = "ru")
    fun russianCopyFollowsTheDraft() {
        val res = app.resources
        val ru = Locale.forLanguageTag("ru")
        // Expected Russian text comes from values-ru (the repository keeps Russian only in resources).
        assertEquals(res.getQuantityString(R.plurals.notif_dev_title, 2, 2), NoticeText.title(res, two))
        assertEquals(res.getQuantityString(R.plurals.notif_dev_title, 5, 5), NoticeText.title(res, five))
        // Russian uses different plural forms for 2 ("few") and 5 ("many").
        assertNotEquals(NoticeText.title(res, two).drop(1), NoticeText.title(res, five).drop(1))
        val hr = res.getString(R.string.metric_sleeping_hr_short)
        val rr = res.getString(R.string.metric_respiratory_rate_short)
        assertTrue(NoticeText.body(res, two, ru).startsWith(res.getString(R.string.notif_list_and, hr, rr)))
        assertTrue(NoticeText.body(res, five, ru).contains(res.getQuantityString(R.plurals.notif_list_more, 2, 2)))
        assertNotEquals(NoticeText.bodyLong(res, mixed), "")
        assertTrue("Russian title is fully translated", NoticeText.title(res, two).none { it in 'A'..'z' })
    }

    @Test
    fun noStringSaysVitalsOrSoundsAlarming() {
        val ids = R.string::class.java.fields.map { it.getInt(null) }
        // Russian stems for "vital", "alarm" and "danger" are built from code points (RU_STEMS): the repository keeps
        // readable Russian only in resources.
        val forbidden =
            listOf("vital", "alert", "warning", "abnormal", "danger") + RU_STEMS
        for (locale in listOf("en", "ru")) {
            val config =
                android.content.res
                    .Configuration(app.resources.configuration)
                    .apply { setLocale(Locale.forLanguageTag(locale)) }
            val res = app.createConfigurationContext(config).resources
            ids.forEach { id ->
                val text = res.getString(id).lowercase(Locale.ROOT)
                forbidden.forEach { word -> assertFalse("$locale: $text", text.contains(word)) }
                assertFalse("$locale: $text", text.contains("!"))
            }
        }
    }

    @Test
    fun nightResultIsPostedOnNightResultsWithFixedIdAndADeepLink() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidNightNotifier(app)

        assertTrue(notifier.show(two))

        val channel = checkNotNull(manager.getNotificationChannel("night_results"))
        assertEquals("Night results", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("2 measurements moved outside your usual range", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals(0, posted.actions?.size ?: 0)
        assertEquals(null, posted.group)
        val publicExtras = posted.publicVersion.extras
        assertEquals("NightBaseline", publicExtras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
        assertEquals("Last night's result is ready.", publicExtras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())

        val intent = shadowOf(posted.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(app.packageName, intent.`package`)
        assertEquals("nightbaseline://night/2026-11-25", intent.dataString)
        assertEquals("2026-11-25", intent.getStringExtra(NightIntents.EXTRA_NIGHT_DATE))

        // A newer night replaces the older notification (fixed id and tag).
        assertTrue(notifier.show(mixed))
        val all = shadowOf(manager).allNotifications
        assertEquals(1, all.size)
        assertEquals(
            "nightbaseline://night/2026-12-01",
            shadowOf(all.single().contentIntent).savedIntent.dataString,
        )
    }

    @Test
    fun withoutThePermissionNothingIsPosted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(AndroidNightNotifier(app).show(two))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun dataProblemsArePostedOnceOnALowChannelAndClearedWhenResolved() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val problems = AndroidProblemNotifier(app)

        problems.update(DataProblem.PERMISSION)
        val channel = checkNotNull(manager.getNotificationChannel("data_access"))
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Problems reading data", channel.name)
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("NightBaseline can't read your sleep data", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("nightbaseline://fix/permission", shadowOf(posted.contentIntent).savedIntent.dataString)

        // Swiped away: not posted again while the problem lasts.
        manager.cancelAll()
        problems.update(DataProblem.PERMISSION)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())

        // Another problem replaces it; resolving cancels everything.
        problems.update(DataProblem.HEALTH_CONNECT)
        assertEquals(
            "nightbaseline://fix/health-connect",
            shadowOf(shadowOf(manager).allNotifications.single().contentIntent).savedIntent.dataString,
        )
        problems.update(null)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        // After it was resolved, a returning problem is posted again.
        problems.update(DataProblem.PERMISSION)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    private companion object {
        private fun stem(vararg codePoints: Int) = String(codePoints, 0, codePoints.size)

        val RU_STEMS =
            listOf(
                stem(0x432, 0x438, 0x442, 0x430, 0x43B),
                stem(0x442, 0x440, 0x435, 0x432, 0x43E, 0x433),
                stem(0x43E, 0x43F, 0x430, 0x441, 0x43D),
            )
    }
}
