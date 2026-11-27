// Debug-only catalog: the control options (font scales, metric counts) are literal choices.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.NbChipTab
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.interaction.nbClickable
import io.github.alien2003.nightbaseline.ui.interaction.nbVerticalScroll
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.material.grayscale
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme

/**
 * The theme, effects, font scale, motion and grayscale of [config] around [content], on the window background.
 * Shared by the catalog screen and the screenshot tests, so both render exactly the same thing.
 */
@Composable
fun CatalogFrame(
    config: CatalogConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NightBaselineTheme(
        darkTheme = config.dark,
        effects = if (config.glass) EffectsDecision.ForcedGlass else EffectsDecision.ForcedSolid,
        reducedMotion = config.reducedMotion,
        fontScale = config.fontScale,
    ) {
        Box(
            modifier
                .background(NbTheme.colors.background)
                .grayscale(config.grayscale),
        ) { content() }
    }
}

/** The debug component catalog: controls on top, then every component of the selected page in all its states. */
@Composable
fun CatalogScreen(onClose: () -> Unit) {
    var dark by rememberSaveable { mutableStateOf(false) }
    var glass by rememberSaveable { mutableStateOf(true) }
    var font by rememberSaveable { mutableStateOf<Float?>(null) }
    var gray by rememberSaveable { mutableStateOf(false) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    var metrics by rememberSaveable { mutableStateOf(6) }
    var page by rememberSaveable { mutableStateOf(CatalogPage.Foundations) }
    val config = CatalogConfig(dark, glass, font, gray, reduced, metrics)
    CatalogFrame(config, Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .nbVerticalScroll(rememberNbScrollState()),
        ) {
            Column(Modifier.padding(NbDimens.gutterCompact), verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                NbIconButton(NbIcons.Back, stringResource(R.string.a11y_back), onClose)
                NbText(stringResource(R.string.catalog_title), style = NbTheme.typography.largeTitle, color = NbTheme.colors.textPrimary)
                Control(stringResource(R.string.catalog_page), CatalogPage.entries, page, { it.title }) { page = it }
                Control(stringResource(R.string.catalog_theme), listOf(false, true), dark, { if (it) "Dark" else "Light" }) { dark = it }
                val glassLabel = stringResource(R.string.catalog_glass)
                val solidLabel = stringResource(R.string.catalog_solid)
                Control(
                    stringResource(R.string.catalog_effects),
                    listOf(true, false),
                    glass,
                    { if (it) glassLabel else solidLabel },
                ) { glass = it }
                Control(
                    stringResource(R.string.catalog_font),
                    listOf(null, 1f, 1.35f, 2f),
                    font,
                    { it?.toString() ?: "System" },
                ) { font = it }
                Control(stringResource(R.string.catalog_grayscale), listOf(false, true), gray, { if (it) "On" else "Off" }) { gray = it }
                Control(stringResource(R.string.catalog_reduced_motion), listOf(false, true), reduced, { if (it) "On" else "Off" }) {
                    reduced = it
                }
                Control(stringResource(R.string.catalog_metrics), (2..6).toList(), metrics, { "$it" }) { metrics = it }
            }
            CatalogPageContent(page, metricCount = metrics)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Control(
    label: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
        NbText(label, style = NbTheme.typography.labelSmall, color = NbTheme.colors.textTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(NbSpace.s2), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            options.forEach { option ->
                NbChipTab(name(option), selected = option == selected, onClick = { onSelect(option) })
            }
        }
    }
}
