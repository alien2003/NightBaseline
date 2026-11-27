package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.interaction.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.interaction.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType

/** Test tag of every content card: the layout tests check that the cards of one column share one width and start. */
const val CARD_TAG: String = "nb-card"

/** Test tag of a grouped list row that forms a card with its neighbours (History's night rows). */
const val ROW_TAG: String = "nb-row"

/** Marks a content card for the layout tests ([CARD_TAG]); placed right before the card surface, after any margin. */
fun Modifier.cardTag(): Modifier = testTag(CARD_TAG)

/**
 * A solid content card (`material.solid.card`: `surface.1`, one content radius `shape.l` 20 dp, no blur, no tint),
 * padded 16 dp, items 12 dp apart. Content inside is padded, not clipped.
 */
@Composable
fun NbCard(
    modifier: Modifier = Modifier,
    shape: Shape = NbShapes.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .cardTag()
                .solidCard(shape)
                .padding(NbDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
        content = content,
    )
}

/**
 * The hero card of Home and Night detail (`screens.md` 4, conformance K4): the 28 dp night glyph in the status colour
 * directly on the card (no container), the night label, the
 * headline (`state_*_title`, `type.headline`) and the body with the compared count. One merged TalkBack node
 * (`a11y_summary`); the glyph is decorative. The optional [action] (`states.md` 2: "See {metric}", "What this
 * means") is a text button under the body and its own TalkBack node. [showLabel] false hides the visible night label
 * where a title or heading right above already shows it (it stays in the TalkBack text).
 */
@Composable
fun NightSummaryCard(
    night: NightUi,
    source: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    showLabel: Boolean = true,
    maxWidth: Dp = NbDimens.textMax,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val type = NbTheme.dataType
    val label = text.nightLabel(night)
    val title = text.nightTitle(night, source)
    val body = text.nightBody(night, source)
    val description = text.string(R.string.a11y_summary, label, title, body)
    BoxWithConstraints(
        Modifier
            .widthIn(max = maxWidth)
            .then(modifier)
            .fillMaxWidth()
            .cardTag()
            .solidCard(NbShapes.l)
            // The action's own 12 dp inner padding stands in for most of the bottom padding.
            .padding(
                start = NbDimens.cardPadding,
                top = NbDimens.cardPadding,
                end = NbDimens.cardPadding,
                bottom = if (action != null) NbSpace.s1 else NbDimens.cardPadding,
            ),
    ) {
        // In narrow panes (a book half next to the rail) the glyph goes above the text, so the headline keeps
        // whole words on its lines.
        val stacked = this.maxWidth < SUMMARY_STACK_BELOW
        val content: @Composable (Modifier) -> Unit = { textModifier ->
            Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2), modifier = textModifier) {
                Column(
                    Modifier.clearAndSetSemantics { contentDescription = description },
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                ) {
                    if (showLabel) NbText(label, style = type.statusCalm, color = colors.textSecondary)
                    NbText(title, style = type.headline, color = colors.textPrimary)
                    NbText(body, style = type.summary, color = colors.textSecondary)
                }
                // Pulled back by the button's inner padding so its label lines up with the body text.
                action?.let {
                    NbTextButton(it, onClick = onAction, color = colors.data.primaryText, modifier = Modifier.offset(x = -NbSpace.s4))
                }
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s4)) {
                NightGlyphBadge(night.state.glyph, night.state.tone, Modifier.clearAndSetSemantics {})
                content(Modifier)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s4)) {
                NightGlyphBadge(night.state.glyph, night.state.tone, Modifier.clearAndSetSemantics {})
                content(Modifier.weight(1f))
            }
        }
    }
}

/** Below this inner width the summary card stacks its glyph above the text. */
private val SUMMARY_STACK_BELOW = 300.dp

/**
 * One metric of one night (`screens.md` 4): name, last night's value + unit as the largest text, the [RangeBand],
 * the status label (glyph + words, no container) and the usual range in words. Variants follow [MetricUi.state]: learning (dotted
 * band, "Learning, n of 7"), no value (dash and reason), not compared, not recorded by the source (collapsed one-line
 * tile, no band), access off. The whole tile is one 48 dp+ target and one TalkBack node (`a11y_tile*`).
 *
 * @param selected list-detail selection: a neutral `state.selected` fill (no outline) plus `selected` semantics.
 * @param explain Night detail: also show and announce the state sentence (`detail_*`) and the middle value for
 *   compared and learning states (missing states always show their sentence).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricRangeTile(
    metric: MetricUi,
    source: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    forced: ForcedInteraction? = null,
    explain: Boolean = false,
    maxWidth: Dp = NbDimens.textMax,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val type = NbTheme.dataType
    val explanation = if (explain) text.explanation(metric, source) else null
    val description = listOfNotNull(text.tileDescription(metric, source), explanation).joinToString(" ")
    val shape = NbShapes.l
    Column(
        modifier =
            Modifier
                .widthIn(max = maxWidth)
                .then(modifier)
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.targetMin)
                .cardTag()
                .solidCard(shape)
                .then(if (selected) Modifier.background(colors.data.selected, shape) else Modifier)
                .nbClickable(onClick = onClick, shape = shape, forced = forced)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }.padding(NbDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
            if (metric.state == MetricState.NotProvided) {
                CollapsedTileContent(metric, source)
                return@Column
            }
            NbText(text.name(metric.metric), style = type.itemTitle, color = colors.textPrimary)
            FlowRow(verticalArrangement = Arrangement.Bottom, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                val value = text.value(metric)
                NbText(
                    value?.number ?: DASH,
                    style = NbTheme.typography.numericValue,
                    color = if (value == null) colors.textSecondary else colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                value?.unit?.let {
                    NbText(it, style = NbTheme.typography.unit, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
                }
            }
            if (metric.state != MetricState.AccessOff) {
                RangeBand(metric.range, metric.value, metric.state)
            }
            // Status and usual range always stacked: side by side they wrapped differently from tile to tile, so
            // neighbouring cards had different rhythms (review round 3).
            Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
                StatusLabel(text.status(metric, source), metric.state.glyph, metric.state.tone)
                metric.range?.takeIf { metric.state != MetricState.Learning }?.let { range ->
                    NbText(
                        text.usualRange(metric.metric, range, text.decimals(metric)),
                        style = type.statusCalm,
                        color = colors.textSecondary,
                    )
                }
            }
            if (!metric.state.compared && metric.state != MetricState.Learning) {
                NbText(text.detail(metric, source), style = type.summary, color = colors.textSecondary)
            }
            explanation?.let { NbText(it, style = type.summary, color = colors.textSecondary) }
        }
    }
}

@Composable
private fun CollapsedTileContent(
    metric: MetricUi,
    source: String,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
        MetricGlyphIcon(metric.state.glyph, colors.statusNeutral, size = NbDimens.iconInline)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            NbText(text.name(metric.metric), style = NbTheme.dataType.itemTitle, color = colors.textSecondary)
            NbText(text.status(metric, source), style = NbTheme.dataType.statusCalm, color = colors.textSecondary)
        }
    }
}

/** The state sentence plus the middle value of a compared or learning metric (Night detail tiles), else null. */
private fun UiText.explanation(
    metric: MetricUi,
    source: String,
): String? {
    if (!metric.state.compared && metric.state != MetricState.Learning) return null
    val median = metric.range?.let { string(R.string.stats_median, value(metric.metric, it.median, decimals(metric)).text) }
    return listOfNotNull(detail(metric, source), median?.let { "$it." }).joinToString(" ")
}

/** The dash shown in place of a missing value. */
const val DASH: String = "–"

/**
 * `DisclaimerFooter` (`screens.md` 9, conformance K12): a start-aligned ColorOS footer, "Not a medical device." in
 * 12 sp `text.tertiary` plus the About link in `primaryText` (48 dp target), at the end of Home, Night detail and
 * Metric detail. Text starts 32 dp from the screen edge, like the section headers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisclaimerFooter(
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Wraps the link below the sentence when both do not fit on one line (200 % font, long translations).
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(start = NbDataDimens.sectionHeaderStart, top = NbSpace.s3),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        NbText(
            stringResource(R.string.disclaimer_short),
            style = NbTheme.typography.caption,
            color = NbTheme.colors.textTertiary,
        )
        Spacer(Modifier.width(NbSpace.s1))
        // Pulled back by the button's inner padding so the link reads as part of the sentence.
        NbTextButton(
            stringResource(R.string.footer_about_link),
            onClick = onAbout,
            color = NbTheme.colors.data.primaryText,
            modifier = Modifier.offset(x = -NbSpace.s2),
        )
    }
}
