package io.github.alien2003.nightbaseline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.GroupCard
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.SettingsGroup
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.components.SettingsTrailing
import io.github.alien2003.nightbaseline.ui.material.EffectsReason
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.onboarding.HealthConnectStatus
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data

/** The source picker: intro and the detected sources as radio rows. */
@Composable
internal fun SourcePickerContent(
    state: SettingsState,
    actions: SettingsActions,
) {
    Column(
        Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        NbText(stringResource(R.string.onb_source_body), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
        val missing = state.healthConnectMissingTitle
        if (missing != null) {
            // Health Connect is missing or needs an update: no source can be listed until it works.
            val body =
                if (state.healthConnect ==
                    HealthConnectStatus.NeedsUpdate
                ) {
                    R.string.state_hc_update_body
                } else {
                    R.string.state_hc_none_body
                }
            EmptySourcesCard(
                title = stringResource(missing),
                body = stringResource(body),
                action = stringResource(R.string.action_open_store),
                onAction = actions.onInstallHealthConnect,
                state = state,
                actions = actions,
                note = stringResource(R.string.hc_still_missing),
            )
            return@Column
        }
        if (state.sources.isEmpty()) {
            // No app wrote sleep in the lookback: say so instead of an empty card, with a way to look again.
            EmptySourcesCard(
                title = stringResource(R.string.onb_nosource_title),
                body = stringResource(R.string.onb_nosource_body),
                action = stringResource(R.string.action_open_hc),
                onAction = actions.onOpenHealthConnect,
                state = state,
                actions = actions,
                note = stringResource(R.string.sources_still_none),
            )
            return@Column
        }
        GroupCard(Modifier.selectableGroup()) {
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

/**
 * The card of a source page with nothing to list: [title], [body], the [action] that can fix it and "Check again",
 * which reads "Checking…" while it runs and is followed by [note] when it still found nothing (announced once).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptySourcesCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    state: SettingsState,
    actions: SettingsActions,
    note: String,
) {
    NbCard(Modifier.fillMaxWidth()) {
        NbText(
            title,
            style = NbTheme.typography.body,
            color = NbTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        NbText(body, style = NbTheme.typography.bodySmall, color = NbTheme.colors.textSecondary)
        if (state.sourcesRechecked && !state.checkingSources) {
            NbText(
                note,
                style = NbTheme.typography.bodySmall,
                color = NbTheme.colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FlowRow(Modifier.offset(x = -NbSpace.s4)) {
            NbTextButton(action, onClick = onAction, color = NbTheme.colors.data.primaryText)
            NbTextButton(
                stringResource(if (state.checkingSources) R.string.action_checking else R.string.action_check_again),
                onClick = if (state.checkingSources) ({}) else actions.onCheckSources,
                color = NbTheme.colors.data.primaryText,
            )
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
        Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        NbCard(Modifier.fillMaxWidth()) {
            PaneHeading(stringResource(R.string.app_name))
            NbText(
                stringResource(R.string.about_version, state.version),
                style = NbTheme.typography.bodySmall,
                color = NbTheme.colors.textSecondary,
            )
            TextBlock(stringResource(R.string.setup_about_disclaimer_title), stringResource(R.string.disclaimer_full))
            NbText(stringResource(R.string.about_local), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
            NbText(stringResource(R.string.about_any_source), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
        }
        GroupCard {
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
        Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        NbText(stringResource(R.string.setup_licenses_intro), style = NbTheme.typography.body, color = colors.textSecondary)
        GroupCard {
            state.licenses.forEachIndexed { i, entry -> InfoRow(entry.name, entry.license, divider = i > 0) }
        }
        NbText(stringResource(R.string.setup_licenses_app), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
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

/** The Automatic glass row's summary string for its [selected] state and the automatic [reason] ([glassAutoSummary]). */
internal fun glassAutoSummaryRes(
    selected: Boolean,
    reason: EffectsReason,
): Int = reason.summary ?: if (selected) R.string.setup_glass_on_now else R.string.setup_glass_available
