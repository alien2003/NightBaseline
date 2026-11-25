// Glyph geometry is expressed as fractions of the glyph box; naming every fraction adds nothing.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.model.MetricGlyph
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.theme.NbColors
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** `color.status.*` of [tone]. */
fun NbColors.tone(tone: StatusTone): Color =
    when (tone) {
        StatusTone.Within -> statusWithin
        StatusTone.Moved -> statusMoved
        StatusTone.Neutral -> statusNeutral
        StatusTone.Info -> statusInfo
    }

/** `color.status.*.container` of [tone]. */
fun NbColors.toneContainer(tone: StatusTone): Color =
    when (tone) {
        StatusTone.Within -> statusWithinContainer
        StatusTone.Moved -> statusMovedContainer
        StatusTone.Neutral -> statusNeutralContainer
        StatusTone.Info -> statusInfoContainer
    }

/** `color.status.*.onContainer` of [tone]. */
fun NbColors.toneOnContainer(tone: StatusTone): Color =
    when (tone) {
        StatusTone.Within -> statusWithinOnContainer
        StatusTone.Moved -> statusMovedOnContainer
        StatusTone.Neutral -> statusNeutralOnContainer
        StatusTone.Info -> statusInfoOnContainer
    }

/**
 * The shape that carries a metric status (`screens.md` 6): filled circle, triangle up / down, dotted ring, dash,
 * slashed circle, key. Decorative for TalkBack: the meaning is always in the accompanying label.
 */
@Composable
fun MetricGlyphIcon(
    glyph: MetricGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = NbDimens.iconChip,
) {
    if (glyph == MetricGlyph.Key) {
        VectorGlyph(NbIcons.Key, color, modifier, size)
        return
    }
    Canvas(modifier.size(size).clearAndSetSemantics {}) { drawMetricGlyph(glyph, color) }
}

/** Draws [glyph] filling this scope's box. Shared by the band marker and the chart points. */
fun DrawScope.drawMetricGlyph(
    glyph: MetricGlyph,
    color: Color,
    center: Offset = this.center,
    box: Float = size.minDimension,
) {
    val stroke = (box * 0.12f).coerceAtLeast(1.5f * density)
    when (glyph) {
        MetricGlyph.FilledCircle -> {
            drawCircle(color, radius = box * 0.38f, center = center)
        }

        MetricGlyph.TriangleUp -> {
            drawPath(triangle(center, box * 0.9f, up = true), color)
        }

        MetricGlyph.TriangleDown -> {
            drawPath(triangle(center, box * 0.9f, up = false), color)
        }

        MetricGlyph.DottedRing -> {
            dottedRing(color, center, box * 0.36f, stroke)
        }

        MetricGlyph.Dash -> {
            val half = box * 0.32f
            drawLine(
                color,
                Offset(center.x - half, center.y),
                Offset(center.x + half, center.y),
                strokeWidth = stroke * 1.2f,
                cap = StrokeCap.Round,
            )
        }

        MetricGlyph.SlashedCircle -> {
            slashedCircle(color, center, box * 0.36f, stroke)
        }

        MetricGlyph.Key -> {
            dottedRing(color, center, box * 0.36f, stroke)
        }
    }
}

/** An equilateral triangle of side [side] centred on [center], pointing up or down (the direction of the change). */
fun triangle(
    center: Offset,
    side: Float,
    up: Boolean,
): Path {
    val h = side * sqrt(3f) / 2f
    val sign = if (up) 1f else -1f
    // The centroid sits at a third of the height, so the shape looks centred.
    val apexY = center.y - sign * h * 2f / 3f
    val baseY = center.y + sign * h / 3f
    return Path().apply {
        moveTo(center.x, apexY)
        lineTo(center.x + side / 2f, baseY)
        lineTo(center.x - side / 2f, baseY)
        close()
    }
}

private fun DrawScope.dottedRing(
    color: Color,
    center: Offset,
    radius: Float,
    stroke: Float,
) {
    // Eight dashes around the ring (tokens.md 8).
    val circumference = (2 * PI * radius).toFloat()
    val segment = circumference / 8f
    drawCircle(
        color,
        radius = radius,
        center = center,
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment * 0.55f, segment * 0.45f))),
    )
}

private fun DrawScope.slashedCircle(
    color: Color,
    center: Offset,
    radius: Float,
    stroke: Float,
) {
    drawCircle(color, radius = radius, center = center, style = Stroke(width = stroke))
    val d = radius * cos(PI / 4).toFloat()
    drawLine(color, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * The shape that carries a night status (`screens.md` 6): circle with check, rounded square with one or two
 * triangles, dotted ring, half-filled circle, circle with dash, two half circles, slashed circle, and the outline
 * icons of data-access problems. [background] is the surface behind it (used to cut the check and triangles).
 */
@Composable
fun NightGlyphIcon(
    glyph: NightGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = NbDimens.icon,
    background: Color = NbTheme.colors.surface1,
) {
    val vector =
        when (glyph) {
            NightGlyph.Key -> NbIcons.Key
            NightGlyph.CloudClock -> NbIcons.CloudClock
            NightGlyph.Download -> NbIcons.Download
            else -> null
        }
    if (vector != null) {
        VectorGlyph(vector, color, modifier, size)
        return
    }
    Canvas(modifier.size(size).clearAndSetSemantics {}) { drawNightGlyph(glyph, color, background) }
}

/** Draws a night [glyph] filling this scope's box. */
@Suppress("LongMethod") // One geometric recipe per glyph of screens.md 6.
fun DrawScope.drawNightGlyph(
    glyph: NightGlyph,
    color: Color,
    background: Color,
) {
    val box = size.minDimension
    val stroke = box * 0.09f
    val r = box * 0.42f
    when (glyph) {
        NightGlyph.CheckCircle -> {
            drawCircle(color, radius = r)
            val check =
                Path().apply {
                    moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
                    lineTo(center.x - r * 0.1f, center.y + r * 0.36f)
                    lineTo(center.x + r * 0.5f, center.y - r * 0.34f)
                }
            drawPath(check, background, style = Stroke(width = stroke * 1.3f, cap = StrokeCap.Round))
        }

        NightGlyph.MovedOne, NightGlyph.MovedTwo -> {
            val side = r * 2f
            drawRoundRect(
                color,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.28f),
            )
            if (glyph == NightGlyph.MovedOne) {
                drawPath(triangle(center, side * 0.5f, up = true), background)
            } else {
                drawPath(triangle(Offset(center.x - side * 0.2f, center.y), side * 0.36f, up = true), background)
                drawPath(triangle(Offset(center.x + side * 0.2f, center.y), side * 0.36f, up = true), background)
            }
        }

        NightGlyph.DottedRing -> {
            dottedRing(color, center, r, stroke)
        }

        NightGlyph.HalfCircle -> {
            drawCircle(color, radius = r, style = Stroke(width = stroke))
            drawArc(
                color,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size =
                    Size(r * 2, r * 2),
            )
        }

        NightGlyph.DashCircle -> {
            drawCircle(color, radius = r, style = Stroke(width = stroke))
            val half = r * 0.5f
            drawLine(
                color,
                Offset(center.x - half, center.y),
                Offset(center.x + half, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        NightGlyph.TwoHalves -> {
            val small = r * 0.7f
            val dx = r * 0.4f
            drawArc(
                color,
                90f,
                180f,
                useCenter = true,
                topLeft = Offset(center.x - dx - small, center.y - small),
                size = Size(small * 2, small * 2),
            )
            drawArc(
                color,
                -90f,
                180f,
                useCenter = false,
                topLeft = Offset(center.x + dx - small, center.y - small),
                size = Size(small * 2, small * 2),
                style = Stroke(width = stroke),
            )
        }

        NightGlyph.SlashedCircle -> {
            slashedCircle(color, center, r, stroke)
        }

        NightGlyph.Key, NightGlyph.CloudClock, NightGlyph.Download -> {
            dottedRing(color, center, r, stroke)
        }

        NightGlyph.None -> {}
    }
}

@Composable
private fun VectorGlyph(
    vector: ImageVector,
    color: Color,
    modifier: Modifier,
    size: Dp,
) {
    Icon(vector, contentDescription = null, tint = color, modifier = modifier.size(size))
}

/**
 * A status chip: tonal container (`status.*.container`) with the shape [glyph] in the status colour and the text
 * [label] in `status.*.onContainer`. Status is never carried by colour alone. Merged into its parent for TalkBack.
 */
@Composable
fun StatusChip(
    label: String,
    glyph: MetricGlyph,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Row(
        modifier =
            modifier
                .sizeIn(minHeight = 28.dp)
                .background(colors.toneContainer(tone), NbShapes.pill)
                .padding(horizontal = NbSpace.s4, vertical = NbSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricGlyphIcon(glyph, colors.tone(tone))
        Text(label, style = NbTheme.typography.label, color = colors.toneOnContainer(tone))
    }
}

/** Night glyph inside a tonal circle container (summary card, 40 dp glyph in a 56 dp container). */
@Composable
fun NightGlyphBadge(
    glyph: NightGlyph,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    container: Dp = NbDimens.iconSummaryContainer,
    glyphSize: Dp = NbDimens.iconSummary,
) {
    val colors = NbTheme.colors
    val containerColor = colors.toneContainer(tone)
    Box(
        modifier = modifier.size(container).background(containerColor, NbShapes.circle),
        contentAlignment = Alignment.Center,
    ) {
        NightGlyphIcon(glyph, colors.tone(tone), size = glyphSize, background = containerColor)
    }
}
