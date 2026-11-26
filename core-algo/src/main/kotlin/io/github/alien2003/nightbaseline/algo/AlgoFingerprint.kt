package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Identifies what produced a stored evaluation: the algorithm's behaviour ([ALGO_VERSION]) and the effective
 * parameters (including the sensitivity's z threshold). A stored night whose fingerprint differs from the current one
 * is re-evaluated from its stored [NightSummary].
 */
object AlgoFingerprint {
    /**
     * Version of the evaluation behaviour. Bump it on every change that can change an evaluation of the same summaries
     * with the same [AlgoParams] (for example a new clamp or a different baseline rule).
     *
     * History: 1 = first release of the algorithm (rows stored before fingerprints existed count as 1);
     * 2 = the lower edge of the sleep-duration range never goes below [AlgoParams.minNightMinutes].
     */
    const val ALGO_VERSION: Int = 2

    private const val HASH_CHARS = 16
    private val json = Json { encodeDefaults = true }

    /** `v{ALGO_VERSION}-{hash of the parameters}`, stable across runs and devices. */
    fun of(params: AlgoParams): String {
        val canonical = json.encodeToString(AlgoParams.serializer(), params)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
        return "v$ALGO_VERSION-${hex.take(HASH_CHARS)}"
    }

    private const val BYTE_MASK = 0xff
}
