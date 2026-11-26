package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Shape tokens of `tokens.md` section 5. Radii >= 16 dp use continuous corners ([SmoothCornerShape]). One content
 * radius (20 dp) for every card; shell surfaces are capsules; inner shapes are concentric with their container
 * ([concentric]).
 */
@Immutable
object NbShapes {
    /** `shape.xs` 4 dp. */
    val xs: Shape = RoundedCornerShape(4.dp)

    /** `shape.s` 8 dp: small icon containers. */
    val s: Shape = RoundedCornerShape(8.dp)

    /** `shape.m` 12 dp: small elements only. */
    val m: Shape = RoundedCornerShape(12.dp)

    /** `shape.popup` 16 dp, smoothing 0.6: chart tooltip, popups. */
    val popup: Shape = SmoothCornerShape(16.dp)

    /** `shape.l` 20 dp, smoothing 0.55: cards, tiles, chart card, settings groups, banners, state cards. */
    val l: Shape = SmoothCornerShape(20.dp, smoothing = CARD_SMOOTHING)

    /** `shape.xl` 28 dp, smoothing 0.6: onboarding illustration only. */
    val xl: Shape = SmoothCornerShape(28.dp)

    /** `shape.sheet` 32 dp, top corners only. */
    val sheet: Shape = SmoothCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

    /** `shape.dialog` 30 dp, smoothing 0.6. */
    val dialog: Shape = SmoothCornerShape(30.dp)

    /** `shape.pill`: exact semicircle ends (chips, switch, track, small controls). */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** `shape.smoothPill`: capsule with slightly smoothed ends, fuller than a circle (bar, rail, buttons). */
    val smoothPill: Shape = SmoothPillShape

    /** `shape.circle`. */
    val circle: Shape = CircleShape

    /** Radius of [l], for the nested-radius rule and the focus ring. */
    val lRadius: Dp = 20.dp

    /** Radius of [m]. */
    val mRadius: Dp = 12.dp

    /** Radius of [s]. */
    val sRadius: Dp = 8.dp

    /** Radius of [dialog]. */
    val dialogRadius: Dp = 30.dp

    /** Radius of [sheet]. */
    val sheetRadius: Dp = 32.dp

    /**
     * Concentric inner radius: [outer] minus the [inset] between the two edges, never below 12 dp
     * (`tokens.md` 5, nested radius rule), e.g. a button 16 dp inside a 30 dp dialog gets 14 dp.
     */
    fun concentric(
        outer: Dp,
        inset: Dp,
    ): Dp = maxOf(outer - inset, NbDimens.concentricMinRadius)

    private const val CARD_SMOOTHING = 0.55f
}

/**
 * A capsule whose ends are slightly smoothed (radius = h / 2 / 1.08, smoothing 0.08), which reads fuller near the
 * straight edge than an exact semicircle, as the measured ColorOS bar does.
 */
@Immutable
object SmoothPillShape : Shape {
    private const val RADIUS_DIVISOR = 1.08f
    private const val SMOOTHING = 0.08f

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = min(size.width, size.height) / 2f / RADIUS_DIVISOR
        val radius = with(density) { radiusPx.toDp() }
        return SmoothCornerShape(radius, SMOOTHING).createOutline(size, layoutDirection, density)
    }
}

/**
 * A rounded rectangle with continuous ("smoothed", squircle-like) corners: each corner is a circular arc blended
 * into the straight edges by two cubic curves, so the curvature changes gradually (the profile is fuller near the
 * edge than a plain circle). [smoothing] 0 gives an ordinary rounded corner. The outline is convex, so platform
 * shadows also work on API 28-29. The last path is cached per size.
 */
@Immutable
class SmoothCornerShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = DEFAULT_SMOOTHING,
) : Shape {
    /** All four corners with [radius]. */
    constructor(radius: Dp, smoothing: Float = DEFAULT_SMOOTHING) : this(radius, radius, radius, radius, smoothing)

    private var cacheKey: Triple<Size, LayoutDirection, Float>? = null
    private var cachedPath: Path? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val key = Triple(size, layoutDirection, density.density)
        val cached = cachedPath
        if (cached != null && key == cacheKey) return Outline.Generic(cached)
        val ltr = layoutDirection == LayoutDirection.Ltr
        val (tl, tr) = with(density) { if (ltr) topStart.toPx() to topEnd.toPx() else topEnd.toPx() to topStart.toPx() }
        val (br, bl) = with(density) { if (ltr) bottomEnd.toPx() to bottomStart.toPx() else bottomStart.toPx() to bottomEnd.toPx() }
        val path = buildPath(size, tl, tr, br, bl)
        cacheKey = key
        cachedPath = path
        return Outline.Generic(path)
    }

    private fun buildPath(
        size: Size,
        tl: Float,
        tr: Float,
        br: Float,
        bl: Float,
    ): Path {
        val w = size.width
        val h = size.height
        val max = min(w, h) / 2f
        // Clockwise: top-right, bottom-right, bottom-left, top-left. For each corner, A points back along the edge
        // the path arrives on and B along the edge it leaves on.
        val corners =
            listOf(
                Corner(Offset(w, 0f), Offset(-1f, 0f), Offset(0f, 1f), CornerGeometry.of(tr, smoothing, max)),
                Corner(Offset(w, h), Offset(0f, -1f), Offset(-1f, 0f), CornerGeometry.of(br, smoothing, max)),
                Corner(Offset(0f, h), Offset(1f, 0f), Offset(0f, -1f), CornerGeometry.of(bl, smoothing, max)),
                Corner(Offset(0f, 0f), Offset(0f, 1f), Offset(1f, 0f), CornerGeometry.of(tl, smoothing, max)),
            )
        return Path().apply {
            val start = corners.last().point(0f, corners.last().geometry.p)
            moveTo(start.x, start.y)
            corners.forEach { addCorner(it) }
            close()
        }
    }

    private fun Path.addCorner(corner: Corner) {
        val g = corner.geometry
        if (g.r <= 0f) {
            lineTo(corner.origin.x, corner.origin.y)
            return
        }

        fun to(
            u: Float,
            v: Float,
        ) = corner.point(u, v)
        val s = to(g.p, 0f)
        lineTo(s.x, s.y)
        val p0 = Offset(g.p - g.a - g.b - g.c, g.d)
        val p3 = Offset(g.d, g.p - g.a - g.b - g.c)
        cubic(to(g.p - g.a, 0f), to(g.p - g.a - g.b, 0f), to(p0.x, p0.y))
        // The circular middle section as one cubic (k = 4/3 tan(theta / 4) r), computed in the corner's frame.
        val center = Offset(g.r, g.r)
        val k = 4f / 3f * tan(g.arcRadians / 4f) * g.r
        val t0 = tangent(p0 - center, p3 - p0)
        val t3 = tangent(p3 - center, p3 - p0)
        val c1 = p0 + t0 * k
        val c2 = p3 - t3 * k
        cubic(to(c1.x, c1.y), to(c2.x, c2.y), to(p3.x, p3.y))
        cubic(to(0f, g.p - g.a - g.b), to(0f, g.p - g.a), to(0f, g.p))
    }

    private fun Path.cubic(
        c1: Offset,
        c2: Offset,
        end: Offset,
    ) = cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)

    /** Unit tangent of the circle at [radial], pointing in the direction of [travel]. */
    private fun tangent(
        radial: Offset,
        travel: Offset,
    ): Offset {
        val len = radial.getDistance().takeIf { it > 0f } ?: return Offset.Zero
        val t = Offset(-radial.y / len, radial.x / len)
        return if (t.x * travel.x + t.y * travel.y < 0f) -t else t
    }

    override fun equals(other: Any?): Boolean =
        other is SmoothCornerShape &&
            other.topStart == topStart &&
            other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd &&
            other.bottomStart == bottomStart &&
            other.smoothing == smoothing

    override fun hashCode(): Int = listOf(topStart, topEnd, bottomEnd, bottomStart, smoothing).hashCode()

    private class Corner(
        val origin: Offset,
        val a: Offset,
        val b: Offset,
        val geometry: CornerGeometry,
    ) {
        fun point(
            u: Float,
            v: Float,
        ): Offset = Offset(origin.x + a.x * u + b.x * v, origin.y + a.y * u + b.y * v)
    }

    /** Distances of one smoothed corner (the construction of Figma's corner smoothing). */
    private class CornerGeometry(
        val r: Float,
        val p: Float,
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val arcRadians: Float,
    ) {
        companion object {
            fun of(
                radius: Float,
                smoothing: Float,
                maxExtent: Float,
            ): CornerGeometry {
                val r = min(radius, maxExtent)
                if (r <= 0f) return CornerGeometry(0f, 0f, 0f, 0f, 0f, 0f, 0f)
                val p = min((1f + smoothing) * r, maxExtent)
                val s = (p / r - 1f).coerceIn(0f, smoothing)
                val arcMeasure = QUARTER_DEGREES * (1f - s)
                val arcSection = sin(rad(arcMeasure / 2f)) * r * sqrt(2f)
                val alpha = (QUARTER_DEGREES - arcMeasure) / 2f
                val p3ToP4 = r * tan(rad(alpha / 2f))
                val beta = HALF_QUARTER_DEGREES * s
                val c = p3ToP4 * cos(rad(beta))
                val d = c * tan(rad(beta))
                val b = ((p - arcSection - c - d) / 3f).coerceAtLeast(0f)
                return CornerGeometry(r, p, 2f * b, b, c, d, rad(arcMeasure))
            }

            private fun rad(degrees: Float): Float = (degrees * Math.PI / HALF_TURN_DEGREES).toFloat()
        }
    }

    private companion object {
        const val DEFAULT_SMOOTHING = 0.6f
        const val QUARTER_DEGREES = 90f
        const val HALF_QUARTER_DEGREES = 45f
        const val HALF_TURN_DEGREES = 180.0
    }
}
