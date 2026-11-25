package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * A History feed row (`screens.md` 4, 7.4): night label, night glyph + status label, and a wrapping row of small
 * per-metric glyphs (2..6). One merged TalkBack node ("{night label}: {status}. {k} moved: {metrics}."), role button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightRow(
    night: NightUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    forced: ForcedInteraction? = null,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val label = text.nightLabel(night)
    val status = text.nightStatusLabel(night)
    val moved = night.movedMetrics.map { text.name(it.metric) }
    val description = if (moved.isEmpty()) "$label: $status." else "$label: $status. ${text.list(moved)}."
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.textMax)
            .sizeIn(minHeight = NbDimens.rowMinHeight)
            .background(if (selected) colors.surface3 else colors.surface1, NbShapes.l)
            .nbClickable(onClick = onClick, shape = NbShapes.l, forced = forced)
            .semantics {
                contentDescription = description
                this.selected = selected
            }.padding(horizontal = NbSpace.s5, vertical = NbDimens.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), background = if (selected) colors.surface3 else colors.surface1)
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            Text(label, style = NbTheme.typography.titleSmall, color = colors.textPrimary)
            Text(status, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        }
        FlowRow(
            Modifier.widthIn(max = 72.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s1),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
        ) {
            night.metrics.filter { it.state.compared || it.state.moved }.forEach { metric ->
                MetricGlyphIcon(metric.state.glyph, colors.tone(metric.state.tone), size = 12.dp)
            }
        }
    }
}

/**
 * A calendar day cell (`screens.md` 4): day number and the night status glyph (shape, not only colour), today
 * outlined, the selected day on `surface.3` with a bold number. A 48 dp target ("{full date}: {status}").
 */
@Composable
fun CalendarCell(
    date: LocalDate,
    night: NightUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    today: Boolean = false,
    selected: Boolean = false,
    forced: ForcedInteraction? = null,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val status = night?.let { text.nightStatusLabel(it) } ?: text.string(R.string.history_status_nodata)
    val description = text.string(R.string.a11y_history_day, text.dateLong(date), status)
    val background = if (selected) colors.surface3 else colors.surface1
    Column(
        modifier
            .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbDimens.targetMin)
            .background(background, NbShapes.s)
            .then(if (today) Modifier.border(1.5.dp, colors.outline, NbShapes.s) else Modifier)
            .nbClickable(onClick = onClick, shape = NbShapes.s, enabled = night != null, forced = forced)
            .semantics {
                contentDescription = description
                this.selected = selected
            }.padding(vertical = NbSpace.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
    ) {
        Text(
            text.int(date.dayOfMonth),
            style = NbTheme.typography.numericAxis.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (night == null) colors.textTertiary else colors.textPrimary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (night != null) {
                NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = 16.dp, background = background)
            }
        }
    }
}

/**
 * The History month grid (`screens.md` 4): weekday header, 7 columns of [CalendarCell] (collection semantics with
 * row and column info), previous / next month buttons.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    nights: Map<LocalDate, NightUi>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    selected: LocalDate? = null,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val firstDay = WeekFields.of(text.locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + DAYS) % DAYS
    val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val rows = cells.chunked(DAYS)
    Column(modifier.widthIn(max = NbDimens.textMax), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbIconButton(NbIcons.Back, stringResource(R.string.a11y_prev_month), onPrevious, tint = colors.textPrimary)
            Text(
                text.month(month.atDay(1)),
                style = NbTheme.typography.titleSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            NbIconButton(NbIcons.Chevron, stringResource(R.string.a11y_next_month), onNext, tint = colors.textPrimary)
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            repeat(DAYS) { i ->
                val day = firstDay.plus(i.toLong())
                Text(
                    // Any week works for the letters; the first cell of the month row starts at [firstDay].
                    text.weekdayLetter(month.atDay(1).with(TemporalAdjusters.nextOrSame(day))),
                    style = NbTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(
            Modifier.semantics { collectionInfo = CollectionInfo(rows.size, DAYS) },
            verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
        ) {
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
                    repeat(DAYS) { c ->
                        val date = row.getOrNull(c)
                        Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = false)) {
                            if (date != null) {
                                CalendarCell(
                                    date = date,
                                    night = nights[date],
                                    onClick = { onSelect(date) },
                                    today = date == today,
                                    selected = date == selected,
                                    modifier =
                                        Modifier.fillMaxWidth().semantics {
                                            collectionItemInfo = CollectionItemInfo(r, 1, c, 1)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DAYS = 7

/**
 * The recent-nights strip of Home (`screens.md` 4): weekday letter and night glyph per night, titled "Last 7 nights"
 * with "See all". Nights are individual 48 dp targets only when they fit (7 x 48 dp); otherwise the whole strip is one
 * target that opens History (`a11y_history_strip`). [interactive] false renders the view-only glyph row (tabletop top
 * half).
 */
@Composable
fun HistoryStrip(
    nights: List<NightUi>,
    onNight: (NightUi) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    individualTargets: Boolean = true,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val stripDescription =
        text.string(R.string.a11y_history_strip, text.list(nights.map { "${text.dateShort(it.wakeDate)}: ${text.nightStatusLabel(it)}" }))
    NbCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (nights.size > DAYS) R.string.home_strip_title_14 else R.string.home_strip_title),
                style = NbTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (interactive) NbTextButton(stringResource(R.string.action_see_all), onClick = onSeeAll)
        }
        val perNight = interactive && individualTargets
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (interactive && !individualTargets) {
                        Modifier
                            .nbClickable(onClick = onSeeAll, shape = NbShapes.m)
                            .semantics { contentDescription = stripDescription }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            nights.forEach { night ->
                val description = text.string(R.string.a11y_history_day, text.dateLong(night.wakeDate), text.nightStatusLabel(night))
                Column(
                    Modifier
                        .weight(1f)
                        .sizeIn(minHeight = NbDimens.targetMin)
                        .then(
                            if (perNight) {
                                Modifier.nbClickable(onClick = { onNight(night) }, shape = NbShapes.s).semantics {
                                    contentDescription =
                                        description
                                }
                            } else {
                                Modifier.clearAndSetSemantics {}
                            },
                        ).padding(vertical = NbSpace.s2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                ) {
                    Text(text.weekdayLetter(night.wakeDate), style = NbTheme.typography.labelSmall, color = colors.textTertiary)
                    NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = 20.dp)
                }
            }
        }
    }
}

/** A settings section header (`type.labelSmall`, `text.tertiary`), 16 dp above and 8 dp below. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        style = NbTheme.typography.labelSmall,
        color = NbTheme.colors.textTertiary,
        modifier =
            modifier
                .padding(start = NbSpace.s5, top = NbSpace.s5, bottom = NbSpace.s3)
                .semantics { heading() },
    )
}

/** A group of settings rows: one solid card with 16 dp side inset and hairline separators inset to the text column. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().widthIn(max = NbDimens.textMax)) {
        SectionHeader(title)
        Column(Modifier.fillMaxWidth().solidCard(NbShapes.l), content = content)
    }
}

/** Trailing element of a [SettingsRow]. */
sealed interface SettingsTrailing {
    /** A chevron: opens a sub-screen. */
    data object Chevron : SettingsTrailing

    /** A value text (e.g. the source name). */
    data class Value(
        /** The value. */
        val text: String,
    ) : SettingsTrailing

    /** A switch; the whole row toggles it. */
    data class Toggle(
        /** Current state. */
        val checked: Boolean,
    ) : SettingsTrailing

    /** A radio button; the whole row selects it. */
    data class Radio(
        /** Current state. */
        val selected: Boolean,
    ) : SettingsTrailing

    /** Nothing (read-only row). */
    data object None : SettingsTrailing
}

/**
 * A settings row (`screens.md` 4): at least 56 dp, title + optional summary + trailing switch / radio / value /
 * chevron. The whole row is the touch target and one TalkBack node with the matching role and state; a disabled row
 * reads its reason (the summary).
 */
@Suppress("CyclomaticComplexMethod") // Role, state and trailing element follow one exhaustive SettingsTrailing switch.
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: SettingsTrailing = SettingsTrailing.Chevron,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    forced: ForcedInteraction? = null,
) {
    val colors = NbTheme.colors
    val role =
        when (trailing) {
            is SettingsTrailing.Toggle -> Role.Switch
            is SettingsTrailing.Radio -> Role.RadioButton
            else -> Role.Button
        }
    Column(modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                Modifier
                    .padding(start = NbSpace.s5)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.separator),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.rowMinHeight)
                .nbClickable(onClick = onClick, shape = NbShapes.l, role = role, enabled = enabled, forced = forced, pressScale = false)
                .semantics(mergeDescendants = true) {
                    when (trailing) {
                        is SettingsTrailing.Toggle -> toggleableState = ToggleableState(trailing.checked)
                        is SettingsTrailing.Radio -> selected = trailing.selected
                        is SettingsTrailing.Value -> stateDescription = trailing.text
                        else -> Unit
                    }
                }.padding(horizontal = NbSpace.s5, vertical = NbDimens.rowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        ) {
            if (trailing is SettingsTrailing.Radio) {
                RadioButton(selected = trailing.selected, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics {})
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
                Text(title, style = NbTheme.typography.titleSmall, color = if (enabled) colors.textPrimary else colors.textTertiary)
                summary?.let { Text(it, style = NbTheme.typography.bodySmall, color = colors.textSecondary) }
            }
            when (trailing) {
                SettingsTrailing.Chevron -> {
                    Icon(
                        NbIcons.Chevron,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(NbDimens.iconInline),
                    )
                }

                is SettingsTrailing.Value -> {
                    Text(
                        trailing.text,
                        style = NbTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }

                is SettingsTrailing.Toggle -> {
                    Switch(
                        checked = trailing.checked,
                        onCheckedChange = null,
                        enabled = enabled,
                        modifier = Modifier.clearAndSetSemantics {},
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor = colors.primary,
                                checkedThumbColor = colors.onPrimary,
                                uncheckedTrackColor = colors.surface3,
                                uncheckedBorderColor = colors.outline,
                                uncheckedThumbColor = colors.outline,
                            ),
                    )
                }

                is SettingsTrailing.Radio, SettingsTrailing.None -> {}
            }
        }
    }
}
