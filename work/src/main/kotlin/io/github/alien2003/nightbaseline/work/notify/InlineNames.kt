package io.github.alien2003.nightbaseline.work.notify

import java.util.Locale

/**
 * Metric names inside a sentence. The names are standalone labels that start with a capital letter ("Sleeping heart
 * rate"); Russian writes them in lower case after the first word of a sentence, so in a Russian list only the first
 * name keeps its capital and, after a colon ("Higher than usual: ..."), none does. English keeps its title-case names
 * (`states.md` 6.1). Shared by the app's lists and the notification, so both read the same.
 */
object InlineNames {
    /**
     * [names] for a list inside a sentence in [locale]: in Russian every item after the first is lowercased, and the
     * first one too when [inline] (the list follows other words). Only a word that is a capital letter followed by
     * lowercase letters changes, so acronyms and mixed-case ids ("SpO2", "HRV") stay as they are.
     */
    fun of(
        names: List<String>,
        locale: Locale,
        inline: Boolean = false,
    ): List<String> {
        if (locale.language != RUSSIAN) return names
        return names.mapIndexed { i, name -> if (i > 0 || inline) lowerFirst(name, locale) else name }
    }

    private fun lowerFirst(
        name: String,
        locale: Locale,
    ): String {
        val word = name.takeWhile { it.isLetterOrDigit() }
        val capitalised = word.length > 1 && word[0].isUpperCase() && word.drop(1).all { it.isLetter() && it.isLowerCase() }
        return if (capitalised) name.replaceFirstChar { it.lowercase(locale) } else name
    }

    private const val RUSSIAN = "ru"
}
