package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
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
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NightGlyphBadge
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.components.StateCard
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * The default summary of the selected night (History detail pane, tabletop top half, calendar reflow): the summary or
 * state card of the night (no state actions: they belong to Home), one solid line per metric (glyph, name, status
 * label, value) and, when [interactive], "Open night" -> Night detail. In the tabletop top half [interactive] is false
 * (view only). The History screen takes any composable in its place (slot), so the full Night detail pane can be
 * plugged in once it exists.
 */
@Composable
fun HistoryNightPreview(
    night: NightUi,
    source: String,
    interactive: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    if (!interactive) {
        GlanceSummary(night, source, modifier)
        return
    }
    Column(modifier.fillMaxWidth().widthIn(max = NbDimens.textMax), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
        if (night.state.hasSummary) {
            NightSummaryCard(night, source)
        } else {
            Text(
                text.nightLabel(night),
                style = NbTheme.typography.titleSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = NbSpace.s2).semantics { heading() },
            )
            StateCard(night.state.glyph, night.state.tone, text.nightTitle(night, source), text.nightBody(night, source))
        }
        if (night.metrics.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().solidCard(NbShapes.l).padding(vertical = NbSpace.s2)) {
                night.metrics.forEachIndexed { i, metric ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .padding(start = NbSpace.s5 + NbDimens.icon + NbSpace.s4)
                                .fillMaxWidth()
                                .height(SEPARATOR)
                                .background(colors.separator),
                        )
                    }
                    MetricLine(metric, source)
                }
            }
        }
        if (interactive) PrimaryButton(stringResource(R.string.history_open_night), onClick = onOpen)
    }
}

/**
 * The view-only summary of the tabletop top half: glyph, night label and headline in one solid card (one TalkBack
 * node). The body and metric lines are left to the Night detail: half a folded screen cannot hold them at large font.
 */
@Composable
private fun GlanceSummary(
    night: NightUi,
    source: String,
    modifier: Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val label = text.nightLabel(night)
    val title = text.nightTitle(night, source)
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.textMax)
            .solidCard(NbShapes.xl)
            .clearAndSetSemantics { contentDescription = "$label. $title" }
            .padding(NbSpace.s5),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NightGlyphBadge(night.state.glyph, night.state.tone, container = 48.dp, glyphSize = NbDimens.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            Text(label, style = NbTheme.typography.labelSmall, color = colors.textSecondary)
            Text(title, style = NbTheme.typography.title, color = colors.textPrimary)
        }
    }
}

@Composable
private fun MetricLine(
    metric: MetricUi,
    source: String,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val description = text.tileDescription(metric, source)
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = NbDimens.targetMin)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = NbSpace.s5, vertical = NbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        MetricGlyphIcon(metric.state.glyph, colors.tone(metric.state.tone))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            Text(text.name(metric.metric), style = NbTheme.typography.titleSmall, color = colors.textPrimary)
            Text(text.status(metric, source), style = NbTheme.typography.caption, color = colors.textSecondary)
        }
        Text(text.value(metric)?.text ?: "–", style = NbTheme.typography.numericSmall, color = colors.textPrimary)
    }
}

private val SEPARATOR = 1.dp
