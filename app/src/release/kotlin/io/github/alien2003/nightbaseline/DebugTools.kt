package io.github.alien2003.nightbaseline

import androidx.compose.runtime.Composable

/** Release builds have no debug tools: no component catalog, none of its code or strings. */
object DebugTools {
    /** Always null in release builds. */
    val catalog: (@Composable (onClose: () -> Unit) -> Unit)? = null
}
