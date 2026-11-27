// Debug-only catalog: sizes of swatches and demo boxes are layout literals, not tokens.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.CalendarCell
import io.github.alien2003.nightbaseline.ui.components.FloatingNavBar
import io.github.alien2003.nightbaseline.ui.components.FloatingNavRail
import io.github.alien2003.nightbaseline.ui.components.GlassDialog
import io.github.alien2003.nightbaseline.ui.components.GlassSheet
import io.github.alien2003.nightbaseline.ui.components.HistoryStrip
import io.github.alien2003.nightbaseline.ui.components.InlineBanner
import io.github.alien2003.nightbaseline.ui.components.LearningCard
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.MetricRangeTile
import io.github.alien2003.nightbaseline.ui.components.MonthCalendar
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NightRow
import io.github.alien2003.nightbaseline.ui.components.NightStateCard
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.PeriodSelector
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.components.RangeBand
import io.github.alien2003.nightbaseline.ui.components.SecondaryButton
import io.github.alien2003.nightbaseline.ui.components.SettingsGroup
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.components.SettingsTrailing
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.StatsRow
import io.github.alien2003.nightbaseline.ui.components.StatusLabel
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.TrendChart
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.interaction.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.interaction.ModalKind
import io.github.alien2003.nightbaseline.ui.interaction.StaticModal
import io.github.alien2003.nightbaseline.ui.material.LocalHazeState
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricGlyph
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.RangeUi
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NbContrast
import io.github.alien2003.nightbaseline.ui.theme.NbContrastPairs
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import java.time.YearMonth

/** Renders one catalog page with [metricCount] metrics where a list of metrics is shown. */
@Composable
fun CatalogPageContent(
    page: CatalogPage,
    modifier: Modifier = Modifier,
    metricCount: Int = 6,
) {
    Column(
        modifier.fillMaxWidth().padding(NbDimens.gutterCompact),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s6),
    ) {
        NbText(page.title, style = NbTheme.typography.largeTitle, color = NbTheme.colors.textPrimary)
        when (page) {
            CatalogPage.Foundations -> FoundationsPage()
            CatalogPage.Shell -> ShellPage()
            CatalogPage.Metrics -> MetricsPage(metricCount)
            CatalogPage.Nights -> NightsPage(metricCount)
            CatalogPage.Charts -> ChartsPage()
            CatalogPage.Lists -> ListsPage()
        }
    }
}

/** A titled group of entries laid out in as many columns of at least [minEntry] as fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(
    title: String,
    minEntry: Dp = 340.dp,
    content: @Composable (entryWidth: Dp) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s4)) {
        NbText(title, style = NbTheme.typography.title, color = NbTheme.colors.textPrimary)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = NbSpace.s5
            val columns = ((maxWidth + gap) / (minEntry + gap)).toInt().coerceAtLeast(1)
            val width = (maxWidth - gap * (columns - 1)) / columns
            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                content(width)
            }
        }
    }
}

/** One labelled entry of a [Section]. */
@Composable
private fun Entry(
    label: String,
    width: Dp,
    content: @Composable () -> Unit,
) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
        NbText(label, style = NbTheme.typography.caption, color = NbTheme.colors.textTertiary)
        content()
    }
}

// --- Foundations ---------------------------------------------------------------------------------------------------

@Composable
private fun FoundationsPage() {
    val colors = NbTheme.colors
    val swatches =
        listOf(
            "background" to colors.background,
            "surface.1" to colors.surface1,
            "surface.2" to colors.surface2,
            "surface.3" to colors.surface3,
            "text.primary" to colors.textPrimary,
            "text.secondary" to colors.textSecondary,
            "text.tertiary" to colors.textTertiary,
            "outline" to colors.outline,
            "primary" to colors.primary,
            "primaryText" to colors.primaryText,
            "primaryFill" to colors.primaryFill,
            "status.within" to colors.statusWithin,
            "status.moved" to colors.statusMoved,
            "status.neutral" to colors.statusNeutral,
            "status.info" to colors.statusInfo,
            "band.fill" to colors.bandFill,
            "chart.point" to colors.chartPoint,
            "focus.ring" to colors.focusRing,
        )
    ContrastSection()
    Section("Colour tokens", minEntry = 150.dp) { width ->
        swatches.forEach { (name, color) ->
            Entry(name, width) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(color, NbShapes.s)
                        .border(1.dp, colors.separator, NbShapes.s),
                )
            }
        }
    }
    Section("Type scale") { width ->
        val t = NbTheme.typography
        listOf<Pair<String, TextStyle>>(
            "largeTitle 28/34" to t.largeTitle,
            "headline 22/28" to t.headline,
            "title 20/26" to t.title,
            "titleSmall 16/22" to t.titleSmall,
            "body 16/24" to t.body,
            "bodySmall 14/20" to t.bodySmall,
            "label 14/20" to t.label,
            "labelSmall 12/16" to t.labelSmall,
            "caption 12/16" to t.caption,
            "numeric.display 48/56 tnum" to t.numericDisplay,
            "numeric.value 32/38 tnum" to t.numericValue,
            "numeric.small 15/20 tnum" to t.numericSmall,
            "numeric.axis 12/16 tnum" to t.numericAxis,
        ).forEach { (name, style) ->
            Entry(name, width) {
                NbText(if (name.startsWith("numeric")) "1234567890" else "Last night 1110", style = style, color = colors.textPrimary)
            }
        }
    }
    Section("Shapes and elevation", minEntry = 150.dp) { width ->
        listOf<Pair<String, Shape>>(
            "shape.s 8" to NbShapes.s,
            "shape.m 12" to NbShapes.m,
            "shape.l 20 smooth" to NbShapes.l,
            "shape.xl 28 smooth" to NbShapes.xl,
            "shape.dialog 32" to NbShapes.dialog,
            "shape.pill" to NbShapes.pill,
        ).forEach { (name, shape) ->
            Entry(name, width) {
                NbCard(Modifier.fillMaxWidth().height(64.dp), shape = shape) {}
            }
        }
    }
    Section("Status glyphs", minEntry = 150.dp) { width ->
        MetricGlyph.entries.forEach { glyph ->
            Entry("metric: ${glyph.name}", width) {
                MetricGlyphIcon(glyph, colors.textPrimary, size = NbDimens.icon)
            }
        }
        NightGlyph.entries.filter { it != NightGlyph.None }.forEach { glyph ->
            Entry("night: ${glyph.name}", width) {
                NightGlyphIcon(glyph, colors.textPrimary, size = 32.dp, background = colors.background)
            }
        }
    }
}

/** Every token pair with its ratio in this theme; failing pairs are listed first. */
@Composable
private fun ContrastSection() {
    val colors = NbTheme.colors
    val rows = NbContrastPairs.map { it to (NbContrast.ratio(it, colors) ?: 0.0) }
    val fails = rows.filter { (pair, ratio) -> ratio < pair.min }
    Section("Contrast (${if (colors.isDark) "dark" else "light"}): ${rows.size} pairs, ${fails.size} fail", minEntry = 300.dp) { width ->
        (fails + rows.filter { it !in fails }).forEach { (pair, ratio) ->
            Entry(pair.name, width) {
                NbText(
                    "%.2f : 1 (min %.1f) %s".format(ratio, pair.min, if (ratio >= pair.min) "pass" else "FAIL"),
                    style = NbTheme.typography.numericAxis,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

// --- Shell ---------------------------------------------------------------------------------------------------------

/**
 * A busy synthetic backdrop (coloured stripes, blobs and text) as the blur source of the glass component on top of
 * it, so glass and its solid fallback can be compared (`principles.md` 3).
 */
@Composable
private fun OverBackdrop(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberHazeState()
    val colors = NbTheme.colors
    // At least [height]; grows with its content at large font so the demo itself never clips.
    Box(modifier.fillMaxWidth().heightIn(min = height), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .hazeSource(state)
                .background(colors.background),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stripe = 18.dp.toPx()
                val palette = listOf(colors.statusMoved, colors.primary, colors.statusWithin, colors.primaryText, colors.textPrimary)
                var x = -size.height
                var i = 0
                while (x < size.width) {
                    drawLine(palette[i % palette.size], Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stripe * 0.6f)
                    x += stripe * 2
                    i++
                }
                drawCircle(
                    Brush.radialGradient(listOf(colors.statusMoved, Color.Transparent)),
                    radius = size.minDimension * 0.4f,
                    center =
                        Offset(size.width * 0.3f, size.height * 0.4f),
                )
            }
            NbText(
                "Backdrop text 52–61 bpm. ".repeat(12),
                style = NbTheme.typography.body,
                color = colors.textPrimary,
                // Decorative and intentionally cut by the box: not part of the accessibility tree.
                modifier = Modifier.padding(NbSpace.s4).clearAndSetSemantics {},
            )
        }
        CompositionLocalProvider(LocalHazeState provides state) { content() }
    }
}

@Composable
private fun ShellPage() {
    Section("Floating bottom bar") { width ->
        TopLevel.entries.forEach { selected ->
            Entry("selected: ${selected.name}", width) {
                OverBackdrop(120.dp) { FloatingNavBar(selected, onSelect = {}) }
            }
        }
        ForcedInteraction.entries.forEach { forced ->
            Entry("History ${forced.name.lowercase()}", width) {
                OverBackdrop(120.dp) { FloatingNavBar(TopLevel.Home, onSelect = {}, forced = TopLevel.History to forced) }
            }
        }
    }
    Section("Floating rail", minEntry = 220.dp) { width ->
        Entry("labels", width) { OverBackdrop(300.dp) { FloatingNavRail(TopLevel.History, onSelect = {}) } }
        Entry("icons only (WideShort)", width) {
            OverBackdrop(300.dp) { FloatingNavRail(TopLevel.Home, onSelect = {}, showLabels = false) }
        }
        Entry("focus on Settings", width) {
            OverBackdrop(300.dp) { FloatingNavRail(TopLevel.Home, onSelect = {}, forced = TopLevel.Settings to ForcedInteraction.Focus) }
        }
    }
    Section("Top bar") { width ->
        Entry("large title at rest (transparent)", width) {
            OverBackdrop(150.dp) { ShellTopBar("Last night", subtitle = "Night of Nov 24–25", large = true) }
        }
        Entry("collapsed, content under it (glass)", width) {
            OverBackdrop(150.dp) {
                ShellTopBar("Last night", large = true, scroll = 200.dp, onBack = {})
            }
        }
    }
    Section("Sheet and dialog", minEntry = 360.dp) { width ->
        Entry("bottom sheet", width) {
            OverBackdrop(380.dp) {
                Box(Modifier.fillMaxWidth().heightIn(min = 380.dp), contentAlignment = Alignment.BottomCenter) {
                    StaticModal {
                        GlassSheet(title = rememberUiText().string(R.string.explainer_title)) {
                            NbCard(Modifier.fillMaxWidth()) {
                                NbText(
                                    rememberUiText().string(R.string.explainer_range),
                                    style = NbTheme.typography.body,
                                    color = NbTheme.colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Entry("dialog", width) {
            val text = rememberUiText()
            OverBackdrop(380.dp) {
                StaticModal(ModalKind.Dialog) {
                    GlassDialog(
                        title = text.string(R.string.sens_reset_title),
                        body = text.string(R.string.sens_reset_body),
                        confirm = text.string(R.string.action_reset),
                        onConfirm = {},
                        dismiss = text.string(R.string.action_cancel),
                        onDismiss = {},
                        modifier = Modifier.padding(NbSpace.s5),
                    )
                }
            }
        }
    }
}

// --- Metrics -------------------------------------------------------------------------------------------------------

@Composable
private fun MetricsPage(metricCount: Int) {
    Section("Metric tile: every state (sleeping heart rate)") { width ->
        MetricState.entries.forEach { state ->
            Entry(state.name, width) {
                MetricRangeTile(SampleData.metric(MetricId.SLEEPING_HR, state), SampleData.SOURCE, onClick = {})
            }
        }
        Entry("hover", width) {
            MetricRangeTile(
                SampleData.metric(MetricId.SPO2, MetricState.Within),
                SampleData.SOURCE,
                onClick = {},
                forced = ForcedInteraction.Hover,
            )
        }
        Entry("focus + selected", width) {
            MetricRangeTile(
                SampleData.metric(MetricId.RESPIRATORY_RATE, MetricState.Higher),
                SampleData.SOURCE,
                onClick = {},
                selected = true,
                forced = ForcedInteraction.Focus,
            )
        }
    }
    Section("Metric tiles: last night, $metricCount metrics") { width ->
        SampleData.lastNight(metricCount).metrics.forEach { metric ->
            Entry(metric.metric.key, width) { MetricRangeTile(metric, SampleData.SOURCE, onClick = {}) }
        }
    }
    Section("Range band") { width ->
        val range = RangeUi(52.0, 61.0, 56.0)
        listOf(
            "inside" to (56.5 to MetricState.Within),
            "outside right (higher)" to (64.0 to MetricState.Higher),
            "outside left (lower)" to (49.0 to MetricState.Lower),
            "clamped right" to (80.0 to MetricState.Higher),
            "clamped left" to (30.0 to MetricState.Lower),
            "side ignored (outside, usual)" to (63.0 to MetricState.SideIgnoredHigher),
        ).forEach { (label, v) ->
            Entry(label, width) { RangeBand(range, v.first, v.second) }
        }
        Entry("learning outline", width) { RangeBand(null, 57.0, MetricState.Learning) }
        Entry("no value", width) { RangeBand(range, null, MetricState.NoData) }
    }
    Section("Status chips", minEntry = 200.dp) { width ->
        val text = rememberUiText()
        MetricState.entries.forEach { state ->
            Entry(state.name, width) {
                StatusLabel(text.status(SampleData.metric(MetricId.SLEEPING_HR, state), SampleData.SOURCE), state.glyph, state.tone)
            }
        }
    }
}

// --- Nights --------------------------------------------------------------------------------------------------------

@Composable
private fun NightsPage(metricCount: Int) {
    Section("Night summary") { width ->
        listOf(NightState.AllWithin, NightState.SomeMoved, NightState.Moved).forEach { state ->
            Entry(state.name, width) {
                val night = SampleData.night(state)
                NightSummaryCard(night.copy(metrics = night.metrics.take(metricCount)), SampleData.SOURCE)
            }
        }
        Entry("generated last night", width) { NightSummaryCard(SampleData.lastNight, SampleData.SOURCE) }
    }
    Section("Learning") { width ->
        Entry("L0", width) { LearningCard(SampleData.night(NightState.LearningStart), SampleData.SOURCE, onHowItWorks = {}) }
        Entry("L4", width) { LearningCard(SampleData.night(NightState.Learning), SampleData.SOURCE, onHowItWorks = {}) }
        Entry("L4, last night short", width) {
            LearningCard(SampleData.night(NightState.Learning), SampleData.SOURCE, onHowItWorks = {}, lastNightShort = true)
        }
        Entry("progress 0..7", width) {
            Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s4)) {
                listOf(0, 3, 6).forEach { LearningProgress(it, skipped = if (it == 3) 1 else 0) }
            }
        }
    }
    Section("State cards") { width ->
        NightState.entries.filter { !it.hasSummary && it != NightState.Learning && it != NightState.LearningStart }.forEach { state ->
            Entry(state.name, width) { NightStateCard(SampleData.night(state), SampleData.SOURCE) }
        }
    }
    Section("Banners") { width ->
        BannerKind.entries.forEach { kind ->
            Entry(kind.name, width) { InlineBanner(kind, SampleData.SOURCE, onDismiss = {}) }
        }
    }
}

// --- Charts --------------------------------------------------------------------------------------------------------

@Composable
private fun ChartsPage() {
    Section("Period selector") { width ->
        ChartPeriod.entries.forEach { period ->
            Entry("selected ${period.nights}", width) { PeriodSelector(period, onSelect = {}) }
        }
        Entry("focus on 90", width) {
            PeriodSelector(
                ChartPeriod.Month,
                onSelect = {},
                forced =
                    ChartPeriod.Quarter to ForcedInteraction.Focus,
            )
        }
    }
    Section("Trend chart", minEntry = 380.dp) { width ->
        ChartPeriod.entries.forEach { period ->
            Entry("heart rate, ${period.nights} nights", width) {
                TrendChart(SampleData.series(MetricId.SLEEPING_HR, period.nights), SampleData.SOURCE, selected = null, onSelect = {})
            }
        }
        Entry("tooltip on the last night", width) {
            val series = SampleData.series(MetricId.RESPIRATORY_RATE, ChartPeriod.Month.nights)
            TrendChart(series, SampleData.SOURCE, selected = series.points.lastIndex, onSelect = {})
        }
        Entry("learning nights (hollow, no band)", width) {
            TrendChart(SampleData.learningSeries(MetricId.SPO2, ChartPeriod.Week.nights), SampleData.SOURCE, selected = null, onSelect = {})
        }
        Entry("gaps (90 nights of HRV)", width) {
            TrendChart(SampleData.series(MetricId.HRV, ChartPeriod.Quarter.nights), SampleData.SOURCE, selected = 40, onSelect = {})
        }
    }
    Section("Stats row", minEntry = 380.dp) { width ->
        Entry("sleep duration, 30 nights", width) { StatsRow(SampleData.series(MetricId.SLEEP_DURATION, ChartPeriod.Month.nights)) }
    }
}

// --- Lists ---------------------------------------------------------------------------------------------------------

@Composable
private fun ListsPage() {
    val recent = SampleData.recent(7)
    Section("History rows") { width ->
        listOf(NightState.AllWithin, NightState.SomeMoved, NightState.Moved, NightState.Learning, NightState.Short, NightState.NoData)
            .forEach { state -> Entry(state.name, width) { NightRow(SampleData.night(state), onClick = {}) } }
        Entry("selected + focus", width) { NightRow(recent.last(), onClick = {}, selected = true, forced = ForcedInteraction.Focus) }
    }
    Section("Calendar", minEntry = 360.dp) { width ->
        Entry("November 2026", width) {
            NbCard(Modifier.fillMaxWidth()) {
                MonthCalendar(
                    month = YearMonth.from(SampleData.LAST_WAKE),
                    nights = SampleData.nights.associateBy { it.wakeDate },
                    onSelect = {},
                    today = SampleData.LAST_WAKE,
                    selected = SampleData.LAST_WAKE.minusDays(3),
                )
            }
        }
        Entry("cells: plain, today, selected, empty", width) {
            Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                val night = recent.last()
                CalendarCell(night.wakeDate, night, onClick = {}, modifier = Modifier.size(56.dp))
                CalendarCell(night.wakeDate, night, onClick = {}, today = true, modifier = Modifier.size(56.dp))
                CalendarCell(night.wakeDate, night, onClick = {}, selected = true, modifier = Modifier.size(56.dp))
                CalendarCell(night.wakeDate.plusDays(1), null, onClick = {}, modifier = Modifier.size(56.dp))
            }
        }
    }
    Section("History strip") { width ->
        Entry("7 nights, tappable", width) { HistoryStrip(recent, onNight = {}, onSeeAll = {}) }
        Entry("one target (narrow pane)", width) { HistoryStrip(recent, onNight = {}, onSeeAll = {}, individualTargets = false) }
        Entry("view only (tabletop top half)", width) { HistoryStrip(recent, onNight = {}, onSeeAll = {}, interactive = false) }
    }
    Section("Settings rows") { width ->
        val text = rememberUiText()
        Entry("group", width) {
            SettingsGroup(text.string(R.string.settings_group_source)) {
                SettingsRow(text.string(R.string.settings_source), onClick = {}, trailing = SettingsTrailing.Value(SampleData.SOURCE))
                SettingsRow(text.string(R.string.settings_hc_permissions), onClick = {}, showDivider = true)
                SettingsRow(
                    text.string(R.string.settings_last_read_title),
                    onClick = {},
                    summary = text.string(R.string.settings_never_read),
                    trailing = SettingsTrailing.None,
                    showDivider = true,
                )
            }
        }
        Entry("switches", width) {
            SettingsGroup(text.string(R.string.settings_group_notifications)) {
                SettingsRow(text.string(R.string.settings_notif_results), onClick = {}, trailing = SettingsTrailing.Toggle(true))
                SettingsRow(
                    text.string(R.string.settings_notif_problems),
                    onClick = {},
                    summary = text.string(R.string.settings_notif_blocked),
                    trailing = SettingsTrailing.Toggle(false),
                    enabled = false,
                    showDivider = true,
                )
            }
        }
        Entry("radio rows + hover", width) {
            SettingsGroup(text.string(R.string.settings_group_sensitivity)) {
                SettingsRow(
                    text.string(R.string.sens_fewer),
                    onClick = {},
                    summary = text.string(R.string.sens_fewer_desc),
                    trailing = SettingsTrailing.Radio(false),
                    forced = ForcedInteraction.Hover,
                )
                SettingsRow(
                    text.string(R.string.sens_balanced),
                    onClick = {},
                    summary = text.string(R.string.sens_balanced_desc),
                    trailing = SettingsTrailing.Radio(true),
                    showDivider = true,
                )
            }
        }
    }
    Section("Buttons", minEntry = 200.dp) { width ->
        val text = rememberUiText()
        Entry("primary", width) { PrimaryButton(text.string(R.string.action_check_now), onClick = {}) }
        Entry(
            "primary pressed",
            width,
        ) { PrimaryButton(text.string(R.string.action_check_now), onClick = {}, forced = ForcedInteraction.Pressed) }
        Entry(
            "primary focus",
            width,
        ) { PrimaryButton(text.string(R.string.action_check_now), onClick = {}, forced = ForcedInteraction.Focus) }
        Entry("primary disabled", width) { PrimaryButton(text.string(R.string.action_check_now), onClick = {}, enabled = false) }
        Entry("secondary", width) { SecondaryButton(text.string(R.string.action_see_history), onClick = {}) }
        Entry("text", width) { NbTextButton(text.string(R.string.action_learn_more), onClick = {}) }
        Entry("tones", width) {
            Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                StatusTone.entries.forEach { tone -> Box(Modifier.size(24.dp).background(NbTheme.colors.tone(tone), NbShapes.circle)) }
            }
        }
    }
}
