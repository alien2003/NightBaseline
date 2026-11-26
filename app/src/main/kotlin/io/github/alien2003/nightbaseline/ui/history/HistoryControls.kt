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
import androidx.compose.foundation.layout.offset
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
import io.github.alien2003.nightbaseline.ui.components.GroupCard
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbChipTab
import io.github.alien2003.nightbaseline.ui.components.NbHairline
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbTabRow
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.SectionHeader
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls

/**
 * The "List / Calendar" switch (`screens.md` 7.4, `coloros-conformance.md` L6): a ColorOS tab row ([NbTabRow]) under
 * the top bar, centred, with a short underline under the selected label (weight, colour and underline; no tonal
 * capsule). Each tab is a 48 dp `Role.Tab` target.
 */
@Composable
fun HistoryViewToggle(
    selected: HistoryView,
    onSelect: (HistoryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels =
        HistoryView.entries.map {
            stringResource(
                if (it ==
                    HistoryView.List
                ) {
                    R.string.history_view_list
                } else {
                    R.string.history_view_calendar
                },
            )
        }
    NbTabRow(labels, HistoryView.entries.indexOf(selected), onSelect = { onSelect(HistoryView.entries[it]) }, modifier = modifier)
}

/**
 * Filter chips "All / Moved / Learning and gaps" (`screens.md` 7.4, `coloros-conformance.md` L7): single choice
 * ColorOS chip tabs ([NbChipTab]): 36 dp pills in 48 dp targets, no outline and no check; the selected chip has a
 * brighter fill, a rim and a bold label. Chips wrap at large font.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryFilterChips(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s2),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
    ) {
        HistoryFilter.entries.forEach { filter ->
            val label =
                stringResource(
                    when (filter) {
                        HistoryFilter.All -> R.string.history_filter_all
                        HistoryFilter.Moved -> R.string.history_filter_moved
                        HistoryFilter.Gaps -> R.string.history_filter_gaps
                    },
                )
            NbChipTab(label, selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

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
            .solidCard(NbShapes.l)
            .padding(start = NbSpace.s5, top = NbSpace.s4, bottom = NbSpace.s4, end = NbSpace.s3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        NightGlyphIcon(glyph, NbTheme.controls.accentText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            Text(stringResource(message), style = NbTheme.typography.bodySmall, color = colors.textPrimary)
            NbTextButton(stringResource(action), onClick = onAction, modifier = Modifier.offset(x = -NbSpace.s4))
        }
    }
}

/**
 * A sticky month header of the feed ("November 2026"), styled as a ColorOS section header outside the month's card
 * (`coloros-conformance.md` T5, L11): 12/16/500 tertiary, solid window background behind it, heading semantics.
 */
@Composable
fun HistoryMonthHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().background(NbTheme.colors.background)) {
        SectionHeader(title)
    }
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
            style = NbControlType.itemTitle,
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

/** `H-LOADING`: static skeleton rows in one grouped card (no shimmer: idle screens never animate), one node. */
@Composable
fun HistorySkeleton(
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    val description = stringResource(R.string.state_loading_body)
    GroupCard(modifier.clearAndSetSemantics { contentDescription = description }) {
        repeat(rows) { i ->
            if (i > 0) NbHairline(start = NbSpace.s5 * 2 + NbDimens.icon)
            Row(
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = NbDimens.targetMin + NbSpace.s4)
                    .padding(horizontal = NbSpace.s5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s5),
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
            .background(colors.surface1, NbShapes.l)
            .nbClickable(onClick = onClick, shape = NbShapes.l)
            .clearAndSetSemantics { contentDescription = "$label: $status." }
            .padding(horizontal = NbSpace.s4, vertical = NbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = NbDimens.iconInline)
        Column(Modifier.weight(1f)) {
            Text(text.dateShort(night.wakeDate), style = NbControlType.itemTitle, color = colors.textPrimary)
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
            NightGlyphIcon(NightGlyph.DottedRing, colors.tone(StatusTone.Neutral), size = EMPTY_GLYPH)
            Text(it, style = NbTheme.typography.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        Text(body, style = NbTheme.typography.body, color = colors.textSecondary)
        if (action != null) {
            NbTextButton(action, onClick = onAction)
        }
    }
}

private val EMPTY_GLYPH = 28.dp
