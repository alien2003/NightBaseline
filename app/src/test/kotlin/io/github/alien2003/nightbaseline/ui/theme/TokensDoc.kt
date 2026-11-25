package io.github.alien2003.nightbaseline.ui.theme

import java.io.File

/**
 * Parser of `docs/design/tokens.md` in the formats of `tools/design/contrast_check.py`: colour tables (header with
 * `token`, `light`, `dark`) and the pairs table (`| pair | fg | bg | theme | min |`). The path comes from
 * app/build.gradle.kts (`-Dnightbaseline.tokens.file`).
 */
object TokensDoc {
    /** One colour value: `#RRGGBB`, `#RRGGBB @ NN%` or an alias `= token`. */
    data class Value(
        val rgb: Int?,
        val alphaPercent: Double,
        val alias: String?,
    )

    private val hex = Regex("""#([0-9A-Fa-f]{6})(?:\s*@\s*(\d+(?:\.\d+)?)\s*%)?""")
    private val alias = Regex("""=\s*([A-Za-z][\w.]*)""")

    private val lines: List<String> by lazy {
        val path = System.getProperty("nightbaseline.tokens.file") ?: "../docs/design/tokens.md"
        File(path).readLines()
    }

    private fun tables(): List<Pair<List<String>, List<List<String>>>> {
        val out = mutableListOf<Pair<List<String>, List<List<String>>>>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].trimStart().startsWith("|")) {
                val block = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) block += lines[i++].trim()
                val cells = block.map(::split)
                if (cells.size >= 2 && cells[1].all { it.isBlank() || it.trim().matches(Regex(":?-{3,}:?")) }) {
                    out += cells[0].map { clean(it).lowercase() } to cells.drop(2)
                }
            } else {
                i++
            }
        }
        return out
    }

    private fun split(row: String): List<String> =
        row
            .trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }

    private fun clean(cell: String) = cell.replace("`", "").trim()

    /** Every colour token: name -> (light, dark). */
    val colors: Map<String, Pair<Value, Value>> by lazy {
        tables()
            .filter { (header, _) -> "token" in header && "light" in header && "dark" in header }
            .flatMap { (header, rows) ->
                val t = header.indexOf("token")
                val l = header.indexOf("light")
                val d = header.indexOf("dark")
                rows.map { clean(it[t]) to (parse(clean(it[l])) to parse(clean(it[d]))) }
            }.toMap()
    }

    /** The pairs table rows. */
    val pairs: List<ContrastPair> by lazy {
        tables()
            .filter { (header, _) -> header == listOf("pair", "fg", "bg", "theme", "min") }
            .flatMap { (_, rows) ->
                rows.map {
                    val theme = ContrastTheme.valueOf(clean(it[3]).replaceFirstChar { c -> c.uppercase() })
                    ContrastPair(clean(it[0]), clean(it[1]), clean(it[2]), theme, clean(it[4]).toDouble())
                }
            }
    }

    private fun parse(raw: String): Value {
        hex.matchEntire(raw)?.let { m ->
            return Value(m.groupValues[1].toInt(16), m.groupValues[2].ifEmpty { "100" }.toDouble(), null)
        }
        alias.matchEntire(raw)?.let { m -> return Value(null, 100.0, m.groupValues[1]) }
        error("bad colour value $raw")
    }

    /** Resolves [name] in [dark] or light to (rgb, alpha percent), following aliases. */
    fun resolve(
        name: String,
        dark: Boolean,
    ): Pair<Int, Double> {
        var current = name
        repeat(MAX_ALIAS_DEPTH) {
            val (light, darkValue) = colors[current] ?: error("unknown token $current")
            val value = if (dark) darkValue else light
            if (value.alias == null) return checkNotNull(value.rgb) to value.alphaPercent
            current = value.alias
        }
        error("alias cycle at $name")
    }

    private const val MAX_ALIAS_DEPTH = 8
}
