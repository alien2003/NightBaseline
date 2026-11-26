package io.github.alien2003.nightbaseline

import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import io.github.alien2003.nightbaseline.ui.theme.DarkNbColors
import io.github.alien2003.nightbaseline.ui.theme.LightNbColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The system bar style of [applySystemBarStyle] (`coloros-conformance.md` D1): transparent bars without a platform
 * scrim, icons for the in-app theme even when the system theme is the other one, and the style survives a
 * configuration change handled without recreation (rotation): nothing re-applies the system theme's icons or the
 * 90 % white navigation bar scrim that `enableEdgeToEdge` brought back after every rotation. The window background
 * follows the in-app theme too (dark in-app on a light system and the other way round), so the gaps around the pages
 * of a predictive back preview show the app's background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-port-notnight")
class SystemBarStyleTest {
    private fun activity(): ActivityController<ComponentActivity> = Robolectric.buildActivity(ComponentActivity::class.java).setup()

    @Test
    fun darkAppThemeOnLightSystemGetsLightIconsAndStaysSoAfterRotation() {
        val controller = activity()
        val window = controller.get().window
        window.applySystemBarStyle(dark = true)
        assertBars(controller.get(), dark = true, "after applying the dark in-app theme")

        val landscape = Configuration(controller.get().resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        controller.get().onConfigurationChanged(landscape)
        window.decorView.dispatchConfigurationChanged(landscape)
        assertBars(controller.get(), dark = true, "after a rotation")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-port-night")
    fun lightAppThemeOnDarkSystemGetsTheLightWindowBackground() {
        val controller = activity()
        controller.get().window.applySystemBarStyle(dark = false)
        assertBars(controller.get(), dark = false, "light in-app theme on a dark system")
    }

    @Test
    fun lightAppThemeGetsDarkIconsAndSwitchesBack() {
        val controller = activity()
        val window = controller.get().window
        window.applySystemBarStyle(dark = false)
        assertBars(controller.get(), dark = false, "light in-app theme")
        window.applySystemBarStyle(dark = true)
        assertBars(controller.get(), dark = true, "switched to dark")
        window.applySystemBarStyle(dark = false)
        assertBars(controller.get(), dark = false, "switched back to light")
    }

    @Suppress("DEPRECATION") // The bar colours are what API 28-34 draw behind the icons.
    private fun assertBars(
        activity: ComponentActivity,
        dark: Boolean,
        moment: String,
    ) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals("light status bar icons flag $moment", !dark, controller.isAppearanceLightStatusBars)
        assertEquals("light navigation bar icons flag $moment", !dark, controller.isAppearanceLightNavigationBars)
        assertEquals("status bar colour $moment", Color.TRANSPARENT, window.statusBarColor)
        assertEquals("navigation bar colour (no scrim) $moment", Color.TRANSPARENT, window.navigationBarColor)
        assertFalse("navigation bar contrast scrim $moment", window.isNavigationBarContrastEnforced)
        assertFalse("status bar contrast scrim $moment", window.isStatusBarContrastEnforced)
        assertEquals(
            "content drawn into the cutout $moment",
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            window.attributes.layoutInDisplayCutoutMode,
        )
        // The gaps of a predictive back preview show the window: it carries the in-app background, not the system's.
        val background = (if (dark) DarkNbColors else LightNbColors).background.toArgb()
        assertEquals("window background $moment", background, (window.decorView.background as ColorDrawable).color)
    }
}
