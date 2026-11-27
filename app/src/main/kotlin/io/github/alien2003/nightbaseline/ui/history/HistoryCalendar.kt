package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.CalendarCell
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.cardTag
import io.github.alien2003.nightbaseline.ui.interaction.NbKeyIntent
import io.github.alien2003.nightbaseline.ui.interaction.NbSwitcher
import io.github.alien2003.nightbaseline.ui.interaction.nbHorizontalScroll
import io.github.alien2003.nightbaseline.ui.interaction.nbKeyIntents
import io.github.alien2003.nightbaseline.ui.interaction.nbStepSwipe
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.abs

/**
 * The History month grid (`screens.md` 4 `MonthCalendar`, 7.4; `layouts.md` 10.1), built from [CalendarCell]:
 * - month title (heading) with previous / next month buttons (`a11y_prev_month` / `a11y_next_month`), shown only
 *   where a month with nights exists, plus a horizontal swipe;
 * - every day a >= 48 x 48 dp cell with the night glyph (shape, never colour alone), today outlined, the selection on
 *   `surface.3` with a bold number; days without a night are not targets;
 * - collection semantics with row / column info for TalkBack;
 * - keyboard: arrow keys move between days in 2D (crossing into the neighbouring month), Page Up / Page Down change the
 *   month, Home / End jump to the first / last night of the month; Enter opens the focused day.
 *
 * When the pane is narrower than 7 cells of 48 dp (a 346 dp book half), the grid is transposed: weekdays become rows
 * and weeks columns (at most 6), so every cell keeps its 48 dp target instead of shrinking. Arrow keys follow the
 * layout in both forms.
 */
@Composable
fun HistoryCalendar(
    month: YearMonth,
    nights: Map<LocalDate, NightUi>,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    firstMonth: YearMonth = month,
) {
    val text = rememberUiText()
    val firstDay = WeekFields.of(text.locale).firstDayOfWeek
    val lastMonth = YearMonth.from(today)
    val canPrevious = month > firstMonth
    val canNext = month < lastMonth
    val requesters = remember(month) { mutableMapOf<LocalDate, FocusRequester>() }
    var focused by remember { mutableStateOf<LocalDate?>(null) }
    var pendingFocus by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(month, pendingFocus) {
        val target = pendingFocus ?: return@LaunchedEffect
        requesters[target]?.let {
            it.requestFocus()
            pendingFocus = null
        }
    }
    val move: (LocalDate?) -> Unit = { target ->
        if (target != null) {
            if (YearMonth.from(target) == month) requesters[target]?.requestFocus() else onMonth(YearMonth.from(target))
            pendingFocus = target
        }
    }
    BoxWithConstraints(
        modifier
            .widthIn(max = NbDimens.textMax)
            .fillMaxWidth()
            .cardTag()
            .solidCard(NbShapes.l)
            .padding(CARD_PADDING),
    ) {
        val transposed = maxWidth < NbDimens.targetMin * DAYS + CELL_GAP * (DAYS - 1)
        // Narrower still (a book half next to the rail): the weeks scroll sideways, cells keep 48 dp.
        val fits = maxWidth >= WEEKDAY_COLUMN + (NbDimens.targetMin + CELL_GAP) * MAX_WEEKS
        Column(
            Modifier
                .fillMaxWidth()
                .nbStepSwipe(
                    onPrevious = { onMonth(month.minusMonths(1)) }.takeIf { canPrevious },
                    onNext = { onMonth(month.plusMonths(1)) }.takeIf { canNext },
                ).nbKeyIntents { intent ->
                    val from = focused ?: return@nbKeyIntents false
                    val target = keyTarget(intent, from, nights, today, transposed) ?: return@nbKeyIntents intent.isCalendarKey()
                    move(target)
                    true
                },
            verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
        ) {
            MonthTitle(
                text.month(month.atDay(1)),
                onPrevious = { onMonth(month.minusMonths(1)) }.takeIf { canPrevious },
                onNext = { onMonth(month.plusMonths(1)) }.takeIf { canNext },
            )
            // Months are a sequence: a month change (buttons, swipe, keys) is the Pager kind (ADR 0011).
            NbSwitcher(month, key = { it }, modifier = Modifier.fillMaxWidth(), id = "month", order = naturalOrder()) { shownMonth ->
                // The grid's rows as the month card lays them out (the header row, then one row per week).
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                    val weeks = weeksOf(shownMonth, firstDay)
                    val cell: @Composable (LocalDate?, Int, Int, Modifier) -> Unit = { date, row, column, cellModifier ->
                        DaySlot(date, today, cellModifier) { day ->
                            val requester = requesters.getOrPut(day) { FocusRequester() }
                            CalendarCell(
                                date = day,
                                night = nights[day],
                                onClick = { onDay(day) },
                                today = day == today,
                                selected = day == selected,
                                modifier =
                                    cellModifier
                                        .onFocusChanged { if (it.isFocused) focused = day }
                                        .focusRequester(requester)
                                        .semantics { collectionItemInfo = CollectionItemInfo(row, 1, column, 1) },
                            )
                        }
                    }
                    if (transposed) {
                        TransposedGrid(weeks, firstDay, cell, scrolls = !fits)
                    } else {
                        WeekGrid(weeks, firstDay, cell)
                    }
                }
            }
        }
    }
}

/** Weeks of [month] as 7 slots each (null before the first and after the last day), starting at [firstDay]. */
private fun weeksOf(
    month: YearMonth,
    firstDay: DayOfWeek,
): List<List<LocalDate?>> {
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + DAYS) % DAYS
    val days = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return days.chunked(DAYS).map { it + List(DAYS - it.size) { null } }
}

@Composable
private fun MonthTitle(
    title: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val colors = NbTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onPrevious != null) {
            ShellRoundButton(NbIcons.Back, stringResource(R.string.a11y_prev_month), onPrevious)
        } else {
            Spacer(Modifier.size(NbDimens.targetMin))
        }
        NbText(
            title,
            style = NbControlType.itemTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onNext != null) {
            ShellRoundButton(NbIcons.Chevron, stringResource(R.string.a11y_next_month), onNext)
        } else {
            Spacer(Modifier.size(NbDimens.targetMin))
        }
    }
}

/** The usual form: a weekday header row, then one row per week. */
@Composable
private fun WeekGrid(
    weeks: List<List<LocalDate?>>,
    firstDay: DayOfWeek,
    cell: @Composable (LocalDate?, Int, Int, Modifier) -> Unit,
) {
    val text = rememberUiText()
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        repeat(DAYS) { i -> WeekdayLetter(text.weekdayLetter(anyDate(firstDay.plus(i.toLong()))), Modifier.weight(1f)) }
    }
    Column(
        Modifier.semantics { collectionInfo = CollectionInfo(weeks.size, DAYS) },
        verticalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        weeks.forEachIndexed { r, week ->
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                week.forEachIndexed { c, date -> cell(date, r, c, Modifier.weight(1f)) }
            }
        }
    }
}

/** The narrow form: one row per weekday (letter first), one column per week. */
@Composable
private fun TransposedGrid(
    weeks: List<List<LocalDate?>>,
    firstDay: DayOfWeek,
    cell: @Composable (LocalDate?, Int, Int, Modifier) -> Unit,
    scrolls: Boolean = false,
) {
    val text = rememberUiText()
    Column(
        Modifier
            .then(if (scrolls) Modifier.nbHorizontalScroll(rememberNbScrollState(), id = "calendar-1") else Modifier)
            .semantics { collectionInfo = CollectionInfo(DAYS, weeks.size) },
        verticalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        repeat(DAYS) { d ->
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP), verticalAlignment = Alignment.CenterVertically) {
                WeekdayLetter(
                    text.weekdayLetter(anyDate(firstDay.plus(d.toLong()))),
                    Modifier.width(WEEKDAY_COLUMN).clearAndSetSemantics {},
                )
                weeks.forEachIndexed { w, week ->
                    cell(week[d], d, w, if (scrolls) Modifier.width(NbDimens.targetMin) else Modifier.weight(1f))
                }
            }
        }
    }
}

/** An empty slot before / after the month, a day still to come, or [content] for a day up to today. */
@Composable
private fun DaySlot(
    date: LocalDate?,
    today: LocalDate,
    modifier: Modifier,
    content: @Composable (LocalDate) -> Unit,
) {
    val text = rememberUiText()
    when {
        date == null -> Spacer(modifier)

        // Days still to come are not nights yet: a quiet number, no cell surface, not a target.
        date.isAfter(today) -> FutureDay(text.int(date.dayOfMonth), modifier)

        else -> content(date)
    }
}

@Composable
private fun FutureDay(
    day: String,
    modifier: Modifier,
) {
    Box(modifier.heightIn(min = NbDimens.targetMin).clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
        // Same 32 dp number box as a CalendarCell, so future days line up with past ones.
        Box(Modifier.padding(top = NbSpace.s1).heightIn(min = FUTURE_DISC), contentAlignment = Alignment.Center) {
            NbText(
                day,
                style = NbTheme.typography.numericAxis.copy(fontSize = NbTheme.typography.body.fontSize),
                color = NbTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun WeekdayLetter(
    letter: String,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        NbText(letter, style = NbTheme.typography.caption, color = NbTheme.controls.summary, textAlign = TextAlign.Center)
    }
}

/** A date on [day] (any week works for the weekday letter). */
private fun anyDate(day: DayOfWeek): LocalDate = REFERENCE_MONDAY.plusDays((day.value - 1).toLong())

/** Day step of an arrow key in the usual or the transposed grid; null for other keys. */
private fun keyStep(
    move: NbKeyIntent.Move,
    transposed: Boolean,
): Int {
    // dx is already in the reading direction (the framework decodes RTL once).
    val across = if (transposed) DAYS else 1
    val down = if (transposed) 1 else DAYS
    return move.dx * across + move.dy * down
}

/** The day an arrow, Page Up / Down, Home or End key moves to from [from]; null when there is none or not a key of ours. */
private fun keyTarget(
    intent: NbKeyIntent,
    from: LocalDate,
    nights: Map<LocalDate, NightUi>,
    today: LocalDate,
    transposed: Boolean,
): LocalDate? =
    when (intent) {
        is NbKeyIntent.Move -> {
            keyStep(intent, transposed).toLong().let { nearestNight(from.plusDays(it), it, nights, today) }
        }

        is NbKeyIntent.Page -> {
            nightInMonth(YearMonth.from(from).plusMonths(intent.delta.toLong()), from.dayOfMonth, nights)
        }

        is NbKeyIntent.Edge -> {
            nights.keys
                .filter {
                    YearMonth.from(it) == YearMonth.from(from)
                }.let { if (intent.end) it.maxOrNull() else it.minOrNull() }
        }

        else -> {
            null
        }
    }

/** The intents the calendar owns while a day is focused (consumed even when they lead nowhere). */
private fun NbKeyIntent.isCalendarKey(): Boolean = this is NbKeyIntent.Move || this is NbKeyIntent.Page || this is NbKeyIntent.Edge

/** The first date from [start] in steps of [step] days that has a night and is not after [today]; null if none near. */
private fun nearestNight(
    start: LocalDate,
    step: Long,
    nights: Map<LocalDate, NightUi>,
    today: LocalDate,
): LocalDate? =
    generateSequence(start) { it.plusDays(step) }
        .take(MAX_SEARCH_STEPS)
        .takeWhile { !it.isAfter(today) }
        .firstOrNull { it in nights }

/** The night in [month] closest to [day] of month, or null when the month has none. */
private fun nightInMonth(
    month: YearMonth,
    day: Int,
    nights: Map<LocalDate, NightUi>,
): LocalDate? =
    nights.keys
        .filter { YearMonth.from(it) == month }
        .minByOrNull { abs(it.dayOfMonth - day) }

private const val DAYS = 7
private const val MAX_SEARCH_STEPS = 62
private val CELL_GAP = 2.dp
private val CARD_PADDING = 8.dp
private val FUTURE_DISC = 32.dp

/** Weeks a month can touch (columns of the transposed grid). */
private const val MAX_WEEKS = 6

/** Cells grow to 64 dp at most (`layouts.md` 5.3: 48-64 dp); wider panes center the grid. */
private val WEEKDAY_COLUMN = 20.dp
private val REFERENCE_MONDAY: LocalDate = LocalDate.EPOCH.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
