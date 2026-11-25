package io.github.alien2003.nightbaseline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.home.HomeScreen
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme

/**
 * The single activity: edge-to-edge, no orientation lock, resizable (manifest), fold / unfold handled without
 * recreation. For now it shows the placeholder Home on synthetic sample data, and in debug builds the component
 * catalog (top bar action). The data layer is wired in a later step.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NightBaselineTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    val catalog = DebugTools.catalog
    if (showCatalog && catalog != null) {
        BackHandler { showCatalog = false }
        catalog { showCatalog = false }
        return
    }
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val layout =
        resolveLayout(
            LayoutInputs(
                widthDp = window.width / density.density,
                heightDp = window.height / density.density,
                fontScale = density.fontScale,
            ),
        )
    HomeScreen(
        state = SampleData.home(),
        layout = layout,
        debugAction =
            if (catalog != null) {
                {
                    NbIconButton(
                        NbIcons.More,
                        contentDescription = stringResource(R.string.settings_catalog),
                        onClick = { showCatalog = true },
                        tint = NbTheme.colors.onGlassPrimary,
                        onGlass = true,
                    )
                }
            } else {
                null
            },
    )
}
