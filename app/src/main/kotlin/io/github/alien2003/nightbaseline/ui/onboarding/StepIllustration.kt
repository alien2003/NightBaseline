// The illustration is a drawing: its proportions are fractions of the canvas, named only where they mean something.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.components.triangle
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/** Height of the step illustration (`layouts.md` 5.5). */
internal val IllustrationHeight: Dp = 160.dp

/** Kinds of the step illustration. */
internal enum class Illustration {
    /** Every point inside the band. */
    Within,

    /** One point above the band (how it works). */
    Outside,

    /** A dotted band outline with a few hollow points (learning). */
    Learning,
}

internal fun OnboardingState.illustration(): Illustration =
    when {
        step == OnboardingStep.HowItWorks -> Illustration.Outside
        step == OnboardingStep.Ready && !rangeReady -> Illustration.Learning
        else -> Illustration.Within
    }

/**
 * Our own abstract band-and-points drawing (no vendor art): nights as points left to right over a horizontal usual
 * range band with its middle line. Static, decorative (cleared semantics), drawn on a solid nested surface.
 */
@Composable
internal fun StepIllustration(
    kind: Illustration,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .height(IllustrationHeight)
            .solidCard(NbShapes.l)
            .clearAndSetSemantics {},
    ) {
        Canvas(Modifier.matchParentSize().padding(horizontal = NbSpace.s7, vertical = NbSpace.s6)) {
            val bandTop = size.height * 0.34f
            val bandBottom = size.height * 0.70f
            val radius = CornerRadius(12.dp.toPx())
            val edge = NbDimens.bandEdgeStroke.toPx()
            if (kind == Illustration.Learning) {
                drawRoundRect(
                    colors.bandLearningOutline,
                    topLeft = Offset(0f, bandTop),
                    size = Size(size.width, bandBottom - bandTop),
                    cornerRadius = radius,
                    style = Stroke(edge, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            } else {
                drawRoundRect(colors.bandFill, Offset(0f, bandTop), Size(size.width, bandBottom - bandTop), radius)
                val mid = (bandTop + bandBottom) / 2
                drawLine(colors.bandEdge, Offset(0f, mid), Offset(size.width, mid), edge)
            }
            val offsets = listOf(0.1f, -0.25f, 0.3f, -0.05f, 0.2f, -0.3f, 0.05f)
            val count = if (kind == Illustration.Learning) 3 else offsets.size
            val step = size.width / offsets.size
            val pointRadius = 5.dp.toPx()
            for (i in 0 until count) {
                val x = step * (i + 0.5f)
                val center = (bandTop + bandBottom) / 2
                val outside = kind == Illustration.Outside && i == offsets.lastIndex
                if (outside) {
                    drawPath(triangle(Offset(x, size.height * 0.14f), 14.dp.toPx(), up = true), colors.statusMoved)
                } else if (kind == Illustration.Learning) {
                    drawCircle(
                        colors.chartPoint,
                        pointRadius,
                        Offset(x, center + offsets[i] * (bandBottom - bandTop)),
                        style = Stroke(edge),
                    )
                } else {
                    drawCircle(colors.chartPoint, pointRadius, Offset(x, center + offsets[i] * (bandBottom - bandTop)))
                }
            }
        }
    }
}
