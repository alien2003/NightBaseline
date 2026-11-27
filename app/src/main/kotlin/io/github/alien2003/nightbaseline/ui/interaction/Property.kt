package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * A property motion (ADR 0011): one value of a control (a selection edge, a knob, a band marker) following [target]
 * on one spring of [NbPhysics]; a snap under reduced motion.
 */
@Composable
fun animateNbFloat(
    target: Float,
    spring: NbSpring,
    label: String = "value",
): State<Float> = animateFloatAsState(target, LocalNbMotion.current.of(spring), label = label)

/** The two states of a reveal in [NbFrame]s. */
private const val REVEAL_SHOWN = "shown"
private const val REVEAL_HIDDEN = "hidden"

/**
 * An element that appears and disappears in place (ADR 0011 kind Reveal, relation InPlace): shown while [value] is
 * non-null, with the last value kept while it leaves. p runs on the hosts' driver ([NbDriver], so the R7 clock and
 * the Nav spring of every other kind; review V3: it ran on its own `Animatable`, outside R7 and the frame probe):
 * alpha p, height share p, rising [NbPhysics.REVEAL_RISE_DP] into place. Shown at rest on the first composition (a
 * screen opens with its banner in place); nothing is composed once it is gone. Every moving frame is reported to
 * [NbMotionProbe.frames] (host "reveal").
 */
@Composable
fun <T : Any> NbReveal(
    value: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduced = LocalNbMotion.current.reduced
    val reducedNow by rememberUpdatedState(reduced)
    val driver = remember { NbDriver(NbPhysics.Nav, if (value != null) 1f else 0f) { reducedNow } }
    val retained = remember { RetainedReveal<T>() }
    if (value != null) retained.value = value
    val wanted = value != null
    val asked = remember { RevealRequest() }
    if (wanted != asked.wanted) {
        asked.wanted = wanted
        asked.at = NbClock.nowNanos()
    }
    LaunchedEffect(wanted, reduced) {
        val target = if (wanted) 1f else 0f
        val v = driver.v
        driver.run(target, v, if (v != 0f) driver.continueOrigin() else asked.at)
        if (target == 0f) retained.value = null
    }
    val present by remember { derivedStateOf { driver.p > 0f } }
    val shown = value ?: retained.value
    if ((value == null && !present) || shown == null) return
    val rise = with(LocalDensity.current) { NbPhysics.REVEAL_RISE_DP * density }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val share = driver.p.coerceIn(0f, 1f)
                layout(placeable.width, (placeable.height * share).roundToInt()) { placeable.place(0, 0) }
            }.graphicsLayer {
                val p = driver.p
                val pose = NbPoses.reveal(p, rise)
                if (p > 0f && p < 1f) {
                    NbMotionProbe.frames?.invoke(
                        NbFrame(
                            "reveal",
                            NbKind.Reveal,
                            NbPhysics.Nav,
                            p,
                            REVEAL_SHOWN,
                            REVEAL_HIDDEN,
                            pose,
                            NbLayerPose.Rest,
                            false,
                            size.width,
                        ),
                    )
                }
                alpha = pose.alpha
                translationY = pose.ty
            },
    ) { content(shown) }
}

/** The last non-null value of a reveal, kept while it leaves (snapshot state: a discarded composition rolls it back). */
private class RetainedReveal<T : Any> {
    var value: T? by mutableStateOf(null)
}

/** The request of a reveal: whether it is wanted and when that was asked (its R7 origin). */
private class RevealRequest {
    var wanted: Boolean? by mutableStateOf(null)
    var at: Long? by mutableStateOf(null)
}
