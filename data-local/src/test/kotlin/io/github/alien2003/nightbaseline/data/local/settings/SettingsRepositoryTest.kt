package io.github.alien2003.nightbaseline.data.local.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.alien2003.nightbaseline.algo.AlgoParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsRepositoryTest {
    @TempDir
    lateinit var dir: File

    private fun <T> withRepo(block: suspend (DataStoreSettingsRepository) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
            return runBlocking { block(DataStoreSettingsRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaultsWhenNothingIsStored() =
        withRepo { repo ->
            val s = repo.settings.first()
            assertEquals(AppSettings(), s)
            assertEquals("com.heytap.health.international", s.sourcePackage)
            assertTrue(s.notificationsEnabled)
            assertEquals(SensitivityLevel.STANDARD, s.sensitivity)
            assertEquals(GlassMode.AUTO, s.glassMode)
        }

    @Test
    fun settersAreReadBack() =
        withRepo { repo ->
            repo.setSourcePackage("org.example.source")
            repo.setNotificationsEnabled(false)
            repo.setSensitivity(SensitivityLevel.HIGH)
            repo.setOnboardingDone(true)
            repo.setGlassMode(GlassMode.OFF)

            assertEquals(
                AppSettings("org.example.source", false, SensitivityLevel.HIGH, onboardingDone = true, glassMode = GlassMode.OFF),
                repo.settings.first(),
            )
        }

    @Test
    fun blankSourceIsRejected() =
        withRepo { repo ->
            assertThrows<IllegalArgumentException> { repo.setSourcePackage(" ") }
            Unit
        }

    @Test
    fun sensitivityMapsToTheZThreshold() {
        assertEquals(3.0, AppSettings(sensitivity = SensitivityLevel.LOW).algoParams().zThreshold)
        assertEquals(2.5, AppSettings(sensitivity = SensitivityLevel.STANDARD).algoParams().zThreshold)
        assertEquals(2.0, AppSettings(sensitivity = SensitivityLevel.HIGH).algoParams().zThreshold)
        // The default sensitivity keeps the algorithm defaults, and every level gives valid parameters.
        assertEquals(AlgoParams(), AppSettings().algoParams())
        SensitivityLevel.entries.forEach { assertEquals(emptyList<String>(), AppSettings(sensitivity = it).algoParams().validate()) }
    }
}
