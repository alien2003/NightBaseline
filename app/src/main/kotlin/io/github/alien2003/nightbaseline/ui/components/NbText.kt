package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * Text on Compose foundation only (no Material): [style] defaults to `type.body` and [color] to `text.primary`. The
 * replacement for `material3.Text`; the sweep after the ColorOS conformance packages swaps every call site.
 */
@Composable
fun NbText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NbTheme.typography.body,
    color: Color = NbTheme.colors.textPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, fontSize = fontSize, textAlign = textAlign ?: TextAlign.Unspecified),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

/** [NbText] for an [AnnotatedString] (links, mixed weights). */
@Composable
fun NbText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = NbTheme.typography.body,
    color: Color = NbTheme.colors.textPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        onTextLayout = onTextLayout,
        overflow = overflow,
        maxLines = maxLines,
    )
}

/**
 * A monochrome icon on Compose foundation only (no Material): the vector tinted with [tint] (default
 * `text.primary`), [size] square (default `icon.size` 24 dp). A null [contentDescription] makes it decorative.
 */
@Composable
fun NbIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = NbTheme.colors.textPrimary,
    size: Dp = NbDimens.icon,
) {
    val semantics =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
        } else {
            Modifier
        }
    Image(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier.size(size).then(semantics),
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}
