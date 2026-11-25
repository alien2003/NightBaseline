package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/** Nights of the learning period. */
const val LEARNING_NIGHTS: Int = 7

/** One segment of [LearningProgress]. */
enum class LearningSegment {
    /** A counted night: filled. */
    Counted,

    /** A short night that did not count: half filled. */
    Skipped,

    /** Not yet: outline only. */
    Pending,
}

/**
 * Seven segments filling in plus the visible "n of 7 nights" label (`screens.md` 4). Counted, skipped (short night)
 * and pending segments differ by shape (filled, half filled, outline), not only colour. One TalkBack node with
 * progress semantics 0..7 (`a11y_learning_progress`).
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
        Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s2), modifier = Modifier.fillMaxWidth()) {
            segments.forEach { segment ->
                Canvas(Modifier.weight(1f).height(NbDimens.learningSegmentHeight)) {
                    val radius = CornerRadius(size.height / 2)
                    val stroke = NbDimens.bandEdgeStroke.toPx()
                    when (segment) {
                        LearningSegment.Counted -> {
                            drawRoundRect(colors.accent, cornerRadius = radius)
                        }

                        LearningSegment.Skipped -> {
                            drawRoundRect(colors.accent, cornerRadius = radius, style = Stroke(stroke))
                            clipRect(right = size.width / 2) { drawRoundRect(colors.accent, cornerRadius = radius) }
                        }

                        LearningSegment.Pending -> {
                            drawRoundRect(
                                colors.outline,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                cornerRadius = radius,
                                style = Stroke(stroke),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text.string(R.string.learning_progress, text.int(n)),
            style = NbTheme.typography.label,
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
    NbCard(modifier.fillMaxWidth()) {
        Text(
            text.nightTitle(night, source),
            style = NbTheme.typography.headline,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        LearningProgress(night.historyNights, skipped = if (lastNightShort) 1 else 0)
        Text(text.nightBody(night, source), style = NbTheme.typography.body, color = colors.textSecondary)
        if (lastNightShort) {
            Text(stringResource(R.string.state_learning_short_note), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        }
        NbTextButton(stringResource(R.string.action_how_it_works), onClick = onHowItWorks)
    }
}

/**
 * The shared pattern of every data problem (`screens.md` 4, `states.md` 2): decorative icon in its tonal container,
 * title as a heading, body, a primary action and an optional secondary text action. Info and neutral tones only:
 * there is no error colour.
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
    NbCard(modifier.fillMaxWidth()) {
        NightGlyphBadge(glyph, tone, container = 48.dp, glyphSize = NbDimens.icon)
        Text(title, style = NbTheme.typography.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        Text(body, style = NbTheme.typography.body, color = colors.textSecondary)
        if (primaryAction != null || secondaryAction != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
                verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                primaryAction?.let { PrimaryButton(it, onPrimary) }
                secondaryAction?.let { NbTextButton(it, onSecondary) }
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
 * A non-blocking banner above still-valid content (`screens.md` 4): solid `shape.m`, outline icon, text that wraps at
 * large font, an action and, where the state allows it, a dismiss button. Announced once as a polite live region.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineBanner(
    kind: BannerKind,
    source: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val dismissible = onDismiss != null && (kind == BannerKind.NotificationsOff || kind == BannerKind.RangeReady)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = NbDimens.textMax)
                .solidCard(NbShapes.m)
                .padding(start = NbSpace.s5, top = NbSpace.s3, bottom = NbSpace.s3, end = NbSpace.s2)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Box(Modifier.padding(top = NbSpace.s4)) {
            val icon = kind.icon()
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.statusInfo, modifier = Modifier.size(NbDimens.icon))
            } else {
                NightGlyphIcon(NightGlyph.DottedRing, colors.statusWithin)
            }
        }
        Column(Modifier.weight(1f).padding(vertical = NbSpace.s4), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            Text(text.banner(kind, source), style = NbTheme.typography.bodySmall, color = colors.textPrimary)
            text.bannerAction(kind)?.let { label ->
                NbTextButton(label, onClick = onAction, modifier = Modifier.padding(top = NbSpace.s1))
            }
        }
        if (dismissible) {
            NbIconButton(
                NbIcons.Close,
                contentDescription = stringResource(R.string.a11y_dismiss),
                onClick = onDismiss,
                tint = colors.textSecondary,
            )
        }
    }
}

/** A small solid box inside a card (`material.solid.nested`, `shape.m`) for explanations. */
@Composable
fun NestedBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(NbTheme.colors.surface2, NbShapes.m)
            .padding(NbSpace.s4),
    ) { content() }
}
