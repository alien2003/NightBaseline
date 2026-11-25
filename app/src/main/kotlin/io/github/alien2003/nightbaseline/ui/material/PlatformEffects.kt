package io.github.alien2003.nightbaseline.ui.material

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Reads the inputs of [resolveEffectsMode] from the platform and keeps them current: the animator duration scale
 * (ContentObserver), battery saver (`ACTION_POWER_SAVE_MODE_CHANGED`), the contrast level (API 34+ listener), the
 * thermal status (API 29+ listener), and the low-RAM and memory facts (read once). [preference] and [measuredJank]
 * come from the app's settings.
 */
@Composable
fun rememberPlatformEffectsInputs(
    preference: GlassPreference = GlassPreference.Automatic,
    measuredJank: Boolean = false,
): EffectsInputs {
    val context = LocalContext.current.applicationContext
    val static = remember(context) { staticInputs(context) }
    var dynamic by remember(context) { mutableStateOf(dynamicInputs(context)) }

    DisposableEffect(context) {
        val refresh = { dynamic = dynamicInputs(context) }
        val handler = Handler(Looper.getMainLooper())
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = refresh()
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val power = context.getSystemService(PowerManager::class.java)
        val thermalListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
                PowerManager.OnThermalStatusChangedListener { refresh() }.also { power.addThermalStatusListener(it) }
            } else {
                null
            }
        val uiMode = context.getSystemService(UiModeManager::class.java)
        val contrastListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && uiMode != null) {
                UiModeManager.ContrastChangeListener { refresh() }.also { uiMode.addContrastChangeListener(context.mainExecutor, it) }
            } else {
                null
            }
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
                power?.removeThermalStatusListener(thermalListener)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && contrastListener != null) {
                uiMode?.removeContrastChangeListener(contrastListener)
            }
        }
    }
    return dynamic.copy(
        preference = preference,
        measuredJank = measuredJank,
        lowRamDevice = static.lowRamDevice,
        totalMemoryBytes = static.totalMemoryBytes,
    )
}

private fun staticInputs(context: Context): EffectsInputs {
    val activity = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
    return EffectsInputs(
        lowRamDevice = activity?.isLowRamDevice ?: false,
        totalMemoryBytes = memory.totalMem.takeIf { it > 0 },
    )
}

private fun dynamicInputs(context: Context): EffectsInputs {
    val power = context.getSystemService(PowerManager::class.java)
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val contrast =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f
        } else {
            0f
        }
    val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus ?: 0 else 0
    return EffectsInputs(
        sdkInt = Build.VERSION.SDK_INT,
        animatorDurationScale = scale,
        powerSaveMode = power?.isPowerSaveMode ?: false,
        contrastLevel = contrast,
        thermalStatus = thermal,
    )
}
