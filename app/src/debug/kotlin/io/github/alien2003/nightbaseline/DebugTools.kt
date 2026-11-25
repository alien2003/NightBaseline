package io.github.alien2003.nightbaseline

import androidx.compose.runtime.Composable
import io.github.alien2003.nightbaseline.catalog.CatalogScreen

/** Debug-build tools: the component catalog (`screens.md` 7.10). Release builds have a null implementation. */
object DebugTools {
    /** The component catalog screen. */
    val catalog: (@Composable (onClose: () -> Unit) -> Unit)? = { onClose -> CatalogScreen(onClose) }
}
