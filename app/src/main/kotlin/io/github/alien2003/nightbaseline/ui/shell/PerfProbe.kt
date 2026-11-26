package io.github.alien2003.nightbaseline.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

/**
 * Counting hooks for the cost tests (`TabSwitchCostTest`): how often a screen or row body runs (composition or
 * recomposition) and how often a ViewModel maps its inputs to UI state. A no-op in the app: nothing installs a
 * [sink], so a hook is one volatile read.
 */
object PerfProbe {
    /** Receives the name of every hit; set by tests only. */
    @Volatile
    var sink: ((String) -> Unit)? = null

    /** Records one hit of [name]. */
    fun hit(name: String) {
        sink?.invoke(name)
    }
}

/** Records one run of the calling composable's body under [name] (it runs in the caller's restart scope). */
@Composable
@NonRestartableComposable
fun CountComposition(name: String) {
    PerfProbe.sink?.invoke(name)
}
