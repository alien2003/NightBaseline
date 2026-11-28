package io.github.alien2003.nightbaseline.display

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.annotation.RequiresApi

/**
 * Asks for the panel's highest refresh rate while the person is using the activity's window (ADR 0008), through
 * public API only:
 * - API 30+: `WindowManager.LayoutParams.preferredDisplayModeId` = the fastest mode at the current resolution
 *   ([DisplayModes.highestRefreshAt]), held from resume and from every touch, released ([DisplayModes.NO_PREFERENCE])
 *   [idleMillis] after the last touch ends and on pause, so an idle screen goes back to the system's choice. This
 *   window attribute is what the ColorOS refresh-rate manager logs as "Win preferred rateId".
 * - API 35+: in addition the touch boost of the frame-rate category system stays on
 *   (`Window.setFrameRateBoostOnTouchEnabled`); Compose itself votes `FrameRateCategory.High` for every frame in which
 *   something moves, and the shell adds `Modifier.preferredFrameRate` for fades.
 * - API 28–29: nothing (no seamless refresh-rate switching guarantee; the system default stays).
 *
 * Battery saver and the user's refresh-rate setting outrank an app's request in the platform's display-mode
 * director, so they still cap the rate.
 */
class HighRefreshRate(
    private val activity: Activity,
    private val idleMillis: Long = IDLE_MILLIS,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val release = Runnable { apply(DisplayModes.NO_PREFERENCE) }
    private var resumed = false
    private var listener: DisplayManager.DisplayListener? = null

    /** Call from `onResume`: holds the high rate and follows display changes (fold / unfold, resolution). */
    fun onResume() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        resumed = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) Api35.enableTouchBoost(activity)
        if (listener == null) {
            val l = DisplayListener()
            activity.getSystemService(DisplayManager::class.java)?.registerDisplayListener(l, handler)
            listener = l
        }
        hold()
        scheduleRelease()
    }

    /** Call from `onPause`: releases the request and stops following the display. */
    fun onPause() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        resumed = false
        handler.removeCallbacks(release)
        listener?.let { activity.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(it) }
        listener = null
        apply(DisplayModes.NO_PREFERENCE)
    }

    /** Call from `onUserInteraction` (touch down, keys, trackball): holds the rate for another [idleMillis]. */
    fun onUserInteraction() {
        if (!resumed) return
        hold()
        scheduleRelease()
    }

    /** Call after the activity dispatched a touch, with the event's masked action: no release while a finger is down. */
    fun onTouch(actionMasked: Int) {
        if (!resumed) return
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> handler.removeCallbacks(release)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleRelease()
        }
    }

    private fun scheduleRelease() {
        handler.removeCallbacks(release)
        handler.postDelayed(release, idleMillis)
    }

    private fun hold() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) apply(Api30.preferredModeId(activity))
    }

    /** Writes the window attribute only when it changes (each write relayouts the window). */
    private fun apply(modeId: Int) {
        val window = activity.window ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == modeId) return
        attrs.preferredDisplayModeId = modeId
        window.attributes = attrs
    }

    private inner class DisplayListener : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            // Our own mode switch lands here too; the selection is stable at the same resolution, so no write follows.
            if (resumed && activity.window.attributes.preferredDisplayModeId != DisplayModes.NO_PREFERENCE) hold()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private object Api30 {
        fun preferredModeId(activity: Activity): Int {
            val display = activity.display ?: return DisplayModes.NO_PREFERENCE
            val current = display.mode
            val modes =
                display.supportedModes.map { ModeSpec(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
            return DisplayModes.highestRefreshAt(modes, current.modeId, current.physicalWidth, current.physicalHeight)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private object Api35 {
        fun enableTouchBoost(activity: Activity) {
            activity.window?.setFrameRateBoostOnTouchEnabled(true)
        }
    }

    private companion object {
        /** Longer than a fling or a page transition after the finger lifts. */
        const val IDLE_MILLIS = 5_000L
    }
}
