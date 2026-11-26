package io.github.alien2003.nightbaseline.nav

/**
 * When a Health Connect permission request from the shell (the background banner, "Allow access" on Home or History)
 * should continue in the Health Connect app instead.
 *
 * After two denials Health Connect no longer shows its dialog: the request returns at once with what was already
 * granted. The onboarding flow counts denials for this; the shell does not, so it looks at the result instead.
 */
internal object PermissionFallback {
    /**
     * A result that came back faster than this cannot be an answer the person gave in a dialog. Hypothesis, not
     * measured on every device: launching Health Connect and returning without a dialog takes a few hundred ms, while
     * reading the dialog and tapping a button takes well over a second.
     */
    const val NO_DIALOG_MS: Long = 700L

    /**
     * True when the request for [wanted] returned [granted] in a way that means no dialog was shown: nothing that was
     * asked for was added and either nothing at all is granted, or the result came back within [NO_DIALOG_MS]
     * ([elapsedMs] since the launch). A deliberate "Don't allow" in a dialog that really appeared is left alone.
     */
    fun mustOpenHealthConnect(
        wanted: Set<String>,
        granted: Set<String>,
        elapsedMs: Long,
    ): Boolean {
        if (wanted.isEmpty() || wanted.any { it in granted }) return false
        return granted.isEmpty() || elapsedMs < NO_DIALOG_MS
    }
}
