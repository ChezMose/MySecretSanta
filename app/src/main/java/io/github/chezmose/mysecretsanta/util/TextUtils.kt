package io.github.chezmose.mysecretsanta.util

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val DIACRITICS = "\\p{Mn}+".toRegex()

/** Clé de recherche : minuscules, sans accents ni espaces autour. */
fun String.searchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.FRENCH)
        .trim()

/** Tri alphabétique français : « Élodie » se range avec les E, sans tenir compte de la casse. */
fun <T> List<T>.sortedByName(name: (T) -> String): List<T> {
    val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }
    return sortedWith { a, b -> collator.compare(name(a), name(b)) }
}

/** « 0 membre », « 1 membre », « 2 membres ». */
fun membersLabel(count: Int): String = if (count <= 1) "$count membre" else "$count membres"
