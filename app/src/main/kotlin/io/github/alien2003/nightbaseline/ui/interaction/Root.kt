package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

/**
 * The root of the interaction framework (ADR 0011), installed once by the theme: the motion configuration, the one
 * press indication, the one overscroll (none under reduced motion), and the back router with its single root
 * handler. Everything below animates, scrolls, presses and goes back through these.
 *
 * @param pressed the pressed highlight colour of the theme.
 */
@Composable
fun NbInteractionRoot(
    reduced: Boolean,
    pressed: Color,
    content: @Composable () -> Unit,
) {
    val config = remember(reduced) { NbMotionConfig(reduced) }
    val density = LocalDensity.current
    val indication = remember(pressed, reduced) { NbPressIndication(pressed, reduced) }
    val overscroll = remember(density, reduced) { if (reduced) null else NbOverscrollFactory(density) }
    val router = remember { NbBackRouter() }
    CompositionLocalProvider(
        LocalNbMotion provides config,
        LocalIndication provides indication,
        LocalOverscrollFactory provides overscroll,
        LocalNbBackRouter provides router,
    ) {
        NbBackRoot(router)
        content()
    }
}

/**
 * Layout changes are not navigation (ADR 0011 R6): a stack change made by [block] (a fold or unfold that re-projects
 * the pages) snaps instead of playing a push or pop, and any running back gesture is dropped.
 */
suspend fun nbReproject(
    router: NbBackRouter?,
    block: () -> Unit,
) {
    if (router == null) {
        block()
        return
    }
    router.abort()
    router.reprojectDepth++
    try {
        block()
        // Two frames: the composition that sees the change, and the changes its effects make (a pane selection that
        // follows the pop). User input cannot land in them: the fold itself aborted every gesture above.
        withFrameNanos { }
        withFrameNanos { }
    } finally {
        router.reprojectDepth--
    }
}
