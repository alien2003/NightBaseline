package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import io.github.alien2003.nightbaseline.ui.components.GroupCard
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NbHairline
import io.github.alien2003.nightbaseline.ui.components.NightGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.components.StateCard
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls

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
        CompactNightSummary(night, source, modifier)
        return
    }
    Column(modifier.fillMaxWidth().widthIn(max = NbDimens.textMax), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
        if (night.state.hasSummary) {
            NightSummaryCard(night, source)
        } else {
            Text(
                text.nightLabel(night),
                style = NbControlType.sectionHeader,
                color = NbTheme.controls.sectionHeader,
                modifier = Modifier.padding(horizontal = NbSpace.s5).semantics { heading() },
            )
            StateCard(night.state.glyph, night.state.tone, text.nightTitle(night, source), text.nightBody(night, source))
        }
        if (night.metrics.isNotEmpty()) {
            GroupCard {
                night.metrics.forEachIndexed { i, metric ->
                    if (i > 0) NbHairline(start = NbControlDimens.rowPaddingHorizontal * 2 + NbDimens.icon)
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
fun CompactNightSummary(
    night: NightUi,
    source: String,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val label = text.nightLabel(night)
    val title = text.nightTitle(night, source)
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.textMax)
            .solidCard(NbShapes.l)
            .clearAndSetSemantics { contentDescription = "$label. $title" }
            .padding(NbSpace.s5),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = SUMMARY_GLYPH)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            Text(label, style = NbControlType.summary, color = colors.textSecondary)
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
            .sizeIn(minHeight = NbControlDimens.rowMinHeight)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = NbControlDimens.rowPaddingHorizontal, vertical = NbControlDimens.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbControlDimens.rowPaddingHorizontal),
    ) {
        Box(Modifier.size(NbDimens.icon), contentAlignment = Alignment.Center) {
            MetricGlyphIcon(metric.state.glyph, colors.tone(metric.state.tone))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbControlDimens.rowTitleGap)) {
            Text(text.name(metric.metric), style = NbControlType.itemTitle, color = colors.textPrimary)
            Text(text.status(metric, source), style = NbControlType.summary, color = colors.textSecondary)
        }
        Text(text.value(metric)?.text ?: "–", style = NbTheme.typography.numericSmall, color = colors.textPrimary)
    }
}

private val SUMMARY_GLYPH = 28.dp
