package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.convexSurface
import io.github.alien2003.nightbaseline.ui.material.rememberShellPressState
import io.github.alien2003.nightbaseline.ui.material.shellClickable
import io.github.alien2003.nightbaseline.ui.material.shellPressGlow
import io.github.alien2003.nightbaseline.ui.material.shellPressInput
import io.github.alien2003.nightbaseline.ui.material.shellPressScale
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens

/**
 * The round shell button of the top bar, sheets and dialogs (`coloros-conformance.md` C1, measured on the ColorOS
 * back and "more" buttons): a solid convex 40 dp circle (vertical gradient, thin top rim, brighter bottom rim, soft
 * shadow; never blurred) with a 20 dp glyph, centred in a 48 dp touch target. Press: shrink and touch-following glow.
 * The caller places the target so the visible circle sits 16 dp from the screen edge (target edge at 12 dp).
 */
@Composable
fun ShellRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forced: ForcedInteraction? = null,
) {
    val inset = (NbDimens.targetMin - ShellDimens.roundButton) / 2
    val press = rememberShellPressState()
    Box(
        modifier
            .size(NbDimens.targetMin)
            .shellPressInput(press)
            .shellClickable(onClick = onClick, shape = NbShapes.circle, visualInset = inset, forced = forced)
            .semantics { this.contentDescription = contentDescription },
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
