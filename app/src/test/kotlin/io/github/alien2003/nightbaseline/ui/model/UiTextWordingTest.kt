package io.github.alien2003.nightbaseline.ui.model

import android.app.Application
import android.content.res.Configuration
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.chartSummary
import io.github.alien2003.nightbaseline.work.notify.NightNotice
import io.github.alien2003.nightbaseline.work.notify.NoticeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * The wording fixes of the emulator walkthrough on the app's merged resources (English and Russian): one name per
 * measurement, Russian names in lower case inside a sentence, the low side of `M-SIDE-IGNORED`, learning progress
 * that counts the shown night, signed skin temperature, unbreakable values, capitalised month headings, chart
 * summaries with the readout's denominator, and notification sentences that keep their spaces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UiTextWordingTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private fun text(tag: String): UiText {
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        return UiText(app.createConfigurationContext(config).resources, locale)
    }

    private val en get() = text("en-US")
    private val ru get() = text("ru-RU")

    private fun moved(ids: List<MetricId>) =
        NightUi(
            wakeDate = LocalDate.of(2026, 11, 25),
            startDate = LocalDate.of(2026, 11, 24),
            state = NightState.Moved,
            metrics = ids.map { MetricUi(it, MetricState.Higher, 20.0, RangeUi(14.0, 16.0, 15.0, 28)) },
            deviating = ids.size,
        )

    @Test
    fun `the summary uses the cards' names, Russian lowercases them inside the sentence`() {
        val night = moved(listOf(MetricId.SLEEPING_HR, MetricId.RESPIRATORY_RATE, MetricId.SPO2))
        val body = en.nightBody(night, "Source")
        assertTrue(body, body.startsWith("${en.name(MetricId.SLEEPING_HR)}, ${en.name(MetricId.RESPIRATORY_RATE)}, and "))
        assertTrue(body, body.contains(en.name(MetricId.SPO2)))
        val r = ru
        val ruBody = r.nightBody(night, "Source")
        val first = r.name(MetricId.SLEEPING_HR)
        val second = r.name(MetricId.RESPIRATORY_RATE)
        assertTrue(ruBody, ruBody.startsWith(first))
        assertTrue(ruBody, ruBody.contains(second.replaceFirstChar { it.lowercase(r.locale) }))
        assertFalse(ruBody, ruBody.contains(", $second"))
        // Acronyms stay as they are.
        assertTrue(ruBody, ruBody.contains("(SpO2)"))
        val acronyms = r.list(listOf("SpO2", "HRV"))
        assertTrue(acronyms, acronyms.startsWith("SpO2 ") && acronyms.endsWith(" HRV"))
    }

    @Test
    fun `a lower value on an ignored side says lower in both languages`() {
        val metric = MetricUi(MetricId.RESPIRATORY_RATE, MetricState.SideIgnoredLower, 13.7, RangeUi(13.8, 15.4, 14.6, 28))
        assertEquals(en.string(R.string.metric_detail_side_ignored_lower, en.name(MetricId.RESPIRATORY_RATE)), en.detail(metric, "S"))
        assertTrue(en.detail(metric, "S").startsWith("Lower values"))
        assertEquals(ru.string(R.string.metric_detail_side_ignored_lower, ru.name(MetricId.RESPIRATORY_RATE)), ru.detail(metric, "S"))
        val higher = metric.copy(state = MetricState.SideIgnoredHigher, value = 15.9)
        assertTrue(en.detail(higher, "S").startsWith("Higher values"))
    }

    @Test
    fun `learning progress counts the shown night and says when the range is ready`() {
        val base = NightUi(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 24), NightState.Learning, emptyList())
        val third = base.copy(historyNights = 2, counted = true)
        assertEquals(3, third.collectedNights)
        assertEquals(en.plural(R.plurals.state_learning_body, 4, "4"), en.nightBody(third, "S"))
        val seventh = base.copy(historyNights = 6, counted = true)
        assertEquals(7, seventh.collectedNights)
        assertEquals(en.string(R.string.state_learning_ready_body), en.nightBody(seventh, "S"))
        // A short night does not count: the progress stays.
        assertEquals(2, base.copy(historyNights = 2, counted = false).collectedNights)
        val metric = MetricUi(MetricId.SPO2, MetricState.Learning, 95.0, learnedNights = 7)
        assertEquals(en.string(R.string.detail_learning_ready), en.detail(metric, "S"))
        assertEquals(en.plural(R.plurals.detail_learning, 2, "2"), en.detail(metric.copy(learnedNights = 5), "S"))
    }

    @Test
    fun `skin temperature is a signed change with a real minus and no dash next to it`() {
        val e = en
        val value = e.value(MetricId.SKIN_TEMPERATURE, -0.1)
        assertEquals("−0.1", value.number)
        assertEquals("°C vs usual", value.unit)
        assertEquals("+0.3", e.value(MetricId.SKIN_TEMPERATURE, 0.3).number)
        assertEquals("0.0", e.value(MetricId.SKIN_TEMPERATURE, -0.01).number)
        val range = RangeUi(-0.5, 0.5, 0.0, 28)
        assertEquals("Usual range: −0.5 to +0.5 °C", e.usualRange(MetricId.SKIN_TEMPERATURE, range))
        assertEquals("−0.5 to +0.5 °C", e.range(MetricId.SKIN_TEMPERATURE, range))
        // The distance outside the range is a size, not a direction.
        val high = MetricUi(MetricId.SKIN_TEMPERATURE, MetricState.Higher, 0.8, range)
        assertEquals(e.string(R.string.detail_higher, "0.3 °C"), e.detail(high, "S"))
        val r = ru
        assertEquals("−0,1", r.value(MetricId.SKIN_TEMPERATURE, -0.1).number)
        assertEquals(r.string(R.string.unit_celsius_vs_usual), r.value(MetricId.SKIN_TEMPERATURE, -0.1).unit)
        assertTrue(r.usualRange(MetricId.SKIN_TEMPERATURE, range).contains("−0,5"))
        // Other metrics keep the plain format.
        assertEquals("52", e.value(MetricId.SLEEPING_HR, 52.0).number)
        assertEquals("Usual range: 45–58 bpm", e.usualRange(MetricId.SLEEPING_HR, RangeUi(45.0, 58.0, 51.0, 28)))
    }

    @Test
    fun `values never break between number and unit or inside breaths per minute`() {
        val v = en.value(MetricId.RESPIRATORY_RATE, 14.2)
        assertEquals("14.2 breaths/⁠min", v.text)
        assertFalse(en.range(MetricId.RESPIRATORY_RATE, RangeUi(13.7, 15.4, 14.5, 28)).contains(' '))
        val label = en.nightLabel(LocalDate.of(2026, 11, 23))
        assertTrue(label, label.startsWith("Night of "))
        assertFalse(label, label.removePrefix("Night of ").any { it == ' ' || it == ' ' })
    }

    @Test
    fun `month headings start with a capital letter in Russian`() {
        val r = ru.month(LocalDate.of(2026, 11, 1))
        assertTrue(r, r.first().isUpperCase())
        assertTrue(r, r.contains("2026"))
        assertEquals("November 2026", en.month(LocalDate.of(2026, 11, 1)))
        // Inside sentences the month stays lower case.
        assertTrue(ru.dateLong(LocalDate.of(2026, 11, 25)).none { it.isUpperCase() })
    }

    @Test
    fun `the chart summary counts compared nights like the readout and ends a sentence once`() {
        val days = (0 until 10).map { LocalDate.of(2026, 11, 20).plusDays(it.toLong()) }
        val range = RangeUi(50.0, 60.0, 55.0, 7)
        val points =
            days.mapIndexed { i, d ->
                when {
                    i < 3 -> ChartPointUi(d, 55.0, null, MetricState.Learning)
                    i == 9 -> ChartPointUi(d, 65.0, range, MetricState.Higher)
                    else -> ChartPointUi(d, 55.0, range, MetricState.Within)
                }
            }
        val series = MetricSeriesUi(MetricId.SLEEPING_HR, points)
        val e = en
        val summary = e.chartSummary(series, "S")
        assertTrue(summary, summary.contains(e.string(R.string.chart_summary_outside, "1", "7", e.dateShort(days[9]))))
        val inside = series.copy(points = points.dropLast(1))
        assertTrue(e.chartSummary(inside, "S").contains(e.plural(R.plurals.chart_summary_all_inside, 6, "6")))
        val r = ru
        val ruSummary = r.chartSummary(series, "S")
        assertFalse(ruSummary, ruSummary.contains(".."))
    }

    @Test
    fun `Russian calls the middle value a median`() {
        // Built from code points: the repository keeps readable Russian only in resources.
        val median = String(intArrayOf(0x41C, 0x435, 0x434, 0x438, 0x430, 0x43D, 0x430), 0, 7)
        assertEquals(median, ru.string(R.string.stats_label_median))
    }

    @Test
    fun `notification sentences keep their spaces with the app's merged resources`() {
        val notice = NightNotice("2026-11-25", higher = listOf(MetricId.RESPIRATORY_RATE), lower = listOf(MetricId.SPO2))
        for (tag in listOf("en-US", "ru-RU")) {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val res = app.createConfigurationContext(config).resources
            val body = NoticeText.bodyLong(res, notice)
            assertTrue(body, Regex("""\)?\. \S""").findAll(body).count() >= 2)
            assertFalse(body, Regex("""\.[^\s.]""").containsMatchIn(body.replace("(SpO2)", "")))
        }
    }
}
