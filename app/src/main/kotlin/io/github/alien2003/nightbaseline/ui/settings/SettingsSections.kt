package io.github.alien2003.nightbaseline.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.GroupCard
import io.github.alien2003.nightbaseline.ui.components.NbChoiceRow
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.SectionFooter
import io.github.alien2003.nightbaseline.ui.components.SectionHeader
import io.github.alien2003.nightbaseline.ui.components.SettingsGroup
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.components.SettingsTrailing
import io.github.alien2003.nightbaseline.ui.material.EffectsReason
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import io.github.alien2003.nightbaseline.ui.material.LocalEffects
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls

/**
 * Data source group: source (opens the picker), Health Connect permissions, last read, set up again. Without a usable
 * Health Connect the source row says so and the permissions row becomes the install / update action.
 */
@Composable
internal fun SourceGroup(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = true,
) {
    val missing = state.healthConnectMissingTitle
    GroupOrCard(if (title) stringResource(R.string.settings_group_source) else null) {
        SettingsRow(
            stringResource(R.string.settings_source),
            onClick = { actions.onOpenPage(SettingsPage.SourcePicker) },
            summary = missing?.let { stringResource(it) } ?: lastReadSummary(state),
            trailing = SettingsTrailing.Value(state.sourceLabel),
        )
        if (missing != null) {
            SettingsRow(
                stringResource(R.string.settings_hc_install),
                onClick = actions.onInstallHealthConnect,
                showDivider = true,
            )
        } else {
            SettingsRow(
                stringResource(R.string.settings_hc_permissions),
                onClick = actions.onOpenHealthConnect,
                showDivider = true,
            )
        }
        SettingsRow(
            stringResource(R.string.settings_setup_again),
            onClick = actions.onSetupAgain,
            showDivider = true,
        )
    }
}

@Composable
internal fun lastReadSummary(state: SettingsState): String =
    state.lastRead?.let { stringResource(R.string.settings_last_read, it) } ?: stringResource(R.string.settings_never_read)

/**
 * Notifications group: the system permission state (`ST-NOTIF-BLOCKED`: the switches are disabled and explain why;
 * the permission row opens system settings), "Night results" and "Problems reading data".
 */
@Composable
internal fun NotificationsGroup(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = true,
) {
    val blocked = state.notificationsBlocked
    GroupOrCard(if (title) stringResource(R.string.settings_group_notifications) else null) {
        SettingsRow(
            stringResource(R.string.setup_notif_permission),
            onClick =
                when (state.notificationAccess) {
                    NotificationAccess.NotAsked -> actions.onRequestNotifications
                    else -> actions.onOpenNotificationSettings
                },
            summary =
                when (state.notificationAccess) {
                    NotificationAccess.Allowed -> stringResource(R.string.setup_notif_perm_allowed)
                    NotificationAccess.NotAsked -> stringResource(R.string.setup_notif_perm_not_asked)
                    NotificationAccess.Blocked -> stringResource(R.string.setup_notif_perm_blocked)
                },
        )
        val blockedSummary = stringResource(R.string.settings_notif_blocked)
        SettingsRow(
            stringResource(R.string.settings_notif_results),
            onClick = { actions.onNotifyResults(!state.notifyResults) },
            summary = if (blocked) blockedSummary else stringResource(R.string.setup_notif_results_summary),
            trailing = SettingsTrailing.Toggle(state.notifyResults && !blocked),
            enabled = !blocked,
            showDivider = true,
        )
        SettingsRow(
            stringResource(R.string.settings_notif_problems),
            onClick = { actions.onNotifyProblems(!state.notifyProblems) },
            summary = if (blocked) blockedSummary else stringResource(R.string.setup_notif_problems_summary),
            trailing = SettingsTrailing.Toggle(state.notifyProblems && !blocked),
            enabled = !blocked,
            showDivider = true,
        )
    }
}

/** Sensitivity as one row with the preset name (Compact: opens `settings/sensitivity`). */
@Composable
internal fun SensitivityRowGroup(
    state: SettingsState,
    actions: SettingsActions,
) {
    SettingsGroup(stringResource(R.string.settings_group_sensitivity)) {
        SettingsRow(
            stringResource(state.sensitivity.title),
            onClick = { actions.onOpenPage(SettingsPage.Sensitivity) },
            summary = stringResource(state.sensitivity.description),
        )
    }
}

/**
 * The sensitivity presets as one grouped card of single-choice rows (`coloros-conformance.md` L8), then the preview
 * line (hidden while learning, `ST-SENS-LEARNING`). Used as the sub-screen (Compact), inline (Medium) and in the
 * detail pane.
 */
@Composable
internal fun SensitivityContent(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = false,
) {
    val text = rememberUiText()
    val controls = NbTheme.controls
    Column(Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth()) {
        if (title) {
            // Inline in the main list: a section header like every other group (review round 4), the intro as its
            // first line of quiet text.
            SectionHeader(stringResource(R.string.settings_group_sensitivity))
        }
        NbText(
            stringResource(R.string.setup_sens_intro),
            style = NbTheme.typography.body,
            color = controls.summary,
            modifier = Modifier.padding(horizontal = NbControlDimens.sectionHeaderStart, vertical = NbSpace.s2),
        )
        GroupCard(Modifier.padding(top = NbSpace.s2).selectableGroup()) {
            SensitivityPreset.entries.forEachIndexed { i, preset ->
                PresetRow(
                    stringResource(preset.title),
                    stringResource(preset.description),
                    selected = preset == state.sensitivity,
                    onClick = { actions.onSensitivity(preset) },
                    showDivider = i > 0,
                )
            }
        }
        val preview =
            state.previewNotes?.let {
                text.plural(R.plurals.sens_preview, it, text.int(it), text.int(state.previewNights))
            } ?: stringResource(R.string.settings_sens_preview_learning)
        SectionFooter(preview)
    }
}

/**
 * Appearance (`coloros-conformance.md` S3, L2): two grouped cards of single-choice rows under their own section
 * headers, theme (System / Light / Dark) and glass effects (Automatic / Off; Automatic names the reason when the
 * resolver turned glass off, `glass_off_*`), then the read-only motion note as a footer. [title] is accepted for
 * symmetry with the other groups; the two headers are shown in every placement.
 */
@Suppress("UnusedParameter")
@Composable
internal fun AppearanceGroup(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = true,
) {
    Column(Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth()) {
        SettingsGroup(stringResource(R.string.settings_theme)) {
            NbChoiceRow(
                labels = ThemeChoice.entries.map { stringResource(it.label) },
                selected = ThemeChoice.entries.indexOf(state.theme),
                onSelect = { actions.onTheme(ThemeChoice.entries[it]) },
                art = { ThemePreview(ThemeChoice.entries[it]) },
            )
        }
        SettingsGroup(stringResource(R.string.settings_glass), Modifier.selectableGroup()) {
            SettingsRow(
                stringResource(R.string.glass_auto),
                onClick = { actions.onGlass(GlassPreference.Automatic) },
                summary = glassAutoSummary(state),
                trailing = SettingsTrailing.Radio(state.glass == GlassPreference.Automatic),
            )
            SettingsRow(
                stringResource(R.string.glass_off),
                onClick = { actions.onGlass(GlassPreference.Off) },
                summary = stringResource(R.string.setup_glass_off_user),
                trailing = SettingsTrailing.Radio(state.glass == GlassPreference.Off),
                showDivider = true,
            )
        }
        SectionFooter(stringResource(R.string.settings_motion_note))
    }
}

/**
 * The Automatic row's summary: the reason the automatic mode keeps glass off on this device (also while Off is
 * selected), otherwise "On now" when Automatic is selected and "Available on this device" when it is not.
 */
@Composable
internal fun glassAutoSummary(state: SettingsState): String {
    val selected = state.glass == GlassPreference.Automatic
    // With Automatic selected the decision's reason is the automatic one; with Off it is UserOff, so ask the theme.
    val reason = if (selected) state.glassReason else LocalEffects.current.automaticReason
    return glassAutoSummaryRes(selected, reason).let { stringResource(it) }
}

/** Data and privacy: local only, read-only, and the in-app privacy policy. */
@Composable
internal fun PrivacyGroup(
    actions: SettingsActions,
    title: Boolean = true,
    policyRow: Boolean = true,
) {
    GroupOrCard(if (title) stringResource(R.string.setup_group_privacy) else null) {
        InfoRow(stringResource(R.string.setup_privacy_local), stringResource(R.string.about_local))
        InfoRow(stringResource(R.string.setup_privacy_readonly), stringResource(R.string.onb_perm_body), divider = true)
        if (policyRow) {
            SettingsRow(
                stringResource(R.string.setup_privacy_policy),
                onClick = { actions.onOpenPage(SettingsPage.Privacy) },
                summary = stringResource(R.string.setup_privacy_policy_summary),
                showDivider = true,
            )
        }
    }
}

/** About group of the list: "About NightBaseline" (version) and the "Not a medical device" line. */
@Composable
internal fun AboutGroup(
    state: SettingsState,
    actions: SettingsActions,
) {
    SettingsGroup(stringResource(R.string.settings_group_about)) {
        SettingsRow(
            stringResource(R.string.settings_about),
            onClick = { actions.onOpenPage(SettingsPage.About) },
            summary = stringResource(R.string.about_version, state.version),
        )
        InfoRow(stringResource(R.string.disclaimer_short), null, divider = true)
    }
}

/** A read-only row (title + optional summary) in the row rhythm (>= 48 dp), one TalkBack node, no action. */
@Composable
internal fun InfoRow(
    title: String,
    summary: String?,
    divider: Boolean = false,
) {
    val controls = NbTheme.controls
    Column(Modifier.fillMaxWidth()) {
        if (divider) Divider()
        Column(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbControlDimens.rowMinHeight)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = NbControlDimens.rowPaddingHorizontal, vertical = NbControlDimens.rowPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(NbControlDimens.rowTitleGap, Alignment.CenterVertically),
        ) {
            NbText(title, style = NbControlType.itemTitle, color = controls.title)
            summary?.let { NbText(it, style = NbControlType.summary, color = controls.summary) }
        }
    }
}

/** A [SettingsGroup] with a header, or (in a detail pane, where the pane has its own heading) the bare grouped card. */
@Composable
private fun GroupOrCard(
    title: String?,
    content: @Composable () -> Unit,
) {
    if (title != null) {
        SettingsGroup(title) { content() }
    } else {
        GroupCard { content() }
    }
}

/**
 * Our own small phone drawing for a theme choice (decorative): a rounded outline with two cards, in light colours,
 * dark colours, or split half and half for "System".
 */
@Composable
private fun ThemePreview(choice: ThemeChoice) {
    val ring = NbTheme.controls.radioRing
    Canvas(Modifier.size(PREVIEW_WIDTH, PREVIEW_HEIGHT)) {
        val radius = CornerRadius(PREVIEW_CORNER.toPx())
        val mid = size.width / 2
        when (choice) {
            ThemeChoice.Light -> {
                previewScreen(PREVIEW_LIGHT_BACK, Color.White, 0f, size.width, radius)
            }

            ThemeChoice.Dark -> {
                previewScreen(Color.Black, PREVIEW_DARK_CARD, 0f, size.width, radius)
            }

            ThemeChoice.System -> {
                previewScreen(PREVIEW_LIGHT_BACK, Color.White, 0f, mid, radius)
                previewScreen(Color.Black, PREVIEW_DARK_CARD, mid, size.width, radius)
            }
        }
        drawRoundRect(ring, cornerRadius = radius, style = Stroke(1.dp.toPx()))
    }
}

private fun DrawScope.previewScreen(
    back: Color,
    card: Color,
    left: Float,
    right: Float,
    radius: CornerRadius,
) {
    clipRect(left = left, right = right) {
        drawRoundRect(back, cornerRadius = radius)
        val pad = size.width * PREVIEW_PAD
        val cardHeight = size.height * PREVIEW_CARD
        for (n in 0..1) {
            drawRoundRect(
                card,
                topLeft = Offset(pad, size.height * PREVIEW_FIRST_CARD + n * (cardHeight + pad)),
                size = Size(size.width - 2 * pad, cardHeight),
                cornerRadius = CornerRadius(pad / 2),
            )
        }
    }
}

private val PREVIEW_WIDTH = 40.dp
private val PREVIEW_HEIGHT = 68.dp
private val PREVIEW_CORNER = 8.dp
private const val PREVIEW_PAD = 0.14f
private const val PREVIEW_CARD = 0.16f
private const val PREVIEW_FIRST_CARD = 0.3f

/** Window colour of the light preview (the measured ColorOS light background). */
@Suppress("MagicNumber")
private val PREVIEW_LIGHT_BACK = Color(0xFFF0F1F2)

/** Card colour of the dark preview. */
@Suppress("MagicNumber")
private val PREVIEW_DARK_CARD = Color(0xFF2E2E2E)
