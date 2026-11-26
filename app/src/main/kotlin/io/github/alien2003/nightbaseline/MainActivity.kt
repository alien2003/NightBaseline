package io.github.alien2003.nightbaseline

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.alien2003.nightbaseline.data.local.settings.GlassMode
import io.github.alien2003.nightbaseline.data.local.settings.ThemeMode
import io.github.alien2003.nightbaseline.display.HighRefreshRate
import io.github.alien2003.nightbaseline.nav.AppNavHost
import io.github.alien2003.nightbaseline.nav.AppRootViewModel
import io.github.alien2003.nightbaseline.nav.DeepLink
import io.github.alien2003.nightbaseline.nav.DeepLinks
import io.github.alien2003.nightbaseline.nav.KeyShortcuts
import io.github.alien2003.nightbaseline.nav.LocalKeyShortcuts
import io.github.alien2003.nightbaseline.nav.rememberSystemActions
import io.github.alien2003.nightbaseline.nav.rememberWindowLayoutInputs
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import io.github.alien2003.nightbaseline.ui.material.rememberPlatformEffectsInputs
import io.github.alien2003.nightbaseline.ui.material.resolveEffectsMode
import io.github.alien2003.nightbaseline.ui.shell.BackPreviewHandler
import io.github.alien2003.nightbaseline.ui.shell.BackSettle
import io.github.alien2003.nightbaseline.ui.shell.backPreviewPage
import io.github.alien2003.nightbaseline.ui.shell.rememberBackPreview
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import io.github.alien2003.nightbaseline.work.notify.AndroidNightNotifier
import io.github.alien2003.nightbaseline.work.notify.NightIntents

/**
 * The single activity: edge-to-edge, no orientation lock, resizable; fold / unfold, rotation and resize are handled
 * without recreation (manifest `configChanges`), and the layout follows the live window metrics and folding features.
 * The system bar icons follow the app's own theme, not the system's (a forced light or dark theme keeps them
 * readable), over transparent bars; the style is applied by hand ([applySystemBarStyle]) on start, on every theme
 * change and after every configuration change, so a rotation or resize never brings back icons for the system theme
 * or a platform scrim. It hosts the navigation graph, turns notification taps into deep links (the
 * `nightbaseline://` data URI or the night date extra, both validated), lists the keyboard shortcuts for the system helper
 * (Meta+/) and hands keys nothing focused consumed to them ([KeyShortcuts]), so they work right after a touch.
 * While it is in use it asks for the panel's highest refresh rate ([HighRefreshRate], ADR 0008).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val root: AppRootViewModel by viewModels()
    private val deepLink = mutableStateOf<DeepLink?>(null)
    private val refreshRate = HighRefreshRate(this)
    private val shortcuts = KeyShortcuts()

    /** Whether the in-app theme is dark (the system bar icons follow it); null until the first composition. */
    private var barsDark: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Until the stored theme is known the bars follow the system theme, like the app's "System" default.
        window.applySystemBarStyle(dark = systemDark())
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { CompositionLocalProvider(LocalKeyShortcuts provides shortcuts) { AppRoot() } }
    }

    // The app-wide shortcuts work without keyboard focus too (after a touch nothing is focused): a key the window
    // leaves unconsumed goes to the shortcut handler of the navigation graph.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = shortcuts.dispatch(event) { super.dispatchKeyEvent(it) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation, resize and fold are handled without recreation: restate the in-app style, never the system's.
        window.applySystemBarStyle(barsDark ?: systemDark())
    }

    private fun systemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun onResume() {
        super.onResume()
        refreshRate.onResume()
    }

    override fun onPause() {
        refreshRate.onPause()
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        refreshRate.onUserInteraction()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        refreshRate.onTouch(ev.actionMasked)
        return handled
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
        // Applied after the composition that resolved the theme, before its first frame is drawn.
        SideEffect {
            if (barsDark != dark) {
                barsDark = dark
                window.applySystemBarStyle(dark)
            }
        }
        NightBaselineTheme(darkTheme = dark, effects = resolveEffectsMode(rememberPlatformEffectsInputs(glass))) {
            var showCatalog by rememberSaveable { mutableStateOf(false) }
            val catalog = DebugTools.catalog
            if (showCatalog && catalog != null) {
                // Back closes the debug catalog with the same predictive preview as a page (ADR 0009).
                val preview = rememberBackPreview()
                BackPreviewHandler(preview, enabled = true, key = CATALOG_BACK_KEY, settle = BackSettle.InPlace) {
                    showCatalog = false
                }
                Box(Modifier.fillMaxSize().backPreviewPage(preview, CATALOG_BACK_KEY)) { catalog { showCatalog = false } }
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
                            ShellRoundButton(
                                NbIcons.MoreVertical,
                                contentDescription = stringResource(R.string.settings_catalog),
                                onClick = { showCatalog = true },
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

/**
 * Edge-to-edge with transparent status and navigation bars whose icons follow the in-app theme ([dark]: light icons;
 * `coloros-conformance.md` D1), content drawn into the display cutout, and no platform contrast scrim (the shell draws
 * its own behind a 3-button bar). Done by hand instead of `enableEdgeToEdge`: that installs a hidden view which
 * re-applies the styles of its first call on every configuration change (androidx.activity 1.13), with icons for the
 * system theme and a 90 % white or 50 % grey navigation bar scrim. Idempotent.
 */
@Suppress("DEPRECATION") // statusBarColor / navigationBarColor still decide the bar backgrounds below API 35.
internal fun Window.applySystemBarStyle(dark: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    val cutout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    if (attributes.layoutInDisplayCutoutMode != cutout) attributes = attributes.also { it.layoutInDisplayCutoutMode = cutout }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isStatusBarContrastEnforced = false
        isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(this, decorView).run {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

/** Key of the debug catalog in its back preview. */
private const val CATALOG_BACK_KEY: String = "catalog"
