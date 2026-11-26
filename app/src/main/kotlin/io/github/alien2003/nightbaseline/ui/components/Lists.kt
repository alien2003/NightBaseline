package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.CountComposition
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/** Where a row sits in a grouped card: its corner shape and whether a hairline separates it from the row above. */
enum class GroupPosition {
    /** The only row: a card of its own. */
    Single,

    /** First row of a card: top corners rounded. */
    First,

    /** A middle row: square, hairline above. */
    Middle,

    /** Last row: bottom corners rounded, hairline above. */
    Last,
    ;

    /** The row's shape inside its 20 dp card. */
    val shape: Shape
        get() =
            when (this) {
                Single -> NbShapes.l
                First -> RoundedCornerShape(topStart = NbShapes.lRadius, topEnd = NbShapes.lRadius)
                Middle -> RectangleShape
                Last -> RoundedCornerShape(bottomStart = NbShapes.lRadius, bottomEnd = NbShapes.lRadius)
            }

    /** A hairline is drawn above this row. */
    val divider: Boolean get() = this == Middle || this == Last

    /** Constants. */
    companion object {
        /** Position of item [index] of [count] items in one card. */
        fun of(
            index: Int,
            count: Int,
        ): GroupPosition =
            when {
                count <= 1 -> Single
                index == 0 -> First
                index == count - 1 -> Last
                else -> Middle
            }
    }
}

/**
 * A History feed row (`screens.md` 4, `coloros-conformance.md` L11): one row of a month's grouped 20 dp card (see
 * [position]): a 24 dp night glyph, night label (16/500) and status summary, and, on nights where something moved,
 * a wrapping set of small per-metric glyphs (2..6) at the end (moved in amber, the rest grey); >= 48 dp, 10 dp
 * vertical padding; hairline above inset 56 dp / 16 dp; the selected row on a neutral fill (never a tonal colour).
 * Rows in a grouped card light up on press but do not shrink. One merged TalkBack node ("{night label}: {status}.
 * {k} moved: {metrics}."), role button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightRow(
    night: NightUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    forced: ForcedInteraction? = null,
    maxWidth: Dp = NbDimens.textMax,
    position: GroupPosition = GroupPosition.Single,
) {
    CountComposition("NightRow")
    val text = rememberUiText()
    val colors = NbTheme.colors
    val controls = NbTheme.controls
    val label = text.nightLabel(night)
    val status = text.nightStatusLabel(night)
    val moved = night.movedMetrics.map { text.name(it.metric) }
    val description = if (moved.isEmpty()) "$label: $status." else "$label: $status. ${text.list(moved)}."
    val shape = position.shape
    Column(
        Modifier
            .widthIn(max = maxWidth)
            .then(modifier)
            .fillMaxWidth()
            .testTag(ROW_TAG)
            .background(colors.surface1, shape)
            .clip(shape),
    ) {
        if (position.divider) NbHairline(start = NbControlDimens.rowPaddingHorizontal * 2 + NbDimens.icon)
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbControlDimens.rowMinHeight)
                .then(if (selected) Modifier.background(controls.selectedRow) else Modifier)
                .nbClickable(onClick = onClick, shape = RectangleShape, forced = forced, pressScale = position == GroupPosition.Single)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }.padding(horizontal = NbControlDimens.rowPaddingHorizontal, vertical = NbControlDimens.rowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbControlDimens.rowPaddingHorizontal),
        ) {
            NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), background = colors.surface1)
            Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NbControlDimens.rowTitleGap)) {
                NbText(label, style = NbControlType.itemTitle, color = controls.title)
                NbText(status, style = NbControlType.summary, color = controls.summary)
            }
            // Per-metric shapes only on nights where something moved: which ones moved (amber triangles) among the
            // compared ones (calm grey dots). "All usual" rows stay clean; the summary already says it.
            if (night.movedMetrics.isNotEmpty()) {
                FlowRow(
                    Modifier.widthIn(max = 72.dp).clearAndSetSemantics {},
                    maxItemsInEachRow = METRIC_GLYPHS_PER_ROW,
                    horizontalArrangement = Arrangement.spacedBy(NbSpace.s1),
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
                ) {
                    night.metrics.filter { it.state.compared || it.state.moved }.forEach { metric ->
                        val tone = if (metric.state.moved) metric.state.tone else StatusTone.Neutral
                        MetricGlyphIcon(metric.state.glyph, colors.tone(tone), size = 12.dp)
                    }
                }
            }
        }
    }
}

/**
 * A calendar day cell (`screens.md` 4, `coloros-conformance.md` L13): transparent on the calendar card, >= 48 dp; the
 * day number 16/400 in a 32 dp circle, the night status glyph below it (shape, not only colour). Today: number in the
 * accent at 500. Selected: an accent disc behind the number with a white 500 number. Days without a night are not
 * targets. One node ("{full date}: {status}").
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
    val controls = NbTheme.controls
    val status = night?.let { text.nightStatusLabel(it) } ?: text.string(R.string.history_status_nodata)
    val description = text.string(R.string.a11y_history_day, text.dateLong(date), status)
    val numberColor =
        when {
            selected -> controls.onFill
            night == null -> colors.textTertiary
            today -> controls.accentText
            else -> controls.title
        }
    Column(
        modifier
            .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbDimens.targetMin)
            .nbClickable(onClick = onClick, shape = NbShapes.m, enabled = night != null, forced = forced, pressScale = false)
            .semantics {
                contentDescription = description
                this.selected = selected
            }.padding(vertical = NbSpace.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
    ) {
        Box(
            Modifier
                .sizeIn(minWidth = DAY_DISC, minHeight = DAY_DISC)
                .then(if (selected) Modifier.background(controls.fillTop, NbShapes.circle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            NbText(
                text.int(date.dayOfMonth),
                style =
                    NbTheme.typography.numericAxis.copy(
                        fontSize = NbTheme.typography.body.fontSize,
                        lineHeight = DAY_LINE,
                        fontWeight = if (selected || today) FontWeight.Medium else FontWeight.Normal,
                    ),
                color = numberColor,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Box(Modifier.size(DAY_GLYPH), contentAlignment = Alignment.Center) {
            if (night != null) {
                NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = DAY_GLYPH, background = colors.surface1)
            }
        }
    }
}

private val DAY_DISC = 32.dp
private val DAY_GLYPH = 14.dp
private val DAY_LINE = 20.sp

/**
 * A compact month grid (`screens.md` 4) for the component catalog: month title with round previous / next buttons,
 * weekday letters and 7 columns of [CalendarCell] on one card (collection semantics with row and column info). The
 * History screen uses `HistoryCalendar`, which adds keyboard, swipe and the narrow transposed form.
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
    val controls = NbTheme.controls
    val firstDay = WeekFields.of(text.locale).firstDayOfWeek
    // Built once per month: equal dates keep their instances, so the cells and their click lambdas stay skippable.
    val rows =
        remember(month, firstDay) {
            val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + DAYS) % DAYS
            (List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }).chunked(DAYS)
        }
    Column(
        modifier
            .widthIn(max = NbDimens.textMax)
            .cardTag()
            .solidCard(NbShapes.l)
            .padding(NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShellRoundButton(NbIcons.Back, stringResource(R.string.a11y_prev_month), onPrevious)
            NbText(
                text.month(month.atDay(1)),
                style = NbControlType.itemTitle,
                color = controls.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            ShellRoundButton(NbIcons.Chevron, stringResource(R.string.a11y_next_month), onNext)
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            repeat(DAYS) { i ->
                val day = firstDay.plus(i.toLong())
                NbText(
                    // Any week works for the letters; the first cell of the month row starts at [firstDay].
                    text.weekdayLetter(month.atDay(1).with(TemporalAdjusters.nextOrSame(day))),
                    style = NbTheme.typography.caption,
                    color = controls.summary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(Modifier.semantics { collectionInfo = CollectionInfo(rows.size, DAYS) }) {
            rows.forEachIndexed { r, row ->
                Row {
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

/** 2..6 metric glyphs in a NightRow wrap as even rows of 3 (never 5 + 1). */
private const val METRIC_GLYPHS_PER_ROW = 3

/**
 * The recent-nights strip of Home and Learning (`screens.md` 4, `coloros-conformance.md` L12): one solid card like the
 * other Home cards (review round 4). Inside it a header row with the title ("Last 7 nights") in the card title style
 * and "See all" as a text action at its end (48 dp target), then one column per night with the weekday label (14 sp
 * secondary) and the 28 dp night glyph. Nights are individual 48 dp targets only when they fit (7 x 48 dp); otherwise
 * the row of nights is one target that opens History (`a11y_history_strip`). [interactive] false renders the
 * view-only card without "See all" (tabletop top half).
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
    val controls = NbTheme.controls
    val stripDescription =
        text.string(R.string.a11y_history_strip, text.list(nights.map { "${text.dateShort(it.wakeDate)}: ${text.nightStatusLabel(it)}" }))
    Column(
        modifier
            .fillMaxWidth()
            .cardTag()
            .solidCard(NbShapes.l)
            .clip(NbShapes.l),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.targetMin)
                .padding(start = NbDimens.cardPadding, end = if (interactive) NbSpace.s1 else NbDimens.cardPadding, top = NbSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NbText(
                when {
                    nights.size > DAYS -> stringResource(R.string.home_strip_title_14)

                    nights.size == DAYS -> stringResource(R.string.home_strip_title)

                    // Learning: fewer nights are stored than the strip holds.
                    else -> text.plural(R.plurals.home_strip_title_n, nights.size, text.int(nights.size))
                },
                style = NbTheme.dataType.itemTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (interactive) {
                NbTextButton(stringResource(R.string.action_see_all), onClick = onSeeAll, color = colors.data.primaryText)
            }
        }
        val perNight = interactive && individualTargets
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (interactive && !individualTargets) {
                        Modifier
                            .nbClickable(onClick = onSeeAll, shape = NbShapes.l, pressScale = false)
                            .semantics { contentDescription = stripDescription }
                    } else {
                        Modifier
                    },
                ).padding(start = NbSpace.s3, end = NbSpace.s3, bottom = NbSpace.s3),
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
                                Modifier.nbClickable(onClick = { onNight(night) }, shape = NbShapes.m, pressScale = false).semantics {
                                    contentDescription = description
                                }
                            } else {
                                Modifier.clearAndSetSemantics {}
                            },
                        ).padding(vertical = NbSpace.s2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                ) {
                    NbText(text.weekdayLetter(night.wakeDate), style = NbTheme.typography.bodySmall, color = controls.summary)
                    NightGlyphIcon(night.state.glyph, colors.tone(night.state.tone), size = STRIP_GLYPH)
                }
            }
        }
    }
}

/** Night glyph of the strip: as large as the summary card's glyph, so the strip reads at the size of its neighbours. */
private val STRIP_GLYPH = 28.dp

/**
 * A section header outside a grouped card (`coloros-conformance.md` T5, S2): 12/16/500 `text.tertiary`, 24 dp above
 * (16 dp group gap + 8), 8 dp below, text 16 dp in from the card edge (32 dp from the screen edge).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    NbText(
        title,
        style = NbControlType.sectionHeader,
        color = NbTheme.controls.sectionHeader,
        modifier =
            modifier
                .padding(
                    start = NbControlDimens.sectionHeaderStart,
                    end = NbControlDimens.sectionHeaderStart,
                    top = NbControlDimens.sectionHeaderTop,
                    bottom = NbControlDimens.sectionHeaderBottom,
                ).semantics { heading() },
    )
}

/**
 * A footer under a grouped card (COUI footer): 12 sp `text.tertiary`, 16 dp in from the card edge, 8 dp below the
 * card. Read-only explanations that belong to the group above.
 */
@Composable
fun SectionFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    NbText(
        text,
        style = NbTheme.typography.caption,
        color = NbTheme.controls.sectionHeader,
        modifier =
            modifier.padding(
                start = NbControlDimens.sectionHeaderStart,
                end = NbControlDimens.sectionHeaderStart,
                top = NbSpace.s3,
            ),
    )
}

/**
 * A grouped list card (`coloros-conformance.md` S3): one solid 20 dp card, no vertical padding (the rows carry it),
 * clipped so a pressed first or last row follows the corners. Rows separate themselves with inset hairlines.
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = NbDimens.textMax)
            .then(modifier)
            .fillMaxWidth()
            .cardTag()
            .solidCard(NbShapes.l)
            .clip(NbShapes.l),
        content = content,
    )
}

/** A group of settings rows: the section header outside, then one [GroupCard] whose rows are split by inset hairlines. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.widthIn(max = NbDimens.textMax).then(modifier).fillMaxWidth()) {
        SectionHeader(title)
        GroupCard(content = content)
    }
}

/** Trailing element of a [SettingsRow]. */
sealed interface SettingsTrailing {
    /** A chevron: opens a sub-screen. */
    data object Chevron : SettingsTrailing

    /** A value text followed by a chevron (e.g. the source name; the row opens a picker). */
    data class Value(
        /** The value. */
        val text: String,
    ) : SettingsTrailing

    /** A switch; the whole row toggles it. */
    data class Toggle(
        /** Current state. */
        val checked: Boolean,
    ) : SettingsTrailing

    /** A radio button (trailing, as in ColorOS single-choice lists); the whole row selects it. */
    data class Radio(
        /** Current state. */
        val selected: Boolean,
    ) : SettingsTrailing

    /** Nothing (read-only row). */
    data object None : SettingsTrailing
}

/**
 * A settings row (`screens.md` 4, `coloros-conformance.md` S1, L1-L3): >= 48 dp, padding 16 / 10, title 16/500 +
 * optional summary 14/400 (2 dp apart) + trailing switch / radio / value + chevron / chevron, 12 dp from the text.
 * The whole row is the touch target and one TalkBack node with the matching role and state; it lights up on press but
 * does not shrink (it sits in a grouped card). A disabled row reads its reason (the summary). [showDivider] draws the
 * 1 px hairline inset 16 dp at both ends above the row. Flipping a switch row plays the toggle haptic.
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
    val controls = NbTheme.controls
    val haptic = rememberToggleHaptic()
    val role =
        when (trailing) {
            is SettingsTrailing.Toggle -> Role.Switch
            is SettingsTrailing.Radio -> Role.RadioButton
            else -> Role.Button
        }
    val click: () -> Unit =
        if (trailing is SettingsTrailing.Toggle) {
            {
                haptic(!trailing.checked)
                onClick()
            }
        } else {
            onClick
        }
    Column(modifier.fillMaxWidth()) {
        if (showDivider) NbHairline()
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbControlDimens.rowMinHeight)
                .nbClickable(onClick = click, shape = RectangleShape, role = role, enabled = enabled, forced = forced, pressScale = false)
                .semantics(mergeDescendants = true) {
                    when (trailing) {
                        is SettingsTrailing.Toggle -> toggleableState = ToggleableState(trailing.checked)
                        is SettingsTrailing.Radio -> selected = trailing.selected
                        is SettingsTrailing.Value -> stateDescription = trailing.text
                        else -> Unit
                    }
                }.padding(horizontal = NbControlDimens.rowPaddingHorizontal, vertical = NbControlDimens.rowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbControlDimens.rowTrailingGap),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbControlDimens.rowTitleGap)) {
                NbText(title, style = NbControlType.itemTitle, color = if (enabled) controls.title else colors.textTertiary)
                summary?.let { NbText(it, style = NbControlType.summary, color = controls.summary) }
            }
            when (trailing) {
                SettingsTrailing.Chevron -> {
                    NbChevron()
                }

                is SettingsTrailing.Value -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                        NbText(
                            trailing.text,
                            style = NbTheme.typography.bodySmall,
                            color = controls.summary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = VALUE_MAX).clearAndSetSemantics {},
                        )
                        NbChevron()
                    }
                }

                is SettingsTrailing.Toggle -> {
                    NbSwitch(checked = trailing.checked, enabled = enabled)
                }

                is SettingsTrailing.Radio -> {
                    NbRadio(selected = trailing.selected, enabled = enabled)
                }

                SettingsTrailing.None -> {}
            }
        }
    }
}

/** A trailing value never takes more than this; longer values wrap. */
private val VALUE_MAX = 160.dp
