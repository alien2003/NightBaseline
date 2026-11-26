package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType
import io.github.alien2003.nightbaseline.ui.theme.hairline

/** Nights of the learning period. */
const val LEARNING_NIGHTS: Int = 7

/** One segment of [LearningProgress]. */
enum class LearningSegment {
    /** A counted night: filled in the accent. */
    Counted,

    /** A short night that did not count: half filled. */
    Skipped,

    /** Not yet: empty neutral track. */
    Pending,
}

/**
 * Seven 8 dp segments filling in plus the visible "n of 7 nights" label (`screens.md` 4, conformance K8): counted
 * nights in the accent, pending nights as empty neutral track, a skipped (short) night half filled. The visible count
 * carries the meaning; the segments repeat it. One TalkBack node with progress semantics 0..7
 * (`a11y_learning_progress`).
 */
@Composable
fun LearningProgress(
    counted: Int,
    modifier: Modifier = Modifier,
    skipped: Int = 0,
) {
    val colors = NbTheme.colors
    val text = rememberUiText()
    val n = counted.coerceIn(0, LEARNING_NIGHTS)
    val segments =
        List(LEARNING_NIGHTS) { i ->
            when {
                i < n -> LearningSegment.Counted
                i < n + skipped -> LearningSegment.Skipped
                else -> LearningSegment.Pending
            }
        }
    val description = text.string(R.string.a11y_learning_progress, text.int(n))
    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(n.toFloat(), 0f..LEARNING_NIGHTS.toFloat(), LEARNING_NIGHTS)
            },
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        val pending = colors.data.progressPending
        Row(horizontalArrangement = Arrangement.spacedBy(NbDataDimens.progressGap), modifier = Modifier.fillMaxWidth()) {
            segments.forEach { segment ->
                Canvas(Modifier.weight(1f).height(NbDataDimens.progressSegment)) {
                    val radius = CornerRadius(size.height / 2)
                    when (segment) {
                        LearningSegment.Counted -> {
                            drawRoundRect(colors.primary, cornerRadius = radius)
                        }

                        LearningSegment.Skipped -> {
                            drawRoundRect(pending, cornerRadius = radius)
                            clipRect(right = size.width / 2) { drawRoundRect(colors.primary, cornerRadius = radius) }
                        }

                        LearningSegment.Pending -> {
                            drawRoundRect(pending, cornerRadius = radius)
                        }
                    }
                }
            }
        }
        NbText(
            text.string(R.string.learning_progress, text.int(n)),
            style = NbTheme.dataType.statusCalm,
            color = colors.textSecondary,
        )
    }
}

/**
 * The learning card that replaces the night summary while learning (`screens.md` 7.6): title, [LearningProgress],
 * body ("n more nights...") and the "How it works" link.
 */
@Composable
fun LearningCard(
    night: NightUi,
    source: String,
    onHowItWorks: () -> Unit,
    modifier: Modifier = Modifier,
    lastNightShort: Boolean = false,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val type = NbTheme.dataType
    NbCard(modifier.fillMaxWidth()) {
        NbText(
            text.nightTitle(night, source),
            style = type.headline,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        LearningProgress(night.historyNights, skipped = if (lastNightShort) 1 else 0)
        NbText(text.nightBody(night, source), style = type.summary, color = colors.textSecondary)
        if (lastNightShort) {
            NbText(stringResource(R.string.state_learning_short_note), style = type.summary, color = colors.textSecondary)
        }
        // Pulled back by the button's inner padding so its label lines up with the text above.
        NbTextButton(
            stringResource(R.string.action_how_it_works),
            onClick = onHowItWorks,
            color = colors.data.primaryText,
            modifier = Modifier.offset(x = -NbSpace.s4),
        )
    }
}

/**
 * The shared pattern of every data problem (`screens.md` 4, `states.md` 2, conformance K9): a 24 dp decorative icon
 * without a container, the title (20/26 medium) as a heading, the body, a primary action and an optional secondary
 * text action. Info and neutral tones only: there is no error colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StateCard(
    glyph: NightGlyph,
    tone: StatusTone,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryAction: String? = null,
    onPrimary: () -> Unit = {},
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    val colors = NbTheme.colors
    val type = NbTheme.dataType
    NbCard(modifier.fillMaxWidth()) {
        NightGlyphBadge(glyph, tone, container = NbDataDimens.stateIcon, glyphSize = NbDataDimens.stateIcon)
        Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            NbText(title, style = type.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            NbText(body, style = type.summary, color = colors.textSecondary)
        }
        if (primaryAction != null || secondaryAction != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
                verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                primaryAction?.let { PrimaryButton(it, onPrimary) }
                secondaryAction?.let { NbTextButton(it, onSecondary, color = colors.data.primaryText) }
            }
        }
    }
}

/** [StateCard] for a night in a problem or learning state, with the copy of `states.md` 2. */
@Composable
fun NightStateCard(
    night: NightUi,
    source: String,
    modifier: Modifier = Modifier,
    onPrimary: () -> Unit = {},
    onSecondary: () -> Unit = {},
) {
    val text = rememberUiText()
    StateCard(
        glyph = night.state.glyph,
        tone = night.state.tone,
        title = text.nightTitle(night, source),
        body = text.nightBody(night, source),
        modifier = modifier,
        primaryAction = text.nightAction(night, source),
        onPrimary = onPrimary,
        secondaryAction = text.nightSecondaryAction(night),
        onSecondary = onSecondary,
    )
}

/** Text of a banner (`states.md` 3). */
fun UiText.banner(
    kind: BannerKind,
    source: String,
    quietDays: Int = 3,
): String =
    when (kind) {
        BannerKind.Wait -> string(R.string.banner_wait, source)
        BannerKind.Background -> string(R.string.banner_bg)
        BannerKind.ReadFailed -> string(R.string.banner_read_failed)
        BannerKind.SourceQuiet -> plural(R.plurals.banner_source_quiet, quietDays, source, int(quietDays))
        BannerKind.NotificationsOff -> string(R.string.banner_notif_off)
        BannerKind.RangeReady -> string(R.string.banner_range_ready)
    }

/** Action label of a banner, or null (range ready only has dismiss). */
fun UiText.bannerAction(kind: BannerKind): String? =
    when (kind) {
        BannerKind.Wait, BannerKind.ReadFailed -> string(R.string.action_check_now)
        BannerKind.Background -> string(R.string.action_allow)
        BannerKind.SourceQuiet -> string(R.string.action_choose_source)
        BannerKind.NotificationsOff -> string(R.string.action_turn_on)
        BannerKind.RangeReady -> null
    }

private fun BannerKind.icon(): ImageVector? =
    when (this) {
        BannerKind.Wait -> NbIcons.Moon
        BannerKind.Background -> NbIcons.Key
        BannerKind.ReadFailed -> NbIcons.CloudClock
        BannerKind.SourceQuiet -> NbIcons.Cloud
        BannerKind.NotificationsOff -> NbIcons.BellOff
        BannerKind.RangeReady -> null
    }

/**
 * A non-blocking banner above still-valid content (`screens.md` 4, conformance K9): a solid card with the one content
 * radius (`shape.l`), a 24 dp outline icon in `primaryText`, 14 sp text in `text.primary` that wraps at large font, a
 * `primaryText` action and, where the state allows it, a dismiss button. Announced once as a polite live region.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineBanner(
    kind: BannerKind,
    source: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    quietDays: Int = 3,
    maxWidth: Dp = NbDimens.textMax,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val dismissible = onDismiss != null && (kind == BannerKind.NotificationsOff || kind == BannerKind.RangeReady)
    val action = text.bannerAction(kind)
    Row(
        modifier =
            Modifier
                .widthIn(max = maxWidth)
                .then(modifier)
                .fillMaxWidth()
                .cardTag()
                .solidCard(NbShapes.l)
                .padding(
                    start = NbSpace.s5,
                    top = NbSpace.s4,
                    bottom = if (action == null) NbSpace.s4 else NbSpace.s0,
                    end = if (dismissible) NbSpace.s0 else NbSpace.s5,
                ).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Box(Modifier.padding(top = NbSpace.s1)) {
            val icon = kind.icon()
            if (icon != null) {
                NbIcon(icon, contentDescription = null, tint = colors.data.primaryText, modifier = Modifier.size(NbDataDimens.stateIcon))
            } else {
                NightGlyphIcon(NightGlyph.DottedRing, colors.statusWithin)
            }
        }
        Column(Modifier.weight(1f).padding(top = NbSpace.s1)) {
            NbText(text.banner(kind, source, quietDays), style = NbTheme.dataType.summary, color = colors.textPrimary)
            action?.let { label ->
                // Pulled back by the button's inner padding so the action lines up with the text.
                NbTextButton(label, onClick = onAction, color = colors.data.primaryText, modifier = Modifier.offset(x = -NbSpace.s4))
            }
        }
        if (dismissible) {
            NbIconButton(
                NbIcons.Close,
                contentDescription = stringResource(R.string.a11y_dismiss),
                onClick = onDismiss,
                tint = colors.textSecondary,
                modifier = Modifier.offset(y = -NbSpace.s4),
            )
        }
    }
}

/**
 * A separate part of a card for explanations (conformance K11): ColorOS has no tinted boxes inside cards, so the part
 * is split from what is above it by a hairline in `separator` and 12 dp of space.
 */
@Composable
fun NestedBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val line = hairline
    val color = NbTheme.colors.separator
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val y = line.toPx() / 2
                drawLine(color, Offset(0f, y), Offset(size.width, y), line.toPx())
            }.padding(top = NbSpace.s4),
    ) { content() }
}
