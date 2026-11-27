package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * An element that appears and disappears in place (ADR 0011 kind Reveal, relation InPlace): shown while [value] is
 * non-null, with the last value kept while it leaves. p runs on the Nav spring: alpha p, height share p, rising
 * [NbPhysics.REVEAL_RISE_DP] into place. Shown at rest on the first composition (a screen opens with its banner in
 * place); nothing is composed once it is gone.
 */
@Composable
fun <T : Any> NbReveal(
    value: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduced = LocalNbMotion.current.reduced
    val p = remember { Animatable(if (value != null) 1f else 0f, NbPhysics.P_EPS) }
    val retained = remember { arrayOfNulls<Any>(1) }
    if (value != null) retained[0] = value
    LaunchedEffect(value != null, reduced) {
        val target = if (value != null) 1f else 0f
        if (reduced) p.snapTo(target) else p.animateTo(target, NbPhysics.Nav.spec())
        if (target == 0f) retained[0] = null
    }
    val present by remember { derivedStateOf { p.value > 0f } }

    @Suppress("UNCHECKED_CAST")
    val shown = (value ?: retained[0]) as T?
    if ((value == null && !present) || shown == null) return
    val rise = with(LocalDensity.current) { NbPhysics.REVEAL_RISE_DP * density }
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val share = p.value.coerceIn(0f, 1f)
                layout(placeable.width, (placeable.height * share).roundToInt()) { placeable.place(0, 0) }
            }.graphicsLayer {
                val pose = NbPoses.reveal(p.value, rise)
                alpha = pose.alpha
                translationY = pose.ty
            },
    ) { content(shown) }
}
