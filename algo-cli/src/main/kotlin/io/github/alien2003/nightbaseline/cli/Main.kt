package io.github.alien2003.nightbaseline.cli

import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.MetricStatus
import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.NightSummary
import io.github.alien2003.nightbaseline.algo.Pipeline
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """algo-cli — offline runs of the NightBaseline algorithm

  algo-cli origins  --dataset <dir>
      sleep-session counts per origin package
  algo-cli run      --dataset <dir> --origin <package> [--params <json>] [--details <file.jsonl>]
      evaluates every main night of the origin; prints AGGREGATES ONLY (no values);
      --details writes per-night results (with values) — keep it under the git-ignored .data/
  algo-cli synthetic [--seed N] [--nights N] [--params <json>]
      the same run on synthetic nights (with one 3-night episode)

Exit codes: 0 ok, 64 usage, 65 invalid input/params, 66 missing input."""

private const val EXIT_USAGE = 64
private const val EXIT_INVALID_INPUT = 65
private const val EXIT_MISSING_INPUT = 66

private const val DEFAULT_SYNTHETIC_NIGHTS = 60
private const val SYNTHETIC_EPISODE_START = 40
private const val SYNTHETIC_EPISODE_NIGHTS = 3

private const val STATUS_COLUMN_WIDTH = 16
private const val METRIC_COLUMN_WIDTH = 18

private val json =
    Json {
        prettyPrint = false
        encodeDefaults = true
    }

fun main(args: Array<String>) {
    val cmd = args.firstOrNull() ?: usage()
    val opts = args.drop(1).chunked(2).associate { if (it.size == 2 && it[0].startsWith("--")) it[0] to it[1] else usage() }
    val params = opts["--params"]?.let { loadParams(File(it)) } ?: AlgoParams()
    params.validate().takeIf { it.isNotEmpty() }?.let { fail(EXIT_INVALID_INPUT, "invalid params: ${it.joinToString("; ")}") }
    when (cmd) {
        "origins" -> origins(opts)
        "run" -> runDataset(opts, params)
        "synthetic" -> runSynthetic(opts, params)
        else -> usage()
    }
}

private fun origins(opts: Map<String, String>) {
    val dir = datasetDir(opts)
    DatasetReader.origins(dir).toSortedMap().forEach { (o, n) -> println("$o\t$n") }
}

private fun runDataset(
    opts: Map<String, String>,
    params: AlgoParams,
) {
    val dir = datasetDir(opts)
    val origin = opts["--origin"] ?: usage()
    val data = DatasetReader.read(dir, origin)
    if (data.sessions.isEmpty()) fail(EXIT_MISSING_INPUT, "no sleep sessions for $origin")
    report(origin, Pipeline.run(data.sessions, data.channels, params), opts["--details"]?.let(::File))
}

private fun runSynthetic(
    opts: Map<String, String>,
    params: AlgoParams,
) {
    val gen =
        SyntheticNights(
            SyntheticNights.Config(
                seed = opts["--seed"]?.toLong() ?: 1,
                nights = opts["--nights"]?.toInt() ?: DEFAULT_SYNTHETIC_NIGHTS,
                episodes = mapOf(SYNTHETIC_EPISODE_START to SYNTHETIC_EPISODE_NIGHTS),
            ),
        )
    val (s, c) = gen.flatten(gen.generate())
    report("synthetic", Pipeline.run(s, c, params), opts["--details"]?.let(::File))
}

private fun report(
    label: String,
    results: List<Pair<io.github.alien2003.nightbaseline.algo.NightSummary, io.github.alien2003.nightbaseline.algo.NightEvaluation>>,
    details: File?,
) {
    val evals = results.map { it.second }
    println("source: $label")
    println("main nights: ${evals.size}" + if (evals.isNotEmpty()) " (${evals.first().date} .. ${evals.last().date})" else "")
    NightStatus.entries.forEach { s -> println("  ${s.name.padEnd(STATUS_COLUMN_WIDTH)} ${evals.count { it.status == s }}") }
    val evaluated = evals.filter { it.status in setOf(NightStatus.ALL_WITHIN, NightStatus.SOME_DEVIATIONS, NightStatus.DEVIATIONS) }
    println("notifications: ${evals.count { it.notify }} of ${evaluated.size} evaluated nights")
    val metricIds = evals.flatMap { e -> e.metrics.map { it.metric } }.distinct()
    println("per metric (evaluated nights: within / above / below / missing / learning; counted deviations):")
    metricIds.forEach { id ->
        val ms = evaluated.mapNotNull { e -> e.metrics.firstOrNull { it.metric == id } }

        fun c(st: MetricStatus) = ms.count { it.status == st }
        println(
            "  ${id.key.padEnd(METRIC_COLUMN_WIDTH)} ${c(MetricStatus.WITHIN)} / ${c(MetricStatus.ABOVE)} / ${c(MetricStatus.BELOW)} / " +
                "${c(MetricStatus.MISSING)} / ${c(MetricStatus.LEARNING)}; ${ms.count { it.deviates }}",
        )
    }
    val missing =
        evals
            .flatMap { it.metrics }
            .mapNotNull { it.missing }
            .groupingBy { it }
            .eachCount()
    println("missing reasons: $missing")
    details?.let { f ->
        f.parentFile?.mkdirs()
        f.printWriter().use { w ->
            results.forEach { (s, e) ->
                val obj =
                    kotlinx.serialization.json.JsonObject(
                        mapOf(
                            "summary" to json.encodeToJsonElement(NightSummary.serializer(), s),
                            "evaluation" to json.encodeToJsonElement(NightEvaluation.serializer(), e),
                        ),
                    )
                w.println(
                    json.encodeToString(
                        kotlinx.serialization.json.JsonObject
                            .serializer(),
                        obj,
                    ),
                )
            }
        }
        println("details written to ${f.path}")
    }
}

private fun datasetDir(opts: Map<String, String>): File {
    val dir = File(opts["--dataset"] ?: usage())
    if (!dir.isDirectory) fail(EXIT_MISSING_INPUT, "dataset directory not found: $dir")
    return dir
}

private fun loadParams(f: File): AlgoParams {
    if (!f.exists()) fail(EXIT_MISSING_INPUT, "params file not found: $f")
    return runCatching { Json { ignoreUnknownKeys = false }.decodeFromString<AlgoParams>(f.readText()) }
        .getOrElse { fail(EXIT_INVALID_INPUT, "cannot parse params: ${it.message}") }
}

private fun usage(): Nothing = fail(EXIT_USAGE, USAGE)

private fun fail(
    code: Int,
    msg: String,
): Nothing {
    System.err.println(msg)
    exitProcess(code)
}
