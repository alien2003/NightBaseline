package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * A solid card (`material.solid.card`: `surface.1`, `elevation.card` in light, `shape.l`), padded 16 dp, never wider
 * than 600 dp. Content inside is padded, not clipped.
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
                .widthIn(max = NbDimens.textMax)
                .solidCard(shape)
                .padding(NbDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
        content = content,
    )
}

/**
 * The hero card of Home and Night detail (`screens.md` 4): night glyph in its tonal container, the night label, the
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
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val label = text.nightLabel(night)
    val title = text.nightTitle(night, source)
    val body = text.nightBody(night, source)
    val description = text.string(R.string.a11y_summary, label, title, body)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = NbDimens.textMax)
                .solidCard(NbShapes.xl)
                .padding(NbSpace.s6),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s5),
    ) {
        NightGlyphBadge(night.state.glyph, night.state.tone, Modifier.clearAndSetSemantics {})
        Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2), modifier = Modifier.weight(1f)) {
            Column(
                Modifier.clearAndSetSemantics { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
            ) {
                if (showLabel) Text(label, style = NbTheme.typography.labelSmall, color = colors.textSecondary)
                Text(title, style = NbTheme.typography.headline, color = colors.textPrimary)
                Text(body, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
            }
            // Pulled back by the button's inner padding so its label lines up with the body text.
            action?.let { NbTextButton(it, onClick = onAction, modifier = Modifier.offset(x = -NbSpace.s4)) }
        }
    }
}

/**
 * One metric of one night (`screens.md` 4): name, last night's value + unit as the largest text, the [RangeBand],
 * the status chip (glyph + label) and the usual range in words. Variants follow [MetricUi.state]: learning (dotted
 * band, "Learning, n of 7"), no value (dash and reason), not compared, not recorded by the source (collapsed one-line
 * tile, no band), access off. The whole tile is one 48 dp+ target and one TalkBack node (`a11y_tile*`).
 *
 * @param selected list-detail selection: a solid outline plus `selected` semantics.
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
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val explanation = if (explain) text.explanation(metric, source) else null
    val description = listOfNotNull(text.tileDescription(metric, source), explanation).joinToString(" ")
    val shape = NbShapes.l
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = NbDimens.textMax)
                .sizeIn(minHeight = NbDimens.targetMin)
                .solidCard(shape)
                .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
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
            Text(text.name(metric.metric), style = NbTheme.typography.titleSmall, color = colors.textPrimary)
            FlowRow(verticalArrangement = Arrangement.Bottom, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                val value = text.value(metric)
                Text(
                    value?.number ?: DASH,
                    style = NbTheme.typography.numericValue,
                    color = if (value == null) colors.textSecondary else colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                value?.unit?.let {
                    Text(it, style = NbTheme.typography.unit, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
                }
            }
            if (metric.state != MetricState.AccessOff) {
                RangeBand(metric.range, metric.value, metric.state)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
                verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(text.status(metric, source), metric.state.glyph, metric.state.tone)
                metric.range?.takeIf { metric.state != MetricState.Learning }?.let { range ->
                    Text(
                        text.string(
                            R.string.stats_range,
                            text.bare(metric.metric, range.low, text.decimals(metric)),
                            text.value(metric.metric, range.high, text.decimals(metric)).text,
                        ),
                        style = NbTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            if (!metric.state.compared && metric.state != MetricState.Learning) {
                Text(text.detail(metric, source), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
            }
            explanation?.let { Text(it, style = NbTheme.typography.bodySmall, color = colors.textSecondary) }
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
        Column(Modifier.weight(1f)) {
            Text(text.name(metric.metric), style = NbTheme.typography.titleSmall, color = colors.textSecondary)
            Text(text.status(metric, source), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
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
 * `DisclaimerFooter` (`screens.md` 9): "Not a medical device." plus the About link (48 dp target), at the end of Home,
 * Night detail and Metric detail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisclaimerFooter(
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Wraps the link below the sentence when both do not fit on one line (200 % font, long translations).
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.disclaimer_short),
            style = NbTheme.typography.caption,
            color = NbTheme.colors.textTertiary,
        )
        Spacer(Modifier.width(NbSpace.s2))
        NbTextButton(stringResource(R.string.footer_about_link), onClick = onAbout)
    }
}
