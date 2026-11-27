package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.model.ManualCheckUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.dataType

/**
 * The last "Check now" of Home ([ManualCheckUi]), provided by Home around its cards and banners; elsewhere there is
 * none and every "Check now" reads as usual.
 */
val LocalManualCheck = compositionLocalOf<ManualCheckUi> { ManualCheckUi.None }

/** The secondary action label of [night]'s card: "Checking…" instead of "Check now" while [busy]. */
fun UiText.nightSecondaryLabel(
    night: NightUi,
    busy: Boolean,
): String? = nightSecondaryAction(night)?.let { if (busy) string(R.string.action_checking) else it }

/** [action], or nothing while [busy] (a second tap during a running check does nothing). */
fun unlessBusy(
    busy: Boolean,
    action: () -> Unit,
): () -> Unit = if (busy) ({}) else action

/** Whether the "Check now" of a card in [state] is busy (a manual check runs). */
@Composable
fun checkBusy(state: NightState): Boolean =
    LocalManualCheck.current == ManualCheckUi.Checking && (state == NightState.NotSynced || state == NightState.NoData)

/** "Checked at 9:41. Nothing new from {source} yet.", or null. */
fun UiText.checkNote(
    check: ManualCheckUi,
    source: String,
): String? = (check as? ManualCheckUi.NothingNew)?.let { string(R.string.check_result_nothing_new, it.time, source) }

/** "Health Connect is still not available." after a "Check again" that found it missing, or null. */
fun UiText.healthConnectNote(check: ManualCheckUi): String? =
    if (check == ManualCheckUi.HealthConnectStillMissing) string(R.string.hc_still_missing) else null

/** The result line of a manual check: calm secondary text, announced once. */
@Composable
internal fun CheckNote(text: String) {
    NbText(
        text,
        style = NbTheme.dataType.summary,
        color = NbTheme.colors.textSecondary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
