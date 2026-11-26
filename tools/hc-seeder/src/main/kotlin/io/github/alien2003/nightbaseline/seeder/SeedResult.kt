package io.github.alien2003.nightbaseline.seeder

/**
 * The one log line per run that tools/emu/seed.sh waits for. [runId] is seed.sh's `--es run_id`: the line ends with
 * ` run=<id>`, so the script can tell this run's result from older lines in the log buffer (their timestamps do not
 * help once the emulator clock has been set back).
 */
object SeedResult {
    /** Start of every result line. */
    const val PREFIX = "SEED_RESULT"

    /**
     * The result line for [text], kept on one line (logcat splits a multi-line message, and the run id must stay on
     * the line seed.sh matches); characters other than letters, digits and `-` are dropped from [runId].
     */
    fun line(
        text: String,
        runId: String?,
    ): String {
        val flat = text.replace(Regex("\\s*[\\r\\n]+\\s*"), " ").trim()
        val id = runId?.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }.orEmpty()
        return if (id.isEmpty()) "$PREFIX $flat" else "$PREFIX $flat run=$id"
    }
}
