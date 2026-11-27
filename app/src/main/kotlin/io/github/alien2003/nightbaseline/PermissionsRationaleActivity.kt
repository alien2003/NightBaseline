package io.github.alien2003.nightbaseline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.alien2003.nightbaseline.ui.components.GroupCard
import io.github.alien2003.nightbaseline.ui.components.NbHairline
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.interaction.nbVerticalScroll
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.theme.NbControlDimens
import io.github.alien2003.nightbaseline.ui.theme.NbControlType
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme

/**
 * The privacy rationale Health Connect opens from its permission screen (`ACTION_SHOW_PERMISSIONS_RATIONALE`, and
 * the Android 14+ `VIEW_PERMISSION_USAGE` alias): what is read, that nothing is written or shared, and the
 * disclaimer. Static text only.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NightBaselineTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(NbTheme.colors.background)
                            .safeDrawingPadding()
                            .nbVerticalScroll(rememberNbScrollState())
                            .padding(NbDimens.gutterCompact)
                            .widthIn(max = NbDimens.textMax),
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s5),
                ) {
                    val text = NbTheme.colors.textPrimary
                    NbText(
                        stringResource(R.string.onb_perm_title),
                        style = NbTheme.typography.headline,
                        color = text,
                        modifier = Modifier.semantics { heading() },
                    )
                    NbText(stringResource(R.string.onb_perm_body), style = NbTheme.typography.body, color = text)
                    // What is read, as one grouped list card with inset hairlines (ColorOS settings pattern).
                    GroupCard {
                        PERMISSION_LINES.forEachIndexed { i, line ->
                            if (i > 0) NbHairline()
                            NbText(
                                stringResource(line),
                                style = NbControlType.summary,
                                color = NbTheme.colors.textPrimary,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minHeight = NbControlDimens.rowMinHeight)
                                        .padding(
                                            horizontal = NbControlDimens.rowPaddingHorizontal,
                                            vertical = NbControlDimens.rowPaddingVertical,
                                        ),
                            )
                        }
                    }
                    NbText(stringResource(R.string.about_local), style = NbTheme.typography.body, color = text)
                    NbText(
                        stringResource(R.string.disclaimer_full),
                        style = NbTheme.typography.bodySmall,
                        color = NbTheme.colors.textSecondary,
                    )
                    PrimaryButton(text = stringResource(R.string.action_done), onClick = ::finish, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private val PERMISSION_LINES =
    listOf(
        R.string.onb_perm_sleep,
        R.string.onb_perm_hr,
        R.string.onb_perm_rr,
        R.string.onb_perm_spo2,
        R.string.onb_perm_hrv,
        R.string.onb_perm_skin,
        R.string.onb_perm_background,
        R.string.onb_perm_history,
    )
