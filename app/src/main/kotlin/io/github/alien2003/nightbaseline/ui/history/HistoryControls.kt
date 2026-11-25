package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * The "List / Calendar" segmented control (`screens.md` 7.4): a `surface.3` pill track, the selected segment on a
 * `primaryContainer` capsule with a bold label (selection is shape and weight, not colour alone). Each segment is a
 * 48 dp tab.
 */
@Composable
fun HistoryViewToggle(
    selected: HistoryView,
    onSelect: (HistoryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Row(
        modifier
            .background(colors.surface3, NbShapes.pill)
            .padding(NbSpace.s1)
            .selectableGroup(),
    ) {
        HistoryView.entries.forEach { view ->
            val isSelected = view == selected
            Box(
                Modifier
                    .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbDimens.targetMin)
                    .then(if (isSelected) Modifier.background(colors.primaryContainer, NbShapes.pill) else Modifier)
                    .nbClickable(onClick = { onSelect(view) }, shape = NbShapes.pill, role = Role.Tab)
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = NbSpace.s5, vertical = NbSpace.s3),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (view == HistoryView.List) R.string.history_view_list else R.string.history_view_calendar),
                    style = NbTheme.typography.label.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (isSelected) colors.onPrimaryContainer else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Filter chips "All / Moved / Learning and gaps" (`screens.md` 7.4): single choice, each chip >= 48 dp tall, the
 * selected chip carries a check glyph, a bold label and a tonal fill; the others an outline. Chips wrap at large font.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryFilterChips(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    FlowRow(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
    ) {
        HistoryFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val label =
                stringResource(
                    when (filter) {
                        HistoryFilter.All -> R.string.history_filter_all
                        HistoryFilter.Moved -> R.string.history_filter_moved
                        HistoryFilter.Gaps -> R.string.history_filter_gaps
                    },
                )
            // A 48 dp target around a 36 dp chip (the target is the outer box).
            Box(
                Modifier
                    .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbDimens.targetMin)
                    .nbClickable(onClick = { onSelect(filter) }, shape = NbShapes.pill, role = Role.RadioButton)
                    .semantics { this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .sizeIn(minHeight = CHIP_HEIGHT)
                        .then(
                            if (isSelected) {
                                Modifier.background(colors.primaryContainer, NbShapes.pill)
                            } else {
                                Modifier.border(1.dp, colors.outline, NbShapes.pill)
                            },
                        ).padding(horizontal = NbSpace.s4, vertical = NbSpace.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NbSpace.s2),
                ) {
                    if (isSelected) {
                        Icon(NbIcons.Check, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        label,
                        style = NbTheme.typography.label.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = if (isSelected) colors.onPrimaryContainer else colors.textSecondary,
                    )
                }
            }
        }
    }
}

private val CHIP_HEIGHT = 36.dp

/**
 * The banner above still-listed rows when access is off or Health Connect needs an update (`states.md` 5:
 * `banner_perm_history`, `banner_hc_history`). Solid, info tone, outline icon, one action, polite live region.
 */
@Composable
fun HistoryAccessBanner(
    access: HistoryAccess,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    val glyph = if (access == HistoryAccess.Permission) NightGlyph.Key else NightGlyph.Download
    val message = if (access == HistoryAccess.Permission) R.string.banner_perm_history else R.string.banner_hc_history
    val action = if (access == HistoryAccess.Permission) R.string.action_open_hc_permissions else R.string.action_open_store
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.textMax)
            .solidCard(NbShapes.m)
            .padding(start = NbSpace.s5, top = NbSpace.s3, bottom = NbSpace.s3, end = NbSpace.s3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Box(Modifier.padding(top = NbSpace.s4)) { NightGlyphIcon(glyph, colors.statusInfo) }
        Column(Modifier.weight(1f).padding(top = NbSpace.s4), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            Text(stringResource(message), style = NbTheme.typography.bodySmall, color = colors.textPrimary)
            NbTextButton(stringResource(action), onClick = onAction)
        }
    }
}

/** A sticky month header of the feed ("November 2026"): solid window background, heading semantics. */
@Composable
fun HistoryMonthHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        style = NbTheme.typography.titleSmall,
        color = NbTheme.colors.textSecondary,
        modifier =
            modifier
                .fillMaxWidth()
                .background(NbTheme.colors.background)
                .padding(top = NbSpace.s5, bottom = NbSpace.s3, start = NbSpace.s2)
                .semantics { heading() },
    )
}

/**
 * The legend under the calendar ("What the marks mean", `history_legend_title`): glyph + label for every night status
 * a cell can show, so the shapes are learnable. Not interactive; glyphs are decorative, labels are read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryLegend(
    threshold: Int,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val entries =
        listOf(
            NightState.AllWithin to text.string(R.string.history_status_all),
            NightState.SomeMoved to text.plural(R.plurals.history_status_moved, 1, text.int(1)),
            NightState.Moved to text.plural(R.plurals.history_status_moved, threshold, text.int(threshold)),
            NightState.Learning to text.string(R.string.history_status_learning),
            NightState.Short to text.string(R.string.history_status_short),
            NightState.Ambiguous to text.string(R.string.history_status_ambiguous),
            NightState.NoData to text.string(R.string.history_status_nodata),
            NightState.NotEvaluated to text.string(R.string.history_status_few),
        )
    NbCard(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.history_legend_title),
            style = NbTheme.typography.titleSmall,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            entries.forEach { (state, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                    NightGlyphIcon(state.glyph, colors.tone(state.tone), size = LEGEND_GLYPH)
                    Text(label, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
    }
}

private val LEGEND_GLYPH = 18.dp

/** `H-LOADING`: static skeleton rows (no shimmer: idle screens never animate), one node with the loading text. */
@Composable
fun HistorySkeleton(
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    val description = stringResource(R.string.state_loading_body)
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = NbDimens.textMax)
                    .sizeIn(minHeight = NbDimens.rowMinHeight + NbSpace.s4)
                    .background(colors.surface1, NbShapes.l)
                    .padding(horizontal = NbSpace.s5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
            ) {
                Box(Modifier.size(NbDimens.icon).background(colors.surface3, NbShapes.circle))
                Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                    Box(Modifier.width(SKELETON_LONG).height(SKELETON_LINE).background(colors.surface3, NbShapes.xs))
                    Box(Modifier.width(SKELETON_SHORT).height(SKELETON_LINE).background(colors.surface3, NbShapes.xs))
                }
            }
        }
    }
}

private val SKELETON_LONG = 140.dp
private val SKELETON_SHORT = 80.dp
private val SKELETON_LINE = 10.dp

/**
 * A Glance feed row (`layouts.md` 5.3: night label + glyph + count): no per-metric glyphs, one merged node, a 48 dp
 * target that opens the glance Night detail.
 */
@Composable
fun GlanceNightRow(
    night: NightUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val label = text.nightLabel(night)
    val status = text.nightStatusLabel(night)
    Row(
        modifier
            .fillMaxWidth()
            .sizeIn(minHeight = NbDimens.targetMin)
            .background(colors.surface1, NbShapes.m)
            .nbClickable(onClick = onClick, shape = NbShapes.m)
            .clearAndSetSemantics { contentDescription = "$label: $status." }
            .padding(horizontal = NbSpace.s4, vertical = NbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = NbDimens.iconInline)
        Column(Modifier.weight(1f)) {
            Text(text.dateShort(night.wakeDate), style = NbTheme.typography.titleSmall, color = colors.textPrimary)
            Text(status, style = NbTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

/** The empty card of `H-EMPTY` (dotted ring) and the filter-empty card of `H-FILTER-EMPTY` (with "Show all nights"). */
@Composable
fun HistoryEmptyCard(
    title: String?,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = NbTheme.colors
    NbCard(modifier.fillMaxWidth()) {
        // The dotted ring (nights still to come) only on H-EMPTY; a filter with no match is not a learning state.
        title?.let {
            Box(
                Modifier.size(EMPTY_BADGE).background(colors.statusNeutralContainer, NbShapes.circle),
                contentAlignment = Alignment.Center,
            ) {
                NightGlyphIcon(NightGlyph.DottedRing, colors.tone(StatusTone.Neutral), background = colors.statusNeutralContainer)
            }
            Text(it, style = NbTheme.typography.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        Text(body, style = NbTheme.typography.body, color = colors.textSecondary)
        if (action != null) {
            NbTextButton(action, onClick = onAction)
        }
    }
}

private val EMPTY_BADGE = 48.dp
