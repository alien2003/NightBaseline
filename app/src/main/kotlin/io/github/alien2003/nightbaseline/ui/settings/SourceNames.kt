package io.github.alien2003.nightbaseline.ui.settings

import io.github.alien2003.nightbaseline.data.local.settings.AppSettings

/**
 * The name a screen shows for the selected source app. The package manager label while the app is installed; when it
 * is not (the default source on a device without it, or an app removed after setup), the known name of the default
 * source, otherwise the package name with a break opportunity after every dot. A dotted id has no line break
 * opportunity of its own, so in a narrow value column it would break inside a word ("com.heytap.health.interna" /
 * "tional").
 */
object SourceNames {
    /** Name of [AppSettings.DEFAULT_SOURCE_PACKAGE] when it is not installed (plain text, no vendor resource). */
    const val DEFAULT_SOURCE_NAME: String = "OHealth"

    private const val ZERO_WIDTH_SPACE = '​'

    /** [label] from the package manager, or the fallback for [packageName] when it is null. */
    fun display(
        label: String?,
        packageName: String,
    ): String =
        when {
            label != null -> label
            packageName == AppSettings.DEFAULT_SOURCE_PACKAGE -> DEFAULT_SOURCE_NAME
            else -> breakable(packageName)
        }

    /** [packageName] with a zero-width space after each dot, so it wraps at the dots. */
    fun breakable(packageName: String): String = packageName.replace(".", ".$ZERO_WIDTH_SPACE")
}
