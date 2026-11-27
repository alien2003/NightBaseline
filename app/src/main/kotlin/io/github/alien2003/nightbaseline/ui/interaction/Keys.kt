package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * What a key means in the app (ADR 0011, `layouts.md` 10.2). Keys are decoded in one place ([nbDecodeKey]) into these
 * intents, with the reading direction applied once, so a screen never sees a raw key: it can only answer an intent,
 * and a navigation intent ([Back], [Section], a pager [Move] / [Page]) goes through the same hosts as a tap.
 */
@Immutable
sealed interface NbKeyIntent {
    /** Esc, Alt+Left (Alt+Right in RTL): the router's Back. */
    data object Back : NbKeyIntent

    /** Ctrl+1..9: the section at [index] (0-based). */
    data class Section(
        val index: Int,
    ) : NbKeyIntent

    /** Ctrl+letter: an app command ([key] upper case, `R` = check now). */
    data class Command(
        val key: Char,
    ) : NbKeyIntent

    /** An arrow: [dx] +1 forward in the reading direction (Right in LTR, Left in RTL), [dy] +1 down. */
    data class Move(
        val dx: Int,
        val dy: Int,
    ) : NbKeyIntent

    /** Page Down (+1) / Page Up (-1). */
    data class Page(
        val delta: Int,
    ) : NbKeyIntent

    /** Home ([end] false) / End ([end] true). */
    data class Edge(
        val end: Boolean,
    ) : NbKeyIntent
}

/** The one key decoder of the app: a key-down to its [NbKeyIntent], or null when the key means nothing to the app. */
@Suppress("CyclomaticComplexMethod") // One table: every key the app answers.
fun nbDecodeKey(
    event: KeyEvent,
    rtl: Boolean,
): NbKeyIntent? {
    if (event.type != KeyEventType.KeyDown) return null
    val forward = if (rtl) -1 else 1
    val key = event.key
    val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isShiftPressed
    return when {
        key == Key.Escape -> NbKeyIntent.Back
        event.isAltPressed && key == (if (rtl) Key.DirectionRight else Key.DirectionLeft) -> NbKeyIntent.Back
        event.isCtrlPressed && key in DIGITS -> NbKeyIntent.Section(DIGITS.indexOf(key))
        event.isCtrlPressed && key in LETTERS -> NbKeyIntent.Command('A' + LETTERS.indexOf(key))
        !plain -> null
        key == Key.DirectionRight -> NbKeyIntent.Move(forward, 0)
        key == Key.DirectionLeft -> NbKeyIntent.Move(-forward, 0)
        key == Key.DirectionDown -> NbKeyIntent.Move(0, 1)
        key == Key.DirectionUp -> NbKeyIntent.Move(0, -1)
        key == Key.PageDown -> NbKeyIntent.Page(1)
        key == Key.PageUp -> NbKeyIntent.Page(-1)
        key == Key.MoveHome -> NbKeyIntent.Edge(end = false)
        key == Key.MoveEnd -> NbKeyIntent.Edge(end = true)
        else -> null
    }
}

/** [nbDecodeKey] for a platform key event (the keys the window left unconsumed, offered by the activity). */
fun nbDecodeKey(
    event: android.view.KeyEvent,
    rtl: Boolean,
): NbKeyIntent? = nbDecodeKey(KeyEvent(event), rtl)

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

private val LETTERS =
    listOf(
        Key.A,
        Key.B,
        Key.C,
        Key.D,
        Key.E,
        Key.F,
        Key.G,
        Key.H,
        Key.I,
        Key.J,
        Key.K,
        Key.L,
        Key.M,
        Key.N,
        Key.O,
        Key.P,
        Key.Q,
        Key.R,
        Key.S,
        Key.T,
        Key.U,
        Key.V,
        Key.W,
        Key.X,
        Key.Y,
        Key.Z,
    )

/**
 * Key intents on the focused path (the keys reach the element while focus is inside it; before the focused element
 * itself). [onIntent] returns true when it used the intent. The only key handler of the app outside the framework.
 */
@Composable
fun Modifier.nbKeyIntents(onIntent: (NbKeyIntent) -> Boolean): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current by rememberUpdatedState(onIntent)
    return onPreviewKeyEvent { event -> nbDecodeKey(event, rtl)?.let { current(it) } == true }
}

/**
 * A sequence a key can step (the night pager, the month): registered with the app's router while its branch is
 * shown, so the arrows and Page Up / Down step it even when nothing is focused (after a touch). [previous] / [next]
 * are null when there is no neighbour on that side.
 */
@Composable
fun NbStepKeys(
    previous: (() -> Unit)?,
    next: (() -> Unit)?,
) {
    val router = LocalNbBackRouter.current ?: return
    val scope = LocalNbBackScope.current
    val prev by rememberUpdatedState(previous)
    val nxt by rememberUpdatedState(next)
    val target =
        remember(scope) {
            NbStepTarget(scope) { delta ->
                (if (delta < 0) prev else nxt)?.let {
                    it()
                    true
                } == true
            }
        }
    androidx.compose.runtime.DisposableEffect(router, target) {
        router.addStep(target)
        onDispose { router.removeStep(target) }
    }
}

/** A registered step target: [step] (-1 previous, +1 next) returns true when it stepped. */
internal class NbStepTarget(
    private val scope: NbBackScope,
    val step: (Int) -> Boolean,
) {
    val depth: Int get() = scope.depth

    fun enabled(): Boolean = scope.active()
}

/** The step a pager intent means: Left/Right in the reading direction, Page Up / Down; null for any other intent. */
fun NbKeyIntent.stepDelta(): Int? =
    when (this) {
        is NbKeyIntent.Move -> dx.takeIf { dy == 0 && it != 0 }
        is NbKeyIntent.Page -> delta
        else -> null
    }
