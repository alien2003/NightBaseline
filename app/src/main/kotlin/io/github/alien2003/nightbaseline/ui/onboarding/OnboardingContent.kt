package io.github.alien2003.nightbaseline.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIcon
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.components.triangle
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.ui.settings.SourceOptionRow
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls

/** The headline of a step in its current state. */
@Composable
internal fun stepTitle(state: OnboardingState): String {
    val text = rememberUiText()
    return when (state.step) {
        OnboardingStep.Welcome -> {
            stringResource(R.string.onb_welcome_title)
        }

        OnboardingStep.HowItWorks -> {
            stringResource(R.string.onb_how_title)
        }

        OnboardingStep.Source -> {
            stringResource(sourceTitle(state))
        }

        OnboardingStep.Permissions -> {
            stringResource(if (state.permission.denied) R.string.onb_perm_denied_title else R.string.onb_perm_title)
        }

        OnboardingStep.Notifications -> {
            stringResource(R.string.onb_notif_title)
        }

        OnboardingStep.Ready -> {
            if (state.rangeReady) {
                text.plural(R.plurals.onb_ready_history_title, state.pastNights, text.int(state.pastNights))
            } else {
                stringResource(R.string.onb_ready_learn_title)
            }
        }
    }
}

private val PermissionStatus.denied: Boolean get() = this == PermissionStatus.Denied || this == PermissionStatus.DeniedAgain

private fun sourceTitle(state: OnboardingState): Int =
    when (state.healthConnect) {
        HealthConnectStatus.NeedsUpdate -> R.string.state_hc_update_title
        HealthConnectStatus.Unavailable -> R.string.state_hc_none_title
        HealthConnectStatus.Available -> if (state.sources.isEmpty()) R.string.onb_nosource_title else R.string.onb_source_title
        HealthConnectStatus.Checking -> R.string.onb_source_title
    }

/** The buttons of the current step and state (`states.md` 7.3). */
@Composable
internal fun stepButtons(
    state: OnboardingState,
    actions: OnboardingActions,
): StepButtons =
    when (state.step) {
        OnboardingStep.Welcome -> StepButtons(stringResource(R.string.onb_start), actions.onNext)
        OnboardingStep.HowItWorks -> StepButtons(stringResource(R.string.action_next), actions.onNext, back = true)
        OnboardingStep.Source -> sourceButtons(state, actions)
        OnboardingStep.Permissions -> permissionButtons(state, actions)
        OnboardingStep.Notifications -> notificationButtons(state, actions)
        OnboardingStep.Ready -> StepButtons(stringResource(R.string.action_done), actions.onDone)
    }

@Composable
private fun sourceButtons(
    state: OnboardingState,
    actions: OnboardingActions,
): StepButtons =
    when (state.healthConnect) {
        HealthConnectStatus.Checking -> {
            StepButtons(stringResource(R.string.action_next), actions.onUseSource, primaryEnabled = false, back = true)
        }

        HealthConnectStatus.NeedsUpdate -> {
            StepButtons(
                stringResource(R.string.action_open_store),
                actions.onInstallHealthConnect,
                secondary = stringResource(R.string.action_check_again),
                onSecondary = actions.onCheckAgain,
                back = true,
            )
        }

        HealthConnectStatus.Unavailable -> {
            StepButtons(stringResource(R.string.action_about), actions.onAbout, back = true)
        }

        HealthConnectStatus.Available -> {
            val selected = state.selected
            when {
                state.sources.isEmpty() -> {
                    StepButtons(
                        stringResource(R.string.action_check_again),
                        actions.onCheckAgain,
                        secondary = stringResource(R.string.action_open_hc),
                        onSecondary = actions.onOpenHealthConnect,
                        back = true,
                    )
                }

                selected != null -> {
                    StepButtons(stringResource(R.string.onb_use_source, selected.label), actions.onUseSource, back = true)
                }

                else -> {
                    StepButtons(stringResource(R.string.action_next), actions.onUseSource, primaryEnabled = false, back = true)
                }
            }
        }
    }

@Composable
private fun permissionButtons(
    state: OnboardingState,
    actions: OnboardingActions,
): StepButtons =
    when (state.permission) {
        PermissionStatus.NotRequested -> {
            StepButtons(stringResource(R.string.onb_perm_allow), actions.onRequestPermissions, back = true)
        }

        PermissionStatus.Denied -> {
            StepButtons(stringResource(R.string.action_try_again), actions.onRequestPermissions, back = true)
        }

        PermissionStatus.DeniedAgain -> {
            StepButtons(
                stringResource(R.string.action_open_hc),
                actions.onOpenHealthConnect,
                secondary = stringResource(R.string.action_check_again),
                onSecondary = actions.onCheckAgain,
                back = true,
            )
        }

        PermissionStatus.Partial, PermissionStatus.Granted -> {
            StepButtons(stringResource(R.string.action_next), actions.onNext, back = true)
        }
    }

@Composable
private fun notificationButtons(
    state: OnboardingState,
    actions: OnboardingActions,
): StepButtons =
    when (state.notifications) {
        NotificationAccess.NotAsked -> {
            StepButtons(
                stringResource(R.string.onb_notif_allow),
                actions.onAllowNotifications,
                secondary = stringResource(R.string.action_not_now),
                onSecondary = actions.onSkipNotifications,
                back = true,
            )
        }

        NotificationAccess.Allowed, NotificationAccess.Blocked -> {
            StepButtons(stringResource(R.string.action_next), actions.onNext, back = true)
        }
    }

/** The body of the current step: text, lists and notes below the title. */
@Composable
internal fun StepBody(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    when (state.step) {
        OnboardingStep.Welcome -> WelcomeBody()
        OnboardingStep.HowItWorks -> BodyText(stringResource(R.string.onb_how_body))
        OnboardingStep.Source -> SourceBody(state, actions)
        OnboardingStep.Permissions -> PermissionBody(state)
        OnboardingStep.Notifications -> NotificationBody(state)
        OnboardingStep.Ready -> ReadyBody(state)
    }
}

@Composable
private fun BodyText(text: String) {
    NbText(text, style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
}

@Composable
private fun WelcomeBody() {
    BodyText(stringResource(R.string.onb_welcome_body))
    NbCard(Modifier.fillMaxWidth()) {
        Point(NbIcons.Moon, stringResource(R.string.onb_point_daily))
        Point(NbIcons.History, stringResource(R.string.onb_point_own))
        Point(NbIcons.Check, stringResource(R.string.onb_point_local))
    }
    // The disclaimer is its own card (ColorOS has no tinted boxes nested inside cards).
    NbCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            NbText(
                stringResource(R.string.setup_about_disclaimer_title),
                style = NbControlType.itemTitle,
                color = NbTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            NbText(stringResource(R.string.disclaimer_full), style = NbControlType.summary, color = NbTheme.colors.textSecondary)
        }
    }
}

/** One line with a decorative leading icon. */
@Composable
private fun Point(
    icon: ImageVector,
    text: String,
    style: TextStyle = NbTheme.typography.body,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(NbSpace.s4), verticalAlignment = Alignment.CenterVertically) {
        NbIcon(icon, contentDescription = null, tint = NbTheme.controls.accent, modifier = Modifier.size(NbDimens.iconInline))
        NbText(text, style = style, color = NbTheme.colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SourceBody(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    when (state.healthConnect) {
        HealthConnectStatus.Checking -> {
            BodyText(stringResource(R.string.onb_source_body))
            // Static, no spinner: idle screens never animate (states.md LOADING).
            Note(NightGlyph.DashCircle, StatusTone.Neutral, stringResource(R.string.setup_hc_checking))
        }

        HealthConnectStatus.NeedsUpdate -> {
            BadgeLine(NightGlyph.Download, StatusTone.Info)
            BodyText(stringResource(R.string.state_hc_update_body))
        }

        HealthConnectStatus.Unavailable -> {
            BadgeLine(NightGlyph.SlashedCircle, StatusTone.Neutral)
            BodyText(stringResource(R.string.state_hc_none_body))
        }

        HealthConnectStatus.Available -> {
            if (state.sources.isEmpty()) {
                BadgeLine(NightGlyph.DashCircle, StatusTone.Neutral)
                BodyText(stringResource(R.string.onb_nosource_body))
            } else {
                BodyText(stringResource(R.string.onb_source_body))
                Column(Modifier.fillMaxWidth().solidCard(NbShapes.l).selectableGroup()) {
                    state.sources.forEachIndexed { i, source ->
                        SourceOptionRow(
                            source,
                            selected = source.packageName == state.selectedSource,
                            onClick = { actions.onSelectSource(source) },
                            showDivider = i > 0,
                        )
                    }
                }
            }
        }
    }
}

/** The state's glyph on its own (no tonal container), as on the matching state card. Decorative. */
@Composable
private fun BadgeLine(
    glyph: NightGlyph,
    tone: StatusTone,
) {
    NightGlyphIcon(
        glyph,
        NbTheme.colors.tone(
            tone,
        ),
        size = BADGE_GLYPH,
        background = NbTheme.colors.background,
        modifier =
            Modifier.clearAndSetSemantics {
            },
    )
}

/** A solid note: decorative glyph and text (partial access, notification outcome, Health Connect check). */
@Composable
private fun Note(
    glyph: NightGlyph,
    tone: StatusTone,
    text: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .solidCard(NbShapes.l)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = NbSpace.s5, vertical = NbSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NightGlyphIcon(glyph, NbTheme.colors.tone(tone), modifier = Modifier.clearAndSetSemantics {})
        NbText(text, style = NbTheme.typography.bodySmall, color = NbTheme.colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PermissionBody(state: OnboardingState) {
    when (state.permission) {
        PermissionStatus.Denied -> {
            BadgeLine(NightGlyph.Key, StatusTone.Info)
            BodyText(stringResource(R.string.onb_perm_denied_body))
        }

        PermissionStatus.DeniedAgain -> {
            BadgeLine(NightGlyph.Key, StatusTone.Info)
            BodyText(stringResource(R.string.onb_perm_denied_body))
            BodyText(stringResource(R.string.setup_perm_denied_again_body))
        }

        else -> {
            BodyText(stringResource(R.string.onb_perm_body))
        }
    }
    if (state.permission == PermissionStatus.Partial) {
        Note(NightGlyph.Key, StatusTone.Info, stringResource(R.string.onb_perm_partial))
    }
    NbCard(Modifier.fillMaxWidth()) {
        PermissionHeading(stringResource(R.string.setup_perm_core_title))
        Point(NbIcons.Check, stringResource(R.string.onb_perm_sleep), NbTheme.typography.bodySmall)
        Point(NbIcons.Check, stringResource(R.string.onb_perm_hr), NbTheme.typography.bodySmall)
        PermissionHeading(stringResource(R.string.setup_perm_optional_title))
        listOf(
            R.string.onb_perm_rr,
            R.string.onb_perm_spo2,
            R.string.onb_perm_hrv,
            R.string.onb_perm_skin,
            R.string.onb_perm_background,
            R.string.onb_perm_history,
        ).forEach { Point(NbIcons.Info, stringResource(it), NbTheme.typography.bodySmall) }
    }
}

@Composable
private fun PermissionHeading(text: String) {
    NbText(text, style = NbControlType.sectionHeader, color = NbTheme.colors.textSecondary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun NotificationBody(state: OnboardingState) {
    BodyText(stringResource(R.string.onb_notif_body))
    when (state.notifications) {
        NotificationAccess.Allowed -> Note(NightGlyph.CheckCircle, StatusTone.Within, stringResource(R.string.setup_notif_allowed))
        NotificationAccess.Blocked -> Note(NightGlyph.DashCircle, StatusTone.Neutral, stringResource(R.string.setup_notif_denied))
        NotificationAccess.NotAsked -> Unit
    }
}

@Composable
private fun ReadyBody(state: OnboardingState) {
    if (state.rangeReady) {
        BodyText(stringResource(R.string.onb_ready_history_body))
    } else {
        NbCard(Modifier.fillMaxWidth()) {
            // The progress already says "n of 7 nights"; the body explains what happens next instead of repeating it.
            LearningProgress(state.pastNights)
            NbText(
                stringResource(R.string.home_expect_body),
                style = NbTheme.typography.body,
                color = NbTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * Bottom actions (`coloros-conformance.md` L14, ColorOS large-button pattern): a full-width [PrimaryButton] (max
 * 360 dp, 50 dp), and below it, 8 dp apart, the secondary action as a centred text button. "Back" is the round button
 * next to the step indicator; only where nothing interactive may sit above the fold ([backHere], tabletop) is it a
 * text button here. Every target is >= 48 dp.
 */
@Composable
internal fun StepActionRow(
    buttons: StepButtons,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backHere: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        buttons.primary?.let { PrimaryButton(it, buttons.onPrimary, Modifier.fillMaxWidth(), enabled = buttons.primaryEnabled) }
        buttons.secondary?.let { NbTextButton(it, buttons.onSecondary) }
        if (backHere && buttons.back) NbTextButton(stringResource(R.string.setup_action_back), onClick = onBack)
    }
}

/**
 * "Step n of 6" (visible text, announced politely on change) and six decorative dots: 6 dp, the current one a
 * 16 x 6 dp accent pill, the others neutral. With [onBack] a 40 dp round back button leads the row.
 */
@Composable
internal fun StepIndicator(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
    dots: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val controls = NbTheme.controls
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
        if (onBack != null) {
            ShellRoundButton(NbIcons.ArrowBack, stringResource(R.string.setup_action_back), onBack, Modifier.offset(x = -NbSpace.s2))
        }
        Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
            NbText(
                text.string(R.string.onb_step, text.int(step.number)),
                style = NbControlType.label,
                color = colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (dots) {
                Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                    for (n in 1..OnboardingStep.COUNT) {
                        val current = n == step.number
                        Canvas(Modifier.width(if (current) DOT_CURRENT else DOT).height(DOT)) {
                            drawRoundRect(
                                if (current) controls.accent else controls.dotInactive,
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DOT = 6.dp
private val DOT_CURRENT = 16.dp
private val BADGE_GLYPH = 28.dp
