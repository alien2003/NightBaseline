package io.github.alien2003.nightbaseline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.SettingsGroup
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.components.SettingsTrailing
import io.github.alien2003.nightbaseline.ui.material.EffectsReason
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/** Data source group: source (opens the picker), Health Connect permissions, last read, set up again. */
@Composable
internal fun SourceGroup(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = true,
) {
    GroupOrCard(if (title) stringResource(R.string.settings_group_source) else null) {
        SettingsRow(
            stringResource(R.string.settings_source),
            onClick = { actions.onOpenPage(SettingsPage.SourcePicker) },
            summary = lastReadSummary(state),
            trailing = SettingsTrailing.Value(state.sourceLabel),
        )
        SettingsRow(
            stringResource(R.string.settings_hc_permissions),
            onClick = actions.onOpenHealthConnect,
            showDivider = true,
        )
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
 * The sensitivity presets as large radio cards, then the preview line (hidden while learning, `ST-SENS-LEARNING`).
 * Used as the sub-screen (Compact), inline (Medium) and in the detail pane.
 */
@Composable
internal fun SensitivityContent(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = false,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Column(
        Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        if (title) PaneHeading(stringResource(R.string.settings_group_sensitivity), Modifier.padding(top = NbSpace.s5))
        Text(stringResource(R.string.setup_sens_intro), style = NbTheme.typography.body, color = colors.textSecondary)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
            SensitivityPreset.entries.forEach { preset ->
                PresetCard(
                    stringResource(preset.title),
                    stringResource(preset.description),
                    selected = preset == state.sensitivity,
                    onClick = { actions.onSensitivity(preset) },
                )
            }
        }
        val preview =
            state.previewNotes?.let {
                text.plural(R.plurals.sens_preview, it, text.int(it), text.int(state.previewNights))
            } ?: stringResource(R.string.settings_sens_preview_learning)
        Text(preview, style = NbTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

/**
 * Appearance: theme (System / Light / Dark) and glass effects (Automatic / Off) as radio rows; Automatic names the
 * reason when the resolver turned glass off (`glass_off_*`). Then the read-only motion note.
 */
@Composable
internal fun AppearanceGroup(
    state: SettingsState,
    actions: SettingsActions,
    title: Boolean = true,
) {
    GroupOrCard(if (title) stringResource(R.string.settings_group_appearance) else null) {
        GroupLabel(stringResource(R.string.settings_theme))
        Column(Modifier.selectableGroup()) {
            ThemeChoice.entries.forEachIndexed { i, choice ->
                SettingsRow(
                    stringResource(choice.label),
                    onClick = { actions.onTheme(choice) },
                    trailing = SettingsTrailing.Radio(choice == state.theme),
                    showDivider = i > 0,
                )
            }
        }
        Divider()
        GroupLabel(stringResource(R.string.settings_glass))
        Column(Modifier.selectableGroup()) {
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
        Divider()
        NoteRow(stringResource(R.string.settings_motion_note))
    }
}

/** "On now", or the reason the automatic mode turned glass off. */
@Composable
internal fun glassAutoSummary(state: SettingsState): String {
    val reason = state.glassReason.takeIf { state.glass == GlassPreference.Automatic && it != EffectsReason.UserOff }
    return reason?.summary?.let { stringResource(it) } ?: stringResource(R.string.setup_glass_on_now)
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

/** A read-only row (title + optional summary) in the row rhythm (>= 56 dp), one TalkBack node, no action. */
@Composable
internal fun InfoRow(
    title: String,
    summary: String?,
    divider: Boolean = false,
) {
    val colors = NbTheme.colors
    Column(Modifier.fillMaxWidth()) {
        if (divider) Divider()
        Column(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.rowMinHeight)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = NbSpace.s5, vertical = NbDimens.rowPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s1, Alignment.CenterVertically),
        ) {
            Text(title, style = NbTheme.typography.titleSmall, color = colors.textPrimary)
            summary?.let { Text(it, style = NbTheme.typography.bodySmall, color = colors.textSecondary) }
        }
    }
}

/** A read-only note in the row rhythm (secondary text only). */
@Composable
private fun NoteRow(text: String) {
    Text(
        text,
        style = NbTheme.typography.bodySmall,
        color = NbTheme.colors.textSecondary,
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.rowMinHeight)
                .padding(horizontal = NbSpace.s5, vertical = NbSpace.s5),
    )
}

/** A [SettingsGroup] with a header, or (in a detail pane, where the pane has its own heading) the bare solid card. */
@Composable
private fun GroupOrCard(
    title: String?,
    content: @Composable () -> Unit,
) {
    if (title != null) {
        SettingsGroup(title) { content() }
    } else {
        Column(Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax).solidCard(NbShapes.l)) { content() }
    }
}
