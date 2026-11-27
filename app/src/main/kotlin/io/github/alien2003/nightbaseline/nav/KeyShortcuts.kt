package io.github.alien2003.nightbaseline.nav

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.alien2003.nightbaseline.ui.interaction.NbKeyIntent
import io.github.alien2003.nightbaseline.ui.interaction.nbDecodeKey

/**
 * The app-wide keyboard shortcuts at the activity level (`layouts.md` 10.2: Ctrl+1/2/3, Ctrl+R, Esc, Alt+Left).
 *
 * Compose delivers a hardware key only along the path to the focused node; after a touch nothing is focused, so a
 * `Modifier.onPreviewKeyEvent` at the root never sees the key. The activity therefore offers every key event the
 * window did not consume to [dispatch], which hands it to the handler [AppNavHost] registers ([RegisterKeyShortcuts]).
 * The root modifier keeps handling the keys while something is focused, so focused text fields and the calendar and
 * chart arrow keys still come first.
 */
class KeyShortcuts {
    private var handler: ((NbKeyIntent) -> Boolean)? = null
    private var rtl: () -> Boolean = { false }

    /** Key code whose key-down a shortcut consumed: its key-up is consumed too (no fallback Back after Esc). */
    private var downConsumed: Int? = null

    /**
     * [superDispatch] first (the normal window dispatch, focused nodes included); a key it leaves unconsumed goes to
     * the registered shortcut handler. True when either consumed it. (The first navigation key after a touch never
     * gets here: the platform spends it on leaving touch mode, in every app.)
     */
    fun dispatch(
        event: KeyEvent,
        superDispatch: (KeyEvent) -> Boolean,
    ): Boolean {
        if (superDispatch(event)) return true
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val intent = nbDecodeKey(event, rtl())
                val handled = intent != null && handler?.invoke(intent) == true
                downConsumed = if (handled) event.keyCode else null
                handled
            }

            KeyEvent.ACTION_UP -> {
                val pair = downConsumed == event.keyCode
                if (pair) downConsumed = null
                pair
            }

            else -> {
                false
            }
        }
    }

    internal fun register(
        value: ((NbKeyIntent) -> Boolean)?,
        rtl: () -> Boolean = { false },
    ) {
        handler = value
        this.rtl = rtl
    }
}

/** The activity's [KeyShortcuts]; null outside an activity that offers it (tests, screenshots). */
val LocalKeyShortcuts = staticCompositionLocalOf<KeyShortcuts?> { null }

/** Registers [handler] (always its latest version) with [LocalKeyShortcuts] while in the composition. */
@Composable
internal fun RegisterKeyShortcuts(handler: (NbKeyIntent) -> Boolean) {
    val shortcuts = LocalKeyShortcuts.current ?: return
    val current by rememberUpdatedState(handler)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    DisposableEffect(shortcuts) {
        shortcuts.register({ current(it) }, { rtl })
        onDispose { shortcuts.register(null) }
    }
}
