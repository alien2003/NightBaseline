package io.github.alien2003.nightbaseline.nav

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs

/** A fold as WindowManager reports it, in window pixels (a plain value so the mapping is tested on the JVM). */
data class FoldSpec(
    /** `FoldingFeature.Orientation.VERTICAL` (book) rather than horizontal (tabletop). */
    val vertical: Boolean,
    /** `FoldingFeature.isSeparating`: HALF_OPENED, or a hinge that always separates. */
    val separating: Boolean,
    /** `FoldingFeature.occlusionType == FULL`: nothing may be drawn inside the bounds. */
    val occluding: Boolean,
    /** Bounds left, px. */
    val left: Int,
    /** Bounds top, px. */
    val top: Int,
    /** Bounds right, px. */
    val right: Int,
    /** Bounds bottom, px. */
    val bottom: Int,
)

/**
 * `layouts.md` 3.1: the window size in dp (from the current window metrics), the font scale and the folds
 * WindowManager reports (FLAT creases are kept as non-separating folds; HALF_OPENED or hinges are separating:
 * vertical = book, horizontal = tabletop). Pure.
 */
fun layoutInputsOf(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    fontScale: Float,
    folds: List<FoldSpec>,
): LayoutInputs {
    fun fold(f: FoldSpec): FoldInfo {
        val center = if (f.vertical) (f.left + f.right) / 2f else (f.top + f.bottom) / 2f
        val size = if (f.vertical) f.right - f.left else f.bottom - f.top
        return FoldInfo(center / density, separating = f.separating, occludedDp = if (f.occluding) size / density else 0f)
    }
    // Several folds of one orientation are rare; the separating one decides.
    val vertical = folds.filter { it.vertical }.maxByOrNull { if (it.separating) 1 else 0 }?.let(::fold)
    val horizontal = folds.filter { !it.vertical }.maxByOrNull { if (it.separating) 1 else 0 }?.let(::fold)
    return LayoutInputs(
        widthDp = widthPx / density,
        heightDp = heightPx / density,
        fontScale = fontScale,
        verticalFold = vertical,
        horizontalFold = horizontal,
    )
}

/** The folds of a [WindowLayoutInfo]. */
fun foldsOf(info: WindowLayoutInfo): List<FoldSpec> =
    info.displayFeatures.filterIsInstance<FoldingFeature>().map { f ->
        FoldSpec(
            vertical = f.orientation == FoldingFeature.Orientation.VERTICAL,
            separating = f.isSeparating,
            occluding = f.occlusionType == FoldingFeature.OcclusionType.FULL,
            left = f.bounds.left,
            top = f.bounds.top,
            right = f.bounds.right,
            bottom = f.bounds.bottom,
        )
    }

/**
 * The [LayoutInputs] of [activity]'s window, live: WindowInfoTracker's folding features (state, orientation,
 * isSeparating, bounds) and the current window metrics, recomputed on every configuration change (fold, unfold,
 * rotation, resize, multi-window; the activity handles these without recreation) and every posture change.
 */
@Composable
fun rememberWindowLayoutInputs(activity: Activity): LayoutInputs {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by flow.collectAsState(initial = WindowLayoutInfo(emptyList()))
    return remember(configuration, info, density) {
        val bounds = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        layoutInputsOf(bounds.width(), bounds.height(), density.density, density.fontScale, foldsOf(info))
    }
}
