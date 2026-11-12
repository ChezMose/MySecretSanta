package io.github.chezmose.mysecretsanta.message

import io.github.chezmose.mysecretsanta.util.formatLongDate
import java.time.LocalDate

/** Modèle du message envoyé à chaque participant (SPEC.md §5.7). */
object MessageTemplate {

    const val GIVER = "{donneur}"
    const val RECEIVER = "{receveur}"
    const val DRAW = "{tirage}"
    const val BUDGET = "{budget}"
    const val DATE = "{date}"

    val VARIABLES = listOf(GIVER, RECEIVER, DRAW, BUDGET, DATE)

    const val DEFAULT =
        "🎅 Bonjour {donneur} ! Pour « {tirage} », tu offres un cadeau à {receveur}. " +
            "Budget : {budget}. Rendez-vous le {date}. Chut, c'est secret !"

    /** Fin de phrase : ponctuation finale suivie d'un espace. */
    private val SENTENCE_BREAK = Regex("(?<=[.!?…])\\s+")

    /** Message d'erreur si le modèle est inutilisable, sinon `null`. */
    fun validate(template: String): String? =
        when {
            template.isBlank() -> "Le message est obligatoire."
            RECEIVER !in template -> "Le message doit contenir $RECEIVER : sinon, personne ne saura à qui offrir."
            else -> null
        }

    /**
     * Remplace les variables. Une phrase qui contient une variable vide (budget ou date absents)
     * est retirée entièrement. Les retours à la ligne du modèle sont conservés.
     */
    fun render(
        template: String,
        giver: String,
        receiver: String,
        drawName: String,
        budget: String?,
        date: LocalDate?,
    ): String {
        val values = mapOf(
            GIVER to giver.trim(),
            RECEIVER to receiver.trim(),
            DRAW to drawName.trim(),
            BUDGET to budget?.trim().orEmpty(),
            DATE to date?.let { formatLongDate(it) }.orEmpty(),
        )
        val empty = values.filterValues { it.isEmpty() }.keys
        return template.lines().joinToString("\n") { line ->
            line.split(SENTENCE_BREAK)
                .filter { sentence -> empty.none { it in sentence } }
                .joinToString(" ") { sentence ->
                    values.entries.fold(sentence) { text, (variable, value) -> text.replace(variable, value) }
                }
        }.trim()
    }
}
