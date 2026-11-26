// Band geometry is expressed as fractions of the track; naming every fraction adds nothing.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.RangeUi
import io.github.alien2003.nightbaseline.ui.theme.NbColors
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data

/** Where values sit on a band track: the range plus 75 % of its width on each side. */
private const val TRACK_MARGIN = 0.75

/** Position (0..1) of [value] on a track around [range]; values beyond the track are clamped (`clamped` = true). */
fun bandPosition(
    range: RangeUi,
    value: Double,
): Pair<Float, Boolean> {
    val span = (range.high - range.low).takeIf { it > 0 } ?: 1.0
    val min = range.low - span * TRACK_MARGIN
    val max = range.high + span * TRACK_MARGIN
    val raw = ((value - min) / (max - min)).toFloat()
    return raw.coerceIn(0f, 1f) to (raw < 0f || raw > 1f)
}

/**
 * The personal range band (`screens.md` 4, conformance F9 / K6), drawn as the ColorOS slider idiom: a neutral pill
 * track (`control.track`), the usual range as a solid neutral rounded segment (`band.fill`, 3:1 against the card, no
 * edge lines, no median tick), and last night's value marker whose **shape** encodes the status: a white dot with a
 * 2 dp halo in the card colour (a slider thumb) inside the range, a triangle up / down in `status.moved` outside it,
 * a hollow neutral ring while learning. A value beyond the track is clamped to its edge with a chevron. While
 * learning ([range] null) a dotted outline shows where the band will be. Decorative for TalkBack: the parent's text
 * carries value, status and range.
 */
@Composable
fun RangeBand(
    range: RangeUi?,
    value: Double?,
    state: MetricState,
    modifier: Modifier = Modifier,
    height: Dp = NbDimens.bandHeight,
) {
    val colors = NbTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val target = if (range != null && value != null) bandPosition(range, value) else null
    val position by animateFloatAsState(target?.first ?: 0.5f, NbTheme.motion.data(), label = "marker")
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        drawRoundRect(colors.data.bandTrack, cornerRadius = CornerRadius(size.height / 2))
        if (range == null) {
            drawLearningOutline(colors)
        } else {
            drawRange(range, colors, rtl)
        }
        if (target != null || (range == null && value != null)) {
            val x = if (target != null) xOf(position, rtl) else xOf(0.5f, rtl)
            drawMarker(x, state, colors, clamped = target?.second == true)
        }
    }
}

private fun DrawScope.xOf(
    position: Float,
    rtl: Boolean,
): Float {
    val inset = size.height / 2
    val usable = size.width - 2 * inset
    val p = if (rtl) 1f - position else position
    return inset + usable * p
}

private fun DrawScope.drawRange(
    range: RangeUi,
    colors: NbColors,
    rtl: Boolean,
) {
    val lowX = xOf(bandPosition(range, range.low).first, rtl)
    val highX = xOf(bandPosition(range, range.high).first, rtl)
    // Exactly low..high (a value just outside must never sit on the segment); the ends are rounded like a slider's
    // filled part, so the boundary is exact on the track's centre line.
    val left = minOf(lowX, highX)
    val right = maxOf(lowX, highX)
    val r = minOf(size.height / 2, (right - left) / 2)
    drawRoundRect(
        colors.data.bandRange,
        topLeft = Offset(left, 0f),
        size = Size(right - left, size.height),
        cornerRadius = CornerRadius(r),
    )
}

private fun DrawScope.drawLearningOutline(colors: NbColors) {
    val width = size.width * 0.4f
    val stroke = NbDimens.bandEdgeStroke.toPx()
    val dash = 3f * density
    drawRoundRect(
        colors.data.learningOutline,
        topLeft = Offset((size.width - width) / 2, stroke / 2),
        size = Size(width, size.height - stroke),
        cornerRadius = CornerRadius(size.height / 2),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

private fun DrawScope.drawMarker(
    x: Float,
    state: MetricState,
    colors: NbColors,
    clamped: Boolean,
) {
    val center = Offset(x, size.height / 2)
    val halo = NbDimens.markerHalo.toPx()
    val color = colors.markerColor(state)
    val dot = NbDataDimens.markerDot.toPx()
    // 2 dp halo in the card colour keeps the marker separable from the segment and the track.
    val extent: Float
    when (state) {
        MetricState.Higher, MetricState.Lower -> {
            val side = NbDataDimens.statusTriangle.toPx() * 1.2f
            extent = side * 0.5f
            drawCircle(colors.markerHalo, radius = extent + halo, center = center)
            drawPath(triangle(center, side, up = state == MetricState.Higher), color)
        }

        MetricState.Within, MetricState.SideIgnoredHigher, MetricState.SideIgnoredLower -> {
            extent = dot / 2
            drawCircle(colors.markerHalo, radius = extent + halo, center = center)
            drawCircle(colors.data.markerWithin, radius = extent, center = center)
            if (colors.data.markerRim.alpha > 0f) {
                drawCircle(colors.data.markerRim, radius = extent - 0.5f * density, center = center, style = Stroke(1f * density))
            }
        }

        else -> {
            // Learning / not compared: a hollow point in the neutral colour.
            extent = dot / 2
            drawCircle(colors.markerHalo, radius = extent + halo, center = center)
            drawCircle(color, radius = extent - density, center = center, style = Stroke(width = 2f * density))
        }
    }
    if (clamped) {
        // The chevron points outwards, beyond the edge the value was clamped to.
        val dir = if (x > size.width / 2) 1f else -1f
        val cx = x + dir * (extent + halo + 3f * density)
        val h = size.height * 0.28f
        val w = h * 0.7f
        drawLine(
            color,
            Offset(cx - dir * w / 2, center.y - h),
            Offset(cx + dir * w / 2, center.y),
            strokeWidth = 1.5f * density,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(cx + dir * w / 2, center.y),
            Offset(cx - dir * w / 2, center.y + h),
            strokeWidth = 1.5f * density,
            cap = StrokeCap.Round,
        )
    }
}

/** Marker colour for a state (used by the chart legend and the glance rows too). */
fun NbColors.markerColor(state: MetricState): Color =
    when (state) {
        MetricState.Higher, MetricState.Lower -> statusMoved
        MetricState.Within, MetricState.SideIgnoredHigher, MetricState.SideIgnoredLower -> statusWithin
        else -> statusNeutral
    }
