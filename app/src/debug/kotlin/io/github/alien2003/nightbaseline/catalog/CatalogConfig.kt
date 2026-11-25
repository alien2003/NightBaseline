package io.github.alien2003.nightbaseline.catalog

import androidx.compose.runtime.Immutable

/** Everything the catalog controls (`screens.md` 7.10); the screenshot tests build it directly. */
@Immutable
data class CatalogConfig(
    /** Dark tokens. */
    val dark: Boolean = false,
    /** Glass (true) or the solid fallback. */
    val glass: Boolean = true,
    /** Font scale override (1.0 / 1.35 / 2.0); null keeps the system font scale. */
    val fontScale: Float? = null,
    /** Render without colour. */
    val grayscale: Boolean = false,
    /** Reduced-motion variants. */
    val reducedMotion: Boolean = false,
    /** Metrics shown in metric lists (2..6). */
    val metricCount: Int = 6,
)
