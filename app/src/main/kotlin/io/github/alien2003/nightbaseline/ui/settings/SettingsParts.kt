package io.github.alien2003.nightbaseline.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * A Health Connect source as a radio row (`screens.md` 4 `SourceRow`, `layouts.md` 5.5: >= 56 dp): a letter avatar
 * (source icons come from the package manager at runtime and are never bundled), the app label, the last sleep it
 * wrote and the measurements it writes. The whole row selects it; one TalkBack node with radio semantics.
 */
@Composable
fun SourceOptionRow(
    source: SourceOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Column(modifier.fillMaxWidth()) {
        if (showDivider) Divider()
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.rowMinHeight)
                .nbClickable(onClick = onClick, shape = NbShapes.l, role = Role.RadioButton, pressScale = false)
                .semantics(mergeDescendants = true) { this.selected = selected }
                .padding(horizontal = NbSpace.s5, vertical = NbDimens.rowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        ) {
            SourceAvatar(source.label)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
                Text(
                    source.label,
                    style = NbTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else null,
                )
                Text(
                    source.lastSleep?.let { text.string(R.string.source_last_sync, text.dateShort(it)) }
                        ?: text.string(R.string.setup_source_no_sleep),
                    style = NbTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                if (source.metrics.isNotEmpty()) {
                    Text(
                        text.string(R.string.setup_source_writes, text.list(source.metrics.map { text.shortName(it) })),
                        style = NbTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics {},
                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline),
            )
        }
    }
}

/** The first letter of an app label in a tonal circle; decorative (the label is the text next to it). */
@Composable
private fun SourceAvatar(label: String) {
    val colors = NbTheme.colors
    Box(
        Modifier
            .size(NbDimens.targetMin - NbSpace.s3)
            .background(colors.primaryContainer, NbShapes.circle)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.firstOrNull()?.uppercase().orEmpty(),
            style = NbTheme.typography.titleSmall,
            color = colors.onPrimaryContainer,
        )
    }
}

/**
 * A large radio card of the sensitivity screen (`screens.md` 7.7): radio, name and one plain-language line. The
 * selected card has a 2 dp outline, a filled radio and a bold name, so selection does not rely on colour.
 */
@Composable
fun PresetCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.textMax)
            .solidCard(NbShapes.l)
            .then(if (selected) Modifier.border(2.dp, colors.primary, NbShapes.l) else Modifier)
            .nbClickable(onClick = onClick, shape = NbShapes.l, role = Role.RadioButton, pressScale = false)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(NbDimens.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            Text(
                title,
                style = NbTheme.typography.title,
                color = colors.textPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(description, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

/**
 * A section row of the two-pane Settings list: title, summary and chevron; the selected row sits on a `surface.3`
 * capsule with a bold title and `selected` semantics (shape and weight, not only colour).
 */
@Composable
internal fun SectionRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .sizeIn(minHeight = NbDimens.rowMinHeight)
            .background(if (selected) colors.surface3 else colors.surface1, NbShapes.l)
            .nbClickable(onClick = onClick, shape = NbShapes.l, pressScale = false)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = NbSpace.s5, vertical = NbDimens.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            Text(
                title,
                style = NbTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = if (selected) FontWeight.Bold else null,
            )
            Text(summary, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Icon(NbIcons.Chevron, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(NbDimens.iconInline))
    }
}

/** A heading inside a pane or card (`type.title`, heading semantics). */
@Composable
internal fun PaneHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        style = NbTheme.typography.title,
        color = NbTheme.colors.textPrimary,
        modifier = modifier.semantics { heading() },
    )
}

/** A small label inside a settings group card (sub-heading of a set of radio rows). */
@Composable
internal fun GroupLabel(title: String) {
    Text(
        title,
        style = NbTheme.typography.labelSmall,
        color = NbTheme.colors.textSecondary,
        modifier =
            Modifier
                .padding(start = NbSpace.s5, end = NbSpace.s5, top = NbSpace.s4, bottom = NbSpace.s1)
                .semantics { heading() },
    )
}

/** A titled paragraph block inside a card (privacy policy, about). */
@Composable
internal fun TextBlock(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
        Text(
            title,
            style = NbTheme.typography.titleSmall,
            color = NbTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
    }
}

/** Hairline separator inset to the text column. */
@Composable
internal fun Divider() {
    Box(
        Modifier
            .padding(start = NbSpace.s5)
            .fillMaxWidth()
            .height(1.dp)
            .background(NbTheme.colors.separator),
    )
}
