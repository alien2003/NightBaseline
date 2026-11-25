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

/** The source picker: intro and the detected sources as radio rows. */
@Composable
internal fun SourcePickerContent(
    state: SettingsState,
    actions: SettingsActions,
) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        Text(stringResource(R.string.onb_source_body), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
        Column(Modifier.fillMaxWidth().solidCard(NbShapes.l).selectableGroup()) {
            state.sources.forEachIndexed { i, source ->
                SourceOptionRow(
                    source,
                    selected = source.packageName == state.sourcePackage,
                    onClick = { actions.onPickSource(source) },
                    showDivider = i > 0,
                )
            }
        }
    }
}

/** About: name and version, the full disclaimer, local-only and any-source lines, licenses and privacy policy. */
@Composable
internal fun AboutContent(
    state: SettingsState,
    actions: SettingsActions,
) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        NbCard(Modifier.fillMaxWidth()) {
            PaneHeading(stringResource(R.string.app_name))
            Text(
                stringResource(R.string.about_version, state.version),
                style = NbTheme.typography.bodySmall,
                color = NbTheme.colors.textSecondary,
            )
            TextBlock(stringResource(R.string.setup_about_disclaimer_title), stringResource(R.string.disclaimer_full))
            Text(stringResource(R.string.about_local), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
            Text(stringResource(R.string.about_any_source), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
        }
        Column(Modifier.fillMaxWidth().solidCard(NbShapes.l)) {
            SettingsRow(stringResource(R.string.about_licenses), onClick = { actions.onOpenPage(SettingsPage.Licenses) })
            SettingsRow(
                stringResource(R.string.setup_privacy_policy),
                onClick = { actions.onOpenPage(SettingsPage.Privacy) },
                showDivider = true,
            )
        }
    }
}

/** Open-source licenses: intro, one row per library, and the app's own license. */
@Composable
internal fun LicensesContent(state: SettingsState) {
    val colors = NbTheme.colors
    Column(
        Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        Text(stringResource(R.string.setup_licenses_intro), style = NbTheme.typography.body, color = colors.textSecondary)
        Column(Modifier.fillMaxWidth().solidCard(NbShapes.l)) {
            state.licenses.forEachIndexed { i, entry -> InfoRow(entry.name, entry.license, divider = i > 0) }
        }
        Text(stringResource(R.string.setup_licenses_app), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

/** The in-app privacy policy: what is read, where it is kept, what is shared, how to remove it. */
@Composable
internal fun PrivacyContent() {
    NbCard(Modifier.fillMaxWidth()) {
        TextBlock(stringResource(R.string.setup_policy_read_title), stringResource(R.string.setup_policy_read_body))
        TextBlock(stringResource(R.string.setup_policy_kept_title), stringResource(R.string.setup_policy_kept_body))
        TextBlock(stringResource(R.string.setup_policy_shared_title), stringResource(R.string.setup_policy_shared_body))
        TextBlock(stringResource(R.string.setup_policy_remove_title), stringResource(R.string.setup_policy_remove_body))
    }
}

/** Summary of a section in the two-pane list. */
internal fun UiText.sectionSummary(
    section: SettingsSection,
    state: SettingsState,
): String =
    when (section) {
        SettingsSection.Source -> state.sourceLabel
        SettingsSection.Notifications -> notificationsSummary(state)
        SettingsSection.Sensitivity -> string(state.sensitivity.title)
        SettingsSection.Appearance -> "${string(state.theme.label)} · ${string(R.string.settings_glass)}: ${glassValue(state)}"
        SettingsSection.Privacy -> string(R.string.setup_privacy_summary)
        SettingsSection.About -> string(R.string.about_version, state.version)
    }

private fun UiText.notificationsSummary(state: SettingsState): String =
    when {
        state.notificationsBlocked -> string(R.string.settings_notif_blocked)
        state.notifyResults -> string(R.string.setup_on)
        else -> string(R.string.setup_off)
    }

private fun UiText.glassValue(state: SettingsState): String =
    if (state.glass == GlassPreference.Off) string(R.string.glass_off) else string(R.string.glass_auto)
