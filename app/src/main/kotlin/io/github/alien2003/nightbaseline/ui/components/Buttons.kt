package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * Primary pill button (`screens.md` 4): `primary` fill, `onPrimary` label in `type.label`, at least 48 dp tall, never
 * wider than 360 dp. The label wraps at large font instead of being cut.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    forced: ForcedInteraction? = null,
) {
    val colors = NbTheme.colors
    Box(
        modifier =
            modifier
                .widthIn(max = NbDimens.buttonMaxWidth)
                .sizeIn(minHeight = NbDimens.targetMin, minWidth = NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.pill, enabled = enabled, forced = forced)
                .background(if (enabled) colors.primary else colors.surface3, NbShapes.pill)
                .padding(horizontal = NbSpace.s6, vertical = NbSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = NbTheme.typography.label,
            color = if (enabled) colors.onPrimary else colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Tonal pill button: `primaryContainer` fill, `onPrimaryContainer` label. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forced: ForcedInteraction? = null,
) {
    val colors = NbTheme.colors
    Box(
        modifier =
            modifier
                .widthIn(max = NbDimens.buttonMaxWidth)
                .sizeIn(minHeight = NbDimens.targetMin, minWidth = NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.pill, forced = forced)
                .background(colors.primaryContainer, NbShapes.pill)
                .padding(horizontal = NbSpace.s6, vertical = NbSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NbTheme.typography.label, color = colors.onPrimaryContainer, textAlign = TextAlign.Center)
    }
}

/** Text button: `primary` label (a link-coloured action), 48 dp target. [color] overrides it on glass. */
@Composable
fun NbTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NbTheme.colors.primary,
    forced: ForcedInteraction? = null,
) {
    Box(
        modifier =
            modifier
                .sizeIn(minHeight = NbDimens.targetMin, minWidth = NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.pill, forced = forced)
                .padding(horizontal = NbSpace.s4, vertical = NbSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NbTheme.typography.label, color = color, textAlign = TextAlign.Center)
    }
}

/** Icon-only 48 dp button with a required [contentDescription] (no unlabeled icon controls). */
@Composable
fun NbIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NbTheme.colors.textPrimary,
    onGlass: Boolean = false,
    forced: ForcedInteraction? = null,
) {
    Box(
        modifier =
            modifier
                .size(NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.circle, onGlass = onGlass, forced = forced)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(NbDimens.icon))
    }
}
