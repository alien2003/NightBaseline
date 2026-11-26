package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/** Disabled buttons keep their fill and label, at this alpha (`coloros-conformance.md` L4). */
private const val DISABLED_ALPHA = 0.3f

/** The gloss band covers the top part of the capsule and fades out by this fraction of its height (above the label). */
private const val GLOSS_END = 0.4f

/** Touch light on an accent fill (white, `coloros-conformance.md` M2). */
private const val TOUCH_LIGHT_ALPHA = 0.20f

/** Touch light radius relative to the button height. */
private const val TOUCH_LIGHT_RADIUS = 2.2f

/**
 * Primary capsule button (`coloros-conformance.md` L4, `coloros17-glass.md` 4.2): an accent gradient fill
 * (`primaryFill`, darker toward the bottom so the white 16/500 label only gains contrast, >= 4.5:1 everywhere), a soft
 * accent glow below, a faint top gloss band, and a white touch light that follows the finger (110 ms in, 480 ms out;
 * none under reduced motion). At least 50 dp tall, padding 28 / 12, never wider than 360 dp; the label wraps at large
 * font. Disabled keeps the same fill and label at alpha 0.3 (no glow).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    forced: ForcedInteraction? = null,
) {
    val controls = NbTheme.controls
    val source = remember { MutableInteractionSource() }
    val light = touchLight(source, forced == ForcedInteraction.Pressed)
    val shape = NbShapes.pill
    Box(
        modifier =
            modifier
                .widthIn(max = NbDimens.buttonMaxWidth)
                .sizeIn(minHeight = NbControlDimens.buttonHeight, minWidth = NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = shape, enabled = enabled, forced = forced, interactionSource = source)
                .then(
                    if (enabled) {
                        Modifier.dropShadow(
                            shape,
                            Shadow(12.dp, controls.accentGlow, offset = DpOffset(0.dp, 4.dp)),
                        )
                    } else {
                        Modifier
                    },
                ).alpha(if (enabled) 1f else DISABLED_ALPHA)
                .background(Brush.verticalGradient(listOf(controls.fillTop, controls.fillBottom)), shape)
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(
                        outline,
                        Brush.verticalGradient(
                            0f to controls.gloss,
                            GLOSS_END to Color.Transparent,
                            endY = size.height,
                        ),
                    )
                    val glow = light.alpha
                    if (glow > 0f) {
                        val center = light.center ?: Offset(size.width / 2, size.height / 2)
                        drawOutline(
                            outline,
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = TOUCH_LIGHT_ALPHA), Color.Transparent),
                                center = center,
                                radius = size.height * TOUCH_LIGHT_RADIUS,
                            ),
                            alpha = glow,
                        )
                    }
                }.padding(horizontal = NbControlDimens.buttonPaddingHorizontal, vertical = NbSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        NbText(text, style = NbControlType.button, color = controls.onFill, textAlign = TextAlign.Center)
    }
}

/** State of the finger-following light of one button. */
private class TouchLight {
    var center: Offset? by mutableStateOf(null)
    val animatable = Animatable(0f)
    var forced: Boolean = false
    val alpha: Float get() = if (forced) 1f else animatable.value
}

@Composable
private fun touchLight(
    source: MutableInteractionSource,
    forced: Boolean,
): TouchLight {
    val motion = NbTheme.motion
    val light = remember { TouchLight() }
    light.forced = forced
    LaunchedEffect(source, motion) {
        // Tracked per press, and the latest change wins: a release never waits behind the light's fade-in, and the
        // end of one press does not put out the light of another finger.
        val active = mutableSetOf<PressInteraction.Press>()
        source.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    active += interaction
                    light.center = interaction.pressPosition
                }

                is PressInteraction.Release -> {
                    active -= interaction.press
                }

                is PressInteraction.Cancel -> {
                    active -= interaction.press
                }
            }
            if (active.isEmpty()) {
                light.animatable.animateTo(0f, motion.glowOut())
            } else {
                motion.glowIn<Float>()?.let { light.animatable.animateTo(1f, it) }
            }
        }
    }
    return light
}

/**
 * Secondary capsule button (`coloros-conformance.md` L5): a neutral fill (white @ 10 % dark, black @ 6 % light), a
 * `text.primary` 16/500 label, 50 dp tall. No tonal accent container.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    forced: ForcedInteraction? = null,
) {
    val controls = NbTheme.controls
    Box(
        modifier =
            modifier
                .widthIn(max = NbDimens.buttonMaxWidth)
                .sizeIn(minHeight = NbControlDimens.buttonHeight, minWidth = NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.pill, enabled = enabled, forced = forced)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .background(controls.secondaryFill, NbShapes.pill)
                .padding(horizontal = NbControlDimens.buttonPaddingHorizontal, vertical = NbSpace.s4),
        contentAlignment = Alignment.Center,
    ) {
        NbText(text, style = NbControlType.button, color = controls.title, textAlign = TextAlign.Center)
    }
}

/**
 * Text button (`coloros-conformance.md` L5): a `primaryText` 16/22/500 label, padding 12 / 8, 48 dp target. [color]
 * overrides it on glass.
 */
@Composable
fun NbTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NbTheme.controls.accentText,
    forced: ForcedInteraction? = null,
) {
    Box(
        modifier =
            modifier
                .sizeIn(minHeight = NbDimens.targetMin, minWidth = NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.pill, forced = forced)
                .padding(horizontal = NbSpace.s4, vertical = NbSpace.s3),
        contentAlignment = Alignment.Center,
    ) {
        NbText(text, style = NbControlType.button, color = color, textAlign = TextAlign.Center)
    }
}

/** Icon-only 48 dp button with a required [contentDescription] (no unlabeled icon controls). */
@Composable
fun NbIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NbTheme.colors.textPrimary,
    onGlass: Boolean = false,
    forced: ForcedInteraction? = null,
) {
    Box(
        modifier =
            modifier
                .size(NbDimens.targetMin)
                .nbClickable(onClick = onClick, shape = NbShapes.circle, onGlass = onGlass, forced = forced)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        NbIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(NbDimens.icon))
    }
}
