package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.pow

/**
 * How two states of a host relate (ADR 0011). A host derives the relation from the state change itself (a stack
 * compares depths, a section host compares bar order, a switcher compares keys), never from the call site, so the same
 * change moves the same way whatever triggered it (tap, link, round button, bar, rail, keyboard, gesture).
 */
enum class NbRelation {
    /** One level deeper or shallower in a stack (a pushed page, a settings sub-page, an onboarding step). */
    Depth,

    /** Siblings with an order shown one at a time (the top-level sections in bar order). */
    Section,

    /** Neighbours in a sequence (nights, months). */
    Sequence,

    /** Unordered peers in a fixed place (two-pane selection, view toggle, filter result). */
    Peer,

    /** An overlay from the bottom edge. */
    SheetOverlay,

    /** A centred overlay. */
    DialogOverlay,

    /** A dialog docked at the bottom edge (phone widths). */
    DockedOverlay,

    /** An element that appears or disappears in place. */
    InPlace,
}

/**
 * The kinds of animated change (ADR 0011): each is one spring of [NbPhysics] and one pure pose function of the scalar
 * p. There is no "none" kind; a snap is how reduced motion renders any kind.
 */
enum class NbKind(
    /** The spring that drives p. */
    val spring: NbSpring,
) {
    /** Push / pop: the upper page slides over the full width, the lower one moves a third and dims. */
    Page(NbPhysics.Nav),

    /** A top-level section switch: the Page geometry with the higher bar index as the upper layer. */
    Tab(NbPhysics.Nav),

    /** Previous / next in a sequence: both pages slide by the full width. */
    Pager(NbPhysics.Nav),

    /** Fade-through of unordered peers in a fixed place. */
    Swap(NbPhysics.Nav),

    /** A bottom sheet. */
    Sheet(NbPhysics.Nav),

    /** A centred dialog. */
    Dialog(NbPhysics.Nav),

    /** A dialog docked at the bottom edge. */
    DockedDialog(NbPhysics.Nav),

    /** An element appearing or disappearing in place. */
    Reveal(NbPhysics.Nav),
}

/** The total function from a relation to its kind (ADR 0011 I1). */
fun kindOf(relation: NbRelation): NbKind =
    when (relation) {
        NbRelation.Depth -> NbKind.Page
        NbRelation.Section -> NbKind.Tab
        NbRelation.Sequence -> NbKind.Pager
        NbRelation.Peer -> NbKind.Swap
        NbRelation.SheetOverlay -> NbKind.Sheet
        NbRelation.DialogOverlay -> NbKind.Dialog
        NbRelation.DockedOverlay -> NbKind.DockedDialog
        NbRelation.InPlace -> NbKind.Reveal
    }

/**
 * What one layer draws: translation in px, scale, alpha, and the black dim over it (drawn as reduced alpha on the
 * dark theme's black window).
 */
@Immutable
data class NbLayerPose(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val dim: Float = 0f,
) {
    /** True when the layer draws exactly as without a transform. */
    val atRest: Boolean get() = tx == 0f && ty == 0f && scale == 1f && alpha == 1f && dim == 0f

    companion object {
        /** No transform. */
        val Rest: NbLayerPose = NbLayerPose()
    }
}

/** The poses of the two layers of a change: [to] is the state being reached, [from] the one being left. */
@Immutable
data class NbPose(
    val to: NbLayerPose,
    val from: NbLayerPose,
)

/** The pure pose functions of every kind (ADR 0011). p is 0 at the start state and 1 at the end state. */
object NbPoses {
    /**
     * The stack pair (Page, Tab) when a share [s] of the upper layer is shown: upper `tx = e W (1 - s^gamma)`, lower
     * `tx = -e W / 3 s` with the dim `0.3 s` (dark theme). Push is s = p, pop is s = 1 - p, on the same layers, so a
     * reversal is continuous by construction.
     *
     * @param e the side the upper layer comes from or leaves to: +1 the right edge, -1 the left edge.
     */
    fun stack(
        s: Float,
        e: Int,
        width: Float,
        dark: Boolean,
    ): Pair<NbLayerPose, NbLayerPose> {
        val shown = s.coerceIn(0f, 1f)
        val upper = NbLayerPose(tx = e * width * (1f - shown.pow(NbPhysics.PAGE_GAMMA)))
        val lower = NbLayerPose(tx = -e * width * NbPhysics.PARALLAX * shown, dim = if (dark) NbPhysics.DIM * shown else 0f)
        return upper to lower
    }

    /** Pager: the outgoing page `tx = -e p W`, the incoming `tx = e (1 - p) W`; e = +1 when moving forward. */
    fun pager(
        p: Float,
        e: Int,
        width: Float,
    ): NbPose {
        val q = p.coerceIn(0f, 1f)
        return NbPose(to = NbLayerPose(tx = e * (1f - q) * width), from = NbLayerPose(tx = -e * q * width))
    }

    /** Swap: fade-through, the outgoing fades out over the first half, the incoming fades in over the second. */
    fun swap(p: Float): NbPose {
        val q = p.coerceIn(0f, 1f)
        return NbPose(to = NbLayerPose(alpha = max(0f, 2f * q - 1f)), from = NbLayerPose(alpha = max(0f, 1f - 2f * q)))
    }

    /** A modal panel at p (0 hidden, 1 at rest) of [kind] Sheet, Dialog or DockedDialog, of height [height] px. */
    fun modal(
        kind: NbKind,
        p: Float,
        height: Float,
    ): NbLayerPose {
        val q = p.coerceIn(0f, 1f)
        return when (kind) {
            NbKind.Dialog -> {
                val scale = NbPhysics.DIALOG_START_SCALE + (1f - NbPhysics.DIALOG_START_SCALE) * q
                NbLayerPose(scale = scale, alpha = (q / NbPhysics.DIALOG_OPAQUE_AT).coerceAtMost(1f))
            }

            NbKind.DockedDialog -> {
                NbLayerPose(ty = (1f - q) * height, alpha = (q / NbPhysics.DIALOG_OPAQUE_AT).coerceAtMost(1f))
            }

            else -> {
                NbLayerPose(ty = (1f - q) * height)
            }
        }
    }

    /** Reveal: alpha p, rising [risePx] into place. The height share is applied by the host's layout. */
    fun reveal(
        p: Float,
        risePx: Float,
    ): NbLayerPose {
        val q = p.coerceIn(0f, 1f)
        return NbLayerPose(ty = (1f - q) * risePx, alpha = q)
    }

    /** The radio fill scale bump `1 + 0.08 sin(pi p)` on the Select spring. */
    fun radio(p: Float): Float = if (p <= 0f || p >= 1f) 1f else 1f + NbPhysics.RADIO_PULSE * kotlin.math.sin(Math.PI.toFloat() * p)
}

/** Maps a gesture's finger fraction to p for each kind (the "gesture -> p" column of ADR 0011). */
object NbGestureMap {
    /**
     * A back gesture on a stack pair that starts at [p0] (0 at rest; a gesture that catches a running pop starts where
     * it is): the upper page moves exactly with the finger from where it is, so its travel share is `u0 + g`
     * (`u = 1 - s^gamma`, s = 1 - p, g the finger's travel share), and its edge moves by exactly the finger's travel
     * (review: `u0 + (1 - u0) g` scaled the finger by `1 - u0` when a gesture caught a running pop).
     */
    fun stackPop(
        g: Float,
        p0: Float,
    ): Float {
        val u0 = 1f - (1f - p0.coerceIn(0f, 1f)).pow(NbPhysics.PAGE_GAMMA)
        val u = (u0 + g.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val s = (1f - u).pow(1f / NbPhysics.PAGE_GAMMA)
        return (1f - s).coerceIn(0f, 1f)
    }

    /** A back gesture on a modal that starts at [p0] of its hide: the panel follows the finger linearly. */
    fun linear(
        g: Float,
        p0: Float,
    ): Float = (p0 + (1f - p0) * g.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}
