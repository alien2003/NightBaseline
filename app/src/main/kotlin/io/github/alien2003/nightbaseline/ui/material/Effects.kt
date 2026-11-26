package io.github.alien2003.nightbaseline.ui.material

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.alien2003.nightbaseline.R

/** The two effect modes of `tokens.md` 0 and 7: glass shell, or the finished solid fallback. Geometry is identical. */
enum class EffectsMode {
    /** Backdrop blur with tint and rim on the shell (bar, rail, top bar, sheets, dialogs). */
    Glass,

    /** Solid fills with the same rim, shapes and shadows. */
    Solid,
}

/** The person's choice in Settings > Appearance > Glass effects. */
enum class GlassPreference {
    /** Glass unless a rule of `tokens.md` 7.3 turns it off. */
    Automatic,

    /** Always solid. */
    Off,
}

/**
 * Why the resolver chose its mode (`tokens.md` 7.3, first matching rule wins). [summary] is the Settings summary,
 * null where the setting shows its own value ("Automatic" / "Off").
 */
enum class EffectsReason(
    @param:StringRes val summary: Int?,
) {
    /** Rule 1: the person turned glass effects off. */
    UserOff(null),

    /** Rule 2: API < 31 has no RenderEffect blur. */
    Unsupported(R.string.glass_off_unsupported),

    /** Rule 3: animations removed (animator duration scale 0). */
    AnimationsOff(R.string.glass_off_animations),

    /** Rule 4: battery saver. */
    BatterySaver(R.string.glass_off_battery),

    /** Rule 5: higher contrast or reduced transparency requested. */
    Transparency(R.string.glass_off_transparency),

    /** Rule 6: low-RAM device or less than 3 GiB of memory. */
    WeakDevice(R.string.glass_off_device),

    /** Rule 7: measured jank with glass visible. */
    Jank(R.string.glass_off_device),

    /** Rule 8: the device is hot (temporary). */
    Thermal(R.string.glass_off_device),

    /** No rule matched: glass. */
    Automatic(null),
}

/**
 * Everything [resolveEffectsMode] looks at, read from the platform by [rememberPlatformEffectsInputs] or constructed
 * directly by tests and the component catalog.
 */
@Immutable
data class EffectsInputs(
    /** Settings > Appearance > Glass effects. */
    val preference: GlassPreference = GlassPreference.Automatic,
    /** `Build.VERSION.SDK_INT`. */
    val sdkInt: Int = GLASS_MIN_SDK,
    /** `Settings.Global.ANIMATOR_DURATION_SCALE` (1 by default, 0 when animations are removed). */
    val animatorDurationScale: Float = 1f,
    /** `PowerManager.isPowerSaveMode`. */
    val powerSaveMode: Boolean = false,
    /** `UiModeManager.getContrast()` on API 34+ (0 = standard), 0 below. */
    val contrastLevel: Float = 0f,
    /** A platform "reduce transparency" request where one is exposed through public API. */
    val reduceTransparency: Boolean = false,
    /** `ActivityManager.isLowRamDevice()`. */
    val lowRamDevice: Boolean = false,
    /** `ActivityManager.MemoryInfo.totalMem` in bytes; null when unknown. */
    val totalMemoryBytes: Long? = null,
    /** Stored result of the jank measurement (`tokens.md` 7.3 rule 7). */
    val measuredJank: Boolean = false,
    /** `PowerManager.getCurrentThermalStatus()` (0 = none) on API 29+. */
    val thermalStatus: Int = 0,
)

/** Rule 6 of `tokens.md` 7.3: a low-RAM device or less than 3 GiB of memory. */
private val EffectsInputs.weakDevice: Boolean
    get() = lowRamDevice || (totalMemoryBytes ?: Long.MAX_VALUE) < WEAK_DEVICE_MEMORY_BYTES

/** The resolved effects: the mode, the reason, and whether the reduced-motion variants apply (`tokens.md` 9.6). */
@Immutable
data class EffectsDecision(
    /** Glass or solid. */
    val mode: EffectsMode,
    /** The rule that decided it. */
    val reason: EffectsReason,
    /** Reduced motion: animations removed or battery saver on. */
    val reducedMotion: Boolean,
    /**
     * The reason the Automatic setting would give right now, whatever is selected: Settings describes the Automatic
     * row with it ("Not available on this Android version" even while Off is selected), never with [reason].
     */
    val automaticReason: EffectsReason = reason,
) {
    /** Shortcut for [mode] == [EffectsMode.Glass]. */
    val glass: Boolean get() = mode == EffectsMode.Glass

    /** Forced decisions for previews, the component catalog and screenshot tests. */
    companion object {
        /** Glass, normal motion. */
        val ForcedGlass: EffectsDecision = EffectsDecision(EffectsMode.Glass, EffectsReason.Automatic, reducedMotion = false)

        /** Solid fallback, normal motion. */
        val ForcedSolid: EffectsDecision =
            EffectsDecision(EffectsMode.Solid, EffectsReason.UserOff, reducedMotion = false, automaticReason = EffectsReason.Automatic)
    }
}

/** First API level with `RenderEffect` blur. */
const val GLASS_MIN_SDK: Int = 31

/** Below this much memory the device counts as weak (`tokens.md` 7.3 rule 6). */
const val WEAK_DEVICE_MEMORY_BYTES: Long = 3L * 1024 * 1024 * 1024

/** `PowerManager.THERMAL_STATUS_SEVERE`. */
const val THERMAL_STATUS_SEVERE: Int = 3

/**
 * The fallback resolver of `tokens.md` 7.3: the first matching rule wins and becomes the reason. Pure, so every rule
 * is unit-tested; the platform observation lives in [rememberPlatformEffectsInputs].
 */
fun resolveEffectsMode(inputs: EffectsInputs): EffectsDecision {
    val animationsOff = inputs.animatorDurationScale == 0f
    val reducedMotion = animationsOff || inputs.powerSaveMode
    val automatic =
        when {
            inputs.sdkInt < GLASS_MIN_SDK -> EffectsReason.Unsupported
            animationsOff -> EffectsReason.AnimationsOff
            inputs.powerSaveMode -> EffectsReason.BatterySaver
            inputs.contrastLevel > 0f || inputs.reduceTransparency -> EffectsReason.Transparency
            inputs.weakDevice -> EffectsReason.WeakDevice
            inputs.measuredJank -> EffectsReason.Jank
            inputs.thermalStatus >= THERMAL_STATUS_SEVERE -> EffectsReason.Thermal
            else -> EffectsReason.Automatic
        }
    val reason = if (inputs.preference == GlassPreference.Off) EffectsReason.UserOff else automatic
    val mode = if (reason == EffectsReason.Automatic) EffectsMode.Glass else EffectsMode.Solid
    return EffectsDecision(mode, reason, reducedMotion, automaticReason = automatic)
}

/**
 * The effects decision in effect below the theme root. `NightBaselineTheme` provides the platform-resolved value;
 * screenshot tests and the component catalog provide [EffectsDecision.ForcedGlass] or [EffectsDecision.ForcedSolid].
 */
val LocalEffects = staticCompositionLocalOf { EffectsDecision.ForcedSolid }
