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
import io.github.alien2003.nightbaseline.ui.components.NbHairline
import io.github.alien2003.nightbaseline.ui.components.NbIcon
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbTabRow
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.SectionHeader
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.cardTag
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.interaction.nbClickable
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
 * The List / Calendar switch (`screens.md` 7.4, review round 4): a solid round top-bar action ([ShellRoundButton]) like
 * the actions of ColorOS system apps, so History has a single tab level (the filters). The glyph shows the view it
 * switches to (calendar while the list is shown, list while the calendar is shown) and the TalkBack label says so.
 */
@Composable
fun HistoryViewButton(
    selected: HistoryView,
    onSelect: (HistoryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toCalendar = selected == HistoryView.List
    ShellRoundButton(
        icon = if (toCalendar) NbIcons.History else NbIcons.ListView,
        contentDescription = stringResource(if (toCalendar) R.string.history_show_calendar else R.string.history_show_list),
        onClick = { onSelect(if (toCalendar) HistoryView.Calendar else HistoryView.List) },
        modifier = modifier,
    )
}

/**
 * The filters "All / Moved / Learning and gaps" (`screens.md` 7.4, `coloros-conformance.md` L6): the one ColorOS tab
 * row of History ([NbTabRow]), centred under the top bar, with the short underline under the selected label. Each tab
 * is a 48 dp `Role.Tab` target; labels wrap at large font.
 */
@Composable
fun HistoryFilterTabs(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels =
        HistoryFilter.entries.map { filter ->
            stringResource(
                when (filter) {
                    HistoryFilter.All -> R.string.history_filter_all
                    HistoryFilter.Moved -> R.string.history_filter_moved
                    HistoryFilter.Gaps -> R.string.history_filter_gaps
                },
            )
        }
    NbTabRow(labels, HistoryFilter.entries.indexOf(selected), onSelect = { onSelect(HistoryFilter.entries[it]) }, modifier = modifier)
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
            .widthIn(max = NbDimens.textMax)
            .fillMaxWidth()
            .cardTag()
            .solidCard(NbShapes.l)
            .padding(start = NbSpace.s5, top = NbSpace.s4, bottom = NbSpace.s4, end = NbSpace.s3)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        NightGlyphIcon(glyph, NbTheme.controls.accentText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            NbText(stringResource(message), style = NbTheme.typography.bodySmall, color = colors.textPrimary)
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
        NbText(
            stringResource(R.string.history_legend_title),
            style = NbControlType.itemTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            // States that share a shape (no data, too few readings) share one entry, so no shape appears twice.
            entries.groupBy { it.first.glyph }.values.forEach { group ->
                val state = group.first().first
                val label = group.joinToString(" / ") { it.second }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                    NightGlyphIcon(state.glyph, colors.tone(state.tone), size = LEGEND_GLYPH)
                    NbText(label, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
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
            NbText(text.dateShort(night.wakeDate), style = NbControlType.itemTitle, color = colors.textPrimary)
            NbText(status, style = NbTheme.typography.caption, color = colors.textSecondary)
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
            NbText(it, style = NbTheme.typography.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        }
        NbText(body, style = NbTheme.typography.body, color = colors.textSecondary)
        if (action != null) {
            NbTextButton(action, onClick = onAction)
        }
    }
}

private val EMPTY_GLYPH = 28.dp
