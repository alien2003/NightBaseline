package io.github.alien2003.nightbaseline.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.alien2003.nightbaseline.R

/** The three top-level destinations (`screens.md` 2), always labelled. */
enum class TopLevel(
    /** Label (`nav_*`). */
    @param:StringRes val label: Int,
    /** Outline icon (unselected). */
    val icon: ImageVector,
    /** Filled icon (selected). */
    val selectedIcon: ImageVector,
) {
    /** Last night (Home). */
    Home(R.string.nav_home, NbIcons.Night, NbIcons.NightFilled),

    /** History. */
    History(R.string.nav_history, NbIcons.History, NbIcons.HistoryFilled),

    /** Settings. */
    Settings(R.string.nav_settings, NbIcons.Settings, NbIcons.SettingsFilled),
}
