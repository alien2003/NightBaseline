package io.github.alien2003.nightbaseline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.MetricRangeTile
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType

/**
 * The metric tiles in registry order (`screens.md` 7.1), in [columns] columns; with an odd count the last tile spans
 * the row. Core metrics the source does not provide stay in place as collapsed tiles. Tiles of one row share the
 * height of the tallest, so the grid keeps one rhythm.
 */
@Composable
internal fun MetricGrid(
    metrics: List<MetricUi>,
    source: String,
    columns: Int,
    maxWidth: Dp,
    onMetric: (MetricId) -> Unit,
    selected: MetricId? = null,
    explain: Boolean = false,
) {
    val density = LocalDensity.current
    metrics.chunked(columns).forEach { row ->
        // Tiles of one row share the height of the tallest (reported after the first layout), so the grid keeps one
        // rhythm. Intrinsic heights are not used: they under-measure the wrapping chip rows and would clip text.
        val heights = remember(row, columns, maxWidth, density) { mutableStateMapOf<MetricId, Int>() }
        val tallest = with(density) { (heights.values.maxOrNull() ?: 0).toDp() }
        Row(Modifier.widthIn(max = maxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
            row.forEach { metric ->
                MetricRangeTile(
                    metric,
                    source,
                    onClick = { onMetric(metric.metric) },
                    selected = metric.metric == selected,
                    explain = explain,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = if (row.size > 1) tallest else 0.dp)
                            .onSizeChanged { heights[metric.metric] = it.height },
                )
            }
        }
    }
}

/**
 * `LOADING` (`states.md` 2): a static skeleton (no shimmer: idle screens never animate) with the loading sentence as
 * its only text and TalkBack node.
 */
@Composable
internal fun LoadingCard(modifier: Modifier = Modifier) {
    val body = stringResource(R.string.state_loading_body)
    val colors = NbTheme.colors
    NbCard(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = body }) {
        Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s5), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(NbDataDimens.summaryGlyph).background(colors.surface2, NbShapes.circle))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                SkeletonLine(SKELETON_LABEL)
                SkeletonLine(SKELETON_TITLE)
            }
        }
        NbText(body, style = NbTheme.dataType.summary, color = colors.textSecondary)
    }
}

/** Static skeleton tiles under the loading card (one per expected metric). */
@Composable
internal fun SkeletonTiles(
    count: Int,
    columns: Int,
    maxWidth: Dp,
) {
    val colors = NbTheme.colors
    List(count) { it }.chunked(columns).forEach { row ->
        Row(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            row.forEach { _ ->
                NbCard(Modifier.weight(1f)) {
                    SkeletonLine(SKELETON_NAME)
                    SkeletonLine(SKELETON_VALUE, height = SkeletonValueHeight)
                    Box(Modifier.fillMaxWidth().height(NbDimens.bandHeight).background(colors.data.bandTrack, NbShapes.pill))
                }
            }
            repeat(columns - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** A grey placeholder bar of a skeleton, [fraction] of the width. */
@Composable
private fun SkeletonLine(
    fraction: Float,
    height: Dp = SkeletonLineHeight,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .background(NbTheme.colors.surface2, NbShapes.pill),
    )
}

private const val SKELETON_LABEL = 0.5f
private const val SKELETON_TITLE = 0.9f
private const val SKELETON_NAME = 0.45f
private const val SKELETON_VALUE = 0.3f
private val SkeletonLineHeight = 14.dp
private val SkeletonValueHeight = 28.dp
