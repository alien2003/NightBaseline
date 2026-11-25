package io.github.alien2003.nightbaseline

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.alien2003.nightbaseline.data.local.settings.GlassMode
import io.github.alien2003.nightbaseline.data.local.settings.ThemeMode
import io.github.alien2003.nightbaseline.nav.AppNavHost
import io.github.alien2003.nightbaseline.nav.AppRootViewModel
import io.github.alien2003.nightbaseline.nav.DeepLink
import io.github.alien2003.nightbaseline.nav.DeepLinks
import io.github.alien2003.nightbaseline.nav.rememberSystemActions
import io.github.alien2003.nightbaseline.nav.rememberWindowLayoutInputs
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import io.github.alien2003.nightbaseline.ui.material.rememberPlatformEffectsInputs
import io.github.alien2003.nightbaseline.ui.material.resolveEffectsMode
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import io.github.alien2003.nightbaseline.work.notify.AndroidNightNotifier
import io.github.alien2003.nightbaseline.work.notify.NightIntents

/**
 * The single activity: edge-to-edge, no orientation lock, resizable; fold / unfold, rotation and resize are handled
 * without recreation (manifest `configChanges`), and the layout follows the live window metrics and folding features.
 * It hosts the navigation graph, turns notification taps into deep links (the `nightbaseline://` data URI or the
 * night date extra, both validated) and lists the keyboard shortcuts for the system helper (Meta+/).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val root: AppRootViewModel by viewModels()
    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val link = DeepLinks.parse(intent?.dataString, intent?.getStringExtra(NightIntents.EXTRA_NIGHT_DATE)) ?: return
        // Opening the night clears its note (states.md 6.1 "Auto-cancel / clearing").
        if (link is DeepLink.Night) {
            NotificationManagerCompat
                .from(
                    this,
                ).cancel(AndroidNightNotifier.TAG, AndroidNightNotifier.NOTIFICATION_ID)
        }
        deepLink.value = link
    }

    @Composable
    private fun AppRoot() {
        val state by root.state.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { root.refresh() }
        val s = state
        val glass = if (s?.glass == GlassMode.OFF) GlassPreference.Off else GlassPreference.Automatic
        val dark =
            when (s?.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
        NightBaselineTheme(darkTheme = dark, effects = resolveEffectsMode(rememberPlatformEffectsInputs(glass))) {
            var showCatalog by rememberSaveable { mutableStateOf(false) }
            val catalog = DebugTools.catalog
            if (showCatalog && catalog != null) {
                BackHandler { showCatalog = false }
                catalog { showCatalog = false }
                return@NightBaselineTheme
            }
            if (s == null) return@NightBaselineTheme
            val layout = resolveLayout(rememberWindowLayoutInputs(this))
            AppNavHost(
                layout = layout,
                onboardingDone = s.onboardingDone,
                system = rememberSystemActions(),
                newestNight = s.newestNight,
                deepLink = deepLink.value,
                onDeepLinkHandled = { deepLink.value = null },
                onRefresh = root::refresh,
                onCheckNow = root::checkNow,
                debugAction =
                    catalog?.let {
                        {
                            NbIconButton(
                                NbIcons.More,
                                contentDescription = stringResource(R.string.settings_catalog),
                                onClick = { showCatalog = true },
                                tint = NbTheme.colors.onGlassPrimary,
                                onGlass = true,
                            )
                        }
                    },
            )
        }
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val shortcuts =
            listOf(
                KeyboardShortcutInfo(getString(R.string.nav_home), KeyEvent.KEYCODE_1, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.nav_history), KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.nav_settings), KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.action_check_now), KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.a11y_back), KeyEvent.KEYCODE_ESCAPE, 0),
            )
        data += KeyboardShortcutGroup(getString(R.string.app_name), shortcuts)
    }
}
