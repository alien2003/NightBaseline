package io.github.alien2003.nightbaseline.ui.material

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The fallback rules of `tokens.md` 7.3 in order (first match wins) and the reduced-motion rule of 9.6. */
class EffectsResolverTest {
    private val base = EffectsInputs(sdkInt = 35, totalMemoryBytes = 8L shl 30)

    @Test
    fun `glass when nothing applies`() {
        val d = resolveEffectsMode(base)
        assertEquals(EffectsMode.Glass, d.mode)
        assertEquals(EffectsReason.Automatic, d.reason)
        assertFalse(d.reducedMotion)
    }

    @Test
    fun `each rule turns glass off with its reason`() {
        val cases =
            listOf(
                base.copy(preference = GlassPreference.Off) to EffectsReason.UserOff,
                base.copy(sdkInt = 30) to EffectsReason.Unsupported,
                base.copy(animatorDurationScale = 0f) to EffectsReason.AnimationsOff,
                base.copy(powerSaveMode = true) to EffectsReason.BatterySaver,
                base.copy(contrastLevel = 0.5f) to EffectsReason.Transparency,
                base.copy(reduceTransparency = true) to EffectsReason.Transparency,
                base.copy(lowRamDevice = true) to EffectsReason.WeakDevice,
                base.copy(totalMemoryBytes = 2L shl 30) to EffectsReason.WeakDevice,
                base.copy(measuredJank = true) to EffectsReason.Jank,
                base.copy(thermalStatus = THERMAL_STATUS_SEVERE) to EffectsReason.Thermal,
            )
        cases.forEach { (inputs, reason) ->
            val d = resolveEffectsMode(inputs)
            assertEquals(EffectsMode.Solid, d.mode, "$inputs")
            assertEquals(reason, d.reason, "$inputs")
        }
    }

    @Test
    fun `the first matching rule wins`() {
        val d = resolveEffectsMode(base.copy(sdkInt = 28, powerSaveMode = true, lowRamDevice = true))
        assertEquals(EffectsReason.Unsupported, d.reason)
        assertEquals(EffectsReason.AnimationsOff, resolveEffectsMode(base.copy(animatorDurationScale = 0f, powerSaveMode = true)).reason)
    }

    @Test
    fun `reduced motion follows animations off and battery saver, whatever decides the mode`() {
        assertTrue(resolveEffectsMode(base.copy(animatorDurationScale = 0f)).reducedMotion)
        assertTrue(resolveEffectsMode(base.copy(powerSaveMode = true, preference = GlassPreference.Off)).reducedMotion)
        assertFalse(resolveEffectsMode(base.copy(animatorDurationScale = 0.5f)).reducedMotion)
        assertFalse(resolveEffectsMode(base.copy(sdkInt = 28)).reducedMotion)
    }

    @Test
    fun `mild thermal status keeps glass`() {
        assertEquals(EffectsMode.Glass, resolveEffectsMode(base.copy(thermalStatus = THERMAL_STATUS_SEVERE - 1)).mode)
    }
}
