package io.github.alien2003.nightbaseline.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.DASH
import io.github.alien2003.nightbaseline.ui.components.InlineBanner
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NightGlyphBadge
import io.github.alien2003.nightbaseline.ui.components.NightRow
import io.github.alien2003.nightbaseline.ui.components.PeriodSelector
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.components.RangeBand
import io.github.alien2003.nightbaseline.ui.components.StatsRow
import io.github.alien2003.nightbaseline.ui.components.TrendChart
import io.github.alien2003.nightbaseline.ui.components.statusGlyph
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.dataType
import java.time.LocalDate

/**
 * The supporting pane of Large and ExtraLarge Home (`layouts.md` 5.1): "Recent nights", the last 14 nights newest
 * first as History rows; a row opens that night.
 */
@Composable
internal fun RecentNightsPane(
    nights: List<NightUi>,
    onNight: (LocalDate) -> Unit,
) {
    BlockHeading(stringResource(R.string.home_recent_nights))
    nights.asReversed().forEach { night -> NightRow(night, onClick = { onNight(night.wakeDate) }) }
}

/**
 * Glance Home (`layouts.md` 5.1, flip cover screens and tiny windows): the night label with "More" at its end, the
 * night glyph with the headline, then one line per metric (glyph, short name, value, status label) as 48 dp targets;
 * a thin band per metric from 300 dp width. States show title, body and one button. No strip, no footer.
 */
@Composable
internal fun GlanceHome(
    night: NightUi,
    metricsNight: NightUi?,
    source: String,
    banner: BannerKind?,
    quietDays: Int,
    padding: PaddingValues,
    widthDp: Float,
    actions: HomeActions,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    scroll: ScrollState = rememberScrollState(),
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = NbDimens.gutterGlance, vertical = NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(
                if (night.state == NightState.HealthConnectNone) stringResource(R.string.home_title) else text.nightLabel(night),
                style = NbTheme.dataType.statusCalm,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).padding(start = if (leading == null) NbSpace.s1 else 0.dp),
            )
            NbIconButton(NbIcons.More, stringResource(R.string.action_more), onClick = actions.onMore)
        }
        banner?.let {
            InlineBanner(
                it,
                source,
                onAction = { actions.onBannerAction(it) },
                onDismiss = { actions.onBannerDismiss(it) },
                quietDays = quietDays,
            )
        }
        GlanceNightText(night, source, actions)
        metricsNight?.let { shown ->
            if (shown !== night) BlockHeading(text.string(R.string.home_last_result_heading, text.nightLabel(shown)))
            shown.metrics.forEach { metric -> GlanceMetricLine(metric, source, widthDp >= GLANCE_BAND_MIN_WIDTH, actions.onMetric) }
        }
        trailing?.invoke()
    }
}

private const val GLANCE_BAND_MIN_WIDTH = 300f

/** Glyph and headline (one TalkBack node with the body); problem and learning states add the body and one button. */
@Composable
private fun GlanceNightText(
    night: NightUi,
    source: String,
    actions: HomeActions,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val title = text.nightTitle(night, source)
    val body = text.nightBody(night, source)
    val description = listOf(title, body).filter { it.isNotEmpty() }.joinToString(". ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
        modifier =
            Modifier.clearAndSetSemantics {
                contentDescription =
                    description
            },
    ) {
        if (night.state != NightState.Loading) NightGlyphBadge(night.state.glyph, night.state.tone, container = 36.dp, glyphSize = 28.dp)
        Text(title.ifEmpty { body }, style = NbTheme.dataType.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
    if (night.state.hasSummary || night.state == NightState.Loading) return
    Text(body, style = NbTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics {})
    val label = text.nightAction(night, source) ?: return
    val learning = night.state == NightState.Learning || night.state == NightState.LearningStart
    PrimaryButton(label, onClick = { if (learning) actions.onHowItWorks() else actions.onStateAction(night.state) })
}

@Composable
private fun GlanceMetricLine(
    metric: MetricUi,
    source: String,
    band: Boolean,
    onMetric: (MetricId) -> Unit,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val value = text.value(metric)?.text ?: DASH
    val status = text.status(metric, source)
    val description = text.tileDescription(metric, source)
    Column(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = NbDimens.targetMin)
            .nbClickable(onClick = { onMetric(metric.metric) }, shape = NbShapes.m)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = NbSpace.s2, vertical = NbSpace.s2),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
            MetricGlyphIcon(metric.state.glyph, colors.statusGlyph(metric.state.tone))
            Column(Modifier.weight(1f)) {
                Text(text.shortName(metric.metric), style = NbTheme.dataType.itemTitle, color = colors.textPrimary)
                Text(
                    status,
                    style = NbTheme.typography.caption,
                    color = if (metric.state.moved) colors.statusMoved else colors.textSecondary,
                )
            }
            if (metric.state != MetricState.NotProvided) {
                Text(value, style = NbTheme.typography.numericSmall, color = colors.textPrimary)
            }
        }
        if (band && metric.state != MetricState.NotProvided && metric.state != MetricState.AccessOff) {
            RangeBand(metric.range, metric.value, metric.state, height = 8.dp)
        }
    }
}
