package io.github.alien2003.nightbaseline.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.NbChevron
import io.github.alien2003.nightbaseline.ui.components.NbHairline
import io.github.alien2003.nightbaseline.ui.components.NbRadio
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.components.SettingsTrailing
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls

/**
 * A Health Connect source as a single-choice row (`screens.md` 4 `SourceRow`, `coloros-conformance.md` L10): the app's
 * own 40 dp icon read from the package manager at runtime (never bundled; a neutral letter disc when it is not
 * available), the app label (16/500 in every state), the last sleep it wrote and the measurements it writes, and a
 * trailing radio. The hairline above starts after the icon (72 dp). The whole row selects it; one TalkBack node with
 * radio semantics.
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
    val controls = NbTheme.controls
    Column(modifier.fillMaxWidth()) {
        if (showDivider) NbHairline(start = NbControlDimens.rowPaddingHorizontal * 2 + NbControlDimens.avatar)
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbControlDimens.rowMinHeight)
                .nbClickable(onClick = onClick, shape = RectangleShape, role = Role.RadioButton, pressScale = false)
                .semantics(mergeDescendants = true) { this.selected = selected }
                .padding(horizontal = NbControlDimens.rowPaddingHorizontal, vertical = NbControlDimens.rowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbControlDimens.rowPaddingHorizontal),
        ) {
            SourceIcon(source)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbControlDimens.rowTitleGap)) {
                Text(source.label, style = NbControlType.itemTitle, color = controls.title)
                Text(
                    source.lastSleep?.let { text.string(R.string.source_last_sync, text.dateShort(it)) }
                        ?: text.string(R.string.setup_source_no_sleep),
                    style = NbControlType.summary,
                    color = controls.summary,
                )
                if (source.metrics.isNotEmpty()) {
                    Text(
                        text.string(R.string.setup_source_writes, text.list(source.metrics.map { text.shortName(it) })),
                        style = NbControlType.summary,
                        color = controls.summary,
                    )
                }
            }
            NbRadio(selected = selected)
        }
    }
}

/** The source app's launcher icon from the package manager, or its first letter on a neutral disc. Decorative. */
@Composable
private fun SourceIcon(source: SourceOption) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { NbControlDimens.avatar.roundToPx() }
    val icon: ImageBitmap? =
        remember(source.packageName, px) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(source.packageName)
                val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, px, px)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(NbControlDimens.avatar).clearAndSetSemantics {})
        return
    }
    val controls = NbTheme.controls
    Box(
        Modifier
            .size(NbControlDimens.avatar)
            .background(controls.avatar, NbShapes.circle)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            source.label
                .firstOrNull()
                ?.uppercase()
                .orEmpty(),
            style = NbControlType.itemTitle,
            color = controls.title,
        )
    }
}

/**
 * One sensitivity preset as a single-choice row of the presets card (`coloros-conformance.md` L8): name 16/500, one
 * plain-language summary line and a trailing radio; selection is the filled radio plus `selected` semantics (no
 * outline, no weight change).
 */
@Composable
fun PresetRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    SettingsRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        summary = description,
        trailing = SettingsTrailing.Radio(selected),
        showDivider = showDivider,
    )
}

/**
 * A section row of the two-pane Settings list: title, summary and chevron; the selected row sits on the neutral
 * `state.selected` fill with a bold title and `selected` semantics (fill and weight, not only colour).
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
    val controls = NbTheme.controls
    Row(
        modifier
            .fillMaxWidth()
            .sizeIn(minHeight = NbControlDimens.rowMinHeight)
            .solidCard(NbShapes.l)
            .clip(NbShapes.l)
            .background(if (selected) controls.selectedRow else colors.surface1)
            .nbClickable(onClick = onClick, shape = NbShapes.l, pressScale = false)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = NbControlDimens.rowPaddingHorizontal, vertical = NbControlDimens.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbControlDimens.rowTrailingGap),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbControlDimens.rowTitleGap)) {
            Text(
                title,
                style = NbControlType.itemTitle,
                color = controls.title,
                fontWeight = if (selected) FontWeight.Bold else null,
            )
            Text(summary, style = NbControlType.summary, color = controls.summary)
        }
        NbChevron()
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

/** A titled paragraph block inside a card (privacy policy, about). */
@Composable
internal fun TextBlock(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
        Text(
            title,
            style = NbControlType.itemTitle,
            color = NbTheme.controls.title,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = NbTheme.typography.body, color = NbTheme.controls.summary)
    }
}

/** Hairline separator between rows of a grouped card, inset 16 dp at both ends. */
@Composable
internal fun Divider() {
    NbHairline()
}
