package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
            .fillMaxWidth()
            .widthIn(max = CALENDAR_MAX + CARD_PADDING * 2)
            .solidCard(NbShapes.l)
            .padding(CARD_PADDING),
    ) {
        val transposed = maxWidth < NbDimens.targetMin * DAYS + CELL_GAP * (DAYS - 1)
        // Narrower still (a book half next to the rail): the weeks scroll sideways, cells keep 48 dp.
        val fits = maxWidth >= WEEKDAY_COLUMN + (NbDimens.targetMin + CELL_GAP) * MAX_WEEKS
        Column(
            Modifier
                .fillMaxWidth()
                .monthSwipe(month, rtl, onPrevious = { if (canPrevious) onMonth(month.minusMonths(1)) }, onNext = {
                    if (canNext) onMonth(month.plusMonths(1))
                })
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val from = focused ?: return@onPreviewKeyEvent false
                    val target = keyTarget(event.key, from, nights, today, keyStep(event.key, transposed, rtl))
                    if (target == null && !event.key.isCalendarKey()) return@onPreviewKeyEvent false
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
            val weeks = weeksOf(month, firstDay)
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

/** A horizontal swipe of at least 64 dp moves to the next (towards the start) or previous month. */
private fun Modifier.monthSwipe(
    month: YearMonth,
    rtl: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier =
    pointerInput(month, rtl) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) > SWIPE_DP.dp.toPx()) {
                    val forward = if (rtl) total > 0 else total < 0
                    if (forward) onNext() else onPrevious()
                }
            },
        ) { _, delta -> total += delta }
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
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
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
    key: Key,
    transposed: Boolean,
    rtl: Boolean,
): Int? {
    val across = if (transposed) DAYS else 1
    val down = if (transposed) 1 else DAYS
    val forward = if (rtl) -across else across
    return when (key) {
        Key.DirectionRight -> forward
        Key.DirectionLeft -> -forward
        Key.DirectionDown -> down
        Key.DirectionUp -> -down
        else -> null
    }
}

/** The day an arrow, Page Up / Down, Home or End key moves to from [from]; null when there is none or not a key of ours. */
private fun keyTarget(
    key: Key,
    from: LocalDate,
    nights: Map<LocalDate, NightUi>,
    today: LocalDate,
    step: Int?,
): LocalDate? =
    when {
        step != null -> nearestNight(from.plusDays(step.toLong()), step.toLong(), nights, today)
        key == Key.PageUp -> nightInMonth(YearMonth.from(from).minusMonths(1), from.dayOfMonth, nights)
        key == Key.PageDown -> nightInMonth(YearMonth.from(from).plusMonths(1), from.dayOfMonth, nights)
        key == Key.MoveHome -> nights.keys.filter { YearMonth.from(it) == YearMonth.from(from) }.minOrNull()
        key == Key.MoveEnd -> nights.keys.filter { YearMonth.from(it) == YearMonth.from(from) }.maxOrNull()
        else -> null
    }

private fun Key.isCalendarKey(): Boolean = this in CALENDAR_KEYS

private val CALENDAR_KEYS =
    setOf(
        Key.DirectionLeft,
        Key.DirectionRight,
        Key.DirectionUp,
        Key.DirectionDown,
        Key.PageUp,
        Key.PageDown,
        Key.MoveHome,
        Key.MoveEnd,
    )

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
private const val SWIPE_DP = 64
private val CELL_GAP = 2.dp
private val CARD_PADDING = 8.dp
private val FUTURE_DISC = 32.dp

/** Weeks a month can touch (columns of the transposed grid). */
private const val MAX_WEEKS = 6

/** Cells grow to 64 dp at most (`layouts.md` 5.3: 48-64 dp); wider panes center the grid. */
private val CALENDAR_MAX = 64.dp * DAYS + CELL_GAP * (DAYS - 1)
private val WEEKDAY_COLUMN = 20.dp
private val REFERENCE_MONDAY: LocalDate = LocalDate.EPOCH.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
