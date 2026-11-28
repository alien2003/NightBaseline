package io.github.alien2003.nightbaseline.ui.interaction

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity

/**
 * The app's activity base (ADR 0011 round 6, M5): the window's raw key and touch dispatch lives in the framework, so
 * no screen code overrides it. A key goes to [onWindowKey] with the window's own dispatch as the fallback (the app's
 * shortcut decoder); every touch is handed on unchanged, and its masked action is reported to [onTouchAction]
 * afterwards (the refresh-rate hint).
 */
abstract class NbActivity : ComponentActivity() {
    /** A key of the window; [window] dispatches it the platform's way. True when it was consumed. */
    protected open fun onWindowKey(
        event: KeyEvent,
        window: (KeyEvent) -> Boolean,
    ): Boolean = window(event)

    /** The masked action of a touch the window has just dispatched. */
    protected open fun onTouchAction(action: Int) = Unit

    final override fun dispatchKeyEvent(event: KeyEvent): Boolean = onWindowKey(event) { super.dispatchKeyEvent(it) }

    final override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        onTouchAction(ev.actionMasked)
        return handled
    }
}
