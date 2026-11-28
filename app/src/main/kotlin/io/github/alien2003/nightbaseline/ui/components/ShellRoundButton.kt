package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.alien2003.nightbaseline.ui.interaction.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbBack
import io.github.alien2003.nightbaseline.ui.interaction.rememberShellPressState
import io.github.alien2003.nightbaseline.ui.interaction.shellClickable
import io.github.alien2003.nightbaseline.ui.interaction.shellPressGlow
import io.github.alien2003.nightbaseline.ui.interaction.shellPressInput
import io.github.alien2003.nightbaseline.ui.interaction.shellPressScale
import io.github.alien2003.nightbaseline.ui.material.convexSurface
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens

/**
 * The round shell button of the top bar, sheets and dialogs (`coloros-conformance.md` C1, measured on the ColorOS
 * back and "more" buttons): a solid convex 40 dp circle (vertical gradient, thin top rim, brighter bottom rim, soft
 * shadow; never blurred) with a 20 dp glyph, centred in a 48 dp touch target. Press: shrink and touch-following glow.
 * The caller places the target so the visible circle sits 16 dp from the screen edge (target edge at 12 dp).
 *
 * A Back button (the [NbIcons.ArrowBack] glyph) always goes through the app's router first ([rememberNbBack], ADR 0011
 * N7): one event into the deepest host's navigation state machine, the same as Esc, 3-button Back and the gesture;
 * [onClick] runs only when no host takes it.
 */
@Composable
fun ShellRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forced: ForcedInteraction? = null,
) {
    val back = rememberNbBack(onClick)
    val click = if (icon == NbIcons.ArrowBack) back else onClick
    val inset = (NbDimens.targetMin - ShellDimens.roundButton) / 2
    val press = rememberShellPressState()
    Box(
        modifier
            .size(NbDimens.targetMin)
            .shellPressInput(press)
            .shellClickable(
                onClick = click,
                shape = NbShapes.circle,
                visualInset = inset,
                forced = forced,
                interactionSource = press.source,
            ).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ShellDimens.roundButton)
                .shellPressScale(press)
                .convexSurface(NbShapes.circle)
                .shellPressGlow(press, NbShapes.circle, inputOffset = inset, forcedPressed = forced == ForcedInteraction.Pressed),
            contentAlignment = Alignment.Center,
        ) {
            NbIcon(
                icon,
                contentDescription = null,
                tint = ShellTokens.colors.convexGlyph,
                modifier = Modifier.size(ShellDimens.roundButtonGlyph),
            )
        }
    }
}
